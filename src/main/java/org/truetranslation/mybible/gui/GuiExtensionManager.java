package org.truetranslation.mybible.gui;

import org.truetranslation.mybible.core.ConfigManager;
import org.truetranslation.mybible.core.ExtensionManager;
import org.truetranslation.mybible.core.ExtensionManager.ExtensionInfo;
import org.truetranslation.mybible.core.ExtensionManager.RegistryExtension;
import org.truetranslation.mybible.core.ExtensionManager.ExtensionValidationException;
import org.truetranslation.mybible.core.ExternalResourceBundleLoader;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class GuiExtensionManager extends JDialog {

    private final ConfigManager configManager;
    private ExtensionManager extensionManager;
    private final ResourceBundle bundle;
    private final Frame owner;

    private JRadioButton installedRadio;
    private JRadioButton availableRadio;
    private JRadioButton upgradableRadio;

    private JTable extensionTable;
    private ExtensionTableModel tableModel;

    private JComboBox<String> typeFilter;
    private JTextField nameFilter;

    private JButton actionButton;
    private JButton infoButton;
    private JButton updateButton;
    private JButton installFromFileButton;
    private JButton closeButton;

    private ListMode currentMode = ListMode.INSTALLED;

    private static final String[] EXTENSION_TYPES = {"All", "theme", "mapping", "localization", "bundle"};

    private enum ListMode {
        INSTALLED, AVAILABLE, UPGRADABLE
    }

    public GuiExtensionManager(Frame owner) {
        super(owner, true);
        this.owner = owner;
        this.configManager = new ConfigManager();

        ExternalResourceBundleLoader loader = new ExternalResourceBundleLoader(
            configManager.getDefaultConfigDir()
        );
        this.bundle = loader.getBundle("i18n.gui");

        try {
            this.extensionManager = new ExtensionManager(configManager, 0);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                MessageFormat.format(bundle.getString("error.extensions.initFailed"), e.getMessage()),
                bundle.getString("error.title"),
                JOptionPane.ERROR_MESSAGE);
            dispose();
            return;
        }

        setTitle(bundle.getString("dialog.extensions.title"));

        initComponents();
        layoutComponents();
        setupListeners();
        setupKeyBindings();

        pack();
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(owner);

        loadExtensions();
    }

    private void initComponents() {
        installedRadio = new JRadioButton(bundle.getString("dialog.extensions.installed"), true);
        availableRadio = new JRadioButton(bundle.getString("dialog.extensions.available"));
        upgradableRadio = new JRadioButton(bundle.getString("dialog.extensions.upgradable"));

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(installedRadio);
        modeGroup.add(availableRadio);
        modeGroup.add(upgradableRadio);

        tableModel = new ExtensionTableModel();
        extensionTable = new JTable(tableModel);
        extensionTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        extensionTable.setAutoCreateRowSorter(true);
        extensionTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        extensionTable.getColumnModel().getColumn(0).setMaxWidth(30);
        extensionTable.getColumnModel().getColumn(0).setMinWidth(30);
        extensionTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        extensionTable.getColumnModel().getColumn(0).setResizable(false);

        extensionTable.getColumnModel().getColumn(0).setCellRenderer(new CheckBoxRenderer());
        extensionTable.getColumnModel().getColumn(0).setCellEditor(new CheckBoxEditor());

        extensionTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    toggleSelectedRows();
                    e.consume();
                }
            }
        });

        extensionTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                int modelRow = table.convertRowIndexToModel(row);
                if (tableModel.isInstalled(modelRow)) {
                    setText("✓ " + value.toString());
                    setForeground(new Color(0, 128, 0));
                } else {
                    setText(value.toString());
                    setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
                }
                
                return this;
            }
        });

        typeFilter = new JComboBox<>(EXTENSION_TYPES);
        nameFilter = new JTextField(20);

        actionButton = new JButton(bundle.getString("button.extensions.uninstall"));
        actionButton.setEnabled(false);
        infoButton = new JButton(bundle.getString("button.extensions.info"));
        updateButton = new JButton(bundle.getString("button.extensions.update"));
        installFromFileButton = new JButton(bundle.getString("button.extensions.installFile"));
        closeButton = new JButton(bundle.getString("button.cancel"));
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(installedRadio);
        topPanel.add(availableRadio);
        topPanel.add(upgradableRadio);

        JScrollPane scrollPane = new JScrollPane(extensionTable);
        scrollPane.setPreferredSize(new Dimension(750, 300));

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        filterPanel.add(new JLabel(bundle.getString("label.extensions.type")));
        filterPanel.add(typeFilter);
        filterPanel.add(new JLabel(bundle.getString("label.extensions.name")));
        filterPanel.add(nameFilter);

        JButton filterButton = new JButton(bundle.getString("button.filter"));
        filterButton.addActionListener(e -> applyFilters());
        filterPanel.add(filterButton);

        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel leftButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        leftButtonPanel.add(updateButton);
        leftButtonPanel.add(installFromFileButton);
        leftButtonPanel.add(infoButton);

        JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        rightButtonPanel.add(actionButton);
        rightButtonPanel.add(closeButton);

        bottomPanel.add(leftButtonPanel, BorderLayout.WEST);
        bottomPanel.add(rightButtonPanel, BorderLayout.EAST);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(filterPanel, BorderLayout.NORTH);
        southPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        ((JPanel)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void setupListeners() {
        ActionListener modeListener = e -> {
            if (installedRadio.isSelected()) {
                currentMode = ListMode.INSTALLED;
                actionButton.setText(bundle.getString("button.extensions.uninstall"));
            } else if (availableRadio.isSelected()) {
                currentMode = ListMode.AVAILABLE;
                actionButton.setText(bundle.getString("button.extensions.install"));
            } else if (upgradableRadio.isSelected()) {
                currentMode = ListMode.UPGRADABLE;
                actionButton.setText(bundle.getString("button.extensions.upgrade"));
            }
            loadExtensions();
        };

        installedRadio.addActionListener(modeListener);
        availableRadio.addActionListener(modeListener);
        upgradableRadio.addActionListener(modeListener);

        extensionTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int column = extensionTable.columnAtPoint(e.getPoint());
                if (column == 0) {
                    toggleAllRows();
                }
            }
        });

        extensionTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateActionButtonState();
            }
        });

        tableModel.addTableModelListener(e -> updateActionButtonState());

        actionButton.addActionListener(e -> performAction());
        infoButton.addActionListener(e -> showExtensionInfo());
        updateButton.addActionListener(e -> updateRegistry());
        installFromFileButton.addActionListener(e -> installFromFile());
        closeButton.addActionListener(e -> dispose());

        KeyAdapter enterListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    applyFilters();
                }
            }
        };
        nameFilter.addKeyListener(enterListener);
    }

    private void setupKeyBindings() {
        JRootPane rootPane = getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        int modifierKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        actionMap.put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_1, modifierKey), "installed");
        actionMap.put("installed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                installedRadio.doClick();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_2, modifierKey), "available");
        actionMap.put("available", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                availableRadio.doClick();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_3, modifierKey), "upgradable");
        actionMap.put("upgradable", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                upgradableRadio.doClick();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, modifierKey), "filter");
        actionMap.put("filter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyFilters();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, modifierKey), "info");
        actionMap.put("info", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showExtensionInfo();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_U, modifierKey), "update");
        actionMap.put("update", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateRegistry();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, modifierKey), "open");
        actionMap.put("open", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                installFromFile();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, modifierKey), "action");
        actionMap.put("action", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (actionButton.isEnabled()) {
                    performAction();
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_T, modifierKey), "focusType");
        actionMap.put("focusType", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                typeFilter.requestFocusInWindow();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, modifierKey), "focusName");
        actionMap.put("focusName", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                nameFilter.requestFocusInWindow();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, modifierKey), "focusTable");
        actionMap.put("focusTable", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                extensionTable.requestFocusInWindow();
            }
        });
    }

    private void loadExtensions() {
        tableModel.clear();

        try {
            String typeFilterStr = getSelectedType();
            String nameFilterStr = nameFilter.getText().trim();

            switch (currentMode) {
                case INSTALLED:
                    List<ExtensionInfo> installed = extensionManager.listInstalledExtensions();
                    for (ExtensionInfo ext : installed) {
                        if (typeFilterStr != null && ext.manifest.type != null && 
                            !ext.manifest.type.equalsIgnoreCase(typeFilterStr)) {
                            continue;
                        }
                        if (!nameFilterStr.isEmpty() && ext.manifest.name != null &&
                            !ext.manifest.name.toLowerCase().contains(nameFilterStr.toLowerCase())) {
                            continue;
                        }
                        tableModel.addExtension(ext, false);
                    }
                    break;

                case AVAILABLE:
                    List<ExtensionInfo> installedExts = extensionManager.listInstalledExtensions();
                    Map<String, String> installedMap = installedExts.stream()
                        .collect(Collectors.toMap(ext -> ext.manifest.name, ext -> ext.manifest.version));

                    List<RegistryExtension> available = extensionManager.listAvailableExtensions(
                        typeFilterStr, nameFilterStr.isEmpty() ? null : nameFilterStr);
                    for (RegistryExtension ext : available) {
                        boolean isInstalled = installedMap.containsKey(ext.name);
                        tableModel.addExtension(ext, isInstalled);
                    }
                    break;

                case UPGRADABLE:
                    List<ExtensionInfo> upgradable = extensionManager.listUpgradableExtensions();
                    for (ExtensionInfo ext : upgradable) {
                        if (typeFilterStr != null && ext.manifest.type != null && 
                            !ext.manifest.type.equalsIgnoreCase(typeFilterStr)) {
                            continue;
                        }
                        if (!nameFilterStr.isEmpty() && ext.manifest.name != null &&
                            !ext.manifest.name.toLowerCase().contains(nameFilterStr.toLowerCase())) {
                            continue;
                        }
                        tableModel.addExtension(ext, false);
                    }
                    break;
            }

            SwingUtilities.invokeLater(() -> resizeColumnWidths());

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                MessageFormat.format(bundle.getString("error.extensions.loadFailed"), e.getMessage()),
                bundle.getString("error.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resizeColumnWidths() {
        for (int column = 0; column < extensionTable.getColumnCount(); column++) {
            if (column == 0) continue;

            TableColumn tableColumn = extensionTable.getColumnModel().getColumn(column);
            int preferredWidth = 50;

            TableCellRenderer headerRenderer = extensionTable.getTableHeader().getDefaultRenderer();
            Component headerComp = headerRenderer.getTableCellRendererComponent(
                extensionTable, tableColumn.getHeaderValue(), false, false, 0, column);
            preferredWidth = Math.max(preferredWidth, headerComp.getPreferredSize().width + 20);

            for (int row = 0; row < extensionTable.getRowCount(); row++) {
                TableCellRenderer cellRenderer = extensionTable.getCellRenderer(row, column);
                Component comp = extensionTable.prepareRenderer(cellRenderer, row, column);
                preferredWidth = Math.max(preferredWidth, comp.getPreferredSize().width + 20);
            }

            tableColumn.setPreferredWidth(preferredWidth);
        }
    }

    private String getSelectedType() {
        String selected = (String) typeFilter.getSelectedItem();
        if (selected == null || selected.equals("All")) {
            return null;
        }
        return selected;
    }

    private void applyFilters() {
        loadExtensions();
    }

    private void toggleSelectedRows() {
        int[] selectedRows = extensionTable.getSelectedRows();
        for (int row : selectedRows) {
            int modelRow = extensionTable.convertRowIndexToModel(row);
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
        List<String> selectedNames = tableModel.getSelectedExtensionNames();
        if (selectedNames.isEmpty()) {
            return;
        }

        switch (currentMode) {
            case INSTALLED:
                uninstallExtensions(selectedNames);
                break;
            case AVAILABLE:
                installExtensions(selectedNames);
                break;
            case UPGRADABLE:
                upgradeExtensions(selectedNames);
                break;
        }
    }

    private void installExtensions(List<String> names) {
        JProgressBar progressBar = new JProgressBar(0, 100);
        JDialog progressDialog = createProgressDialog(bundle.getString("dialog.extensions.installing"), progressBar);

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                int total = names.size();
                int current = 0;

                for (String name : names) {
                    current++;
                    publish(MessageFormat.format("Installing {0} ({1}/{2})", name, current, total));

                    try {
                        extensionManager.installExtension(name);
                    } catch (Exception e) {
                        publish(MessageFormat.format("Failed to install {0}: {1}", name, e.getMessage()));
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
                loadExtensions();
                JOptionPane.showMessageDialog(GuiExtensionManager.this,
                    bundle.getString("msg.extensions.installSuccess"),
                    bundle.getString("success.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private void upgradeExtensions(List<String> names) {
        int confirm = JOptionPane.showConfirmDialog(this,
            MessageFormat.format(bundle.getString("msg.extensions.confirmUpgrade"), names.size()),
            bundle.getString("dialog.title.confirm"),
            JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        JProgressBar progressBar = new JProgressBar(0, 100);
        JDialog progressDialog = createProgressDialog(bundle.getString("dialog.extensions.upgrading"), progressBar);

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                int total = names.size();
                int current = 0;

                for (String name : names) {
                    current++;
                    publish(MessageFormat.format("Upgrading {0} ({1}/{2})", name, current, total));

                    try {
                        extensionManager.upgradeExtension(name);
                    } catch (Exception e) {
                        publish(MessageFormat.format("Failed to upgrade {0}: {1}", name, e.getMessage()));
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
                loadExtensions();
                JOptionPane.showMessageDialog(GuiExtensionManager.this,
                    bundle.getString("msg.extensions.upgradeSuccess"),
                    bundle.getString("success.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private void uninstallExtensions(List<String> names) {
        int confirm = JOptionPane.showConfirmDialog(this,
            MessageFormat.format(bundle.getString("msg.extensions.confirmUninstall"), names.size()),
            bundle.getString("dialog.title.confirm"),
            JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            for (String name : names) {
                extensionManager.uninstallExtension(name);
            }
            loadExtensions();
            JOptionPane.showMessageDialog(this,
                MessageFormat.format(bundle.getString("msg.extensions.uninstallSuccess"), names.size()),
                bundle.getString("success.title"),
                JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                MessageFormat.format(bundle.getString("error.extensions.uninstallFailed"), e.getMessage()),
                bundle.getString("error.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void installFromFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Extension ZIP files", "zip"));
        fileChooser.setDialogTitle(bundle.getString("dialog.extensions.selectZip"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            Path selectedFile = fileChooser.getSelectedFile().toPath();

            try {
                extensionManager.installExtension(selectedFile.toString());
                JOptionPane.showMessageDialog(this,
                    bundle.getString("msg.extensions.installSuccess"),
                    bundle.getString("success.title"),
                    JOptionPane.INFORMATION_MESSAGE);
                loadExtensions();
            } catch (ExtensionValidationException e) {
                JOptionPane.showMessageDialog(this,
                    MessageFormat.format(bundle.getString("error.extensions.validationFailed"), e.getMessage()),
                    bundle.getString("error.title"),
                    JOptionPane.ERROR_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                    MessageFormat.format(bundle.getString("error.extensions.installFailed"), e.getMessage()),
                    bundle.getString("error.title"),
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void updateRegistry() {
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setIndeterminate(true);
        JDialog progressDialog = createProgressDialog(bundle.getString("dialog.extensions.updating"), progressBar);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    extensionManager.updateRegistries();
                } catch (IOException e) {
                    throw new Exception(MessageFormat.format(
                        "Registry update failed: {0}",
                        e.getMessage()));
                }
                return null;
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    get();
                    loadExtensions();
                    JOptionPane.showMessageDialog(GuiExtensionManager.this,
                        bundle.getString("msg.extensions.updateSuccess"),
                        bundle.getString("success.title"),
                        JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(GuiExtensionManager.this,
                        e.getMessage(),
                        bundle.getString("error.title"),
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private void showExtensionInfo() {
        int selectedRow = extensionTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this,
                bundle.getString("msg.extensions.nothingSelected"),
                bundle.getString("dialog.title.info"),
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = extensionTable.convertRowIndexToModel(selectedRow);
        Object extensionObj = tableModel.getExtensionAt(modelRow);

        StringBuilder info = new StringBuilder();
        info.append("<html><body style='width: 400px;'>");

        if (extensionObj instanceof ExtensionInfo) {
            ExtensionInfo ext = (ExtensionInfo) extensionObj;
            info.append("<h3>").append(ext.manifest.name).append("</h3>");
            info.append("<p><b>").append(bundle.getString("label.extensions.version")).append(":</b> ")
                .append(ext.manifest.version).append("</p>");
            info.append("<p><b>").append(bundle.getString("label.extensions.type")).append(":</b> ")
                .append(ext.manifest.type != null ? ext.manifest.type : "unknown").append("</p>");

            if (ext.manifest.author != null) {
                info.append("<p><b>").append(bundle.getString("label.extensions.author")).append(":</b> ")
                    .append(ext.manifest.author).append("</p>");
            }

            if (ext.manifest.description != null) {
                info.append("<p><b>").append(bundle.getString("label.extensions.description")).append(":</b><br>")
                    .append(ext.manifest.description).append("</p>");
            }

            info.append("<p><b>Status:</b> Installed</p>");

        } else if (extensionObj instanceof RegistryExtension) {
            RegistryExtension ext = (RegistryExtension) extensionObj;
            info.append("<h3>").append(ext.name).append("</h3>");
            info.append("<p><b>").append(bundle.getString("label.extensions.version")).append(":</b> ")
                .append(ext.version).append("</p>");
            info.append("<p><b>").append(bundle.getString("label.extensions.type")).append(":</b> ")
                .append(ext.type).append("</p>");

            if (ext.author != null) {
                info.append("<p><b>").append(bundle.getString("label.extensions.author")).append(":</b> ")
                    .append(ext.author).append("</p>");
            }

            if (ext.description != null) {
                info.append("<p><b>").append(bundle.getString("label.extensions.description")).append(":</b><br>")
                    .append(ext.description).append("</p>");
            }

            if (ext.size != null) {
                info.append("<p><b>Size:</b> ").append(ext.size / 1024.0).append(" KB</p>");
            }

            info.append("<p><b>Status:</b> Available</p>");
        }

        info.append("</body></html>");

        JOptionPane.showMessageDialog(this,
            info.toString(),
            bundle.getString("dialog.extensions.infoTitle"),
            JOptionPane.INFORMATION_MESSAGE);
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

    private class ExtensionTableModel extends AbstractTableModel {
        private final String[] columnNames = {
            "✓",
            bundle.getString("dialog.extensions.name"),
            bundle.getString("dialog.extensions.type"),
            bundle.getString("dialog.extensions.version")
        };
        private final List<ExtensionRow> rows = new ArrayList<>();

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
            ExtensionRow row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return row.selected;
                case 1: return row.name;
                case 2: return row.type;
                case 3: return row.version;
                default: return null;
            }
        }

        public boolean isInstalled(int row) {
            return rows.get(row).isInstalled;
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                rows.get(rowIndex).selected = (Boolean) value;
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }

        public void addExtension(ExtensionInfo ext, boolean isInstalled) {
            rows.add(new ExtensionRow(ext.manifest.name, 
                ext.manifest.type != null ? ext.manifest.type : "unknown",
                ext.manifest.version, ext, isInstalled));
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        public void addExtension(RegistryExtension ext, boolean isInstalled) {
            rows.add(new ExtensionRow(ext.name, ext.type, ext.version, ext, isInstalled));
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
            for (ExtensionRow row : rows) {
                row.selected = selected;
            }
            fireTableDataChanged();
        }

        public boolean hasSelection() {
            return rows.stream().anyMatch(r -> r.selected);
        }

        public List<String> getSelectedExtensionNames() {
            return rows.stream()
                .filter(r -> r.selected)
                .map(r -> r.name)
                .collect(Collectors.toList());
        }

        public Object getExtensionAt(int row) {
            return rows.get(row).extension;
        }
    }

    private static class ExtensionRow {
        boolean selected;
        String name;
        String type;
        String version;
        Object extension;
        boolean isInstalled;

        ExtensionRow(String name, String type, String version, Object extension, boolean isInstalled) {
            this.selected = false;
            this.name = name;
            this.type = type;
            this.version = version;
            this.extension = extension;
            this.isInstalled = isInstalled;
        }
    }

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
}
