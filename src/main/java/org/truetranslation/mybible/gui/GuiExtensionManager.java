package org.truetranslation.mybible.gui;

import org.truetranslation.mybible.core.ConfigManager;
import org.truetranslation.mybible.core.ExtensionManager;
import org.truetranslation.mybible.core.ExtensionManager.ExtensionInfo;
import org.truetranslation.mybible.core.ExtensionManager.ExtensionValidationException;
import org.truetranslation.mybible.core.ExternalResourceBundleLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class GuiExtensionManager extends JDialog {

    private final ConfigManager configManager;
    private final ExtensionManager extensionManager;
    private final ResourceBundle bundle;

    private JList<ExtensionListItem> extensionList;
    private DefaultListModel<ExtensionListItem> listModel;
    private List<ExtensionInfo> extensions;

    public GuiExtensionManager(JFrame parent) {
        super(parent, true); // Modal dialog

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

        // Main panel with padding
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // List panel
        listModel = new DefaultListModel<>();
        extensionList = new JList<>(listModel);
        extensionList.setCellRenderer(new ExtensionListCellRenderer());
        extensionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Add keyboard navigation
        extensionList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    toggleSelectedCheckbox();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    showInfo();
                    e.consume();
                }
            }
        });

        // Add mouse listener for double-click info
        extensionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    showInfo();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(extensionList);
        scrollPane.setPreferredSize(new Dimension(580, 350));

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Info label
        JLabel infoLabel = new JLabel(bundle.getString("dialog.extensions.hint"));
        infoLabel.setBorder(new EmptyBorder(5, 0, 5, 0));
        mainPanel.add(infoLabel, BorderLayout.NORTH);

        add(mainPanel, BorderLayout.CENTER);

        // Button panel
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
            listModel.clear();

            if (extensions.isEmpty()) {
                // Show empty state
                ExtensionListItem emptyItem = new ExtensionListItem(null, false);
                listModel.addElement(emptyItem);
            } else {
                for (ExtensionInfo ext : extensions) {
                    ExtensionListItem item = new ExtensionListItem(ext, false);
                    listModel.addElement(item);
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                this,
                MessageFormat.format(bundle.getString("error.extensions.loadFailed"), e.getMessage()),
                bundle.getString("dialog.title.error"),
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void toggleSelectedCheckbox() {
        int index = extensionList.getSelectedIndex();
        if (index >= 0 && index < listModel.size()) {
            ExtensionListItem item = listModel.getElementAt(index);
            if (item.extensionInfo != null) {
                item.checked = !item.checked;
                extensionList.repaint();
            }
        }
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
                loadExtensions(); // Reload the list
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

        // Collect items to uninstall
        List<ExtensionInfo> toUninstall = new ArrayList<>();

        // First check for checked items
        for (int i = 0; i < listModel.size(); i++) {
            ExtensionListItem item = listModel.getElementAt(i);
            if (item.checked && item.extensionInfo != null) {
                toUninstall.add(item.extensionInfo);
            }
        }

        // If nothing is checked, use the selected item
        if (toUninstall.isEmpty()) {
            int selectedIndex = extensionList.getSelectedIndex();
            if (selectedIndex >= 0) {
                ExtensionListItem item = listModel.getElementAt(selectedIndex);
                if (item.extensionInfo != null) {
                    toUninstall.add(item.extensionInfo);
                }
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

        // Confirm uninstallation
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

        // Uninstall extensions
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

        // Show results
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

        loadExtensions(); // Reload the list
    }

    private void showInfo() {
        if (extensions.isEmpty()) {
            return;
        }

        int selectedIndex = extensionList.getSelectedIndex();
        if (selectedIndex < 0) {
            return;
        }

        ExtensionListItem item = listModel.getElementAt(selectedIndex);
        if (item.extensionInfo == null) {
            return;
        }

        ExtensionInfo ext = item.extensionInfo;

        // Build info message
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

    /**
     * List item wrapper class.
     */
    private static class ExtensionListItem {
        ExtensionInfo extensionInfo;
        boolean checked;

        ExtensionListItem(ExtensionInfo extensionInfo, boolean checked) {
            this.extensionInfo = extensionInfo;
            this.checked = checked;
        }
    }

    /**
     * Custom cell renderer for extension list.
     */
    private class ExtensionListCellRenderer extends JPanel implements ListCellRenderer<ExtensionListItem> {
        private JCheckBox checkbox;
        private JLabel nameLabel;
        private JLabel detailsLabel;

        ExtensionListCellRenderer() {
            setLayout(new BorderLayout(10, 5));
            setBorder(new EmptyBorder(5, 10, 5, 10));

            checkbox = new JCheckBox();
            checkbox.setFocusable(false);

            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);

            nameLabel = new JLabel();
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));

            detailsLabel = new JLabel();
            detailsLabel.setFont(detailsLabel.getFont().deriveFont(11f));

            textPanel.add(nameLabel);
            textPanel.add(Box.createVerticalStrut(3));
            textPanel.add(detailsLabel);

            add(checkbox, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends ExtensionListItem> list,
                ExtensionListItem item,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {

            if (item.extensionInfo == null) {
                // Empty state
                checkbox.setVisible(false);
                nameLabel.setText(bundle.getString("msg.extensions.noExtensions"));
                detailsLabel.setText("");
                nameLabel.setForeground(Color.GRAY);
            } else {
                checkbox.setVisible(true);
                checkbox.setSelected(item.checked);

                ExtensionInfo ext = item.extensionInfo;
                nameLabel.setText(ext.manifest.name + " v" + ext.manifest.version);

                String details = ext.manifest.description != null ? ext.manifest.description : "";
                if (ext.manifest.author != null) {
                    details += " " + bundle.getString("label.extensions.separator") + " " + 
                              bundle.getString("label.extensions.by") + " " + ext.manifest.author;
                }
                detailsLabel.setText(details);

                nameLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
                detailsLabel.setForeground(isSelected ? list.getSelectionForeground() : Color.GRAY);
            }

            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            setOpaque(true);

            return this;
        }
    }
}
