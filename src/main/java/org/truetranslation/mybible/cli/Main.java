package org.truetranslation.mybible.cli;

import com.formdev.flatlaf.FlatLightLaf;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import javax.swing.*;

import org.truetranslation.mybible.core.*;
import org.truetranslation.mybible.core.model.Book;
import org.truetranslation.mybible.core.model.GuiVerse;
import org.truetranslation.mybible.core.model.Reference;
import org.truetranslation.mybible.core.model.Verse;
import org.truetranslation.mybible.gui.Gui;
import org.truetranslation.mybible.gui.GuiConfigManager;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
    name = "mybible-cli",
    mixinStandardHelpOptions = true,
    versionProvider = Main.VersionProvider.class,
    resourceBundle = "picocli.main",
    subcommands = {
        Main.GetCommand.class,
        Main.ListCommand.class,
        Main.ParseCommand.class,
        Main.OpenCommand.class,
        Main.GuiCommand.class,
        Main.HelpCommand.class
    }
)
public class Main implements Callable<Integer> {

    static {
        String language = System.getProperty("user.language");
        String country = System.getProperty("user.country");

        if (language != null && !language.isEmpty()) {
            if (country != null && !country.isEmpty()) {
                Locale.setDefault(new Locale(language, country));
            } else {
                Locale.setDefault(new Locale(language));
            }
        }
    }
    private static final ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages");

    @Override
    public Integer call() {
        new CommandLine(this).usage(System.out);
        return 0;
    }

    @Command(name = "get", resourceBundle = "picocli.get")
    static class GetCommand implements Callable<Integer> {
        @Option(names = {"-m", "--module-name"}, descriptionKey = "modulename")
        private String moduleName;
        @Option(names = {"-r", "--reference"}, required = true, descriptionKey = "reference")
        private String referenceString;
        @Option(names = {"-A", "--self-abbr"}, descriptionKey = "selfabbr")
        private boolean useSelfAbbreviations;
        
        @Option(names = {"-a", "--abbr-prefix"}, descriptionKey = "abbrprefix")
        private String abbreviationsPrefix;

        @Option(names = {"-f", "--format"}, descriptionKey = "format")
        private String formatString;
        @Option(names = {"-F", "--save-format"}, descriptionKey = "saveformat")
        private String saveFormatString;
        @Option(names = {"-j", "--json"}, descriptionKey = "json")
        private boolean outputJson;
        @Option(names = {"-v", "--verbose"}, descriptionKey = "verbose")
        private boolean verbose;
        @Option(names = {"-s", "--silent"}, descriptionKey = "silent")
        private boolean silent;

