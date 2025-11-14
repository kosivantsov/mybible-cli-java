package org.truetranslation.mybible.gui;

import org.truetranslation.mybible.core.ConfigManager;
import org.truetranslation.mybible.core.ExternalResourceBundleLoader;
import org.truetranslation.mybible.core.ModuleManager;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GuiModuleManager extends JDialog {

    private final ConfigManager configManager;
    private final GuiConfigManager guiConfigManager;
    private ModuleManager moduleManager;
    private final ResourceBundle bundle;
    private final Frame owner;

    private JRadioButton availableRadio;
    private JRadioButton installedRadio;
    private JRadioButton upgradableRadio;

    private JTable moduleTable;
    private ModuleTableModel tableModel;

    private JTextField nameFilter;
    private JComboBox<String> typeFilter;
    private JComboBox<String> languageFilter;
    private JTextField descFilter;

    private JButton actionButton;
    private JButton infoButton;
    private JButton updateButton;
    private JButton purgeButton;
    private JButton purgeAllButton;
    private JButton reinitButton;
    private JButton closeButton;

    private ListMode currentMode = ListMode.AVAILABLE;

    private static final String[] MODULE_TYPES = {
        "All", "bible", "commentaries", "crossreferences", "devotions",
        "dictionary", "plan", "subheadings", "bundle"
    };

    private enum ListMode {
        AVAILABLE, INSTALLED, UPGRADABLE
    }

    public GuiModuleManager(Frame owner, ConfigManager configManager, GuiConfigManager guiConfigManager) {
        super(owner, true);
        this.owner = owner;
        this.configManager = configManager;
        this.guiConfigManager = guiConfigManager;

        ExternalResourceBundleLoader externalLoader = new ExternalResourceBundleLoader(configManager.getDefaultConfigDir());
        this.bundle = externalLoader.getBundle("i18n.gui");

        setTitle(bundle.getString("moduleMgr.title"));

        // Check if module path is configured
        String modulePath = configManager.getModulesPath();
        if (modulePath == null || modulePath.isEmpty()) {
            if (!promptForModulePath()) {
                dispose();
                return;
            }
        }

        try {
            this.moduleManager = new ModuleManager(configManager, 0);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                MessageFormat.format(bundle.getString("moduleMgr.error.initFailed"), e.getMessage()),
                bundle.getString("error.title"),
                JOptionPane.ERROR_MESSAGE);
            dispose();
            return;
        }

        initComponents();
        layoutComponents();
        setupListeners();
        setupKeyBindings();

        pack();
        setMinimumSize(new Dimension(1000, 600));
        setLocationRelativeTo(owner);

        loadModules();
    }

    private boolean promptForModulePath() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(bundle.getString("moduleMgr.dialog.selectModulePath"));
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = fileChooser.getSelectedFile();
            configManager.setModulesPath(selectedDir.getAbsolutePath());

            if (owner instanceof Gui) {
                ((Gui) owner).updateModulePath(selectedDir.getAbsolutePath());
            }

            return true;
        }

        JOptionPane.showMessageDialog(this,
            bundle.getString("moduleMgr.error.noPathSelected"),
            bundle.getString("error.title"),
            JOptionPane.WARNING_MESSAGE);
        return false;
    }

    private void initComponents() {
        // Radio buttons
        availableRadio = new JRadioButton(bundle.getString("moduleMgr.mode.available"), true);
        installedRadio = new JRadioButton(bundle.getString("moduleMgr.mode.installed"));
        upgradableRadio = new JRadioButton(bundle.getString("moduleMgr.mode.upgradable"));

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(availableRadio);
        modeGroup.add(installedRadio);
        modeGroup.add(upgradableRadio);

        // Table
        tableModel = new ModuleTableModel();
        moduleTable = new JTable(tableModel);
        moduleTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        moduleTable.setAutoCreateRowSorter(true);
        moduleTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        // Configure columns
        moduleTable.getColumnModel().getColumn(0).setMaxWidth(30);
        moduleTable.getColumnModel().getColumn(0).setMinWidth(30);
        moduleTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        moduleTable.getColumnModel().getColumn(0).setResizable(false);

        // Checkbox column renderer and editor
        moduleTable.getColumnModel().getColumn(0).setCellRenderer(new CheckBoxRenderer());
        moduleTable.getColumnModel().getColumn(0).setCellEditor(new CheckBoxEditor());

        // Status indicator for installed modules in available list
        moduleTable.getColumnModel().getColumn(1).setCellRenderer(new NameRenderer());

        // Make table respond to spacebar for checkbox toggle
        moduleTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    toggleSelectedRows();
                    e.consume();
                }
            }
        });

        // Filter components
        nameFilter = new JTextField(12);
        typeFilter = new JComboBox<>(MODULE_TYPES);
        languageFilter = new JComboBox<>();
        languageFilter.setEditable(true);
        languageFilter.setPreferredSize(new Dimension(80, languageFilter.getPreferredSize().height));
        descFilter = new JTextField(15);

        populateLanguageFilter();

        // Buttons
        actionButton = new JButton(bundle.getString("moduleMgr.action.install"));
        actionButton.setEnabled(false);
        infoButton = new JButton(bundle.getString("moduleMgr.button.info"));
        updateButton = new JButton(bundle.getString("moduleMgr.button.update"));
        purgeButton = new JButton(bundle.getString("moduleMgr.button.purge"));
        reinitButton = new JButton(bundle.getString("moduleMgr.button.reinit"));
        purgeAllButton = new JButton(bundle.getString("moduleMgr.button.purgeAll"));
        closeButton = new JButton(bundle.getString("moduleMgr.button.close"));

        JButton filterButton = new JButton(bundle.getString("moduleMgr.button.filter"));
        filterButton.addActionListener(e -> applyFilters());
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));

        // Top panel with radio buttons
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(availableRadio);
        topPanel.add(installedRadio);
        topPanel.add(upgradableRadio);

        // Center panel with table
        JScrollPane scrollPane = new JScrollPane(moduleTable);
        scrollPane.setPreferredSize(new Dimension(900, 400));

        // Filter panel
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        filterPanel.add(new JLabel(bundle.getString("moduleMgr.filter.name")));
        filterPanel.add(nameFilter);
        filterPanel.add(new JLabel(bundle.getString("moduleMgr.filter.type")));
        filterPanel.add(typeFilter);
        filterPanel.add(new JLabel(bundle.getString("moduleMgr.filter.language")));
        filterPanel.add(languageFilter);
        filterPanel.add(new JLabel(bundle.getString("moduleMgr.filter.description")));
        filterPanel.add(descFilter);

        JButton filterButton = new JButton(bundle.getString("moduleMgr.button.filter"));
        filterButton.addActionListener(e -> applyFilters());
        filterPanel.add(filterButton);

        // Bottom panel with buttons
        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel leftButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        leftButtonPanel.add(actionButton);
        leftButtonPanel.add(infoButton);

        JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        rightButtonPanel.add(updateButton);
        rightButtonPanel.add(purgeButton);
        rightButtonPanel.add(reinitButton);
        rightButtonPanel.add(purgeAllButton);
        rightButtonPanel.add(closeButton);

        bottomPanel.add(leftButtonPanel, BorderLayout.WEST);
        bottomPanel.add(rightButtonPanel, BorderLayout.EAST);

        // Combined filter and button panel
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(filterPanel, BorderLayout.NORTH);
        southPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Add all panels
        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        // Add padding
        ((JPanel)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void setupListeners() {
        // Radio button listeners
        ActionListener modeListener = e -> {
            if (availableRadio.isSelected()) {
                currentMode = ListMode.AVAILABLE;
                actionButton.setText(bundle.getString("moduleMgr.action.install"));
            } else if (installedRadio.isSelected()) {
                currentMode = ListMode.INSTALLED;
                actionButton.setText(bundle.getString("moduleMgr.action.remove"));
            } else {
                currentMode = ListMode.UPGRADABLE;
                actionButton.setText(bundle.getString("moduleMgr.action.upgrade"));
            }
            loadModules();
        };

        availableRadio.addActionListener(modeListener);
        installedRadio.addActionListener(modeListener);
        upgradableRadio.addActionListener(modeListener);

        // Table header click for select all
        moduleTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int column = moduleTable.columnAtPoint(e.getPoint());
                if (column == 0) {
                    toggleAllRows();
                }
            }
        });

        // Table selection listener
        moduleTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateActionButtonState();
            }
        });

        tableModel.addTableModelListener(e -> updateActionButtonState());

        // Button listeners
        actionButton.addActionListener(e -> performAction());
        infoButton.addActionListener(e -> showModuleInfo());
        updateButton.addActionListener(e -> updateCache());
        purgeButton.addActionListener(e -> purgeCache(false));
        purgeAllButton.addActionListener(e -> purgeCache(true));
        reinitButton.addActionListener(e -> reinitSources());
        closeButton.addActionListener(e -> dispose());

        // Enter key in filter fields
        KeyAdapter enterListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    applyFilters();
                }
            }
        };
        nameFilter.addKeyListener(enterListener);
        descFilter.addKeyListener(enterListener);
        languageFilter.getEditor().getEditorComponent().addKeyListener(enterListener);
    }

    private void setupKeyBindings() {
        JRootPane rootPane = getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        int modifierKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        // Ctrl/Cmd+F for Filter
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, modifierKey), "filter");
        actionMap.put("filter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyFilters();
            }
        });

        // Ctrl/Cmd+I for Info
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, modifierKey), "info");
        actionMap.put("info", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showModuleInfo();
            }
        });

        // Ctrl/Cmd+Enter for Action
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, modifierKey), "action");
        actionMap.put("action", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (actionButton.isEnabled()) {
                    performAction();
                }
            }
        });

        // Ctrl/Cmd+U for Update Cache
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_U, modifierKey), "update");
        actionMap.put("update", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateCache();
            }
        });

        // Escape to close
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        actionMap.put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }

    private void loadModules() {
        tableModel.clear();

        try {
            String langFilter = getSelectedLanguage();
            String typeFilterStr = getSelectedType();
            String nameFilterStr = nameFilter.getText().trim();
            String descFilterStr = descFilter.getText().trim();

            switch (currentMode) {
                case AVAILABLE:
                    List<ModuleManager.CachedModule> available = moduleManager.listAvailableModules(
                        langFilter, typeFilterStr, nameFilterStr, descFilterStr);
                    Set<String> installedNames = getInstalledModuleNames();
                    for (ModuleManager.CachedModule mod : available) {
                        tableModel.addModule(mod, installedNames.contains(mod.name));
                    }
                    break;

                case INSTALLED:
                    List<ModuleManager.InstalledModule> installed = moduleManager.listInstalledModules(
                        langFilter, typeFilterStr, nameFilterStr, descFilterStr);
                    for (ModuleManager.InstalledModule mod : installed) {
                        tableModel.addModule(mod);
                    }
                    break;

                case UPGRADABLE:
                    List<ModuleManager.UpgradableModule> upgradable = moduleManager.listUpgradableModules(
                        langFilter, typeFilterStr, nameFilterStr, descFilterStr);
                    for (ModuleManager.UpgradableModule mod : upgradable) {
                        tableModel.addModule(mod);
                    }
                    break;
            }

            // Auto-resize columns to fit content
            SwingUtilities.invokeLater(() -> resizeColumnWidths());

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                MessageFormat.format(bundle.getString("moduleMgr.error.loadFailed"), e.getMessage()),
                bundle.getString("error.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resizeColumnWidths() {
        for (int column = 0; column < moduleTable.getColumnCount(); column++) {
            if (column == 0) continue;

            TableColumn tableColumn = moduleTable.getColumnModel().getColumn(column);
            int preferredWidth = 50;

            // Get header width
            TableCellRenderer headerRenderer = moduleTable.getTableHeader().getDefaultRenderer();
            Component headerComp = headerRenderer.getTableCellRendererComponent(
                moduleTable, tableColumn.getHeaderValue(), false, false, 0, column);
            preferredWidth = Math.max(preferredWidth, headerComp.getPreferredSize().width + 20);

            // Get cell widths
            for (int row = 0; row < moduleTable.getRowCount(); row++) {
                TableCellRenderer cellRenderer = moduleTable.getCellRenderer(row, column);
                Component comp = moduleTable.prepareRenderer(cellRenderer, row, column);
                preferredWidth = Math.max(preferredWidth, comp.getPreferredSize().width + 20);
            }

            tableColumn.setPreferredWidth(preferredWidth);
        }
    }

    private Set<String> getInstalledModuleNames() {
        try {
            return moduleManager.listInstalledModules(null, null, null, null)
                .stream()
                .map(m -> m.name)
                .collect(Collectors.toSet());
        } catch (IOException e) {
            return new HashSet<>();
        }
    }

    private void applyFilters() {
        loadModules();
    }

    private String getSelectedLanguage() {
        Object selected = languageFilter.getSelectedItem();
        if (selected == null || selected.toString().trim().isEmpty() || selected.toString().equals("All")) {
            return null;
        }
        return selected.toString();
    }

    private String getSelectedType() {
        String selected = (String) typeFilter.getSelectedItem();
        if (selected == null || selected.equals("All")) {
            return null;
        }
        return selected;
    }

    private void populateLanguageFilter() {
        Set<String> languages = new TreeSet<>();

        try {
            List<ModuleManager.CachedModule> modules = moduleManager.listAvailableModules(null, null, null, null);
            for (ModuleManager.CachedModule mod : modules) {
                if (mod.language != null && !mod.language.isEmpty()) {
                    languages.add(mod.language);
                }
            }
        } catch (IOException e) {
            // Ignore
        }

        languageFilter.setModel(new DefaultComboBoxModel<>(languages.toArray(new String[0])));
        languageFilter.setSelectedItem(null);
    }

    private void toggleSelectedRows() {
        int[] selectedRows = moduleTable.getSelectedRows();
        for (int row : selectedRows) {
            int modelRow = moduleTable.convertRowIndexToModel(row);
            tableModel.toggleCheckbox(modelRow);
        }
    }

    private void toggleAllRows() {
        boolean allSelected = tableModel.areAllSelected();
        tableModel.setAllSelected(!allSelected);
    }

    private void updateActionButtonState() {
        boolean hasSelection = tableModel.hasSelection();
        actionButton.setEnabled(hasSelection);
    }

    private void performAction() {
        List<String> selectedModules = tableModel.getSelectedModuleNames();
        if (selectedModules.isEmpty()) {
            return;
        }

        switch (currentMode) {
            case AVAILABLE:
                installModules(selectedModules);
                break;
            case INSTALLED:
                removeModules(selectedModules);
                break;
            case UPGRADABLE:
                upgradeModules(selectedModules);
                break;
        }
    }

    private void installModules(List<String> moduleNames) {
        JProgressBar progressBar = new JProgressBar(0, 100);
        JDialog progressDialog = createProgressDialog(bundle.getString("moduleMgr.progress.installing"), progressBar);

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                int total = moduleNames.size();
                int current = 0;

                for (String name : moduleNames) {
                    current++;
                    publish(MessageFormat.format(bundle.getString("moduleMgr.progress.installingModule"), 
                        name, current, total));

                    try {
                        moduleManager.installModule(name, null, false, (prog, tot, msg) -> {
                            int percent = (int) ((prog * 100.0) / tot);
                            setProgress(percent);
                        });
                    } catch (IOException e) {
                        publish(MessageFormat.format(bundle.getString("moduleMgr.error.installModuleFailed"), 
                            name, e.getMessage()));
                    }
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                String lastMessage = chunks.get(chunks.size() - 1);
                progressDialog.setTitle(lastMessage);
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                loadModules();
                JOptionPane.showMessageDialog(GuiModuleManager.this,
                    bundle.getString("moduleMgr.success.installComplete"),
                    bundle.getString("success.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            }
        };

        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });

        worker.execute();
        progressDialog.setVisible(true);
    }

    private void removeModules(List<String> moduleNames) {
        int confirm = JOptionPane.showConfirmDialog(this,
            MessageFormat.format(bundle.getString("moduleMgr.confirm.remove"), moduleNames.size()),
            bundle.getString("moduleMgr.confirm.removeTitle"),
            JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            for (String name : moduleNames) {
                moduleManager.removeModule(name);
            }
            loadModules();
            JOptionPane.showMessageDialog(this,
                MessageFormat.format(bundle.getString("moduleMgr.success.removed"), moduleNames.size()),
                bundle.getString("success.title"),
                JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                MessageFormat.format(bundle.getString("moduleMgr.error.removeFailed"), e.getMessage()),
                bundle.getString("error.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void upgradeModules(List<String> moduleNames) {
        JProgressBar progressBar = new JProgressBar(0, 100);
        JDialog progressDialog = createProgressDialog(bundle.getString("moduleMgr.progress.upgrading"), progressBar);

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    moduleManager.upgradeModules(moduleNames, (prog, tot, msg) -> {
                        int percent = (int) ((prog * 100.0) / tot);
                        setProgress(percent);
                    });
                } catch (IOException e) {
                    publish(MessageFormat.format(bundle.getString("moduleMgr.error.upgradeFailed"), e.getMessage()));
                }
                return null;
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                loadModules();
                JOptionPane.showMessageDialog(GuiModuleManager.this,
                    bundle.getString("moduleMgr.success.upgradeComplete"),
                    bundle.getString("success.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            }
        };

        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });

        worker.execute();
        progressDialog.setVisible(true);
    }

    private void showModuleInfo() {
        int selectedRow = moduleTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this,
                bundle.getString("moduleMgr.error.noSelection"),
                bundle.getString("moduleMgr.info.noSelectionTitle"),
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = moduleTable.convertRowIndexToModel(selectedRow);
        String moduleName = (String) tableModel.getValueAt(modelRow, 1);

        try {
            ModuleManager.ModuleInfo info = moduleManager.getModuleInfo(moduleName);

            StringBuilder infoText = new StringBuilder();
            infoText.append(bundle.getString("moduleMgr.info.name")).append(" ").append(info.cached.name).append("\n");
            infoText.append(bundle.getString("moduleMgr.info.language")).append(" ")
                .append(info.cached.language != null ? info.cached.language : "N/A").append("\n");
            infoText.append(bundle.getString("moduleMgr.info.type")).append(" ").append(info.cached.moduleType).append("\n");
            infoText.append(bundle.getString("moduleMgr.info.latestVersion")).append(" ").append(info.cached.updateDate).append("\n");
            infoText.append(bundle.getString("moduleMgr.info.description")).append(" ").append(info.cached.description).append("\n\n");

            if (info.isInstalled) {
                infoText.append(bundle.getString("moduleMgr.info.installed")).append(" ").append(info.installed.updateDate).append("\n");
                if (info.hasUpdate) {
                    infoText.append(bundle.getString("moduleMgr.info.upgradeAvailable")).append(" ")
                        .append(info.cached.updateDate).append("\n");
                } else {
                    infoText.append(bundle.getString("moduleMgr.info.status")).append(" ")
                        .append(bundle.getString("moduleMgr.info.upToDate")).append("\n");
                }
            } else {
                infoText.append(bundle.getString("moduleMgr.info.status")).append(" ")
                    .append(bundle.getString("moduleMgr.info.notInstalled")).append("\n");
            }

            JTextArea textArea = new JTextArea(infoText.toString());
            textArea.setEditable(false);
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

            JOptionPane.showMessageDialog(this,
                new JScrollPane(textArea),
                MessageFormat.format(bundle.getString("moduleMgr.info.title"), moduleName),
                JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                MessageFormat.format(bundle.getString("moduleMgr.error.infoFailed"), e.getMessage()),
                bundle.getString("error.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateCache() {
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setIndeterminate(true);
        JDialog progressDialog = createProgressDialog(bundle.getString("moduleMgr.progress.updatingCache"), progressBar);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    moduleManager.updateCache(null);
                } catch (IOException e) {
                    throw new Exception(MessageFormat.format(
                        bundle.getString("moduleMgr.error.cacheUpdateFailed"), e.getMessage()));
                }
                return null;
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    get();

                    // Reinitialize module manager to reload cache
                    try {
                        moduleManager = new ModuleManager(configManager, 0);
                    } catch (IOException ex) {
                        throw new Exception("Failed to reinitialize module manager: " + ex.getMessage());
                    }

                    populateLanguageFilter();
                    loadModules();
                    JOptionPane.showMessageDialog(GuiModuleManager.this,
                        bundle.getString("moduleMgr.success.cacheUpdated"),
                        bundle.getString("success.title"),
                        JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(GuiModuleManager.this,
                        e.getMessage(),
                        bundle.getString("error.title"),
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private void purgeCache(boolean full) {
        String message = full ? 
            bundle.getString("moduleMgr.confirm.purgeAll") : 
            bundle.getString("moduleMgr.confirm.purge");

        int confirm = JOptionPane.showConfirmDialog(this,
            message,
            bundle.getString("moduleMgr.confirm.purgeTitle"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            if (full) {
                // Remove cache db, downloads, registries and sources
                Path configDir = configManager.getDefaultConfigDir();
                Path cacheDb = configDir.resolve("cache.db");
                Path cacheDir = configDir.resolve(".cache");
                Path sourcesDir = configDir.resolve("sources");

                Files.deleteIfExists(cacheDb);
                deleteDirectory(cacheDir);
                deleteDirectory(sourcesDir);

                // Reinitialize module manager
                moduleManager = new ModuleManager(configManager, 0);

                JOptionPane.showMessageDialog(this,
                    bundle.getString("moduleMgr.success.purgedAll"),
                    bundle.getString("success.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                moduleManager.purgeCache(false);
                JOptionPane.showMessageDialog(this,
                    bundle.getString("moduleMgr.success.purged"),
                    bundle.getString("success.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            }
            populateLanguageFilter();
            loadModules();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                MessageFormat.format(bundle.getString("moduleMgr.error.purgeFailed"), e.getMessage()),
                bundle.getString("error.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted((a, b) -> -a.compareTo(b)).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
            }
        }
    }

    private void reinitSources() {
        int confirm = JOptionPane.showConfirmDialog(this,
            bundle.getString("moduleMgr.confirm.reinit"),
            bundle.getString("moduleMgr.confirm.reinitTitle"),
            JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            moduleManager.initializeSources(true);
            JOptionPane.showMessageDialog(this,
                bundle.getString("moduleMgr.success.reinit"),
                bundle.getString("success.title"),
                JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                MessageFormat.format(bundle.getString("moduleMgr.error.reinitFailed"), e.getMessage()),
                bundle.getString("error.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private JDialog createProgressDialog(String title, JProgressBar progressBar) {
        JDialog dialog = new JDialog(this, title, false);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.add(new JLabel(title), BorderLayout.NORTH);
        dialog.add(progressBar, BorderLayout.CENTER);
        dialog.setSize(400, 100);
        dialog.setLocationRelativeTo(this);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        return dialog;
    }

    // Table Model
    private class ModuleTableModel extends AbstractTableModel {
        private final String[] columnNames = {
            "✓", 
            bundle.getString("moduleMgr.table.moduleName"), 
            bundle.getString("moduleMgr.table.type"), 
            bundle.getString("moduleMgr.table.language")
        };
        private final List<ModuleRow> rows = new ArrayList<>();

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) {
                return Boolean.class;
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ModuleRow row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return row.selected;
                case 1: return row.name;
                case 2: return row.type;
                case 3: return row.language;
                default: return null;
            }
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                rows.get(rowIndex).selected = (Boolean) value;
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }

        public void addModule(ModuleManager.CachedModule mod, boolean isInstalled) {
            rows.add(new ModuleRow(mod.name, mod.moduleType, mod.language, isInstalled));
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        public void addModule(ModuleManager.InstalledModule mod) {
            rows.add(new ModuleRow(mod.name, mod.moduleType, mod.language, false));
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        public void addModule(ModuleManager.UpgradableModule mod) {
            rows.add(new ModuleRow(mod.installed.name, mod.latest.moduleType, mod.installed.language, false));
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        public void clear() {
            int size = rows.size();
            if (size > 0) {
                rows.clear();
                fireTableRowsDeleted(0, size - 1);
            }
        }

        public void toggleCheckbox(int row) {
            rows.get(row).selected = !rows.get(row).selected;
            fireTableCellUpdated(row, 0);
        }

        public boolean areAllSelected() {
            return rows.stream().allMatch(r -> r.selected);
        }

        public void setAllSelected(boolean selected) {
            for (ModuleRow row : rows) {
                row.selected = selected;
            }
            fireTableDataChanged();
        }

        public boolean hasSelection() {
            return rows.stream().anyMatch(r -> r.selected);
        }

        public List<String> getSelectedModuleNames() {
            return rows.stream()
                .filter(r -> r.selected)
                .map(r -> r.name)
                .collect(Collectors.toList());
        }

        public boolean isInstalled(int row) {
            return rows.get(row).installed;
        }
    }

    private static class ModuleRow {
        boolean selected;
        String name;
        String type;
        String language;
        boolean installed;
        ModuleRow(String name, String type, String language, boolean installed) {
            this.selected = false;
            this.name = name;
            this.type = type;
            this.language = language != null ? language : "N/A";
            this.installed = installed;
        }
    }

    // Custom renderers
    private static class CheckBoxRenderer extends JCheckBox implements TableCellRenderer {
        public CheckBoxRenderer() {
            setHorizontalAlignment(JLabel.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                      boolean isSelected, boolean hasFocus,
                                                      int row, int column) {
            setSelected(value != null && (Boolean) value);
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return this;
        }
    }

    private class CheckBoxEditor extends DefaultCellEditor {
        public CheckBoxEditor() {
            super(new JCheckBox());
            JCheckBox checkBox = (JCheckBox) getComponent();
            checkBox.setHorizontalAlignment(JLabel.CENTER);
        }
    }

    private class NameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                      boolean isSelected, boolean hasFocus,
                                                      int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (currentMode == ListMode.AVAILABLE) {
                int modelRow = table.convertRowIndexToModel(row);
                if (tableModel.isInstalled(modelRow)) {
                    setText("✓ " + value.toString());
                    setForeground(new Color(0, 128, 0));
                } else {
                    setText(value.toString());
                    setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
                }
            } else {
                setText(value.toString());
                setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            }
            return c;
        }
    }
}