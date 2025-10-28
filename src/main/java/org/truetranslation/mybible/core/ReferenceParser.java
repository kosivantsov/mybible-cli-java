package org.truetranslation.mybible.core;

import org.truetranslation.mybible.core.model.Book;
import org.truetranslation.mybible.core.model.Reference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ReferenceParser {

    public static class Range {
        public final Reference start;
        public final Reference end;
        public Range(Reference start, Reference end) { this.start = start; this.end = end; }
        @Override
        public String toString() { return String.format("%s %d:%d", start.getBookName(), start.getChapter(), start.getVerse()); }
    }

    public static class RangeWithCount extends Range {
        public final int verseCount;
        public final int bookStartOffset;

        public RangeWithCount(Reference start, Reference end, int verseCount, int bookStartOffset) {
            super(start, end);
            this.verseCount = verseCount;
            this.bookStartOffset = bookStartOffset;
        }
        @Override
        public String toString() {
            return String.format("Range[Start=%s, End=%s, Verses=%d, Offset=%d]", start, end, verseCount, bookStartOffset);
        }
    }

    private static class ParseState {
        Integer book;
        Integer chapter;
        String bookString;
        boolean wasVerse;
    }

    private final BookMapper bookMapper;
    private final Map<Integer, Integer> verseIndex;
    private final LocalizationManager loc = LocalizationManager.getInstance();

    public ReferenceParser(BookMapper bookMapper, Map<Integer, Integer> verseIndex) {
        this.bookMapper = bookMapper;
        this.verseIndex = verseIndex;
    }

    public List<RangeWithCount> parseWithCounts(String rawReference) {
        List<Range> simpleRanges = parseInternal(rawReference);
        if (simpleRanges.isEmpty() && !rawReference.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return simpleRanges.stream()
                .map(range -> new RangeWithCount(
                        range.start,
                        range.end,
                        countVersesInRange(range),
                        calculateBookStartOffset(range.start)
                ))
                .collect(Collectors.toList());
    }
    
    private List<Range> parseInternal(String rawReference) {
        List<Range> finalRanges = new ArrayList<>();
        ParseState state = new ParseState();
        for (String part : rawReference.split("[,;]")) {
            if (part.trim().isEmpty()) continue;
            String[] rangeParts = part.trim().split("-");
            Reference startRef = parseSubPart(rangeParts[0], state);
            if (startRef == null) return new ArrayList<>();
            ParseState startState = new ParseState();
            startState.book = state.book;
            startState.bookString = state.bookString;

            Reference endRef;
            if (rangeParts.length > 1) {
                endRef = parseSubPart(rangeParts[1], state);
                if (endRef == null) return new ArrayList<>();

                if (!state.wasVerse) {
                    int endChapterKey = endRef.getBook() * 1000 + endRef.getChapter();
                    int lastVerseOfEndChapter = verseIndex.getOrDefault(endChapterKey, 1);
                    endRef = new Reference(endRef.getBook(), endRef.getChapter(), lastVerseOfEndChapter, endRef.getBookName());
                }
            } else {
                if (isFullBookReference(rangeParts[0].trim())) {
                     endRef = findLastVerseOfBook(startRef.getBook(), state.bookString);
                } else if (!state.wasVerse) {
                    int compositeKey = startRef.getBook() * 1000 + startRef.getChapter();
                    int lastVerse = verseIndex.getOrDefault(compositeKey, 1);
                    endRef = new Reference(startRef.getBook(), startRef.getChapter(), lastVerse, state.bookString);
                } else {
                    endRef = startRef;
                }
            }

            if (compareReferences(startRef, endRef) > 0) {
                System.err.println(loc.getString("parse.error.range.startAfterEnd", formatRef(startRef, startState), formatRef(endRef, state)));
                return new ArrayList<>();
            }
            finalRanges.add(new Range(startRef, endRef));
        }
        return finalRanges;
    }

    private int calculateBookStartOffset(Reference ref) {
        int bookNum = ref.getBook();
        int targetChapter = ref.getChapter();
        int targetVerse = ref.getVerse();
        int offset = 0;
        
        for (int ch = 1; ch < targetChapter; ch++) {
            int chapterKey = bookNum * 1000 + ch;
            offset += verseIndex.getOrDefault(chapterKey, 0);
        }
        offset += targetVerse;
        return offset;
    }

    private int countVersesInRange(Range range) {
        int startKey = range.start.getBook() * 1000 + range.start.getChapter();
        int endKey = range.end.getBook() * 1000 + range.end.getChapter();
        SortedMap<Integer, Integer> relevantChapters = new TreeMap<>(verseIndex).subMap(startKey, true, endKey, true);
        if (relevantChapters.isEmpty()) return 0;
        if (startKey == endKey) return range.end.getVerse() - range.start.getVerse() + 1;
        int totalVerses = 0;
        boolean firstChapter = true;
        for (Map.Entry<Integer, Integer> chapterEntry : relevantChapters.entrySet()) {
            int currentKey = chapterEntry.getKey();
            int versesInChapter = chapterEntry.getValue();
            if (firstChapter) {
                totalVerses += versesInChapter - range.start.getVerse() + 1;
                firstChapter = false;
            } else if (currentKey == endKey) {
                totalVerses += range.end.getVerse();
            } else {
                totalVerses += versesInChapter;
            }
        }
        return totalVerses;
    }
    
    private boolean bookExistsInModule(int bookNum) {
        int bookPrefix = bookNum * 1000;
        return verseIndex.keySet().stream().anyMatch(k -> k >= bookPrefix && k < (bookPrefix + 1000));
    }
    
    private boolean isFullBookReference(String part) { return !part.matches(".*\\d.*"); }

    private Reference findLastVerseOfBook(int bookNum, String bookName) {
        int bookPrefix = bookNum * 1000;
        int maxKey = verseIndex.keySet().stream().filter(k -> k >= bookPrefix && k < (bookPrefix + 1000)).max(Integer::compare).orElse(bookPrefix + 1);
        int lastChapter = maxKey % 1000;
        int lastVerse = verseIndex.getOrDefault(maxKey, 1);
        return new Reference(bookNum, lastChapter, lastVerse, bookName);
    }
    
    private Reference parseSubPart(String part, ParseState state) {
        try {
            String trimmedPart = part.trim();
            String[] tokens = trimmedPart.split("\\s+");
            String bookString = null;
            Integer bookNum = null;
            int bookTokensCount = 0;
            for (int i = tokens.length; i > 0; i--) {
                String name = String.join(" ", Arrays.copyOfRange(tokens, 0, i));
                Optional<Book> optBook = bookMapper.getBook(name);
                if (optBook.isPresent()) {
                    Book book = optBook.get();
                    bookNum = book.getBookNumber();
                    bookString = name;
                    bookTokensCount = i;
                    break;
                }
            }
            boolean bookWasExplicitlyFound = bookNum != null;
            if (bookWasExplicitlyFound) {
                state.bookString = bookString;
                state.book = bookNum;
            } else { bookNum = state.book; }
            if (bookNum == null || !bookExistsInModule(bookNum)) {
                 System.err.println(loc.getString("parse.error.bookNotFound", tokens[0]));
                 return null;
            }
            String[] remainingTokens = Arrays.copyOfRange(tokens, bookTokensCount, tokens.length);
            if (remainingTokens.length > 1) {
                System.err.println(loc.getString("parse.error.extraTokens", String.join(" ", remainingTokens)));
                return null;
            }
            if (remainingTokens.length == 0) {
                state.wasVerse = false;
                state.chapter = 1;
                return new Reference(state.book, 1, 1, state.bookString);
            } else if (remainingTokens[0].contains(":")) {
                String[] cv = remainingTokens[0].split(":");
                int chapter = Integer.parseInt(cv[0]);
                int verse = Integer.parseInt(cv[1]);
                int compositeKey = bookNum * 1000 + chapter;
                if (!verseIndex.containsKey(compositeKey)) {
                    System.err.println(loc.getString("parse.error.chapterNotFound", chapter, state.bookString));
                    return null;
                }
                if (verse > verseIndex.get(compositeKey) || verse < 1) {
                    System.err.println(loc.getString("parse.error.verseNotFound", verse, chapter, state.bookString, verseIndex.get(compositeKey)));
                    return null;
                }
                state.chapter = chapter;
                state.wasVerse = true;
                return new Reference(bookNum, state.chapter, verse, state.bookString);
            } else {
                int number = Integer.parseInt(remainingTokens[0]);
                if (state.wasVerse && !bookWasExplicitlyFound) {
                     state.wasVerse = true;
                    return new Reference(bookNum, state.chapter, number, state.bookString);
                } else {
                     if (!verseIndex.containsKey(bookNum * 1000 + number)) {
                        System.err.println(loc.getString("parse.error.chapterNotFound", number, state.bookString));
                        return null;
                    }
                    state.chapter = number;
                    state.wasVerse = false;
                    return new Reference(bookNum, state.chapter, 1, state.bookString);
                }
            }
        } catch (NumberFormatException e) {
            System.err.println(loc.getString("parse.error.invalidFormat", part));
            return null;
        }
    }
    
    private int compareReferences(Reference a, Reference b) {
        int bookCompare = Integer.compare(a.getBook(), b.getBook());
        if (bookCompare != 0) return bookCompare;
        int chapterCompare = Integer.compare(a.getChapter(), b.getChapter());
        if (chapterCompare != 0) return chapterCompare;
        return Integer.compare(a.getVerse(), b.getVerse());
    }

    private String formatRef(Reference ref, ParseState state) {
        String bookName = state.bookString != null ? state.bookString : bookMapper.getBook(ref.getBook()).map(Book::getShortName).orElse("Book");
        return String.format("'%s %d:%d'", bookName, ref.getChapter(), ref.getVerse());
    }
}