        @Override
        public Integer call() {
            ConfigManager configManager = new ConfigManager();
            int verbosity = configManager.getVerbosity();
            if (verbose) verbosity = 1;
            if (silent) verbosity = 0;
            configManager.setVerbosity(verbosity);

            if (moduleName == null || moduleName.isEmpty()) {
                moduleName = configManager.getLastUsedModule();
                if (moduleName == null || moduleName.isEmpty()) {
                    System.err.println(bundle.getString("error.module.nameMissing"));
                    return 1;
                }
                if (verbosity > 0) System.out.println(MessageFormat.format(bundle.getString("msg.usingLastModule"), moduleName));
            }
            String modulesPathStr = configManager.getModulesPath();
            if (modulesPathStr == null || modulesPathStr.isEmpty()) {
                System.err.println(bundle.getString("error.module.notConfigured"));
                return 1;
            }
            Path modulePath = Paths.get(modulesPathStr, moduleName + ".sqlite3");
            if (!Files.exists(modulePath)) {
                System.err.println(MessageFormat.format(bundle.getString("error.module.notFound"), modulePath));
                return 1;
            }
            VerseFetcher fetcher = null;
            try {
                String activeFormatString = configManager.getFormatString();
                if (saveFormatString != null) {
                    activeFormatString = saveFormatString;
                    configManager.setFormatString(saveFormatString);
                    if (verbosity > 0) System.out.println(bundle.getString("msg.newFormatSaved"));
                } else if (formatString != null) {
                    activeFormatString = formatString;
                }

                VerseIndexManager indexManager = new VerseIndexManager(configManager, verbosity);
                Map<Integer, Integer> verseIndex = indexManager.getVerseIndex(moduleName, modulePath);

                BookMapper defaultBookMapper = BookMappingManager.getBookMapper(configManager, abbreviationsPrefix);
                
                BookMapper moduleBookMapper;
                try {
                    AbbreviationManager abbrManager = new AbbreviationManager(configManager, verbosity);
                    Path abbrFile = abbrManager.ensureAbbreviationFile(moduleName, modulePath);
                    moduleBookMapper = new BookMapper(abbrManager.loadAbbreviations(abbrFile));
                } catch (Exception e) {
                    if (verbosity > 0) {
                        System.err.println(MessageFormat.format(bundle.getString("parse.error.noModuleAbbrs"), e.getMessage()));
                    }
                    moduleBookMapper = defaultBookMapper;
                }
                
                BookMapper parserMapper = useSelfAbbreviations ? moduleBookMapper : defaultBookMapper;
                ReferenceParser parser = new ReferenceParser(parserMapper, verseIndex);

                List<ReferenceParser.RangeWithCount> rangesWithCount = parser.parseWithCounts(referenceString);
                if (rangesWithCount.isEmpty()) { return 1; }

                List<ReferenceParser.Range> ranges = new ArrayList<>(rangesWithCount);

                fetcher = new VerseFetcher(modulePath);
                List<Verse> verses = fetcher.fetch(ranges);
                configManager.setLastUsedModule(moduleName);
                
                OutputFormatter formatter = new OutputFormatter(activeFormatString, defaultBookMapper, moduleBookMapper, moduleName);

                if (! outputJson) {
                    for (Verse verse : verses) {
                        Reference containingRef = findContainingReference(ranges, verse);
                        System.out.println(formatter.format(verse, containingRef));
                    }
                } else {
                    List<GuiVerse> guiVerses = new ArrayList<>();
                    for (Verse verse : verses) {
                        Reference ref = findContainingReference(ranges, verse);
                        String userProvidedShortName = ref != null ? ref.getBookName() : null;
                        int bookNum = verse.getBookNumber();

                        Optional<Book> defaultBookOpt = defaultBookMapper.getBook(bookNum);
                        String defaultFullName = defaultBookOpt.map(Book::getFullName).orElse("");
                        String defaultShortName = defaultBookOpt.map(book ->
                            (userProvidedShortName != null && book.getShortNames().contains(userProvidedShortName)) ? userProvidedShortName : book.getShortNames().stream().findFirst().orElse("")
                        ).orElse("");

                        Optional<Book> moduleBookOpt = moduleBookMapper.getBook(bookNum);
                        String moduleFullName = moduleBookOpt.map(Book::getFullName).orElse(defaultFullName);
                        String moduleShortName = moduleBookOpt.map(book ->
                            (userProvidedShortName != null && book.getShortNames().contains(userProvidedShortName)) ? userProvidedShortName : book.getShortNames().stream().findFirst().orElse("")
                        ).orElse(defaultShortName);

                        guiVerses.add(new GuiVerse(
                            bookNum,
                            defaultFullName,
                            defaultShortName,
                            moduleFullName,
                            moduleShortName,
                            verse.getChapter(),
                            verse.getVerse(),
                            verse.getText(),
                            moduleName
                        ));
                    }
                    Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
                    String jsonForGui = gson.toJson(guiVerses);
                    System.out.println(jsonForGui);
                }

            } catch (Exception e) {
                System.err.println(MessageFormat.format(bundle.getString("error.unexpected"), e.getMessage()));
                if (verbosity > 0) e.printStackTrace();
                return 1;
            } finally {
                if (fetcher != null) { try { fetcher.close(); } catch (SQLException ex) { ex.printStackTrace(); } }
            }
            return 0;
        }

        private Reference findContainingReference(List<ReferenceParser.Range> ranges, Verse verse) {
            for (ReferenceParser.Range range : ranges) {
                 if (verse.getBookNumber() >= range.start.getBook() && verse.getBookNumber() <= range.end.getBook() &&
                     verse.getChapter() >= range.start.getChapter() && verse.getChapter() <= range.end.getChapter() &&
                     verse.getVerse() >= range.start.getVerse() && verse.getVerse() <= range.end.getVerse()) {
                    return range.start;
                }
            }
            return null;
        }
    }

    @Command(name = "help", resourceBundle = "picocli.help")
    static class HelpCommand implements Callable<Integer> {
        @Spec CommandSpec spec;
        @Parameters(index = "0", descriptionKey = "description", arity = "0..1")
        private String topic;
        @Override
        public Integer call() {
            if (topic == null) {
                CommandLine mainCommand = spec.parent().commandLine();
                mainCommand.usage(System.out);
                System.out.println();
                System.out.println(bundle.getString("help.header"));
                List<String> topics = Arrays.asList("get", "list", "parse", "open", "gui", "help", "format");
                for (String topicName : topics) {
                    String description = bundle.getString("help.topic." + topicName + ".description");
                    String formattedLine = String.format("  @|bold %-6s|@ %s", topicName, description);
                    System.out.println(Help.Ansi.AUTO.string(formattedLine));
                }
                return 0;
            }
            switch (topic.toLowerCase()) {
                case "format": System.out.println(bundle.getString("help.format.text")); break;
                case "version": System.out.println(bundle.getString("app.version")); break;
                default:
                    CommandLine subCommand = spec.parent().subcommands().get(topic.toLowerCase());
                    if (subCommand != null) { subCommand.usage(System.out); } 
                    else { System.err.println(MessageFormat.format(bundle.getString("help.unknown.topic"), topic)); return 1; }
            }
            return 0;
        }
    }

