package org.truetranslation.mybible.gui;

import com.formdev.flatlaf.intellijthemes.*;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

import javax.swing.*;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import org.truetranslation.mybible.core.*;

public class ConfigurationDialog extends JDialog {

    private GuiConfig config;
    private final GuiConfigManager configManager;
    private final JFrame owner;
    private final ResourceBundle bundle;
    private Color chosenBackgroundColor;
    private JPanel colorPreviewPanel;

    private final LookAndFeel originalLaf;
    private boolean saved = false;

    private JTextField formatStringField;
    private JComboBox<ThemeInfo> lafComboBox;
    private JButton colorButton;

    private static class ThemeInfo {
        final String name;
        final String className;
        ThemeInfo(String name, String className) { this.name = name; this.className = className; }
        @Override public String toString() { return name; }
    }

    private static final List<ThemeInfo> THEMES = Arrays.asList(
            new ThemeInfo("Arc Light", FlatArcIJTheme.class.getName()),
            new ThemeInfo("Arc Dark", FlatArcDarkIJTheme.class.getName()),
            new ThemeInfo("macOS Light", FlatMacLightLaf.class.getName()),
            new ThemeInfo("macOS Dark", FlatMacDarkLaf.class.getName()),
            new ThemeInfo("Carbon", FlatCarbonIJTheme.class.getName()),
            new ThemeInfo("Cobalt 2", FlatCobalt2IJTheme.class.getName()),
            new ThemeInfo("Cyan Light", FlatCyanLightIJTheme.class.getName()),
            new ThemeInfo("Dracula", FlatDraculaIJTheme.class.getName()),
            new ThemeInfo("Gradianto Deep Ocean", FlatGradiantoDeepOceanIJTheme.class.getName()),
            new ThemeInfo("Gruvbox Dark Hard", FlatGruvboxDarkHardIJTheme.class.getName()),
            new ThemeInfo("Monocai", FlatMonocaiIJTheme.class.getName()),
            new ThemeInfo("Nord", FlatNordIJTheme.class.getName()),
            new ThemeInfo("One Dark", FlatOneDarkIJTheme.class.getName()),
            new ThemeInfo("Solarized Light", FlatSolarizedLightIJTheme.class.getName()),
            new ThemeInfo("Solarized Dark", FlatSolarizedDarkIJTheme.class.getName())
    );

    private static ConfigManager coreConfigManager = new ConfigManager();

