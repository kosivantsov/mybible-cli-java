package org.truetranslation.mybible.gui;

import com.formdev.flatlaf.intellijthemes.*;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

public class ConfigurationDialog extends JDialog {

    private GuiConfig config;
    private final GuiConfigManager configManager;
    private final JFrame owner;
    private final ResourceBundle bundle;
    private Color chosenBackgroundColor;

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

    public ConfigurationDialog(JFrame owner, GuiConfigManager configManager) {
        super(owner, "Configuration", true);
        this.owner = owner;
        this.configManager = configManager;
        this.config = configManager.getConfig();
        this.bundle = ResourceBundle.getBundle("i18n.gui");
        this.chosenBackgroundColor = config.textAreaBackground;

        this.originalLaf = UIManager.getLookAndFeel();

        setTitle(bundle.getString("dialog.title.configure"));
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = createMainPanel();
        add(mainPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton resetButton = new JButton(bundle.getString("button.resetDefaults"));
        JButton saveButton = new JButton(bundle.getString("button.save"));
        JButton cancelButton = new JButton(bundle.getString("button.cancel"));
        
        buttonPanel.add(resetButton);
        buttonPanel.add(saveButton); 
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

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

    private void resetToDefaults() {
        int response = JOptionPane.showConfirmDialog(
            this,
            bundle.getString("dialog.message.resetConfirm"),
            bundle.getString("dialog.title.resetConfirm"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (response == JOptionPane.YES_OPTION) {
            // Create a new default config object
            GuiConfig defaultConfig = new GuiConfig();
            
            // Set the default config in the manager and save it immediately
            configManager.setConfig(defaultConfig);
            configManager.saveConfig();
            
            // Update the dialog's internal config to match the newly loaded one
            this.config = configManager.getConfig();
            
            // Update all UI elements from the new default config
            updateUiFromConfig();
            
            // Immediately apply the visual changes to the main application window
            applyLookAndFeel();
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
        getContentPane().remove(0);
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
        mainPanel.add(formatStringField, gbc);

        addStyleRow(mainPanel, 1, bundle.getString("label.bookName"), "bookName");
        addStyleRow(mainPanel, 2, bundle.getString("label.chapterNumber"), "chapter");
        addStyleRow(mainPanel, 3, bundle.getString("label.verseNumber"), "verse");
        addStyleRow(mainPanel, 4, bundle.getString("label.verseText"), "verseText");
        addStyleRow(mainPanel, 5, bundle.getString("label.moduleName"), "moduleName");
        addStyleRow(mainPanel, 6, bundle.getString("label.strongsNumbers"), "strongsNumber");
        addStyleRow(mainPanel, 7, bundle.getString("label.morphologyInfo"), "morphologyInfo");
        addStyleRow(mainPanel, 8, bundle.getString("label.alternativeVerseText"), "alternativeVerseText");
        addStyleRow(mainPanel, 9, bundle.getString("label.wordsOfJesus"), "wordsOfJesus");
        addStyleRow(mainPanel, 10, bundle.getString("label.defaultText"), "defaultText");
        addStyleRow(mainPanel, 11, bundle.getString("label.infoText"), "infoText");
        
        gbc.gridy = 12; gbc.gridx = 0; gbc.gridwidth = 1; gbc.weightx = 0;
        mainPanel.add(new JLabel(bundle.getString("label.backgroundColor")), gbc);
        gbc.gridx = 1; gbc.gridwidth = 1;
        colorButton = new JButton(bundle.getString("button.choose"));
        colorButton.setBackground(chosenBackgroundColor != null ? chosenBackgroundColor : UIManager.getColor("Button.background"));
        mainPanel.add(colorButton, gbc);
        colorButton.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, bundle.getString("dialog.title.chooseColor"), chosenBackgroundColor);
            if (newColor != null) {
                 chosenBackgroundColor = newColor;
                 colorButton.setBackground(chosenBackgroundColor);
            }
        });

        gbc.gridy = 13; gbc.gridx = 0; gbc.gridwidth = 1;
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
}
