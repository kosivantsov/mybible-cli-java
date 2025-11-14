package org.truetranslation.mybible.gui;

import org.truetranslation.mybible.core.ConfigManager;
import org.truetranslation.mybible.core.ExtensionManager;
import org.truetranslation.mybible.core.ExtensionManager.ExtensionInfo;
import org.truetranslation.mybible.core.ExtensionManager.ExtensionValidationException;
import org.truetranslation.mybible.core.ExternalResourceBundleLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

public class GuiExtensionManager extends JDialog {

    private final ConfigManager configManager;
    private final ExtensionManager extensionManager;
    private final ResourceBundle bundle;

    private JTable extensionTable;
    private ExtensionTableModel tableModel;
    private List<ExtensionInfo> extensions;
    private boolean sortAscending = true;

    public GuiExtensionManager(JFrame parent) {
        super(parent, true);

        this.configManager = new ConfigManager();
        this.extensionManager = new ExtensionManager(configManager, 1);

        ExternalResourceBundleLoader loader = new ExternalResourceBundleLoader(
            configManager.getDefaultConfigDir()
        );
        this.bundle = loader.getBundle("i18n.gui");

        initUI();
        loadExtensions();

        setSize(600, 500);
        setLocationRelativeTo(parent);
    }

    private void initUI() {
        setTitle(bundle.getString("dialog.extensions.title"));
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        tableModel = new ExtensionTableModel();
        extensionTable = new JTable(tableModel);

        extensionTable.setRowHeight(60);
        extensionTable.setShowGrid(false);
        extensionTable.setIntercellSpacing(new Dimension(0, 0));

        extensionTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        extensionTable.getColumnModel().getColumn(0).setMaxWidth(30);
        extensionTable.getColumnModel().getColumn(1).setPreferredWidth(550);

        extensionTable.getColumnModel().getColumn(1).setCellRenderer(new ExtensionInfoRenderer());

        JTableHeader header = extensionTable.getTableHeader();
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int column = header.columnAtPoint(e.getPoint());
                if (column == 0) {
                    toggleSelectAll();
                } else if (column == 1) {
                    toggleSort();
                }
            }
        });

        header.setDefaultRenderer(new HeaderRenderer());

        JScrollPane scrollPane = new JScrollPane(extensionTable);
        scrollPane.setPreferredSize(new Dimension(580, 350));

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JLabel infoLabel = new JLabel(bundle.getString("dialog.extensions.hint"));
        infoLabel.setBorder(new EmptyBorder(5, 0, 5, 0));
        mainPanel.add(infoLabel, BorderLayout.NORTH);

        add(mainPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));

        JButton installButton = new JButton(bundle.getString("button.extensions.install"));
        JButton uninstallButton = new JButton(bundle.getString("button.extensions.uninstall"));
        JButton infoButton = new JButton(bundle.getString("button.extensions.info"));
        JButton cancelButton = new JButton(bundle.getString("button.cancel"));

        installButton.addActionListener(e -> installExtension());
        uninstallButton.addActionListener(e -> uninstallSelected());
        infoButton.addActionListener(e -> showInfo());
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(installButton);
        buttonPanel.add(uninstallButton);
        buttonPanel.add(infoButton);
        buttonPanel.add(cancelButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void loadExtensions() {
        try {
            extensions = extensionManager.listExtensions();
            tableModel.setExtensions(extensions);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                this,
                MessageFormat.format(bundle.getString("error.extensions.loadFailed"), e.getMessage()),
                bundle.getString("dialog.title.error"),
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void toggleSelectAll() {
        if (extensions.isEmpty()) {
            return;
        }

        boolean anyUnchecked = false;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (!tableModel.isChecked(i)) {
                anyUnchecked = true;
                break;
            }
        }

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setChecked(i, anyUnchecked);
        }
        tableModel.fireTableDataChanged();
    }

    private void toggleSort() {
        if (extensions.isEmpty()) {
            return;
        }

        sortAscending = !sortAscending;
        List<ExtensionInfo> sorted = new ArrayList<>(extensions);

        Collections.sort(sorted, (e1, e2) -> {
            int result = e1.manifest.name.compareToIgnoreCase(e2.manifest.name);
            return sortAscending ? result : -result;
        });

        extensions = sorted;
        tableModel.setExtensions(extensions);
    }

    private void installExtension() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter(
            bundle.getString("dialog.extensions.zipFiles"), "zip"
        ));
        fileChooser.setDialogTitle(bundle.getString("dialog.extensions.selectZip"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            Path selectedFile = fileChooser.getSelectedFile().toPath();

            try {
                extensionManager.installExtension(selectedFile);
                JOptionPane.showMessageDialog(
                    this,
                    bundle.getString("msg.extensions.installSuccess"),
                    bundle.getString("dialog.title.success"),
                    JOptionPane.INFORMATION_MESSAGE
                );
                loadExtensions();
            } catch (ExtensionValidationException e) {
                JOptionPane.showMessageDialog(
                    this,
                    MessageFormat.format(bundle.getString("error.extensions.validationFailed"), e.getMessage()),
                    bundle.getString("dialog.title.error"),
                    JOptionPane.ERROR_MESSAGE
                );
            } catch (IOException e) {
                JOptionPane.showMessageDialog(
                    this,
                    MessageFormat.format(bundle.getString("error.extensions.installFailed"), e.getMessage()),
                    bundle.getString("dialog.title.error"),
                    JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private void uninstallSelected() {
        if (extensions.isEmpty()) {
            return;
        }

        List<ExtensionInfo> toUninstall = new ArrayList<>();

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.isChecked(i)) {
                toUninstall.add(extensions.get(i));
            }
        }

        if (toUninstall.isEmpty()) {
            int selectedRow = extensionTable.getSelectedRow();
            if (selectedRow >= 0) {
                toUninstall.add(extensions.get(selectedRow));
            }
        }

        if (toUninstall.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                bundle.getString("msg.extensions.nothingSelected"),
                bundle.getString("dialog.title.info"),
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        StringBuilder message = new StringBuilder(bundle.getString("msg.extensions.confirmUninstall"));
        message.append("\n\n");
        for (ExtensionInfo ext : toUninstall) {
            message.append("  • ").append(ext.manifest.name)
                   .append(" v").append(ext.manifest.version).append("\n");
        }

        int confirm = JOptionPane.showConfirmDialog(
            this,
            message.toString(),
            bundle.getString("dialog.title.confirm"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        int successCount = 0;
        int failCount = 0;
        StringBuilder errors = new StringBuilder();

        for (ExtensionInfo ext : toUninstall) {
            try {
                extensionManager.uninstallExtension(ext.manifest.name);
                successCount++;
            } catch (IOException e) {
                failCount++;
                errors.append(ext.manifest.name).append(bundle.getString("label.separator")).append(e.getMessage()).append("\n");
            }
        }

        if (failCount == 0) {
            JOptionPane.showMessageDialog(
                this,
                MessageFormat.format(bundle.getString("msg.extensions.uninstallSuccess"), successCount),
                bundle.getString("dialog.title.success"),
                JOptionPane.INFORMATION_MESSAGE
            );
        } else {
            JOptionPane.showMessageDialog(
                this,
                MessageFormat.format(
                    bundle.getString("msg.extensions.uninstallPartial"),
                    successCount,
                    failCount
                ) + "\n\n" + errors.toString(),
                bundle.getString("dialog.title.warning"),
                JOptionPane.WARNING_MESSAGE
            );
        }

        loadExtensions();
    }

    private void showInfo() {
        if (extensions.isEmpty()) {
            return;
        }

        int selectedRow = extensionTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }

        ExtensionInfo ext = extensions.get(selectedRow);

        StringBuilder info = new StringBuilder();
        info.append("<html><body style='width: 400px;'>");
        info.append("<h3>").append(ext.manifest.name).append("</h3>");
        info.append("<p><b>").append(bundle.getString("label.extensions.version")).append("</b> ")
            .append(ext.manifest.version).append("</p>");

        if (ext.manifest.author != null) {
            info.append("<p><b>").append(bundle.getString("label.extensions.author")).append("</b> ")
                .append(ext.manifest.author).append("</p>");
        }

        if (ext.manifest.description != null) {
            info.append("<p><b>").append(bundle.getString("label.extensions.description")).append("</b><br>")
                .append(ext.manifest.description).append("</p>");
        }

        info.append("<p><b>").append(bundle.getString("label.extensions.files")).append("</b></p>");
        info.append("<ul>");

        if (ext.manifest.mappingFiles != null && !ext.manifest.mappingFiles.isEmpty()) {
            info.append("<li><b>").append(bundle.getString("label.extensions.mappings")).append("</b><br>");
            for (String file : ext.manifest.mappingFiles) {
                info.append("&nbsp;&nbsp;• ").append(file).append("<br>");
            }
            info.append("</li>");
        }

        if (ext.manifest.resourceFiles != null && !ext.manifest.resourceFiles.isEmpty()) {
            info.append("<li><b>").append(bundle.getString("label.extensions.resources")).append("</b><br>");
            for (String file : ext.manifest.resourceFiles) {
                info.append("&nbsp;&nbsp;• ").append(file).append("<br>");
            }
            info.append("</li>");
        }

        if (ext.manifest.themeFiles != null && !ext.manifest.themeFiles.isEmpty()) {
            info.append("<li><b>").append(bundle.getString("label.extensions.themes")).append("</b><br>");
            for (String file : ext.manifest.themeFiles) {
                info.append("&nbsp;&nbsp;• ").append(file).append("<br>");
            }
            info.append("</li>");
        }

        info.append("</ul>");
        info.append("</body></html>");

        JOptionPane.showMessageDialog(
            this,
            info.toString(),
            bundle.getString("dialog.extensions.infoTitle"),
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private class ExtensionTableModel extends AbstractTableModel {
        private List<ExtensionInfo> extensions = new ArrayList<>();
        private List<Boolean> checkedStates = new ArrayList<>();

        public void setExtensions(List<ExtensionInfo> extensions) {
            this.extensions = new ArrayList<>(extensions);
            this.checkedStates = new ArrayList<>();
            for (int i = 0; i < extensions.size(); i++) {
                checkedStates.add(false);
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return extensions.isEmpty() ? 1 : extensions.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "✓" : bundle.getString("dialog.extensions.extensionsHeader");
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : ExtensionInfo.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            if (extensions.isEmpty()) {
                return column == 0 ? false : null;
            }
            if (column == 0) {
                return checkedStates.get(row);
            } else {
                return extensions.get(row);
            }
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 0 && !extensions.isEmpty() && row < checkedStates.size()) {
                checkedStates.set(row, (Boolean) value);
                fireTableCellUpdated(row, column);
            }
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 && !extensions.isEmpty();
        }

        public boolean isChecked(int row) {
            return row < checkedStates.size() ? checkedStates.get(row) : false;
        }

        public void setChecked(int row, boolean checked) {
            if (row < checkedStates.size()) {
                checkedStates.set(row, checked);
            }
        }
    }

    private class ExtensionInfoRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(new EmptyBorder(5, 10, 5, 10));

            if (value == null) {
                JLabel label = new JLabel(bundle.getString("msg.extensions.noExtensions"));
                label.setForeground(Color.GRAY);
                panel.add(label);
            } else {
                ExtensionInfo ext = (ExtensionInfo) value;

                JLabel nameLabel = new JLabel(ext.manifest.name + " v" + ext.manifest.version);
                nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));

                String details = ext.manifest.description != null ? ext.manifest.description : "";
                if (ext.manifest.author != null) {
                    details += " " + bundle.getString("label.extensions.separator") + " " + 
                              bundle.getString("label.extensions.by") + " " + ext.manifest.author;
                }
                JLabel detailsLabel = new JLabel(details);
                detailsLabel.setFont(detailsLabel.getFont().deriveFont(11f));
                detailsLabel.setForeground(Color.GRAY);

                panel.add(nameLabel);
                panel.add(Box.createVerticalStrut(3));
                panel.add(detailsLabel);
            }

            if (isSelected) {
                panel.setBackground(table.getSelectionBackground());
            } else {
                panel.setBackground(table.getBackground());
            }
            panel.setOpaque(true);

            return panel;
        }
    }

    private class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (c instanceof JLabel) {
                JLabel label = (JLabel) c;
                label.setHorizontalAlignment(JLabel.CENTER);
                label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
            }
            return c;
        }
    }
}
