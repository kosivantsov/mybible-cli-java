package org.truetranslation.mybible.gui;

import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.util.ResourceBundle;

public class StyleEditorDialog extends JDialog {

    private final TextStyle editedStyle;
    private boolean confirmed = false;

    private JComboBox<String> fontFamilyComboBox;
    private JSpinner fontSizeSpinner;
    private JCheckBox boldCheckBox;
    private JCheckBox italicCheckBox;
    private JRadioButton normalScriptRadio, superscriptRadio, subscriptRadio;
    private JTextPane previewPane;

    public StyleEditorDialog(Dialog owner, TextStyle originalStyle, ResourceBundle bundle, Color previewBackground) {
        super(owner, bundle.getString("dialog.title.styleEditor"), true);
        this.editedStyle = new TextStyle(originalStyle);

        initComponents(bundle, previewBackground);
        updatePreview();
        
        pack();
        setLocationRelativeTo(owner);
    }

    private void initComponents(ResourceBundle bundle, Color previewBackground) {
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel controlsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Font Family
        gbc.gridy = 0; gbc.gridx = 0;
        controlsPanel.add(new JLabel(bundle.getString("label.fontFamily")), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        fontFamilyComboBox = new JComboBox<>(fontNames);
        fontFamilyComboBox.setSelectedItem(editedStyle.fontName);
        fontFamilyComboBox.addActionListener(e -> updateFromControls());
        controlsPanel.add(fontFamilyComboBox, gbc);

        // Font Size
        gbc.gridy = 1; gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        controlsPanel.add(new JLabel(bundle.getString("label.fontSize")), gbc);
        gbc.gridx = 1;
        fontSizeSpinner = new JSpinner(new SpinnerNumberModel(editedStyle.fontSize, 6, 72, 1));
        fontSizeSpinner.addChangeListener(e -> updateFromControls());
        controlsPanel.add(fontSizeSpinner, gbc);

        // Font Style
        gbc.gridy = 2; gbc.gridx = 0;
        controlsPanel.add(new JLabel(bundle.getString("label.fontStyle")), gbc);
        gbc.gridx = 1;
        JPanel stylePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        boldCheckBox = new JCheckBox(bundle.getString("checkbox.bold"), (editedStyle.fontStyle & Font.BOLD) != 0);
        italicCheckBox = new JCheckBox(bundle.getString("checkbox.italic"), (editedStyle.fontStyle & Font.ITALIC) != 0);
        boldCheckBox.addActionListener(e -> updateFromControls());
        italicCheckBox.addActionListener(e -> updateFromControls());
        stylePanel.add(boldCheckBox);
        stylePanel.add(italicCheckBox);
        controlsPanel.add(stylePanel, gbc);

        // Script Style
        gbc.gridy = 3; gbc.gridx = 0;
        controlsPanel.add(new JLabel(bundle.getString("label.scriptStyle")), gbc);
        gbc.gridx = 1;
        normalScriptRadio = new JRadioButton(bundle.getString("radio.normal"), !editedStyle.superscript && !editedStyle.subscript);
        superscriptRadio = new JRadioButton(bundle.getString("radio.superscript"), editedStyle.superscript);
        subscriptRadio = new JRadioButton(bundle.getString("radio.subscript"), editedStyle.subscript);
        ButtonGroup scriptGroup = new ButtonGroup();
        scriptGroup.add(normalScriptRadio);
        scriptGroup.add(superscriptRadio);
        scriptGroup.add(subscriptRadio);
        JPanel scriptPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        scriptPanel.add(normalScriptRadio);
        scriptPanel.add(superscriptRadio);
        scriptPanel.add(subscriptRadio);
        controlsPanel.add(scriptPanel, gbc);
        normalScriptRadio.addActionListener(e -> updateFromControls());
        superscriptRadio.addActionListener(e -> updateFromControls());
        subscriptRadio.addActionListener(e -> updateFromControls());

        // Color
        gbc.gridy = 4; gbc.gridx = 0;
        controlsPanel.add(new JLabel(bundle.getString("label.color")), gbc);
        gbc.gridx = 1;
        JButton colorButton = new JButton(bundle.getString("button.chooseColor"));
        colorButton.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, bundle.getString("button.chooseColor"), editedStyle.color);
            if (newColor != null) {
                editedStyle.color = newColor;
                updatePreview();
            }
        });
        controlsPanel.add(colorButton, gbc);

        // Preview Panel
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(BorderFactory.createTitledBorder(bundle.getString("label.preview")));
        previewPane = new JTextPane();
        previewPane.setEditable(false);
        previewPane.setText(bundle.getString("text.preview"));
        previewPane.setPreferredSize(new Dimension(300, 100));
        previewPane.setMargin(new Insets(10, 10, 10, 10));
        previewPane.setBackground(previewBackground);
        previewPanel.add(previewPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton(bundle.getString("button.ok"));
        JButton cancelButton = new JButton(bundle.getString("button.cancel"));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        okButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        cancelButton.addActionListener(e -> dispose());

        add(controlsPanel, BorderLayout.NORTH);
        add(previewPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void updateFromControls() {
        editedStyle.fontName = (String) fontFamilyComboBox.getSelectedItem();
        editedStyle.fontSize = (Integer) fontSizeSpinner.getValue();
        int style = Font.PLAIN;
        if (boldCheckBox.isSelected()) style |= Font.BOLD;
        if (italicCheckBox.isSelected()) style |= Font.ITALIC;
        editedStyle.fontStyle = style;
        editedStyle.superscript = superscriptRadio.isSelected();
        editedStyle.subscript = subscriptRadio.isSelected();
        updatePreview();
    }

    private void updatePreview() {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attrs, editedStyle.fontName);
        StyleConstants.setFontSize(attrs, editedStyle.fontSize);
        StyleConstants.setBold(attrs, (editedStyle.fontStyle & Font.BOLD) != 0);
        StyleConstants.setItalic(attrs, (editedStyle.fontStyle & Font.ITALIC) != 0);
        
        // Use the pane's foreground color as a fallback if the style's color is null.
        Color fg = (editedStyle.color != null) ? editedStyle.color : previewPane.getForeground();
        StyleConstants.setForeground(attrs, fg);
        
        StyleConstants.setSuperscript(attrs, editedStyle.superscript);
        StyleConstants.setSubscript(attrs, editedStyle.subscript);

        StyledDocument doc = previewPane.getStyledDocument();
        doc.setCharacterAttributes(0, doc.getLength(), attrs, true);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public TextStyle getEditedStyle() {
        return editedStyle;
    }
}