    @Command(name = "gui", resourceBundle = "picocli.gui")
    static class GuiCommand implements Callable<Integer> {
        @Option(names = {"-m", "--module-name"}, descriptionKey = "module")
        String moduleName;

        @Option(names = {"-r", "--reference"}, descriptionKey = "reference")
        String reference;

        @Override
        public Integer call() throws InterruptedException {
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            javax.swing.SwingUtilities.invokeLater(() -> {
                Gui gui = new Gui(moduleName, reference, () -> latch.countDown());
                gui.setVisible(true);
            });
            latch.await();
            return 0;
        }
    }

    @Command(name = "list", resourceBundle = "picocli.list")
    static class ListCommand implements Callable<Integer> {
        @Option(names = {"-p", "--path"}, descriptionKey = "path")
        File modulesPath;
        @Override
        public Integer call() {
            ConfigManager configManager = new ConfigManager();
            if (modulesPath != null) {
                if (!modulesPath.isDirectory()) {
                    System.err.println(bundle.getString("error.path.invalid"));
                    return 1;
                }
                configManager.setModulesPath(modulesPath.getAbsolutePath());
                System.out.println(MessageFormat.format(bundle.getString("msg.modulePathUpdated"), modulesPath.getAbsolutePath()));
            }
            String currentModulesPath = configManager.getModulesPath();
            if (currentModulesPath == null || currentModulesPath.trim().isEmpty()) {
                System.err.println(bundle.getString("error.module.notConfigured"));
                return 1;
            }
            ModuleScanner scanner = new ModuleScanner();
            try {
                List<ModuleScanner.Module> modules = scanner.findModules(Paths.get(currentModulesPath));
                if (modules.isEmpty()) {
                    System.out.println(MessageFormat.format(bundle.getString("msg.noModulesFound"), currentModulesPath));
                } else {
                    modules.forEach(module -> System.out.printf("%s\t%s\t%s%n", module.getLanguage(), module.getName(), module.getDescription()));
                }
            } catch (IOException e) {
                System.err.println(MessageFormat.format(bundle.getString("error.directory.read"), e.getMessage()));
                return 1;
            }
            return 0;
        }
    }

    @Command(name = "parse", resourceBundle = "picocli.parse")
    static class ParseCommand implements Callable<Integer> {
        @Option(names = {"-m", "--module-name"}, descriptionKey = "modulename")
        private String moduleName;

        @Option(names = {"-j", "--json"}, descriptionKey = "json")
        private boolean outputJson;

        @Option(names = {"-r", "--reference"}, required = true, descriptionKey = "reference")
        private String referenceString;
        
        @Option(names = {"-A", "--self-abbr"}, descriptionKey = "selfabbr")
        private boolean useModuleAbbreviations;

        @Option(names = {"-a", "--abbr-prefix"}, paramLabel = "<prefix>", descriptionKey = "abbrprefix")
        private String customMappingPrefix;

        @Override
        public Integer call() {
            ConfigManager configManager = new ConfigManager();
            int verbosity = configManager.getVerbosity();

            if (moduleName == null || moduleName.isEmpty()) {
                moduleName = configManager.getLastUsedModule();
                if (moduleName == null || moduleName.isEmpty()) {
                    System.err.println(bundle.getString("error.module.nameMissing"));
                    return 1;
                }
                if (verbosity > 0) System.out.println(MessageFormat.format(bundle.getString("msg.usingLastModule"), moduleName));
            }
            Path modulePath = Paths.get(configManager.getModulesPath(), moduleName + ".sqlite3");
            if (!Files.exists(modulePath)) {
                System.err.println(MessageFormat.format(bundle.getString("error.module.notFound"), modulePath));
                return 1;
            }

            try {
                VerseIndexManager indexManager = new VerseIndexManager(configManager, verbosity);
                BookMapper bookMapper;

                if (useModuleAbbreviations) {
                    AbbreviationManager abbrManager = new AbbreviationManager(configManager, verbosity);
                    Path abbrFile = abbrManager.ensureAbbreviationFile(moduleName, modulePath);
                    bookMapper = new BookMapper(abbrManager.loadAbbreviations(abbrFile));
                } else if (customMappingPrefix != null) {
                    bookMapper = BookMappingManager.getBookMapper(configManager, customMappingPrefix);
                } else {
                    bookMapper = BookMappingManager.getBookMapper(configManager);
                }

                Map<Integer, Integer> verseIndex = indexManager.getVerseIndex(moduleName, modulePath);
                ReferenceParser parser = new ReferenceParser(bookMapper, verseIndex);

                List<ReferenceParser.RangeWithCount> ranges = parser.parseWithCounts(referenceString);

                if (ranges.isEmpty() && !referenceString.trim().isEmpty()) {
                    return 1;
                }

                if (!outputJson) {
                    System.out.println(MessageFormat.format(bundle.getString("parse.output.header"), referenceString));
                    for (int i = 0; i < ranges.size(); i++) {
                        ReferenceParser.RangeWithCount range = ranges.get(i);
                        
                        String details = MessageFormat.format(bundle.getString("parse.output.range.details"),
                                range.start.toString(),
                                range.end.toString(),
                                range.verseCount
                        );
                        System.out.printf("  %s %d: %s%n",
                                bundle.getString("parse.output.range.label"), (i + 1), details);
                    }
                } else {
                    Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
                    String jsonOutput = gson.toJson(ranges);
                    System.out.println(jsonOutput);
                }

            } catch (Exception e) {
                System.err.println(MessageFormat.format(bundle.getString("error.unexpected"), e.getMessage()));
                e.printStackTrace();
                return 1;
            }
            return 0;
        }
    }

