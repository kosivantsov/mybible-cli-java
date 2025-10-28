package org.truetranslation.mybible.core;

import org.truetranslation.mybible.core.model.Reference;
import org.truetranslation.mybible.core.model.Verse;
import org.truetranslation.mybible.core.ReferenceParser.Range;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class VerseFetcher {

    private Connection connection;

    public VerseFetcher(Path modulePath) throws SQLException {
        String url = "jdbc:sqlite:" + modulePath.toAbsolutePath();
        this.connection = DriverManager.getConnection(url);
    }
    
    public List<Verse> fetch(List<Range> ranges) throws SQLException {
        List<Verse> results = new ArrayList<>();
        for (Range range : ranges) {
            results.addAll(fetchVersesForRange(range));
        }
        return results;
    }

    private List<Verse> fetchVersesForRange(Range range) throws SQLException {
        Reference start = range.start;
        Reference end = range.end;
        List<Verse> results = new ArrayList<>();

        if (start.getBook() == end.getBook()) {
            return queryVerses(start.getBook(), start.getChapter(), start.getVerse(), end.getChapter(), end.getVerse());
        }

        results.addAll(queryVerses(start.getBook(), start.getChapter(), start.getVerse(), null, null));
        for (int bookNum = start.getBook() + 10; bookNum < end.getBook(); bookNum += 10) {
            results.addAll(queryVerses(bookNum, null, null, null, null));
        }
        results.addAll(queryVerses(end.getBook(), 1, 1, end.getChapter(), end.getVerse()));

        return results;
    }

    private List<Verse> queryVerses(int book, Integer startChapter, Integer startVerse, Integer endChapter, Integer endVerse) throws SQLException {
        List<Verse> verses = new ArrayList<>();
        // This query correctly handles precise start and end boundaries.
        String sql = "SELECT book_number, chapter, verse, text FROM verses WHERE book_number = ? " +
                     "AND (chapter > ? OR (chapter = ? AND verse >= ?)) " +
                     "AND (chapter < ? OR (chapter = ? AND verse <= ?)) " +
                     "ORDER BY book_number, chapter, verse";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, book);
            pstmt.setInt(2, startChapter != null ? startChapter : 0);
            pstmt.setInt(3, startChapter != null ? startChapter : 0);
            pstmt.setInt(4, startVerse != null ? startVerse : 0);
            pstmt.setInt(5, endChapter != null ? endChapter : 9999);
            pstmt.setInt(6, endChapter != null ? endChapter : 9999);
            pstmt.setInt(7, endVerse != null ? endVerse : 9999);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    verses.add(new Verse(rs.getInt("book_number"), rs.getInt("chapter"), rs.getInt("verse"), rs.getString("text")));
                }
            }
        }
        return verses;
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
