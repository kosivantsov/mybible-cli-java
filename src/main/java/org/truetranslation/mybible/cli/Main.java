package org.truetranslation.mybible.cli;

import com.formdev.flatlaf.FlatLightLaf;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.*;

import org.truetranslation.mybible.core.*;
import org.truetranslation.mybible.core.ExtensionManager;
import org.truetranslation.mybible.core.ExtensionManager.ExtensionInfo;
import org.truetranslation.mybible.core.ExtensionManager.ExtensionValidationException;
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
        Main.ExtCommand.class,
        Main.ModCommand.class,
        Main.HelpCommand.class
    }
)
public class Main implements Callable<Integer> {
    private static ExternalResourceBundleLoader externalLoader;
    private static final ResourceBundle bundle;

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
        ConfigManager configManager = new ConfigManager();
        externalLoader = new ExternalResourceBundleLoader(configManager.getDefaultConfigDir());
        bundle = externalLoader.getBundle("i18n.messages");
    }

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

        @Option(names = {"-l", "--language"}, descriptionKey = "language")
        private String userLanguage;

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
        @Option(names = {"-z", "--no-ansi"}, descriptionKey = "noansi")
        private boolean noansi;

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

                BookMapper defaultBookMapper = BookMappingManager.getBookMapper(configManager, abbreviationsPrefix, userLanguage);

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

                // Extract module language and create language-aware formatter
                String moduleLanguage = BookMapper.extractModuleLanguage(modulePath);
                OutputFormatter formatter = new OutputFormatter(activeFormatString, defaultBookMapper, moduleBookMapper, moduleName, moduleLanguage, userLanguage);

                if (! outputJson) {
                    for (Verse verse : verses) {
                        Reference containingRef = findContainingReference(ranges, verse);
                        String formattedOutput = formatter.format(verse, containingRef);
                        if (noansi) {
                            formattedOutput = formattedOutput.replaceAll("\u001B\\[[;\\d]*m", "");
                        }
                        System.out.println(formattedOutput);
                    }
                } else {
                    List<GuiVerse> guiVerses = new ArrayList<>();
                    for (Verse verse : verses) {
                        Reference ref = findContainingReference(ranges, verse);
                        String userProvidedShortName = ref != null ? ref.getBookName() : null;
                        int bookNum = verse.getBookNumber();

                        // Use language-aware lookup for default names
                        Optional<Book> defaultBookOpt = defaultBookMapper.getBook(bookNum, userLanguage, moduleLanguage);
                        if (!defaultBookOpt.isPresent()) {
                            defaultBookOpt = defaultBookMapper.getBook(bookNum); // Fallback
                        }

                        String defaultFullName = defaultBookOpt.map(Book::getFullName).orElse("");
                        String defaultShortName = defaultBookOpt.map(book ->
                            (userProvidedShortName != null && book.getShortNames().contains(userProvidedShortName)) ? userProvidedShortName : book.getShortNames().stream().findFirst().orElse("")
                        ).orElse("");

                        Optional<Book> moduleBookOpt = moduleBookMapper.getBook(bookNum);
                        String moduleFullName = moduleBookOpt.map(Book::getFullName).orElse(defaultFullName);
                        String moduleShortName = moduleBookOpt.map(book -> {
                            List<String> shortNames = book.getShortNames();
                            return (shortNames.size() > 1) ? shortNames.get(1) : shortNames.stream().findFirst().orElse("");
                        }).orElse(defaultShortName);

                        guiVerses.add(new GuiVerse(
                            bookNum,
                            defaultFullName,
                            defaultShortName,
                            moduleFullName,
                            moduleShortName,
                            defaultBookMapper.getAllBookNames(bookNum, moduleBookMapper, moduleLanguage, userLanguage),
                            verse.getChapter(),
                            verse.getVerse(),
                            verse.getText(),
                            moduleName
                        ));
                    }
                    Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
                    String printJson = gson.toJson(guiVerses);
                    printJson = compactArrayField(printJson, "allBookNames");
                    System.out.println(printJson);
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
                List<String> topics = Arrays.asList("get", "list", "parse", "open", "gui", "ext", "mod", "help", "format");
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

    @Command(name = "mod", resourceBundle = "picocli.mod")
    static class ModCommand implements Callable<Integer> {

        @Option(names = {"-u", "--update"}, descriptionKey = "update")
        private boolean update;

        @Option(names = {"-l", "--list"}, descriptionKey = "list")
        private String listType;

        @Option(names = {"-i", "--install"}, descriptionKey = "install")
        private String[] installNames;

        @Option(names = {"--install-file"}, paramLabel = "<path>", descriptionKey = "installFile")
        private String installFile;

        @Option(names = {"-r", "--remove"}, descriptionKey = "remove")
        private String[] removeNames;

        @Option(names = {"-g", "--upgrade"}, descriptionKey = "upgrade")
        private String[] upgradeNames;

        @Option(names = {"--upgrade-all"}, descriptionKey = "upgradeAll")
        private boolean upgradeAll;

        @Option(names = {"-I", "--info"}, descriptionKey = "info")
        private String infoName;

        @Option(names = {"--versions"}, descriptionKey = "versions")
        private String versionsName;

        @Option(names = {"--version"}, descriptionKey = "version")
        private String specificVersion;

        @Option(names = {"--reinstall"}, descriptionKey = "reinstall")
        private boolean reinstall;

        @Option(names = {"-L", "--lang", "--language"}, descriptionKey = "language")
        private String language;

        @Option(names = {"-t", "--type"}, descriptionKey = "type")
        private String moduleType;

        @Option(names = {"-n", "--name"}, descriptionKey = "name")
        private String nameFilter;

        @Option(names = {"-d", "--desc"}, descriptionKey = "desc")
        private String descFilter;

        @Option(names = {"--reinit"}, descriptionKey = "reinit")
        private boolean reinit;

        @Option(names = {"--purge"}, descriptionKey = "purge")
        private boolean purge;

        @Option(names = {"--purge-all"}, descriptionKey = "purgeAll")
        private boolean purgeAll;

        @Option(names = {"-v", "--verbose"}, descriptionKey = "verbose")
        private boolean verbose;

        @Option(names = {"--silent"}, descriptionKey = "silent")
        private boolean silent;

        @Override
        public Integer call() {
            ConfigManager configManager = new ConfigManager();
            int verbosity = configManager.getVerbosity();
            if (verbose) verbosity = 1;
            if (silent) verbosity = 0;

            final int finalVerbosity = verbosity;

            try {
                ModuleManager moduleManager = new ModuleManager(configManager, finalVerbosity);

                if (update) {
                    moduleManager.updateCache((current, total, message) -> {
                        if (finalVerbosity > 0) {
                            System.out.printf("\r[%d/%d] %s", current, total, message);
                        }
                    });
                    if (finalVerbosity > 0) System.out.println();
                    return 0;
                }

                if (listType != null) {
                    return listModules(moduleManager, listType);
                }

                if (installNames != null && installNames.length > 0) {
                    return installModules(moduleManager, finalVerbosity);
                }

                if (installFile != null) {
                    moduleManager.installFromFile(installFile);
                    return 0;
                }

                if (removeNames != null && removeNames.length > 0) {
                    return removeModules(moduleManager);
                }

                if (upgradeAll || (upgradeNames != null && upgradeNames.length > 0)) {
                    return upgradeModules(moduleManager, finalVerbosity);
                }

                if (infoName != null) {
                    return showModuleInfo(moduleManager);
                }

                if (versionsName != null) {
                    return listVersions(moduleManager);
                }

                if (reinit) {
                    moduleManager.initializeSources(true);
                    return 0;
                }

                if (purge || purgeAll) {
                    moduleManager.purgeCache(purgeAll);
                    return 0;
                }

                System.err.println(bundle.getString("error.mod.noAction"));
                new CommandLine(this).usage(System.err);
                return 1;

            } catch (IllegalStateException e) {
                System.err.println(e.getMessage());
                return 1;
            } catch (Exception e) {
                System.err.println(MessageFormat.format(bundle.getString("error.unexpected"), e.getMessage()));
                if (finalVerbosity > 0) {
                    e.printStackTrace();
                }
                return 1;
            }
        }

        private int listModules(ModuleManager moduleManager, String type) throws IOException {
            switch (type.toLowerCase()) {
                case "available":
                case "a":
                    List<ModuleManager.CachedModule> available = moduleManager.listAvailableModules(
                        language, moduleType, nameFilter, descFilter);
                    if (available.isEmpty()) {
                        System.out.println(bundle.getString("msg.mod.noAvailable"));
                    } else {
                        printFilterInfo();
                        for (ModuleManager.CachedModule mod : available) {
                            System.out.printf("%-20s %-5s %-10s %s%n", 
                                mod.name, 
                                mod.language != null ? mod.language : "N/A", 
                                mod.moduleType,
                                mod.description);
                        }
                    }
                    break;

                case "installed":
                case "i":
                    List<ModuleManager.InstalledModule> installed = moduleManager.listInstalledModules(
                        language, moduleType, nameFilter, descFilter);
                    if (installed.isEmpty()) {
                        System.out.println(bundle.getString("msg.mod.noInstalled"));
                    } else {
                        printFilterInfo();
                        for (ModuleManager.InstalledModule mod : installed) {
                            System.out.printf("%-20s %-5s %-10s %s%n", 
                                mod.name,
                                mod.language != null ? mod.language : "N/A",
                                mod.updateDate,
                                mod.description);
                        }
                    }
                    break;

                case "upgradable":
                case "u":
                    List<ModuleManager.UpgradableModule> upgradable = moduleManager.listUpgradableModules(
                        language, moduleType, nameFilter, descFilter);
                    if (upgradable.isEmpty()) {
                        System.out.println(bundle.getString("msg.mod.noUpgradable"));
                    } else {
                        printFilterInfo();
                        for (ModuleManager.UpgradableModule mod : upgradable) {
                            System.out.printf("%-20s %s -> %s %s%n",
                                mod.installed.name,
                                mod.installed.updateDate,
                                mod.latest.updateDate,
                                mod.installed.description);
                        }
                    }
                    break;

                default:
                    System.err.println(MessageFormat.format(bundle.getString("error.mod.invalidListType"), type));
                    return 1;
            }
            return 0;
        }

        private void printFilterInfo() {
            List<String> filters = new ArrayList<>();
            if (language != null && !language.isEmpty()) {
                filters.add("Language: " + language);
            }
            if (moduleType != null && !moduleType.isEmpty()) {
                filters.add("Type: " + moduleType);
            }
            if (nameFilter != null && !nameFilter.isEmpty()) {
                filters.add("Name: " + nameFilter);
            }
            if (descFilter != null && !descFilter.isEmpty()) {
                filters.add("Description: " + descFilter);
            }

            if (!filters.isEmpty()) {
                System.out.println(bundle.getString("msg.mod.filtersApplied") + " " + String.join(", ", filters));
            }
        }

        private int installModules(ModuleManager moduleManager, int verbosity) {
            int successCount = 0;
            int failCount = 0;

            for (String name : installNames) {
                try {
                    boolean success = moduleManager.installModule(name, specificVersion, reinstall,
                        (current, total, message) -> {
                            if (verbosity > 0) {
                                System.out.printf("\r%s: [%d/%d]", name, current, total);
                            }
                        });

                    if (success) {
                        successCount++;
                        if (verbosity > 0) System.out.println();
                    }
                } catch (IOException e) {
                    System.err.println(MessageFormat.format(bundle.getString("error.mod.installFailed"), name, e.getMessage()));
                    failCount++;
                }
            }

            if (installNames.length > 1 && verbosity > 0) {
                System.out.println(MessageFormat.format(bundle.getString("msg.mod.installSummary"), successCount, failCount));
            }

            return failCount > 0 ? 1 : 0;
        }

        private int removeModules(ModuleManager moduleManager) {
            int successCount = 0;
            int failCount = 0;

            for (String name : removeNames) {
                try {
                    if (moduleManager.removeModule(name)) {
                        successCount++;
                    }
                } catch (IOException e) {
                    System.err.println(MessageFormat.format(bundle.getString("error.mod.removeFailed"), name, e.getMessage()));
                    failCount++;
                }
            }

            if (removeNames.length > 1) {
                System.out.println(MessageFormat.format(bundle.getString("msg.mod.removeSummary"), successCount, failCount));
            }

            return failCount > 0 ? 1 : 0;
        }

        private int upgradeModules(ModuleManager moduleManager, int verbosity) throws IOException {
            List<String> names = upgradeAll ? null : Arrays.asList(upgradeNames);
            int upgraded = moduleManager.upgradeModules(names, 
                (current, total, message) -> {
                    if (verbosity > 0) {
                        System.out.printf("\r[%d/%d] %s", current, total, message);
                    }
                });

            if (upgraded > 0 && verbosity > 0) {
                System.out.println();
                System.out.println(MessageFormat.format(bundle.getString("msg.mod.upgradeComplete"), upgraded));
            }

            return 0;
        }

        private int showModuleInfo(ModuleManager moduleManager) throws IOException {
            try {
                ModuleManager.ModuleInfo info = moduleManager.getModuleInfo(infoName);

                System.out.println(MessageFormat.format(bundle.getString("msg.mod.info.name"), info.cached.name));
                System.out.println(MessageFormat.format(bundle.getString("msg.mod.info.language"), 
                    info.cached.language != null ? info.cached.language : "N/A"));
                System.out.println(MessageFormat.format(bundle.getString("msg.mod.info.type"), info.cached.moduleType));
                System.out.println(MessageFormat.format(bundle.getString("msg.mod.info.version"), info.cached.updateDate));
                System.out.println(MessageFormat.format(bundle.getString("msg.mod.info.description"), info.cached.description));

                if (info.isInstalled) {
                    System.out.println(MessageFormat.format(bundle.getString("msg.mod.info.installed"), info.installed.updateDate));
                    if (info.hasUpdate) {
                        System.out.println(MessageFormat.format(bundle.getString("msg.mod.info.updateAvailable"), info.cached.updateDate));
                    } else {
                        System.out.println(bundle.getString("msg.mod.info.upToDate"));
                    }
                } else {
                    System.out.println(bundle.getString("msg.mod.info.notInstalled"));
                }

                return 0;
            } catch (IOException e) {
                System.err.println(e.getMessage());
                return 1;
            }
        }

        private int listVersions(ModuleManager moduleManager) throws IOException {
            List<String> versions = moduleManager.listModuleVersions(versionsName);

            if (versions.isEmpty()) {
                System.out.println(MessageFormat.format(bundle.getString("msg.mod.noVersions"), versionsName));
            } else {
                System.out.println(MessageFormat.format(bundle.getString("msg.mod.versionsFor"), versionsName));
                for (String version : versions) {
                    System.out.println("  " + version);
                }
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

    @Command(name = "ext", resourceBundle = "picocli.ext")
    static class ExtCommand implements Callable<Integer> {

        @Option(names = {"-i", "--install"}, paramLabel = "<name|file>", descriptionKey = "install")
        private String installTarget;

        @Option(names = {"-u", "--uninstall"}, paramLabel = "<name>", descriptionKey = "uninstall")
        private String uninstallName;

        @Option(names = {"-g", "--upgrade"}, paramLabel = "<name>", descriptionKey = "upgrade", arity = "0..1")
        private String upgradeName;

        @Option(names = {"-l", "--list"}, paramLabel = "[installed|available|upgradable]", descriptionKey = "list", arity = "0..1")
        private String listMode;

        @Option(names = {"--update"}, descriptionKey = "update")
        private boolean updateRegistries;

        @Option(names = {"-s", "--show", "--info"}, paramLabel = "<name>", descriptionKey = "show")
        private String showName;

        @Option(names = {"-t", "--type"}, paramLabel = "<type>", descriptionKey = "type")
        private String typeFilter;

        @Option(names = {"-f", "--filter"}, paramLabel = "<text>", descriptionKey = "filter")
        private String nameFilter;

        @Option(names = {"-L", "--language"}, paramLabel = "<code>", descriptionKey = "language")
        private String languageFilter;

        @Option(names = {"--all"}, descriptionKey = "all")
        private boolean applyToAll;

        @Option(names = {"--verbose"}, descriptionKey = "verbose")
        private boolean verbose;

        @Option(names = {"--silent"}, descriptionKey = "silent")
        private boolean silent;

        @Override
        public Integer call() {
            ConfigManager configManager = new ConfigManager();
            int verbosity = configManager.getVerbosity();
            if (verbose) verbosity = 1;
            if (silent) verbosity = 0;

            try {
                ExtensionManager extensionManager = new ExtensionManager(configManager, verbosity);

                if (updateRegistries) {
                    return updateExtensionRegistries(extensionManager);
                }

                if (installTarget != null) {
                    return installExtension(extensionManager, installTarget);
                }

                if (upgradeName != null || applyToAll) {
                    return upgradeExtensions(extensionManager);
                }

                if (uninstallName != null || (applyToAll && listMode != null && listMode.equalsIgnoreCase("installed"))) {
                    return uninstallExtensions(extensionManager);
                }

                if (showName != null) {
                    return showExtensionInfo(extensionManager, showName);
                }

                if (listMode != null) {
                    return listExtensions(extensionManager, listMode);
                }

                System.err.println(bundle.getString("error.ext.noOption"));
                return 1;

            } catch (Exception e) {
                System.err.println(MessageFormat.format(
                    bundle.getString("error.unexpected"),
                    e.getMessage()
                ));
                if (verbosity > 0) {
                    e.printStackTrace();
                }
                return 1;
            }
        }

        private int updateExtensionRegistries(ExtensionManager extensionManager) {
            try {
                extensionManager.updateRegistries();
                return 0;
            } catch (IOException e) {
                System.err.println(MessageFormat.format(
                    bundle.getString("error.ext.updateFailed"),
                    e.getMessage()
                ));
                return 1;
            }
        }

        private int installExtension(ExtensionManager extensionManager, String target) {
            try {
                extensionManager.installExtension(target);
                return 0;
            } catch (ExtensionManager.ExtensionValidationException e) {
                System.err.println(MessageFormat.format(
                    bundle.getString("error.ext.validationFailed"),
                    e.getMessage()
                ));
                return 1;
            } catch (IOException e) {
                System.err.println(MessageFormat.format(
                    bundle.getString("error.ext.installFailed"),
                    e.getMessage()
                ));
                return 1;
            }
        }

        private int upgradeExtensions(ExtensionManager extensionManager) {
            try {
                if (applyToAll) {
                    return upgradeAllExtensions(extensionManager);
                } else if (upgradeName != null && !upgradeName.isEmpty()) {
                    extensionManager.upgradeExtension(upgradeName);
                    return 0;
                } else {
                    System.err.println(bundle.getString("error.ext.noUpgradeTarget"));
                    return 1;
                }
            } catch (ExtensionManager.ExtensionValidationException e) {
                System.err.println(MessageFormat.format(
                    bundle.getString("error.ext.validationFailed"),
                    e.getMessage()
                ));
                return 1;
            } catch (IOException e) {
                System.err.println(MessageFormat.format(
                    bundle.getString("error.ext.upgradeFailed"),
                    e.getMessage()
                ));
                return 1;
            }
        }

        private int upgradeAllExtensions(ExtensionManager extensionManager) throws IOException {
            List<ExtensionManager.ExtensionInfo> upgradable = extensionManager.listUpgradableExtensions();

            if (upgradable.isEmpty()) {
                System.out.println(bundle.getString("msg.ext.noUpgradableExtensions"));
                return 0;
            }

            System.out.println(bundle.getString("msg.ext.upgradeAllPrompt"));
            for (ExtensionManager.ExtensionInfo ext : upgradable) {
                System.out.println("  - " + ext.manifest.name + " v" + ext.manifest.version);
            }
            System.out.println();

            System.out.print(bundle.getString("msg.ext.confirmUpgradeAll"));
            System.out.flush();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String response = reader.readLine();
                if (response == null || !response.trim().toLowerCase().matches("y|yes")) {
                    System.out.println(bundle.getString("msg.ext.cancelled"));
                    return 0;
                }
            } catch (IOException e) {
                System.err.println(bundle.getString("error.ext.inputFailed"));
                return 1;
            }

            int successCount = 0;
            int failCount = 0;

            for (ExtensionManager.ExtensionInfo ext : upgradable) {
                try {
                    extensionManager.upgradeExtension(ext.manifest.name);
                    successCount++;
                } catch (Exception e) {
                    System.err.println(MessageFormat.format(
                        bundle.getString("error.ext.upgradeItemFailed"),
                        ext.manifest.name,
                        e.getMessage()
                    ));
                    failCount++;
                }
            }

            System.out.println();
            System.out.println(MessageFormat.format(
                bundle.getString("msg.ext.upgradeSummary"),
                successCount,
                failCount
            ));

            return failCount > 0 ? 1 : 0;
        }

        private int uninstallExtensions(ExtensionManager extensionManager) {
            try {
                if (applyToAll) {
                    return uninstallAllExtensions(extensionManager);
                } else if (uninstallName != null && !uninstallName.isEmpty()) {
                    extensionManager.uninstallExtension(uninstallName);
                    return 0;
                } else {
                    System.err.println(bundle.getString("error.ext.noUninstallTarget"));
                    return 1;
                }
            } catch (IOException e) {
                System.err.println(MessageFormat.format(
                    bundle.getString("error.ext.uninstallFailed"),
                    e.getMessage()
                ));
                return 1;
            }
        }

        private int uninstallAllExtensions(ExtensionManager extensionManager) throws IOException {
            List<ExtensionManager.ExtensionInfo> extensions = extensionManager.listInstalledExtensions();

            if (extensions.isEmpty()) {
                System.out.println(bundle.getString("msg.ext.noExtensions"));
                return 0;
            }

            System.out.println(bundle.getString("msg.ext.uninstallAllPrompt"));
            for (ExtensionManager.ExtensionInfo ext : extensions) {
                System.out.println("  - " + ext.manifest.name + " v" + ext.manifest.version);
            }
            System.out.println();

            System.out.print(bundle.getString("msg.ext.confirmUninstallAll"));
            System.out.flush();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String response = reader.readLine();
                if (response == null || !response.trim().toLowerCase().matches("y|yes")) {
                    System.out.println(bundle.getString("msg.ext.cancelled"));
                    return 0;
                }
            } catch (IOException e) {
                System.err.println(bundle.getString("error.ext.inputFailed"));
                return 1;
            }

            int successCount = 0;
            int failCount = 0;

            for (ExtensionManager.ExtensionInfo ext : extensions) {
                try {
                    extensionManager.uninstallExtension(ext.manifest.name);
                    successCount++;
                } catch (IOException e) {
                    System.err.println(MessageFormat.format(
                        bundle.getString("error.ext.uninstallItemFailed"),
                        ext.manifest.name,
                        e.getMessage()
                    ));
                    failCount++;
                }
            }

            System.out.println();
            System.out.println(MessageFormat.format(
                bundle.getString("msg.ext.uninstallSummary"),
                successCount,
                failCount
            ));

            return failCount > 0 ? 1 : 0;
        }

        private int listExtensions(ExtensionManager extensionManager, String mode) {
            try {
                if (mode == null || mode.isEmpty() || mode.equalsIgnoreCase("installed")) {
                    return listInstalledExtensions(extensionManager);
                } else if (mode.equalsIgnoreCase("available")) {
                    return listAvailableExtensions(extensionManager);
                } else if (mode.equalsIgnoreCase("upgradable")) {
                    return listUpgradableExtensions(extensionManager);
                } else {
                    System.err.println(MessageFormat.format(
                        bundle.getString("error.ext.invalidListMode"),
                        mode
                    ));
                    return 1;
                }
            } catch (IOException e) {
                System.err.println(MessageFormat.format(
                    bundle.getString("error.ext.listFailed"),
                    e.getMessage()
                ));
                return 1;
            }
        }

        private int listInstalledExtensions(ExtensionManager extensionManager) throws IOException {
            List<ExtensionManager.ExtensionInfo> extensions = extensionManager.listInstalledExtensions();

            if (extensions.isEmpty()) {
                System.out.println(bundle.getString("msg.ext.noExtensions"));
                return 0;
            }

            System.out.println(bundle.getString("msg.ext.installedExtensions"));
            System.out.println();

            for (ExtensionManager.ExtensionInfo ext : extensions) {
                String languages = ext.manifest.languages != null && !ext.manifest.languages.isEmpty()
                    ? String.join(", ", ext.manifest.languages)
                    : "N/A";

                System.out.println(String.format("%s\t%s\t%s\tv%s\t%s",
                    ext.manifest.name,
                    ext.manifest.type,
                    languages,
                    ext.manifest.version,
                    ext.manifest.description != null ? ext.manifest.description : ""
                ));
            }

            return 0;
        }

        private int listAvailableExtensions(ExtensionManager extensionManager) throws IOException {
            List<ExtensionManager.RegistryExtension> extensions = 
                extensionManager.listAvailableExtensions(typeFilter, nameFilter, languageFilter);

            if (extensions.isEmpty()) {
                System.out.println(bundle.getString("msg.ext.noAvailableExtensions"));
                return 0;
            }

            System.out.println(bundle.getString("msg.ext.availableExtensions"));
            if (typeFilter != null) {
                System.out.println(MessageFormat.format(
                    bundle.getString("msg.ext.filteredByType"),
                    typeFilter
                ));
            }
            if (nameFilter != null) {
                System.out.println(MessageFormat.format(
                    bundle.getString("msg.ext.filteredByName"),
                    nameFilter
                ));
            }
            if (languageFilter != null) {
                System.out.println(MessageFormat.format(
                    bundle.getString("msg.ext.filteredByLanguage"),
                    languageFilter
                ));
            }
            System.out.println();

            for (ExtensionManager.RegistryExtension ext : extensions) {
                String languages = ext.languages != null && !ext.languages.isEmpty()
                    ? String.join(", ", ext.languages)
                    : "N/A";

                System.out.println(String.format("%s\t%s\t%s\tv%s\t%s",
                    ext.name,
                    ext.type,
                    languages,
                    ext.version,
                    ext.description != null ? ext.description : ""
                ));
            }

            System.out.println();
            System.out.println(MessageFormat.format(
                bundle.getString("msg.ext.totalAvailable"),
                extensions.size()
            ));

            return 0;
        }

        private int listUpgradableExtensions(ExtensionManager extensionManager) throws IOException {
            List<ExtensionManager.ExtensionInfo> upgradable = extensionManager.listUpgradableExtensions();

            if (upgradable.isEmpty()) {
                System.out.println(bundle.getString("msg.ext.noUpgradableExtensions"));
                return 0;
            }

            System.out.println(bundle.getString("msg.ext.upgradableExtensions"));
            System.out.println();

            // Get available extensions to find new versions
            List<ExtensionManager.RegistryExtension> available = extensionManager.listAvailableExtensions(null, null, null);
            Map<String, String> availableVersions = available.stream()
                .collect(Collectors.toMap(ext -> ext.name, ext -> ext.version));

            for (ExtensionManager.ExtensionInfo ext : upgradable) {
                String languages = ext.manifest.languages != null && !ext.manifest.languages.isEmpty()
                    ? String.join(", ", ext.manifest.languages)
                    : "N/A";

                String newVersion = availableVersions.get(ext.manifest.name);
                String versionInfo = newVersion != null 
                    ? String.format("v%s → v%s", ext.manifest.version, newVersion)
                    : "v" + ext.manifest.version;

                System.out.println(String.format("%s\t%s\t%s\t%s\t%s",
                    ext.manifest.name,
                    ext.manifest.type,
                    languages,
                    versionInfo,
                    ext.manifest.description != null ? ext.manifest.description : ""
                ));
            }

            System.out.println();
            System.out.println(MessageFormat.format(
                bundle.getString("msg.ext.totalUpgradable"),
                upgradable.size()
            ));

            return 0;
        }

        private int showExtensionInfo(ExtensionManager extensionManager, String extensionName) {
            try {
                List<ExtensionManager.ExtensionInfo> installedExtensions = 
                    extensionManager.listInstalledExtensions();
                ExtensionManager.ExtensionInfo installedExt = installedExtensions.stream()
                    .filter(ext -> ext.manifest.name.equals(extensionName))
                    .findFirst()
                    .orElse(null);

                List<ExtensionManager.RegistryExtension> availableExtensions = 
                    extensionManager.listAvailableExtensions(null, extensionName);
                ExtensionManager.RegistryExtension availableExt = availableExtensions.stream()
                    .filter(ext -> ext.name.equals(extensionName))
                    .findFirst()
                    .orElse(null);

                if (installedExt == null && availableExt == null) {
                    System.err.println(MessageFormat.format(
                        bundle.getString("error.extension.notFound"),
                        extensionName
                    ));
                    return 1;
                }

                System.out.println(bundle.getString("msg.ext.extensionInfo"));
                System.out.println();

                if (installedExt != null) {
                    displayInstalledInfo(installedExt);
                } else {
                    displayAvailableInfo(availableExt);
                }

                if (installedExt != null && availableExt != null) {
                    System.out.println();
                    if (!installedExt.manifest.version.equals(availableExt.version)) {
                        System.out.println(MessageFormat.format(
                            bundle.getString("msg.ext.updateAvailable"),
                            availableExt.version
                        ));
                    } else {
                        System.out.println(bundle.getString("msg.ext.upToDate"));
                    }
                }

                return 0;
            } catch (IOException e) {
                System.err.println(MessageFormat.format(
                    bundle.getString("error.ext.showFailed"),
                    e.getMessage()
                ));
                return 1;
            }
        }

        private void displayInstalledInfo(ExtensionManager.ExtensionInfo ext) {
            System.out.println(MessageFormat.format(
                bundle.getString("msg.ext.info.name"),
                ext.manifest.name
            ));
            System.out.println(MessageFormat.format(
                bundle.getString("msg.ext.info.version"),
                ext.manifest.version
            ));
            System.out.println(MessageFormat.format(
                bundle.getString("msg.ext.info.type"),
                ext.manifest.type != null ? ext.manifest.type : "unknown"
            ));

            if (ext.manifest.description != null) {
                System.out.println(MessageFormat.format(
                    bundle.getString("msg.ext.info.description"),
                    ext.manifest.description
                ));
            }

            if (ext.manifest.author != null) {
                System.out.println(MessageFormat.format(
                    bundle.getString("msg.ext.info.author"),
                    ext.manifest.author
                ));
            }

            if (ext.manifest.languages != null && !ext.manifest.languages.isEmpty()) {
                System.out.println(MessageFormat.format(
                    bundle.getString("msg.ext.info.languages"),
                    String.join(", ", ext.manifest.languages)
                ));
            }

            System.out.println();
            System.out.println(bundle.getString("msg.ext.info.status") + " " + 
                bundle.getString("msg.ext.info.installed"));

            if (ext.manifest.files != null) {
                System.out.println();
                System.out.println(bundle.getString("msg.ext.info.files"));

                if (ext.manifest.files.mappings != null && !ext.manifest.files.mappings.isEmpty()) {
                    System.out.println("  " + bundle.getString("msg.ext.info.mappings"));
                    for (String file : ext.manifest.files.mappings) {
                        System.out.println("    - " + file);
                    }
                }

                if (ext.manifest.files.resources != null && !ext.manifest.files.resources.isEmpty()) {
                    System.out.println("  " + bundle.getString("msg.ext.info.resources"));
                    for (String file : ext.manifest.files.resources) {
                        System.out.println("    - " + file);
                    }
                }

                if (ext.manifest.files.themes != null && !ext.manifest.files.themes.isEmpty()) {
                    System.out.println("  " + bundle.getString("msg.ext.info.themes"));
                    for (String file : ext.manifest.files.themes) {
                        System.out.println("    - " + file);
                    }
                }
            }
        }

        private void displayAvailableInfo(ExtensionManager.RegistryExtension ext) {
            System.out.println(MessageFormat.format(
                bundle.getString("msg.ext.info.name"),
                ext.name
            ));
            System.out.println(MessageFormat.format(
                bundle.getString("msg.ext.info.version"),
                ext.version
            ));
            System.out.println(MessageFormat.format(
                bundle.getString("msg.ext.info.type"),
                ext.type
            ));

            if (ext.languages != null && !ext.languages.isEmpty()) {
                System.out.println(MessageFormat.format(
                    bundle.getString("msg.ext.info.languages"),
                    String.join(", ", ext.languages)
                ));
            }

            if (ext.description != null) {
                System.out.println(MessageFormat.format(
                    bundle.getString("msg.ext.info.description"),
                    ext.description
                ));
            }

            if (ext.author != null) {
                System.out.println(MessageFormat.format(
                    bundle.getString("msg.ext.info.author"),
                    ext.author
                ));
            }

            if (ext.size != null) {
                System.out.println(MessageFormat.format(
                    bundle.getString("msg.ext.info.size"),
                    ext.size / 1024.0
                ));
            }

            if (ext.publishedDate != null) {
                System.out.println(MessageFormat.format(
                    bundle.getString("msg.ext.info.published"),
                    ext.publishedDate
                ));
            }

            System.out.println();
            System.out.println(bundle.getString("msg.ext.info.status") + " " + 
                bundle.getString("msg.ext.info.notInstalled"));
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

        @Option(names = {"-l", "--language"}, descriptionKey = "language")
        private String userLanguage;

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
                // Extract module language
                String moduleLanguage = BookMapper.extractModuleLanguage(modulePath);

                VerseIndexManager indexManager = new VerseIndexManager(configManager, verbosity);
                BookMapper bookMapper;

                if (useModuleAbbreviations) {
                    AbbreviationManager abbrManager = new AbbreviationManager(configManager, verbosity);
                    Path abbrFile = abbrManager.ensureAbbreviationFile(moduleName, modulePath);
                    bookMapper = new BookMapper(abbrManager.loadAbbreviations(abbrFile));
                } else if (customMappingPrefix != null) {
                    bookMapper = BookMappingManager.getBookMapper(configManager, customMappingPrefix, userLanguage, moduleLanguage);
                } else {
                    bookMapper = BookMappingManager.getBookMapper(configManager, null, userLanguage, moduleLanguage);
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

    private static String compactArrayField(String json, String fieldName) {
        Pattern pattern = Pattern.compile(
            "\"" + fieldName + "\":\\s*\\[\\s*([^\\]]+?)\\s*\\]",
            Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(json);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String arrayContent = matcher.group(1);
            String compacted = arrayContent
                .replaceAll("\\s+", " ")
                .replaceAll(",\\s+", ", ")
                .trim();
            matcher.appendReplacement(result, "\"" + fieldName + "\": [" + compacted + "]");
        }
        matcher.appendTail(result);
        return result.toString();
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
