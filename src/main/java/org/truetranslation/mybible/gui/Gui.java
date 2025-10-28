package org.truetranslation.mybible.gui;

import com.formdev.flatlaf.FlatLightLaf;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.truetranslation.mybible.core.*;
import org.truetranslation.mybible.core.model.Book;
import org.truetranslation.mybible.core.model.Reference;
import org.truetranslation.mybible.core.model.Verse;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.rtf.RTFEditorKit;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Vector;

public class Gui extends JFrame {

    private JTextPane textDisplayPane;
    private JTextField referenceInputField;
    private JComboBox<ModuleScanner.Module> moduleComboBox;
    private JCheckBox rawJsonCheckbox;
    private JTextField mappingFileField;

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
        this.bundle = ResourceBundle.getBundle("gui");
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
            referenceInputField.setText(initialReference);
        }

        if (initialModule != null || initialReference != null) {
            SwingUtilities.invokeLater(this::updateAndDisplayVerseData);
        }
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
        add(new JScrollPane(textDisplayPane), BorderLayout.CENTER);

        JPanel bottomContainer = new JPanel(new BorderLayout(0, 10));
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridy = 0; gbc.gridx = 0; gbc.weightx = 0;
        inputPanel.add(new JLabel(bundle.getString("label.bibleReference")), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        referenceInputField = new JTextField(30);
        inputPanel.add(referenceInputField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        JButton showButton = new JButton(bundle.getString("button.show"));
        inputPanel.add(showButton, gbc);

        gbc.gridy = 1; gbc.gridx = 0;
        inputPanel.add(new JLabel(bundle.getString("label.module")), gbc);
        gbc.gridx = 1;
        moduleComboBox = new JComboBox<>();
        moduleComboBox.setRenderer(new ModuleRenderer());
        inputPanel.add(moduleComboBox, gbc);
        gbc.gridx = 2;
        JButton infoButton = new JButton(bundle.getString("button.info"));
        inputPanel.add(infoButton, gbc);
        gbc.gridy =2; gbc.gridx = 2;
        JButton setModulePathButton = new JButton(bundle.getString("button.modulePathSet"));
        inputPanel.add(setModulePathButton, gbc);

        gbc.gridy = 3; gbc.gridx = 1; gbc.gridwidth = 2;
        rawJsonCheckbox = new JCheckBox(bundle.getString("checkbox.rawJson"));
        rawJsonCheckbox.setSelected(guiConfig.showRawJson);
        inputPanel.add(rawJsonCheckbox, gbc);
        
        gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 1; gbc.weightx = 0;
        inputPanel.add(new JLabel(bundle.getString("label.mappingFile")), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        mappingFileField = new JTextField("default_mapping.json");
        mappingFileField.setEditable(false);
        inputPanel.add(mappingFileField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        JButton browseButton = new JButton(bundle.getString("button.browse"));
        inputPanel.add(browseButton, gbc);

        bottomContainer.add(inputPanel, BorderLayout.NORTH);

        JPanel actionButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton configureButton = new JButton(bundle.getString("button.configure"));
        JButton copyButton = new JButton(bundle.getString("button.copyDisplayedText"));
        JButton quitButton = new JButton(bundle.getString("button.quit"));
        actionButtonPanel.add(configureButton);
        actionButtonPanel.add(copyButton);
        actionButtonPanel.add(quitButton);

        bottomContainer.add(actionButtonPanel, BorderLayout.SOUTH);
        add(bottomContainer, BorderLayout.SOUTH);

        showButton.addActionListener(e -> updateAndDisplayVerseData());
        referenceInputField.addActionListener(e -> updateAndDisplayVerseData());
        moduleComboBox.addActionListener(e -> updateAndDisplayVerseData());
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
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(configManager.getDefaultConfigDir().toFile());
            chooser.setFileFilter(new FileNameExtensionFilter("JSON Mapping Files", "json"));
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                customMappingPath = chooser.getSelectedFile().toPath();
                mappingFileField.setText(customMappingPath.getFileName().toString());
                updateAndDisplayVerseData();
            }
        });
    }

    private void selectModulePath() {
        JFileChooser chooser = new JFileChooser();
        
        // Set to directory selection only
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(bundle.getString("dialog.title.selectModulePath"));
        chooser.setAcceptAllFileFilterUsed(false);
        
        // Start from current modules path if it exists
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
            
            // Reload modules from new path
            loadModules();
            
            // Show confirmation
            JOptionPane.showMessageDialog(this, 
                MessageFormat.format(bundle.getString("dialog.message.modulePathSet"), selectedPath.toString()), 
                bundle.getString("dialog.title.success"), 
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void initKeyboardShortcuts() {
        JRootPane rootPane = getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
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

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_M, menuMask), "focusModuleAction");
        actionMap.put("focusModuleAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { moduleComboBox.requestFocusInWindow(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_J, menuMask), "toggleJsonAction");
        actionMap.put("toggleJsonAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { rawJsonCheckbox.doClick(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask | KeyEvent.SHIFT_DOWN_MASK), "copyAction");
        actionMap.put("copyAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { copyRichTextToClipboard(); }
        });

        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, menuMask), "configureAction");
            actionMap.put("configureAction", new AbstractAction() {
                public void actionPerformed(ActionEvent e) { openConfigurationDialog(); }
            });
        }
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
        dialog.setVisible(true);
        this.guiConfig = guiConfigManager.getConfig();
        updateAndDisplayVerseData();
    }

    private void quitApplication() {
        dispose();
        onWindowClosed.run();
    }

    private void showModuleInfo() {
        ModuleScanner.Module selectedModule = (ModuleScanner.Module) moduleComboBox.getSelectedItem();
        if (selectedModule != null) {
            JOptionPane.showMessageDialog(this, MessageFormat.format(bundle.getString("dialog.message.moduleInfo"), selectedModule.getDescription()), bundle.getString("dialog.title.moduleInfo"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void loadModules() {
        try {
            Path modulesDir = Paths.get(configManager.getModulesPath());
            List<ModuleScanner.Module> modules = moduleScanner.findModules(modulesDir);
            moduleComboBox.setModel(new DefaultComboBoxModel<>(new Vector<>(modules)));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, MessageFormat.format(bundle.getString("dialog.message.moduleLoadFailed"), e.getMessage()), bundle.getString("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadLastUsedModule() {
        String lastUsed = configManager.getLastUsedModule();
        if (lastUsed != null) {
            setSelectedModule(lastUsed);
        }
    }

    public void setSelectedModule(String moduleName) {
        for (int i = 0; i < moduleComboBox.getItemCount(); i++) {
            if (moduleComboBox.getItemAt(i).getName().equals(moduleName)) {
                if (moduleComboBox.getSelectedIndex() != i) {
                    moduleComboBox.setSelectedIndex(i);
                }
                return;
            }
        }
    }

    private void updateAndDisplayVerseData() {
        textDisplayPane.setBackground(guiConfig.textAreaBackground != null ? guiConfig.textAreaBackground : UIManager.getColor("TextPane.background"));
        StyledDocument doc = textDisplayPane.getStyledDocument();

        ModuleScanner.Module selectedModule = (ModuleScanner.Module) moduleComboBox.getSelectedItem();
        String reference = referenceInputField.getText();

        if (selectedModule == null || reference == null || reference.trim().isEmpty()) {
            try {
                doc.remove(0, doc.getLength());
            } catch (BadLocationException e) { /* Ignore */ }
            return;
        }

        VerseFetcher fetcher = null;
        try {
            BookMapper defaultBookMapper;
            if (customMappingPath != null && Files.exists(customMappingPath)) {
                defaultBookMapper = new BookMapper(Files.newInputStream(customMappingPath));
            } else {
                defaultBookMapper = BookMappingManager.getBookMapper(configManager);
            }
            
            BookMapper moduleBookMapper;
            try {
                AbbreviationManager abbrManager = new AbbreviationManager(configManager, 0);
                Path abbrFile = abbrManager.ensureAbbreviationFile(selectedModule.getName(), selectedModule.getPath());
                moduleBookMapper = new BookMapper(abbrManager.loadAbbreviations(abbrFile));
            } catch (Exception e) {
                moduleBookMapper = defaultBookMapper;
            }

            VerseIndexManager indexManager = new VerseIndexManager(configManager, 0);
            Map<Integer, Integer> verseIndex = indexManager.getVerseIndex(selectedModule.getName(), selectedModule.getPath());
            ReferenceParser parser = new ReferenceParser(defaultBookMapper, verseIndex);

            List<ReferenceParser.RangeWithCount> ranges = parser.parseWithCounts(reference);
            if (ranges.isEmpty()) {
                insertDefaultStyledText(doc, MessageFormat.format(bundle.getString("dialog.message.invalidReference"), reference));
                return;
            }
            
            fetcher = new VerseFetcher(selectedModule.getPath());
            List<Verse> verses = fetcher.fetch(new ArrayList<>(ranges));
            
            List<GuiVerse> guiVerses = new ArrayList<>();
            for (Verse verse : verses) {
                Reference ref = findContainingReference(ranges, verse);
                String userProvidedShortName = (ref != null) ? ref.getBookName() : null;
                int bookNum = verse.getBookNumber();

                Optional<Book> defaultBookOpt = defaultBookMapper.getBook(bookNum);
                String defaultFullName = defaultBookOpt.map(Book::getFullName).orElse("");
                String defaultShortName = defaultBookOpt.map(book -> (userProvidedShortName != null && book.getShortNames().contains(userProvidedShortName)) ? userProvidedShortName : book.getShortNames().stream().findFirst().orElse("")).orElse("");

                Optional<Book> moduleBookOpt = moduleBookMapper.getBook(bookNum);
                String moduleFullName = moduleBookOpt.map(Book::getFullName).orElse(defaultFullName);
                String moduleShortName = moduleBookOpt.map(book -> (userProvidedShortName != null && book.getShortNames().contains(userProvidedShortName)) ? userProvidedShortName : book.getShortNames().stream().findFirst().orElse("")).orElse(defaultShortName);

                guiVerses.add(new GuiVerse(bookNum, defaultFullName, defaultShortName, moduleFullName, moduleShortName, verse.getChapter(), verse.getVerse(), verse.getText(), selectedModule.getName()));
            }

            if (guiConfig.showRawJson) {
                Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
                insertDefaultStyledText(doc, gson.toJson(guiVerses));
            } else {
                GuiTextFormatter formatter = new GuiTextFormatter(guiConfig);
                formatter.format(guiVerses, doc);
            }
            textDisplayPane.setCaretPosition(0);
            configManager.setLastUsedModule(selectedModule.getName());

        } catch (Exception e) {
            insertDefaultStyledText(doc, MessageFormat.format(bundle.getString("dialog.message.errorFetching"), e.getMessage()));
            e.printStackTrace();
        } finally {
            if (fetcher != null) {
                try {
                    fetcher.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
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
                StyleConstants.setBold(attrs, (infoStyle.fontStyle & Font.BOLD) != 0);
                StyleConstants.setItalic(attrs, (infoStyle.fontStyle & Font.ITALIC) != 0);
                Color fg = infoStyle.color != null ? infoStyle.color : UIManager.getColor("TextPane.foreground");
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
                    verse.getChapter() >= range.start.getChapter() && verse.getChapter() <= range.end.getChapter() &&
                    verse.getVerse() >= range.start.getVerse() && verse.getVerse() <= range.end.getVerse()) {
                return range.start;
            }
        }
        return null;
    }

    private static class ModuleRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ModuleScanner.Module) {
                ModuleScanner.Module module = (ModuleScanner.Module) value;
                setText(index == -1 ? module.getName() : String.format("%s\t%s", module.getLanguage(), module.getName()));
            }
            return this;
        }
    }

    private static class RtfTransferable implements Transferable {
        private final String plainText;
        private final byte[] rtfBytes;
        private static final DataFlavor rtfFlavor;
        private static final DataFlavor[] flavors;

        static {
            DataFlavor tempRtfFlavor = null;
            try {
                tempRtfFlavor = new DataFlavor("text/rtf; class=java.io.InputStream");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            rtfFlavor = tempRtfFlavor;
            flavors = new DataFlavor[]{rtfFlavor, DataFlavor.stringFlavor};
        }

        public RtfTransferable(String plainText, byte[] rtfBytes) {
            this.plainText = plainText;
            this.rtfBytes = rtfBytes;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return flavors.clone();
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            for (DataFlavor f : flavors) {
                if (f.equals(flavor)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (flavor.equals(rtfFlavor)) {
                return new ByteArrayInputStream(rtfBytes);
            } else if (flavor.equals(DataFlavor.stringFlavor)) {
                return plainText;
            }
            throw new UnsupportedFlavorException(flavor);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(new FlatLightLaf());
            } catch (Exception e) {
                e.printStackTrace();
            }
            Gui gui = new Gui(null, null, () -> System.exit(0));
            gui.setVisible(true);
        });
    }
}
