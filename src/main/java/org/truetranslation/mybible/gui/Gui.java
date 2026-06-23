package org.truetranslation.mybible.gui;

import com.formdev.flatlaf.FlatLightLaf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Vector;
import javax.swing.*;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.text.BadLocationException;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.rtf.RTFEditorKit;

import org.truetranslation.mybible.core.*;
import org.truetranslation.mybible.core.model.Book;
import org.truetranslation.mybible.core.model.GuiVerse;
import org.truetranslation.mybible.core.model.Reference;
import org.truetranslation.mybible.core.model.Verse;

public class Gui extends JFrame {

    private static final String[] LANGUAGE_CODES = {
        "",
        "aa", "ab", "ae", "af", "ak", "am", "an", "ar", "as", "av", "ay", "az",
        "ba", "be", "bg", "bh", "bi", "bm", "bn", "bo", "br", "bs",
        "ca", "ce", "ch", "co", "cr", "cs", "cu", "cv", "cy",
        "da", "de", "dv", "dz",
        "ee", "el", "en", "eo", "es", "et", "eu",
        "fa", "ff", "fi", "fj", "fo", "fr", "fy",
        "ga", "gd", "gl", "gn", "gu", "gv",
        "ha", "he", "hi", "ho", "hr", "ht", "hu", "hy", "hz",
        "ia", "id", "ie", "ig", "ii", "ik", "io", "is", "it", "iu",
        "ja", "jv",
        "ka", "kg", "ki", "kj", "kk", "kl", "km", "kn", "ko", "kr", "ks", "ku", "kv", "kw", "ky",
        "la", "lb", "lg", "li", "ln", "lo", "lt", "lu", "lv",
        "mg", "mh", "mi", "mk", "ml", "mn", "mr", "ms", "mt", "my",
        "na", "nb", "nd", "ne", "ng", "nl", "nn", "no", "nr", "nv", "ny",
        "oc", "oj", "om", "or", "os",
        "pa", "pi", "pl", "ps", "pt",
        "qu",
        "rm", "rn", "ro", "ru", "rw",
        "sa", "sc", "sd", "se", "sg", "si", "sk", "sl", "sm", "sn", "so", "sq", "sr", "ss", "st", "su", "sv", "sw",
        "ta", "te", "tg", "th", "ti", "tk", "tl", "tn", "to", "tr", "ts", "tt", "tw", "ty",
        "ug", "uk", "ur", "uz",
        "ve", "vi", "vo",
        "wa", "wo",
        "xh",
        "yi", "yo",
        "za", "zh", "zu"
    };

    private JTextPane textDisplayPane;
    private JScrollPane textScrollPane;
    private JComboBox<HistoryEntry> referenceInputField;
    private final LinkedList<HistoryEntry> referenceHistory = new LinkedList<>();
    private int historyIndex = -1;
    private JButton historyBackButton;
    private JButton historyFwdButton;
    private JComboBox<ModuleScanner.Module> moduleComboBox;
    private JTextField modulePathField;
    private JCheckBox rawJsonCheckbox;
    private JTextField mappingFileField;
    private JComboBox<String> languageComboBox;
    private JCheckBox useModuleAbbrsCheckbox;

    // collapsible advanced panel
    private JPanel advancedPanel;
    private JButton toggleAdvancedButton;
    private boolean advancedExpanded = false;
    private boolean ignoreNextResize = false;

    //buttons within this panel
    private JButton mapBrowseButton;
    private JButton setModulePathButton;

    private final ConfigManager configManager;
    private final ModuleScanner moduleScanner;
    private final Runnable onWindowClosed;
    private final ResourceBundle bundle;
    private final GuiConfigManager guiConfigManager;
    private GuiConfig guiConfig;
    private Path customMappingPath = null;

    public Gui(String initialModule, String initialReference, Runnable onWindowClosed) {
        this.configManager = new ConfigManager();
        this.moduleScanner = new ModuleScanner();
        this.onWindowClosed = onWindowClosed;
        ExternalResourceBundleLoader externalLoader = new ExternalResourceBundleLoader(
            configManager.getDefaultConfigDir()
        );
        this.bundle = externalLoader.getBundle("i18n.gui");
        this.guiConfigManager = new GuiConfigManager();
        this.guiConfig = guiConfigManager.getConfig();

        setTitle(bundle.getString("window.title"));
        loadAppIcon();
        initUI();
        initKeyboardShortcuts();
        loadModules();

        if (initialModule != null) {
            setSelectedModule(initialModule);
        } else {
            loadLastUsedModule();
        }
        if (initialReference != null) {
            referenceInputField.getEditor().setItem(initialReference);
        }
        if (initialModule != null || initialReference != null) {
            SwingUtilities.invokeLater(this::updateAndDisplayVerseData);
        }
    }

    public void setTextSpacing(JTextPane textPane, float lineSpacing, float spaceAbove, float spaceBelow) {
        StyledDocument doc = textPane.getStyledDocument();
        MutableAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(attrs, lineSpacing);
        StyleConstants.setSpaceAbove(attrs, spaceAbove);
        StyleConstants.setSpaceBelow(attrs, spaceBelow);
        doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
    }

