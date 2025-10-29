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

    private final GuiConfig config;
    private final GuiConfigManager configManager;
    private final JFrame owner;
    private final ResourceBundle bundle;
    private Color chosenBackgroundColor;

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

        setTitle(bundle.getString("dialog.title.configure"));
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridy = 0; gbc.gridx = 0; mainPanel.add(new JLabel(bundle.getString("label.formatString")), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        JTextField formatStringField = new JTextField(config.formatString, 30);
        mainPanel.add(formatStringField, gbc);

        addStyleRow(mainPanel, 1, bundle.getString("label.bookName"), "bookName");
        addStyleRow(mainPanel, 2, bundle.getString("label.chapterNumber"), "chapter");
        addStyleRow(mainPanel, 3, bundle.getString("label.verseNumber"), "verse");
        addStyleRow(mainPanel, 4, bundle.getString("label.verseText"), "verseText");
        addStyleRow(mainPanel, 5, bundle.getString("label.moduleName"), "moduleName");
        addStyleRow(mainPanel, 6, bundle.getString("label.strongsNumbers"), "strongsNumber");
        addStyleRow(mainPanel, 7, bundle.getString("label.wordsOfJesus"), "wordsOfJesus");
        addStyleRow(mainPanel, 8, bundle.getString("label.defaultText"), "defaultText");
        addStyleRow(mainPanel, 9, bundle.getString("label.infoText"), "infoText");

        gbc.gridy = 10; gbc.gridx = 0; gbc.gridwidth = 1; gbc.weightx = 0;
        mainPanel.add(new JLabel(bundle.getString("label.backgroundColor")), gbc);
        gbc.gridx = 1; gbc.gridwidth = 1;
        JButton colorButton = new JButton(bundle.getString("button.choose"));
        mainPanel.add(colorButton, gbc);

        gbc.gridy = 11; gbc.gridx = 0; gbc.gridwidth = 1;
        mainPanel.add(new JLabel(bundle.getString("label.lookAndFeel")), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        JComboBox<ThemeInfo> lafComboBox = new JComboBox<>(THEMES.toArray(new ThemeInfo[0]));
        THEMES.stream().filter(t -> t.className.equals(config.lookAndFeelClassName)).findFirst().ifPresent(lafComboBox::setSelectedItem);
        mainPanel.add(lafComboBox, gbc);
        
        lafComboBox.addActionListener(e -> {
            ThemeInfo selectedTheme = (ThemeInfo) lafComboBox.getSelectedItem();
            if (selectedTheme != null) {
                previewLookAndFeel(selectedTheme.className);
            }
        });

        add(mainPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton(bundle.getString("button.save"));
        JButton cancelButton = new JButton(bundle.getString("button.cancel"));
        buttonPanel.add(saveButton); buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        colorButton.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, bundle.getString("dialog.title.chooseColor"), chosenBackgroundColor);
            if (newColor != null) chosenBackgroundColor = newColor;
        });

        cancelButton.addActionListener(e -> dispose());
        saveButton.addActionListener(e -> {
            config.formatString = formatStringField.getText();
            config.lookAndFeelClassName = ((ThemeInfo) lafComboBox.getSelectedItem()).className;
            config.textAreaBackground = chosenBackgroundColor;
            ensureAllStylesExist();
            configManager.saveConfig();
            applyLookAndFeel();
            dispose();
        });

        initEscapeToClose();

        pack();
        setLocationRelativeTo(owner);
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
            Color previewBg = (config.textAreaBackground != null) ? config.textAreaBackground : UIManager.getColor("TextPane.background");
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
    
    /**
     * Applies the chosen Look and Feel to the owner frame permanently.
     */
    private void applyLookAndFeel() {
        try {
            UIManager.setLookAndFeel(config.lookAndFeelClassName);
            SwingUtilities.updateComponentTreeUI(owner);
            
            // Use validate() and repaint() instead of pack() to prevent resizing.
            owner.validate();
            owner.repaint();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Applies a Look and Feel temporarily to this dialog for preview purposes,
     * without affecting the rest of the application.
     * @param lafClassName The class name of the Look and Feel to preview.
     */
    private void previewLookAndFeel(String lafClassName) {
        LookAndFeel originalLaf = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(lafClassName);
            SwingUtilities.updateComponentTreeUI(this);
            pack();

        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                UIManager.setLookAndFeel(originalLaf);
            } catch (UnsupportedLookAndFeelException e) {
                e.printStackTrace();
            }
        }
    }
}