    public ConfigurationDialog(JFrame owner, GuiConfigManager configManager) {
        super(owner, "Configuration", true);
        this.owner = owner;
        this.configManager = configManager;
        this.config = configManager.getConfig();
        ExternalResourceBundleLoader externalLoader = new ExternalResourceBundleLoader(
            coreConfigManager.getDefaultConfigDir()
        );
        this.bundle = externalLoader.getBundle("i18n.gui");
        this.chosenBackgroundColor = config.textAreaBackground;

        this.originalLaf = UIManager.getLookAndFeel();

        setTitle(bundle.getString("dialog.title.configure"));
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = createMainPanel();
        add(mainPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel themeButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveThemeButton = new JButton(bundle.getString("button.saveTheme"));
        JButton loadThemeButton = new JButton(bundle.getString("button.loadTheme"));
        JButton extensionsButton = new JButton(bundle.getString("button.manageExtensions"));
        themeButtonPanel.add(saveThemeButton);
        themeButtonPanel.add(loadThemeButton);
        themeButtonPanel.add(extensionsButton);

        JPanel actionButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton resetButton = new JButton(bundle.getString("button.resetDefaults"));
        JButton saveButton = new JButton(bundle.getString("button.save"));
        JButton cancelButton = new JButton(bundle.getString("button.cancel"));
        actionButtonPanel.add(resetButton);
        actionButtonPanel.add(saveButton);
        actionButtonPanel.add(cancelButton);

        bottomPanel.add(themeButtonPanel, BorderLayout.NORTH);
        bottomPanel.add(actionButtonPanel, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        saveThemeButton.addActionListener(e -> saveCurrentTheme());
        loadThemeButton.addActionListener(e -> loadTheme());
        extensionsButton.addActionListener(e -> openExtensionManager());
        resetButton.addActionListener(e -> resetToDefaults());
        cancelButton.addActionListener(e -> dispose());
        saveButton.addActionListener(e -> {
            // Update the config object from the UI fields
            config.formatString = formatStringField.getText();
            config.lookAndFeelClassName = ((ThemeInfo) lafComboBox.getSelectedItem()).className;
            config.textAreaBackground = chosenBackgroundColor;
            ensureAllStylesExist();
            // Set the new config in the manager and then save it
            configManager.setConfig(this.config);
            configManager.saveConfig();
            saved = true;
            applyLookAndFeel();
            dispose();
        });

        initEscapeToClose();
        pack();
        setLocationRelativeTo(owner);
    }

    private void openExtensionManager() {
        GuiExtensionManager extManager = new GuiExtensionManager(owner);
        extManager.setVisible(true);

        GuiThemeManager themeManager = new GuiThemeManager();
        List<String> currentThemes = themeManager.getAvailableThemes();

        SwingUtilities.updateComponentTreeUI(this);
        repaint();
    }

    private void resetToDefaults() {
        JPanel messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        JLabel messageLabel = new JLabel(bundle.getString("dialog.message.resetConfirm"));
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JCheckBox resetAllCheckbox = new JCheckBox(bundle.getString("checkbox.label.resetAllSettings"));
        resetAllCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        messagePanel.add(messageLabel);
        messagePanel.add(Box.createRigidArea(new Dimension(0, 5)));
        messagePanel.add(resetAllCheckbox);

        int response = JOptionPane.showConfirmDialog(
            this,
            messagePanel,
            bundle.getString("dialog.title.resetConfirm"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (response == JOptionPane.YES_OPTION) {
            boolean fullResetPerformed = resetAllCheckbox.isSelected();

            if (fullResetPerformed) {
                coreConfigManager.resetToDefaults();
            }

            GuiConfig defaultConfig = new GuiConfig();
            configManager.setConfig(defaultConfig);
            configManager.saveConfig();
            this.config = configManager.getConfig();

            applyLookAndFeel(); 

            Window parentWindow = this.getOwner();
            if (parentWindow != null) {
                SwingUtilities.updateComponentTreeUI(parentWindow);
            }
            SwingUtilities.updateComponentTreeUI(this);

            updateUiFromConfig();

            if (fullResetPerformed) {
                JOptionPane.showMessageDialog(
                    this,
                    bundle.getString("dialog.message.restartRequired"),
                    bundle.getString("dialog.title.restartRequired"),
                    JOptionPane.INFORMATION_MESSAGE
                );
                System.exit(0);
            }
        }
    }

    private void updateUiFromConfig() {
        // Update format string
        formatStringField.setText(this.config.formatString);
        // Update background color
        chosenBackgroundColor = this.config.textAreaBackground;
        colorButton.setBackground(chosenBackgroundColor != null ? chosenBackgroundColor : UIManager.getColor("Button.background"));
        // Update Look and Feel dropdown
        THEMES.stream()
              .filter(t -> t.className.equals(this.config.lookAndFeelClassName))
              .findFirst()
              .ifPresent(lafComboBox::setSelectedItem);
        // Re-create the main panel to update all style rows
        Container contentPane = getContentPane();
        Component oldCenter = ((BorderLayout)contentPane.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            if (oldCenter != null) {
                contentPane.remove(oldCenter);
            }
        JPanel newMainPanel = createMainPanel();
        add(newMainPanel, BorderLayout.CENTER);
        // Refresh the layout
        revalidate();
        repaint();
        pack();
    }

    private JPanel createMainPanel() {
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridy = 0; gbc.gridx = 0; mainPanel.add(new JLabel(bundle.getString("label.formatString")), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        formatStringField = new JTextField(config.formatString, 30);
        String tooltipText = bundle.getString("dialog.tooltip.formatStringInfo");
        formatStringField.setToolTipText(tooltipText);
        mainPanel.add(formatStringField, gbc);
        gbc.gridy = 1; gbc.gridx = 2; gbc.weightx = 0;
        JButton infoButton = new JButton(bundle.getString("button.formatHelp"));
        mainPanel.add(infoButton, gbc);
        infoButton.addActionListener(e -> {
            String infoMessage = bundle.getString("dialog.message.formatStringInfo");
            
            JOptionPane.showMessageDialog(
                this,
                infoMessage,
                bundle.getString("dialog.title.formatStringInfo"),
                JOptionPane.INFORMATION_MESSAGE
            );
        });

        addStyleRow(mainPanel, 2, bundle.getString("label.bookName"), "bookName");
        addStyleRow(mainPanel, 3, bundle.getString("label.chapterNumber"), "chapter");
        addStyleRow(mainPanel, 4, bundle.getString("label.verseNumber"), "verse");
        addStyleRow(mainPanel, 5, bundle.getString("label.moduleName"), "moduleName");
        addStyleRow(mainPanel, 6, bundle.getString("label.defaultText"), "defaultText");
        addStyleRow(mainPanel, 7, bundle.getString("label.verseText"), "verseText");
        addStyleRow(mainPanel, 8, bundle.getString("label.wordsOfJesus"), "wordsOfJesus");
        addStyleRow(mainPanel, 9, bundle.getString("label.alternativeVerseText"), "alternativeVerseText");
        addStyleRow(mainPanel, 10, bundle.getString("label.strongsNumbers"), "strongsNumber");
        addStyleRow(mainPanel, 11, bundle.getString("label.morphologyInfo"), "morphologyInfo");
        addStyleRow(mainPanel, 12, bundle.getString("label.infoText"), "infoText");

        gbc.gridy = 13; gbc.gridx = 0; gbc.gridwidth = 1; gbc.weightx = 0;
        mainPanel.add(new JLabel(bundle.getString("label.backgroundColor")), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        gbc.ipady = 20;
        colorPreviewPanel = new JPanel();
        colorPreviewPanel.setOpaque(true);
        colorPreviewPanel.setBorder(BorderFactory.createLineBorder(Color.gray));
        colorPreviewPanel.setBackground(chosenBackgroundColor != null ? chosenBackgroundColor : UIManager.getColor("Panel.background"));
        mainPanel.add(colorPreviewPanel, gbc);
        gbc.ipady = 0;
        gbc.gridx = 2; gbc.gridwidth = 0;
        colorButton = new JButton(bundle.getString("button.choose"));
        mainPanel.add(colorButton, gbc);
        colorButton.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, bundle.getString("dialog.title.chooseColor"), chosenBackgroundColor);
            if (newColor != null) {
                chosenBackgroundColor = newColor;
                colorPreviewPanel.setBackground(chosenBackgroundColor);
            }
        });

        gbc.gridy = 14; gbc.gridx = 0; gbc.gridwidth = 1;
        mainPanel.add(new JLabel(bundle.getString("label.lookAndFeel")), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        lafComboBox = new JComboBox<>(THEMES.toArray(new ThemeInfo[0]));
        THEMES.stream().filter(t -> t.className.equals(config.lookAndFeelClassName)).findFirst().ifPresent(lafComboBox::setSelectedItem);
        mainPanel.add(lafComboBox, gbc);
        lafComboBox.addActionListener(e -> {
            ThemeInfo selectedTheme = (ThemeInfo) lafComboBox.getSelectedItem();
            if (selectedTheme != null) {
                SwingUtilities.invokeLater(() -> previewLookAndFeel(selectedTheme.className));
            }
        });
        
        return mainPanel;
    }

    @Override
    public void dispose() {
        if (!saved) {
            restoreOriginalLaf();
        }
        super.dispose();
    }

    private void initEscapeToClose() {
        JRootPane rootPane = this.getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "CLOSE");
        actionMap.put("CLOSE", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }

    private void ensureAllStylesExist() {
        GuiConfig defaultConfig = new GuiConfig();
        for (String key : defaultConfig.styles.keySet()) {
            config.styles.putIfAbsent(key, defaultConfig.styles.get(key));
        }
    }
    
    private void addStyleRow(JPanel panel, int y, String labelText, String styleKey) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5); gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = y; gbc.gridx = 0; panel.add(new JLabel(labelText), gbc);

        TextStyle style = config.styles.get(styleKey);
        String styleDescription = (style != null) ? String.format("%s, %dpt", style.fontName, style.fontSize) : "Undefined";
        
        gbc.gridx = 1;
        JLabel styleDisplay = new JLabel(styleDescription);
        panel.add(styleDisplay, gbc);

        gbc.gridx = 2;
        JButton configureButton = new JButton(bundle.getString("button.configure"));
        configureButton.addActionListener(e -> {
            TextStyle currentStyle = config.styles.get(styleKey);
            if (currentStyle == null) {
                currentStyle = new GuiConfig().styles.get(styleKey);
            }
            Color previewBg = (chosenBackgroundColor != null) ? chosenBackgroundColor : UIManager.getColor("TextPane.background");
            StyleEditorDialog editor = new StyleEditorDialog(this, currentStyle, bundle, previewBg);
            editor.setVisible(true);

            if (editor.isConfirmed()) {
                config.styles.put(styleKey, editor.getEditedStyle());
                TextStyle newStyle = editor.getEditedStyle();
                styleDisplay.setText(String.format("%s, %dpt", newStyle.fontName, newStyle.fontSize));
            }
        });
        panel.add(configureButton, gbc);
    }

    private void applyLookAndFeel() {
        try {
            UIManager.setLookAndFeel(config.lookAndFeelClassName);
            SwingUtilities.updateComponentTreeUI(owner);
            
            JTextPane textPane = findTextPane(owner);
            if (textPane != null) {
                textPane.setBackground(config.textAreaBackground);
            }

            owner.validate();
            owner.repaint();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private JTextPane findTextPane(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JTextPane) {
                return (JTextPane) comp;
            }
            if (comp instanceof Container) {
                JTextPane found = findTextPane((Container) comp);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void restoreOriginalLaf() {
        try {
            if (originalLaf != null && !UIManager.getLookAndFeel().getName().equals(originalLaf.getName())) {
                UIManager.setLookAndFeel(originalLaf);
                SwingUtilities.updateComponentTreeUI(owner);
            }
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }
    }

    private void previewLookAndFeel(String lafClassName) {
        try {
            UIManager.setLookAndFeel(lafClassName);
            SwingUtilities.updateComponentTreeUI(this);
            pack();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void saveCurrentTheme() {
        String themeName = JOptionPane.showInputDialog(
            this,
            bundle.getString("dialog.prompt.enterThemeName"),
            bundle.getString("dialog.title.saveTheme"),
            JOptionPane.PLAIN_MESSAGE
        );

        if (themeName != null && !themeName.trim().isEmpty()) {
            try {
                GuiConfig currentConfig = new GuiConfig();
                currentConfig.formatString = formatStringField.getText();
                currentConfig.lookAndFeelClassName = ((ThemeInfo) lafComboBox.getSelectedItem()).className;
                currentConfig.textAreaBackground = chosenBackgroundColor;
                currentConfig.styles = this.config.styles;
                
                GuiThemeManager themeManager = new GuiThemeManager();
                themeManager.saveTheme(themeName, currentConfig);
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error saving theme: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadTheme() {
        GuiThemeManager themeManager = new GuiThemeManager();
        List<String> availableThemes = themeManager.getAvailableThemes();

        if (availableThemes.isEmpty()) {
            JOptionPane.showMessageDialog(this, bundle.getString("dialog.error.noThemesFound"), bundle.getString("dialog.title.error"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String selectedTheme = (String) JOptionPane.showInputDialog(
            this,
            bundle.getString("dialog.prompt.selectTheme"),
            bundle.getString("dialog.title.loadTheme"),
            JOptionPane.PLAIN_MESSAGE,
            null,
            availableThemes.toArray(),
            availableThemes.get(0)
        );

        if (selectedTheme != null) {
            try {
                GuiConfig loadedConfig = themeManager.loadTheme(selectedTheme);
                if (loadedConfig != null) {
                    this.config = loadedConfig;

                    applyLookAndFeel();
                    SwingUtilities.updateComponentTreeUI(this.getOwner());
                    SwingUtilities.updateComponentTreeUI(this);
                    updateUiFromConfig();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error loading theme: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