    @Command(name = "open", resourceBundle = "picocli.open", subcommands = {OpenCommand.OpenConfig.class, OpenCommand.OpenModule.class})
    static class OpenCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            System.err.println(bundle.getString("error.subcommand.required"));
            new CommandLine(this).usage(System.err);
            return 1;
        }

        @Command(name="config", resourceBundle = "picocli.open")
        static class OpenConfig implements Callable<Integer> {
            @Override
            public Integer call() {
                try {
                    ConfigManager configManager = new ConfigManager();
                    Path configPath = configManager.getDefaultConfigDir();
                    if (Files.notExists(configPath)) {
                        Files.createDirectories(configPath);
                    }
                    System.out.println(MessageFormat.format(bundle.getString("msg.opening.path"), configPath.toAbsolutePath()));
                    Desktop.getDesktop().open(configPath.toFile());
                    return 0;
                } catch (UnsupportedOperationException e) {
                    System.err.println(bundle.getString("error.open.unsupported"));
                    return 1;
                } catch (IOException e) {
                    System.err.println(MessageFormat.format(bundle.getString("error.open.failed"), e.getMessage()));
                    return 1;
                }
            }
        }

        @Command(name="module", resourceBundle = "picocli.open")
        static class OpenModule implements Callable<Integer> {
            @Override
            public Integer call() {
                ConfigManager configManager = new ConfigManager();
                String modulesPathStr = configManager.getModulesPath();
                if (modulesPathStr == null || modulesPathStr.isEmpty()) {
                    System.err.println(bundle.getString("error.module.notConfigured"));
                    return 1;
                }
                try {
                    Path modulesPath = Paths.get(modulesPathStr);
                     if (Files.notExists(modulesPath)) {
                        System.err.println(MessageFormat.format(bundle.getString("error.path.notFound"), modulesPath.toAbsolutePath()));
                        return 1;
                    }
                    System.out.println(MessageFormat.format(bundle.getString("msg.opening.path"), modulesPath.toAbsolutePath()));
                    Desktop.getDesktop().open(modulesPath.toFile());
                    return 0;
                } catch (UnsupportedOperationException e) {
                    System.err.println(bundle.getString("error.open.unsupported"));
                    return 1;
                } catch (IOException e) {
                    System.err.println(MessageFormat.format(bundle.getString("error.open.failed"), e.getMessage()));
                    return 1;
                }
            }
        }
    }

    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            return new String[] { bundle.getString("app.version") };
        }
    }

    public static void main(String[] args) {
        boolean launchGuiDefault = (args.length == 0);
        int exitCode;

        if (launchGuiDefault || Arrays.asList(args).contains("gui")) {
            GuiConfigManager configManager = new GuiConfigManager();
            String lafClassName = configManager.getConfig().lookAndFeelClassName;
            try {
                if (lafClassName != null && !lafClassName.isEmpty()) {
                    UIManager.setLookAndFeel(lafClassName);
                } else {
                    UIManager.setLookAndFeel(new FlatLightLaf());
                }
            } catch (Exception e) {
                System.err.println("Failed to set Look and Feel. Continuing with default.");
                if (!launchGuiDefault) {
                    e.printStackTrace();
                }
            }
        }

        if (launchGuiDefault) {
            exitCode = new CommandLine(new Main()).execute("gui");
        } else {
            exitCode = new CommandLine(new Main()).execute(args);
        }
        System.exit(exitCode);
    }
}