    private void loadAppIcon() {
        try {
            java.net.URL iconURL = getClass().getClassLoader().getResource("icons/mybible-cli.png");
            if (iconURL != null) {
                Image image = Toolkit.getDefaultToolkit().getImage(iconURL);
                setIconImage(image);
                if (Taskbar.isTaskbarSupported()) {
                    Taskbar taskbar = Taskbar.getTaskbar();
                    if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                        taskbar.setIconImage(image);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                quitApplication();
            }
        });
        setSize(800, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        textDisplayPane = new JTextPane();
        textDisplayPane.setEditable(false);
        textDisplayPane.setMargin(new Insets(5, 5, 5, 5));
        textScrollPane = new JScrollPane(textDisplayPane);
        textScrollPane.setPreferredSize(new Dimension(780, 400));
        add(textScrollPane, BorderLayout.CENTER);

        // Track manual window resizes to update textScrollPane preferred size,
        // but ignore resizes triggered by the collapse/expand toggle.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (ignoreNextResize) {
                    ignoreNextResize = false;
                    return;
                }
                textScrollPane.setPreferredSize(new Dimension(
                    textScrollPane.getWidth(),
                    textScrollPane.getHeight()));
            }
        });

        JPanel bottomContainer = new JPanel(new BorderLayout(0, 10));
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0 — reference input
        gbc.gridy = 0; gbc.gridx = 0; gbc.weightx = 0;
        inputPanel.add(new JLabel(bundle.getString("label.bibleReference")), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        referenceInputField = new JComboBox<>();
        referenceInputField.setEditable(true);
        referenceInputField.setPrototypeDisplayValue(new HistoryEntry("Genesis 1:1-10", ""));
        referenceInputField.setEditor(new BasicComboBoxEditor() {
            @Override
            public void setItem(Object item) {
                if (item instanceof HistoryEntry) {
                    super.setItem(((HistoryEntry) item).reference);
                } else {
                    super.setItem(item);
                }
            }
        });
        inputPanel.add(referenceInputField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        JButton showButton = new JButton(bundle.getString("button.show"));
        inputPanel.add(showButton, gbc);

        // Row 1 — history navigation buttons
        gbc.gridy = 1; gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 1;
        gbc.insets = new Insets(0, 5, 2, 5);
        historyBackButton = new JButton("◀");
        historyFwdButton  = new JButton("▶");
        historyBackButton.setToolTipText("Previous reference (Alt+B)");
        historyFwdButton.setToolTipText("Next reference (Alt+F)");
        historyBackButton.setEnabled(false);
        historyFwdButton.setEnabled(false);
        historyBackButton.setFocusable(false);
        historyFwdButton.setFocusable(false);
        JPanel navPanel = new JPanel(new GridLayout(1, 2, 4, 0));
        navPanel.add(historyBackButton);
        navPanel.add(historyFwdButton);
        inputPanel.add(navPanel, gbc);
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.gridwidth = 1;

        // Row 2 — module selector
        gbc.gridy = 2; gbc.gridx = 0; gbc.weightx = 0;
        inputPanel.add(new JLabel(bundle.getString("label.module")), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        moduleComboBox = new JComboBox<>();
        moduleComboBox.setRenderer(new ModuleRenderer());
        inputPanel.add(moduleComboBox, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        JButton infoButton = new JButton(bundle.getString("button.info"));
        inputPanel.add(infoButton, gbc);

        // Row 3 — toggle button for advanced options
        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 3; gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 5, 0, 5);
        toggleAdvancedButton = new JButton("▼  " + bundle.getString("button.advancedOptions"));
        toggleAdvancedButton.setHorizontalAlignment(SwingConstants.LEFT);
        toggleAdvancedButton.setBorderPainted(false);
        toggleAdvancedButton.setContentAreaFilled(false);
        toggleAdvancedButton.setFocusPainted(false);
        toggleAdvancedButton.setMargin(new Insets(0, 0, 0, 0));
        toggleAdvancedButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        //toggleAdvancedButton.setFont(toggleAdvancedButton.getFont().deriveFont(Font.BOLD));
        inputPanel.add(toggleAdvancedButton, gbc);
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.gridwidth = 1;

        // Row 4 — collapsible advanced panel
        advancedPanel = new JPanel(new GridBagLayout());
        advancedPanel.setVisible(false);
        GridBagConstraints agbc = new GridBagConstraints();
        agbc.insets = new Insets(2, 5, 2, 5);
        agbc.fill = GridBagConstraints.HORIZONTAL;

        agbc.gridy = 0; agbc.gridx = 0; agbc.weightx = 0;
        advancedPanel.add(new JLabel(bundle.getString("label.modulePath")), agbc);
        agbc.gridx = 1; agbc.weightx = 1.0;
        modulePathField = new JTextField(configManager.getModulesPath());
        modulePathField.setEditable(false);
        advancedPanel.add(modulePathField, agbc);
        agbc.gridx = 2; agbc.weightx = 0;
        setModulePathButton = new JButton(bundle.getString("button.modulePathSet"));
        advancedPanel.add(setModulePathButton, agbc);

        agbc.gridy = 1; agbc.gridx = 1; agbc.gridwidth = 2;
        rawJsonCheckbox = new JCheckBox(bundle.getString("checkbox.rawJson"));
        rawJsonCheckbox.setToolTipText(bundle.getString("dialog.tooltip.rawJson"));
        rawJsonCheckbox.setSelected(guiConfig.showRawJson);
        advancedPanel.add(rawJsonCheckbox, agbc);

        agbc.gridy = 2; agbc.gridx = 0; agbc.gridwidth = 1; agbc.weightx = 0;
        advancedPanel.add(new JLabel(bundle.getString("label.mappingFile")), agbc);
        agbc.gridx = 1; agbc.weightx = 1.0;
        mappingFileField = new JTextField("default_mapping.json");
        mappingFileField.setEditable(false);
        mappingFileField.setToolTipText(bundle.getString("dialog.tooltip.mappingFile"));
        advancedPanel.add(mappingFileField, agbc);
        agbc.gridx = 2; agbc.weightx = 0;
        mapBrowseButton = new JButton(bundle.getString("button.browse"));
        advancedPanel.add(mapBrowseButton, agbc);

        agbc.gridy = 3; agbc.gridx = 0; agbc.gridwidth = 1; agbc.weightx = 0;
        advancedPanel.add(new JLabel(bundle.getString("label.abbrLanguage")), agbc);
        agbc.gridx = 1; agbc.weightx = 1.0; agbc.gridwidth = 1;
        languageComboBox = new JComboBox<>(LANGUAGE_CODES);
        languageComboBox.setEditable(true);
        languageComboBox.setSelectedIndex(0);
        languageComboBox.setToolTipText(bundle.getString("dialog.tooltip.abbrLanguage"));
        advancedPanel.add(languageComboBox, agbc);

        agbc.gridy = 4; agbc.gridx = 1; agbc.gridwidth = 2;
        useModuleAbbrsCheckbox = new JCheckBox(bundle.getString("label.useModuleAbbrs"));
        useModuleAbbrsCheckbox.setSelected(guiConfig.useModuleAbbreviations);
        useModuleAbbrsCheckbox.setToolTipText(bundle.getString("dialog.tooltip.useModuleAbbrs"));
        advancedPanel.add(useModuleAbbrsCheckbox, agbc);

        advancedPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 3; gbc.weightx = 1.0;
        inputPanel.add(advancedPanel, gbc);
        gbc.gridwidth = 1;
        
        JButton[] rightColumnButtons = { showButton, infoButton, setModulePathButton, mapBrowseButton };
        int maxWidth = 0;
        for (JButton b : rightColumnButtons) {
            maxWidth = Math.max(maxWidth, b.getPreferredSize().width);
        }
        Dimension unifiedSize = new Dimension(maxWidth, showButton.getPreferredSize().height);
        for (JButton b : rightColumnButtons) {
            b.setPreferredSize(unifiedSize);
        }

        bottomContainer.add(inputPanel, BorderLayout.NORTH);

        JPanel actionButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton configureButton = new JButton(bundle.getString("button.configure"));
        JButton copyButton      = new JButton(bundle.getString("button.copyDisplayedText"));
        JButton quitButton      = new JButton(bundle.getString("button.quit"));
        actionButtonPanel.add(configureButton);
        actionButtonPanel.add(copyButton);
        actionButtonPanel.add(quitButton);
        bottomContainer.add(actionButtonPanel, BorderLayout.SOUTH);
        add(bottomContainer, BorderLayout.SOUTH);

        // ---- Action listeners ----
        showButton.addActionListener(e -> updateAndDisplayVerseData());
        referenceInputField.addActionListener(e -> updateAndDisplayVerseData());
        moduleComboBox.addActionListener(e -> updateAndDisplayVerseData());
        languageComboBox.addActionListener(e -> updateAndDisplayVerseData());
        useModuleAbbrsCheckbox.addActionListener(e -> {
            guiConfig.useModuleAbbreviations = useModuleAbbrsCheckbox.isSelected();
            guiConfigManager.setConfig(guiConfig);
            guiConfigManager.saveConfig();
            updateMappingRowsEnabled();
            updateAndDisplayVerseData();
        });
        rawJsonCheckbox.addActionListener(e -> {
            guiConfig.showRawJson = rawJsonCheckbox.isSelected();
            guiConfigManager.saveConfig();
            updateAndDisplayVerseData();
        });
        infoButton.addActionListener(e -> showModuleInfo());
        setModulePathButton.addActionListener(e -> selectModulePath());
        configureButton.addActionListener(e -> openConfigurationDialog());
        copyButton.addActionListener(e -> copyRichTextToClipboard());
        quitButton.addActionListener(e -> quitApplication());
        mapBrowseButton.addActionListener(e -> openMapBrowser());
        historyBackButton.addActionListener(e -> navigateBack());
        historyFwdButton.addActionListener(e -> navigateForward());
        toggleAdvancedButton.addActionListener(e -> toggleAdvancedPanel());
    }

    private void toggleAdvancedPanel() {
        int panelHeight = advancedPanel.getPreferredSize().height;

        advancedExpanded = !advancedExpanded;
        advancedPanel.setVisible(advancedExpanded);
        toggleAdvancedButton.setText(
            (advancedExpanded ? "▼  " : "▶  ") + bundle.getString("button.advancedOptions"));

        ignoreNextResize = true;
        Dimension size = getSize();
        size.height += advancedExpanded ? panelHeight : -panelHeight;
        setSize(size);

        revalidate();
    }

    // History navigation
    private void navigateBack() {
        if (referenceHistory.isEmpty()) return;
        historyIndex = Math.min(historyIndex + 1, referenceHistory.size() - 1);
        applyHistoryEntry(referenceHistory.get(historyIndex));
        updateNavigationButtonStates();
        displayVerseDataForCurrentHistoryEntry();
        if (historyIndex >= referenceHistory.size() - 1) {
            historyFwdButton.requestFocusInWindow();
        }
    }

    private void navigateForward() {
        if (historyIndex <= 0) {
            historyIndex = -1;
            updateNavigationButtonStates();
            historyBackButton.requestFocusInWindow();
            return;
        }
        historyIndex--;
        applyHistoryEntry(referenceHistory.get(historyIndex));
        updateNavigationButtonStates();
        displayVerseDataForCurrentHistoryEntry();
        if (historyIndex <= 0) {
            historyBackButton.requestFocusInWindow();
        }
    }

    private void updateNavigationButtonStates() {
        boolean multipleEntries = referenceHistory.size() > 1;
        historyBackButton.setEnabled(multipleEntries && historyIndex < referenceHistory.size() - 1);
        historyFwdButton.setEnabled(multipleEntries && historyIndex > 0);
    }

    private void applyHistoryEntry(HistoryEntry entry) {
        referenceInputField.getEditor().setItem(entry.reference);
        setSelectedModule(entry.moduleName);
    }

    private void displayVerseDataForCurrentHistoryEntry() {
        textDisplayPane.setBackground(guiConfig.textAreaBackground != null
            ? guiConfig.textAreaBackground
            : UIManager.getColor("TextPane.background"));
        StyledDocument doc = textDisplayPane.getStyledDocument();
        setTextSpacing(textDisplayPane, 0.2f, 2.0f, 2.0f);

        ModuleScanner.Module selectedModule = (ModuleScanner.Module) moduleComboBox.getSelectedItem();
        String reference = (String) referenceInputField.getEditor().getItem();

        if (selectedModule == null || reference == null || reference.trim().isEmpty()) {
            try { doc.remove(0, doc.getLength()); } catch (BadLocationException e) { /* ignore */ }
            return;
        }

        VerseFetcher fetcher = null;
        try {
            String userLanguage = (String) languageComboBox.getSelectedItem();
            if (userLanguage != null) {
                userLanguage = userLanguage.trim();
                if (userLanguage.isEmpty()) userLanguage = null;
            }

            String moduleLanguage = BookMapper.extractModuleLanguage(selectedModule.getPath());
            BookMapper defaultBookMapper;
            BookMapper moduleBookMapper;

            AbbreviationManager abbrManager = new AbbreviationManager(configManager, 0);
            Path abbrFile = abbrManager.ensureAbbreviationFile(selectedModule.getName(), selectedModule.getPath());
            moduleBookMapper = new BookMapper(abbrManager.loadAbbreviations(abbrFile));

            if (guiConfig.useModuleAbbreviations) {
                defaultBookMapper = moduleBookMapper;
            } else {
                if (customMappingPath != null && Files.exists(customMappingPath)) {
                    defaultBookMapper = new BookMapper(Files.newInputStream(customMappingPath), userLanguage, moduleLanguage);
                } else {
                    defaultBookMapper = BookMappingManager.getBookMapper(configManager, null, userLanguage, moduleLanguage);
                }
            }

            VerseIndexManager indexManager = new VerseIndexManager(configManager, 0);
            Map<Integer, Integer> verseIndex = indexManager.getVerseIndex(selectedModule.getName(), selectedModule.getPath());
            ReferenceParser parser = new ReferenceParser(defaultBookMapper, verseIndex);

            List<ReferenceParser.RangeWithCount> ranges = parser.parseWithCounts(reference);
            if (ranges.isEmpty()) {
                insertDefaultStyledText(doc, MessageFormat.format(
                    bundle.getString("dialog.message.invalidReference"), reference));
                return;
            }

            fetcher = new VerseFetcher(selectedModule.getPath());
            List<Verse> verses = fetcher.fetch(new ArrayList<>(ranges));
            List<GuiVerse> guiVerses = buildGuiVerses(verses, ranges, defaultBookMapper,
                moduleBookMapper, moduleLanguage, userLanguage, selectedModule.getName());

            if (guiConfig.showRawJson) {
                Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
                insertDefaultStyledText(doc, compactArrayField(gson.toJson(guiVerses), "allBookNames"));
            } else {
                new GuiTextFormatter(guiConfig).format(guiVerses, doc);
            }
            textDisplayPane.setCaretPosition(0);
            configManager.setLastUsedModule(selectedModule.getName());

        } catch (Exception e) {
            insertDefaultStyledText(doc, MessageFormat.format(
                bundle.getString("dialog.message.errorFetching"), e.getMessage()));
            e.printStackTrace();
        } finally {
            if (fetcher != null) {
                try { fetcher.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    private void selectModulePath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(bundle.getString("dialog.title.selectModulePath"));
        chooser.setAcceptAllFileFilterUsed(false);
        String currentPath = configManager.getModulesPath();
        if (currentPath != null && !currentPath.isEmpty()) {
            Path currentDir = Paths.get(currentPath);
            if (Files.exists(currentDir)) {
                chooser.setCurrentDirectory(currentDir.toFile());
            }
        }
        int result = chooser.showDialog(this, bundle.getString("button.modulePathSelect"));
        if (result == JFileChooser.APPROVE_OPTION) {
            Path selectedPath = chooser.getSelectedFile().toPath();
            configManager.setModulesPath(selectedPath.toString());
            modulePathField.setText(configManager.getModulesPath());
            loadModules();
            JOptionPane.showMessageDialog(this,
                MessageFormat.format(bundle.getString("dialog.message.modulePathSet"), selectedPath.toString()),
                bundle.getString("dialog.title.success"),
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public void updateModulePath(String newPath) {
        configManager.setModulesPath(newPath);
        modulePathField.setText(newPath);
        loadModules();
    }

    private void initKeyboardShortcuts() {
        JRootPane rootPane = getRootPane();
        InputMap inputMap   = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "quitAction");
        actionMap.put("quitAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { quitApplication(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, menuMask), "focusRefAction");
        actionMap.put("focusRefAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { referenceInputField.requestFocusInWindow(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, menuMask), "showReferenceAction");
        actionMap.put("showReferenceAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { updateAndDisplayVerseData(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, menuMask | KeyEvent.SHIFT_DOWN_MASK), "setModulePathAction");
        actionMap.put("setModulePathAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { selectModulePath(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, menuMask | KeyEvent.SHIFT_DOWN_MASK), "mapBrowseAction");
        actionMap.put("mapBrowseAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { openMapBrowser(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, menuMask | KeyEvent.SHIFT_DOWN_MASK), "focusLangAction");
        actionMap.put("focusLangAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { languageComboBox.requestFocusInWindow(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_M, menuMask), "focusModuleAction");
        actionMap.put("focusModuleAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { moduleComboBox.requestFocusInWindow(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_J, menuMask), "toggleJsonAction");
        actionMap.put("toggleJsonAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { rawJsonCheckbox.doClick(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, menuMask), "toggleModuleAbbrAction");
        actionMap.put("toggleModuleAbbrAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { useModuleAbbrsCheckbox.doClick(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, menuMask), "infoAction");
        actionMap.put("infoAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { showModuleInfo(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask | KeyEvent.SHIFT_DOWN_MASK), "copyAction");
        actionMap.put("copyAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { copyRichTextToClipboard(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, menuMask), "configureAction");
        actionMap.put("configureAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { openConfigurationDialog(); }
        });

        Action historyBackAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) { historyBackButton.doClick(); }
        };
        Action historyFwdAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) { historyFwdButton.doClick(); }
        };

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.ALT_DOWN_MASK), "historyBackAction");
        actionMap.put("historyBackAction", historyBackAction);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.ALT_DOWN_MASK), "historyFwdAction");
        actionMap.put("historyFwdAction", historyFwdAction);

        JTextField editor = (JTextField) referenceInputField.getEditor().getEditorComponent();
        editor.getActionMap().put("previous-word", null);
        editor.getActionMap().put("next-word",     null);
        InputMap editorMap = editor.getInputMap(JComponent.WHEN_FOCUSED);
        editorMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.ALT_DOWN_MASK), "historyBackAction");
        editorMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.ALT_DOWN_MASK), "historyFwdAction");
        editor.getActionMap().put("historyBackAction", historyBackAction);
        editor.getActionMap().put("historyFwdAction",  historyFwdAction);
    }

    private void copyRichTextToClipboard() {
        StyledDocument doc = textDisplayPane.getStyledDocument();
        String plainText = textDisplayPane.getText();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            RTFEditorKit rtfKit = new RTFEditorKit();
            rtfKit.write(out, doc, 0, doc.getLength());
            byte[] rtfBytes = out.toByteArray();
            RtfTransferable transferable = new RtfTransferable(plainText, rtfBytes);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
        } catch (Exception ex) {
            StringSelection stringSelection = new StringSelection(plainText);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
            ex.printStackTrace();
        }
    }

    private void openConfigurationDialog() {
        ConfigurationDialog dialog = new ConfigurationDialog(this, guiConfigManager);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                refreshModuleList();
            }
        });
        dialog.setVisible(true);
        this.guiConfig = guiConfigManager.getConfig();
        updateAndDisplayVerseData();
    }

    public void refreshModuleList() {
        try {
            String currentSelectionName = null;
            if (moduleComboBox.getSelectedItem() != null) {
                currentSelectionName = ((ModuleScanner.Module) moduleComboBox.getSelectedItem()).getName();
            }
            moduleComboBox.removeAllItems();
            Path modulePath = Paths.get(configManager.getModulesPath());
            if (modulePath != null && Files.exists(modulePath)) {
                ModuleScanner scanner = new ModuleScanner();
                List<ModuleScanner.Module> modules = scanner.findModules(modulePath);
                for (ModuleScanner.Module module : modules) {
                    moduleComboBox.addItem(module);
                }
                if (currentSelectionName != null) {
                    for (int i = 0; i < moduleComboBox.getItemCount(); i++) {
                        if (moduleComboBox.getItemAt(i).getName().equals(currentSelectionName)) {
                            moduleComboBox.setSelectedIndex(i);
                            break;
                        }
                    }
                } else if (moduleComboBox.getItemCount() > 0) {
                    moduleComboBox.setSelectedIndex(0);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to refresh module list: " + e.getMessage());
        }
    }

    private void openMapBrowser() {
        JFileChooser chooser = new JFileChooser(
            configManager.getDefaultConfigDir().resolve("mapping").toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("JSON Mapping Files", "json"));
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            Path selectedFile = chooser.getSelectedFile().toPath();
            try {
                if (BookMapper.isValidMappingFile(Files.newInputStream(selectedFile))) {
                    customMappingPath = selectedFile;
                    mappingFileField.setText(customMappingPath.getFileName().toString());
                    updateAndDisplayVerseData();
                } else {
                    JOptionPane.showMessageDialog(this,
                        bundle.getString("error.gui.invalidMappingFile"),
                        bundle.getString("error.gui.invalidMappingTitle"),
                        JOptionPane.ERROR_MESSAGE);
                    customMappingPath = null;
                    mappingFileField.setText("default_mapping.json");
                    updateAndDisplayVerseData();
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                    MessageFormat.format(bundle.getString("error.gui.fileReadError"), ex.getMessage()),
                    bundle.getString("error.gui.fileErrorTitle"),
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void quitApplication() {
        dispose();
        onWindowClosed.run();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private static String sanitizeDetailedInfo(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        s = s.replaceAll("(?i)</body\\s*>", "");
        s = s.replaceAll("(?i)</html\\s*>", "");
        s = s.replaceAll("(?i)<p\\s*/>",    "<br><br>");
        s = s.replaceAll("(?i)<br\\s*/>",   "<br>");
        s = s.replaceAll("(<br>\\s*){3,}",  "<br><br>");
        return s.trim();
    }

    private void showModuleInfo() {
        ModuleScanner.Module selectedModule = (ModuleScanner.Module) moduleComboBox.getSelectedItem();
        if (selectedModule == null) return;

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: sans-serif; padding: 4px;'>");
        html.append("<h3>").append(escapeHtml(selectedModule.getName())).append("</h3>");

        String lang = selectedModule.getLanguage();
        if (lang != null && !lang.isEmpty()) {
            html.append("<p><b>").append(bundle.getString("moduleMgr.info.language"))
                .append("</b> ").append(escapeHtml(lang)).append("</p>");
        }

        String desc = selectedModule.getDescription();
        if (desc != null && !desc.isEmpty()) {
            html.append("<p><b>").append(bundle.getString("moduleMgr.info.description"))
                .append("</b><br>").append(escapeHtml(desc)).append("</p>");
        }

        String detail = selectedModule.getDetailedInfo();
        if (detail != null && !detail.isEmpty()) {
            html.append("<p>").append(sanitizeDetailedInfo(detail)).append("</p>");
        }

        html.append("</body></html>");

        JTextPane textPane = new JTextPane();
        textPane.setContentType("text/html");
        textPane.setText(html.toString());
        textPane.setEditable(false);
        textPane.setBackground(UIManager.getColor("Panel.background"));
        textPane.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(textPane);
        scroll.setPreferredSize(new Dimension(450, 380));
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JOptionPane.showMessageDialog(
            this, scroll,
            MessageFormat.format(bundle.getString("moduleMgr.info.title"), selectedModule.getName()),
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void loadModules() {
        try {
            Path modulesDir = Paths.get(configManager.getModulesPath());
            List<ModuleScanner.Module> modules = moduleScanner.findModules(modulesDir);
            moduleComboBox.setModel(new DefaultComboBoxModel<>(new Vector<>(modules)));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                MessageFormat.format(bundle.getString("dialog.message.moduleLoadFailed"), e.getMessage()),
                bundle.getString("dialog.title.error"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadLastUsedModule() {
        String lastUsed = configManager.getLastUsedModule();
        if (lastUsed != null) setSelectedModule(lastUsed);
    }

    public void setSelectedModule(String moduleName) {
        for (int i = 0; i < moduleComboBox.getItemCount(); i++) {
            if (moduleComboBox.getItemAt(i).getName().equals(moduleName)) {
                if (moduleComboBox.getSelectedIndex() != i) moduleComboBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private List<GuiVerse> buildGuiVerses(
            List<Verse> verses,
            List<ReferenceParser.RangeWithCount> ranges,
            BookMapper defaultBookMapper,
            BookMapper moduleBookMapper,
            String moduleLanguage,
            String userLanguage,
            String moduleName) {

        List<GuiVerse> guiVerses = new ArrayList<>();
        for (Verse verse : verses) {
            Reference ref = findContainingReference(ranges, verse);
            String userProvidedShortName = (ref != null) ? ref.getBookName() : null;
            int bookNum = verse.getBookNumber();

            Optional<Book> defaultBookOpt = defaultBookMapper.getBook(bookNum);
            String defaultFullName  = defaultBookOpt.map(Book::getFullName).orElse("");
            String defaultShortName = defaultBookOpt.map(book ->
                (userProvidedShortName != null && book.getShortNames().contains(userProvidedShortName))
                    ? userProvidedShortName
                    : book.getShortNames().stream().findFirst().orElse("")
            ).orElse("");

            Optional<Book> moduleBookOpt = moduleBookMapper.getBook(bookNum);
            String moduleFullName  = moduleBookOpt.map(Book::getFullName).orElse(defaultFullName);
            String moduleShortName = moduleBookOpt.map(book -> {
                List<String> shortNames = book.getShortNames();
                return (shortNames.size() > 1)
                    ? shortNames.get(1)
                    : shortNames.stream().findFirst().orElse("");
            }).orElse(defaultShortName);

            guiVerses.add(new GuiVerse(
                bookNum, defaultFullName, defaultShortName,
                moduleFullName, moduleShortName,
                defaultBookMapper.getAllBookNames(bookNum, moduleBookMapper, moduleLanguage, userLanguage),
                verse.getChapter(), verse.getVerse(), verse.getText(),
                moduleName
            ));
        }
        return guiVerses;
    }

    private void updateAndDisplayVerseData() {
        textDisplayPane.setBackground(guiConfig.textAreaBackground != null
            ? guiConfig.textAreaBackground
            : UIManager.getColor("TextPane.background"));
        StyledDocument doc = textDisplayPane.getStyledDocument();
        setTextSpacing(textDisplayPane, 0.2f, 2.0f, 2.0f);

        ModuleScanner.Module selectedModule = (ModuleScanner.Module) moduleComboBox.getSelectedItem();
        String reference = (String) referenceInputField.getEditor().getItem();

        if (selectedModule == null || reference == null || reference.trim().isEmpty()) {
            try { doc.remove(0, doc.getLength()); } catch (BadLocationException e) { /* ignore */ }
            return;
        }

        VerseFetcher fetcher = null;
        try {
            String userLanguage = (String) languageComboBox.getSelectedItem();
            if (userLanguage != null) {
                userLanguage = userLanguage.trim();
                if (userLanguage.isEmpty()) userLanguage = null;
            }

            String moduleLanguage = BookMapper.extractModuleLanguage(selectedModule.getPath());
            BookMapper defaultBookMapper;
            BookMapper moduleBookMapper;

            AbbreviationManager abbrManager = new AbbreviationManager(configManager, 0);
            Path abbrFile = abbrManager.ensureAbbreviationFile(selectedModule.getName(), selectedModule.getPath());
            moduleBookMapper = new BookMapper(abbrManager.loadAbbreviations(abbrFile));

            if (guiConfig.useModuleAbbreviations) {
                defaultBookMapper = moduleBookMapper;
            } else {
                if (customMappingPath != null && Files.exists(customMappingPath)) {
                    defaultBookMapper = new BookMapper(
                        Files.newInputStream(customMappingPath), userLanguage, moduleLanguage);
                } else {
                    defaultBookMapper = BookMappingManager.getBookMapper(
                        configManager, null, userLanguage, moduleLanguage);
                }
            }

            VerseIndexManager indexManager = new VerseIndexManager(configManager, 0);
            Map<Integer, Integer> verseIndex = indexManager.getVerseIndex(
                selectedModule.getName(), selectedModule.getPath());
            ReferenceParser parser = new ReferenceParser(defaultBookMapper, verseIndex);

            List<ReferenceParser.RangeWithCount> ranges = parser.parseWithCounts(reference);
            if (ranges.isEmpty()) {
                insertDefaultStyledText(doc, MessageFormat.format(
                    bundle.getString("dialog.message.invalidReference"), reference));
                return;
            }

            fetcher = new VerseFetcher(selectedModule.getPath());
            List<Verse> verses = fetcher.fetch(new ArrayList<>(ranges));
            List<GuiVerse> guiVerses = buildGuiVerses(verses, ranges, defaultBookMapper,
                moduleBookMapper, moduleLanguage, userLanguage, selectedModule.getName());

            if (guiConfig.showRawJson) {
                Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
                insertDefaultStyledText(doc,
                    compactArrayField(gson.toJson(guiVerses), "allBookNames"));
            } else {
                new GuiTextFormatter(guiConfig).format(guiVerses, doc);
            }

            String trimmed = reference.trim();
            HistoryEntry newEntry = new HistoryEntry(trimmed, selectedModule.getName());
            int existingIdx = referenceHistory.indexOf(newEntry);
            if (existingIdx >= 0) referenceHistory.remove(existingIdx);
            referenceHistory.addFirst(newEntry);
            referenceInputField.setModel(new DefaultComboBoxModel<>(
                referenceHistory.toArray(new HistoryEntry[0])));
            referenceInputField.getEditor().setItem(trimmed);
            historyIndex = -1;
            updateNavigationButtonStates();

            textDisplayPane.setCaretPosition(0);
            configManager.setLastUsedModule(selectedModule.getName());

        } catch (Exception e) {
            insertDefaultStyledText(doc, MessageFormat.format(
                bundle.getString("dialog.message.errorFetching"), e.getMessage()));
            e.printStackTrace();
        } finally {
            if (fetcher != null) {
                try { fetcher.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    private void insertDefaultStyledText(StyledDocument doc, String text) {
        try {
            doc.remove(0, doc.getLength());
            TextStyle infoStyle = guiConfig.styles.get("infoText");
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            if (infoStyle != null) {
                StyleConstants.setFontFamily(attrs, infoStyle.fontName);
                StyleConstants.setFontSize(attrs, infoStyle.fontSize);
                StyleConstants.setBold(attrs,   (infoStyle.fontStyle & Font.BOLD)   != 0);
                StyleConstants.setItalic(attrs, (infoStyle.fontStyle & Font.ITALIC) != 0);
                Color fg = infoStyle.color != null
                    ? infoStyle.color : UIManager.getColor("TextPane.foreground");
                StyleConstants.setForeground(attrs, fg);
            }
            doc.insertString(0, text, attrs);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private Reference findContainingReference(List<ReferenceParser.RangeWithCount> ranges, Verse verse) {
        for (ReferenceParser.Range range : ranges) {
            if (verse.getBookNumber() == range.start.getBook() &&
                    verse.getChapter() >= range.start.getChapter() &&
                    verse.getChapter() <= range.end.getChapter() &&
                    verse.getVerse()   >= range.start.getVerse()   &&
                    verse.getVerse()   <= range.end.getVerse()) {
                return range.start;
            }
        }
        return null;
    }

    private void updateMappingRowsEnabled() {
        boolean useModuleAbbrs = useModuleAbbrsCheckbox.isSelected();
        mappingFileField.setEnabled(!useModuleAbbrs);
        mapBrowseButton.setEnabled(!useModuleAbbrs);
        languageComboBox.setEnabled(!useModuleAbbrs);
    }

    // -----------------------------------------------------------------------
    // Inner classes
    // -----------------------------------------------------------------------

    private static class HistoryEntry {
        final String reference;
        final String moduleName;

        HistoryEntry(String reference, String moduleName) {
            this.reference  = reference;
            this.moduleName = moduleName;
        }

        @Override
        public String toString() {
            return reference + "  [" + moduleName + "]";
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof HistoryEntry)) return false;
            HistoryEntry other = (HistoryEntry) o;
            return reference.equals(other.reference) && moduleName.equals(other.moduleName);
        }

        @Override
        public int hashCode() {
            return 31 * reference.hashCode() + moduleName.hashCode();
        }
    }

    private static class ModuleRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ModuleScanner.Module) {
                ModuleScanner.Module module = (ModuleScanner.Module) value;
                if (index == -1) { setText(module.getName()); return this; }

                JPanel panel = new JPanel(new GridBagLayout());
                GridBagConstraints gbc = new GridBagConstraints();
                panel.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
                Color fg = isSelected ? list.getSelectionForeground() : list.getForeground();

                JLabel langLabel = new JLabel(module.getLanguage());
                langLabel.setForeground(fg);
                langLabel.setPreferredSize(new Dimension(35, langLabel.getPreferredSize().height));
                gbc.gridx = 0; gbc.weightx = 0;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.insets = new Insets(1, 2, 1, 2);
                gbc.anchor = GridBagConstraints.WEST;
                panel.add(langLabel, gbc);

                JLabel nameLabel = new JLabel(module.getName());
                nameLabel.setForeground(fg);
                nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
                nameLabel.setPreferredSize(new Dimension(80, nameLabel.getPreferredSize().height));
                gbc.gridx = 1; gbc.weightx = 0;
                panel.add(nameLabel, gbc);

                JLabel descLabel = new JLabel(module.getDescription());
                descLabel.setForeground(fg);
                gbc.gridx = 2; gbc.weightx = 1.0;
                panel.add(descLabel, gbc);

                return panel;
            }
            return this;
        }
    }

    private static String compactArrayField(String json, String fieldName) {
        Pattern pattern = Pattern.compile(
            "\"" + fieldName + "\":\\s*\\[\\s*([^\\]]+?)\\s*\\]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String compacted = matcher.group(1)
                .replaceAll("\\s+", " ")
                .replaceAll(",\\s+", ", ")
                .trim();
            matcher.appendReplacement(result, "\"" + fieldName + "\": [" + compacted + "]");
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static class RtfTransferable implements Transferable {
        private final String plainText;
        private final byte[] rtfBytes;
        private static final DataFlavor rtfFlavor;
        private static final DataFlavor[] flavors;

        static {
            DataFlavor tmp = null;
            try { tmp = new DataFlavor("text/rtf; class=java.io.InputStream"); }
            catch (ClassNotFoundException e) { e.printStackTrace(); }
            rtfFlavor = tmp;
            flavors = new DataFlavor[]{rtfFlavor, DataFlavor.stringFlavor};
        }

        public RtfTransferable(String plainText, byte[] rtfBytes) {
            this.plainText = plainText;
            this.rtfBytes  = rtfBytes;
        }

        @Override public DataFlavor[] getTransferDataFlavors() { return flavors.clone(); }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            for (DataFlavor f : flavors) if (f.equals(flavor)) return true;
            return false;
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (flavor.equals(rtfFlavor))               return new ByteArrayInputStream(rtfBytes);
            if (flavor.equals(DataFlavor.stringFlavor)) return plainText;
            throw new UnsupportedFlavorException(flavor);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(new FlatLightLaf()); }
            catch (Exception e) { e.printStackTrace(); }
            Gui gui = new Gui(null, null, () -> System.exit(0));
            gui.setVisible(true);
        });
    }
}
