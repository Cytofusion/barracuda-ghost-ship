package com.barracudaghost;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicCheckBoxUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PluginPanel extends net.runelite.client.ui.PluginPanel
{
    private static final String config = "barracudaghost";
    private static final int slots = 10;
    private static final int THUMBNAIL_SIZE = 24;
    private static final String PB_KEY_PREFIX = "pb_";
    private static final String CONFIRMATION_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CONFIRMATION_CODE_LENGTH = 8;

    private static final TrialData.TrialType[] PB_TRIAL_TYPES_IN_ORDER = {
            TrialData.TrialType.TEMPOR_TANTRUM,
            TrialData.TrialType.JUBBLY_JIVE,
            TrialData.TrialType.GWENITH_GLIDE
    };

    private final ConfigManager configManager;
    private final BarracudaGhostPlugin plugin;

    private final JTextField ghostPlaybackField = new JTextField();
    private final JLabel playbackLabel = new JLabel();
    private final JLabel playbackThumbnail = new JLabel();
    private final JCheckBox followModeCheckbox = new JCheckBox("Follow Mode");
    private final JButton playbackDeleteButton = Button("Delete");
    private final JButton pasteImageButton = Button("Paste Ghost");
    private final JComboBox<Integer> playbackSlotPicker = new JComboBox<>();
    private final JTextField outputField = new JTextField();
    private final JLabel lastRunThumbnail = new JLabel();
    private final JComboBox<Integer> slotPicker = new JComboBox<>();
    private final JLabel statusLabel = new JLabel(" ");

    private final JTextField[] slotFields = new JTextField[slots];
    private final JLabel[] slotLabels = new JLabel[slots];
    private final JLabel[] slotThumbnails = new JLabel[slots];

    private final List<TrialData.TrialType> pbTrialTypesOrdered = new ArrayList<>();
    private final List<TrialData.Rank> pbRanksOrdered = new ArrayList<>();
    private final JComboBox<String> pbPicker = new JComboBox<>();
    private final JTextField pbField = new JTextField();
    private final JLabel pbThumbnail = new JLabel();

    private boolean suppressFollowModeListener;
    private Timer statusTimer;
    private static ImageIcon emptyThumbnailIcon;

    public PluginPanel(ConfigManager configManager, BarracudaGhostPlugin plugin)
    {
        this.configManager = configManager;
        this.plugin = plugin;

        for (TrialData.TrialType trialType : PB_TRIAL_TYPES_IN_ORDER)
        {
            for (TrialData.Rank rank : TrialData.Rank.values())
            {
                pbTrialTypesOrdered.add(trialType);
                pbRanksOrdered.add(rank);
            }
        }

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        content.add(buildGhostPlaybackSection());
        content.add(Box.createVerticalStrut(10));
        content.add(buildOutputSection());
        content.add(Box.createVerticalStrut(8));
        content.add(buildStatusLabel());
        content.add(Box.createVerticalStrut(4));
        content.add(buildSeparatorLabel("Saved Runs"));
        content.add(Box.createVerticalStrut(4));

        for (int i = 0; i < slots; i++)
        {
            content.add(buildSlotRow(i + 1));
            content.add(Box.createVerticalStrut(6));
        }

        content.add(Box.createVerticalStrut(24));
        content.add(buildSeparatorLabel("Personal Bests"));
        content.add(Box.createVerticalStrut(4));
        content.add(buildPersonalBestsSection());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        add(scrollPane, BorderLayout.CENTER);

        refresh();
    }

    private JPanel buildGhostPlaybackSection()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = Label("Barracuda Ghost Ship");
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 18f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
        panel.add(label);
        panel.add(Box.createVerticalStrut(14));

        JPanel checkboxRow = new JPanel(new BorderLayout(4, 0));
        checkboxRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        checkboxRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        checkboxRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        followModeCheckbox.setUI(new BasicCheckBoxUI());
        followModeCheckbox.setOpaque(true);
        followModeCheckbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        followModeCheckbox.setForeground(Color.WHITE);
        followModeCheckbox.setFocusPainted(false);
        followModeCheckbox.addActionListener(e -> onFollowModeToggled());
        checkboxRow.add(followModeCheckbox, BorderLayout.WEST);

        panel.add(checkboxRow);
        panel.add(Box.createVerticalStrut(4));

        JPanel labelRow = new JPanel(new BorderLayout(4, 0));
        labelRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        labelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        labelRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        playbackLabel.setForeground(Color.WHITE);
        labelRow.add(playbackLabel, BorderLayout.WEST);

        pasteImageButton.addActionListener(e -> onPasteImageClicked());
        labelRow.add(pasteImageButton, BorderLayout.EAST);

        panel.add(labelRow);
        panel.add(Box.createVerticalStrut(3));

        JPanel playbackFieldRow = new JPanel(new BorderLayout(6, 0));
        playbackFieldRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        playbackFieldRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        playbackFieldRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        TextField(ghostPlaybackField, false);
        playbackFieldRow.add(ghostPlaybackField, BorderLayout.CENTER);

        playbackThumbnail.setVerticalAlignment(JLabel.CENTER);
        playbackThumbnail.setHorizontalAlignment(JLabel.CENTER);
        playbackFieldRow.add(playbackThumbnail, BorderLayout.EAST);

        panel.add(playbackFieldRow);
        panel.add(Box.createVerticalStrut(4));

        JPanel saveRow = new JPanel(new BorderLayout(4, 0));
        saveRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        saveRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        saveRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JPanel slotSubPanel = new JPanel(new BorderLayout(2, 0));
        slotSubPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        slotSubPanel.add(Label("Slot:"), BorderLayout.WEST);

        for (int i = 1; i <= slots; i++)
        {
            playbackSlotPicker.addItem(i);
        }
        slotSubPanel.add(playbackSlotPicker, BorderLayout.CENTER);

        JButton savePlaybackButton = Button("Save to slot");
        savePlaybackButton.addActionListener(e -> onSavePlaybackClicked());

        saveRow.add(slotSubPanel, BorderLayout.WEST);
        saveRow.add(savePlaybackButton, BorderLayout.CENTER);
        panel.add(saveRow);
        panel.add(Box.createVerticalStrut(4));

        playbackDeleteButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        playbackDeleteButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        playbackDeleteButton.addActionListener(e -> onGhostPlaybackDeleteClicked());
        panel.add(playbackDeleteButton);

        return panel;
    }

    private JPanel buildPersonalBestsSection()
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
        row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JPanel pickerRow = new JPanel(new BorderLayout(4, 0));
        pickerRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        pickerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pickerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        for (int i = 0; i < pbTrialTypesOrdered.size(); i++)
        {
            TrialData.TrialType trialType = pbTrialTypesOrdered.get(i);
            TrialData.Rank rank = pbRanksOrdered.get(i);
            pbPicker.addItem(formatTrialTypeName(trialType) + " " + titleCase(rank.name()));
        }
        pbPicker.addActionListener(e -> updatePersonalBestDisplay());
        pickerRow.add(pbPicker, BorderLayout.CENTER);

        row.add(pickerRow);
        row.add(Box.createVerticalStrut(3));

        JPanel fieldRow = new JPanel(new BorderLayout(6, 0));
        fieldRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        fieldRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        fieldRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        TextField(pbField, false);
        fieldRow.add(pbField, BorderLayout.CENTER);

        pbThumbnail.setVerticalAlignment(JLabel.CENTER);
        pbThumbnail.setHorizontalAlignment(JLabel.CENTER);
        fieldRow.add(pbThumbnail, BorderLayout.EAST);

        row.add(fieldRow);
        row.add(Box.createVerticalStrut(3));

        JPanel buttonRow = new JPanel(new GridLayout(1, 2, 4, 0));
        buttonRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JButton loadButton = Button("Load");
        loadButton.addActionListener(e -> onLoadPersonalBestClicked());

        JButton copyButton = Button("Copy");
        copyButton.addActionListener(e -> onCopyPersonalBestClicked());

        buttonRow.add(loadButton);
        buttonRow.add(copyButton);
        row.add(buttonRow);

        return row;
    }

    private String pbKeyForSelected()
    {
        int index = pbPicker.getSelectedIndex();
        TrialData.TrialType trialType = pbTrialTypesOrdered.get(index);
        TrialData.Rank rank = pbRanksOrdered.get(index);
        return PB_KEY_PREFIX + trialType.name() + "_" + rank.name();
    }

    private void updatePersonalBestDisplay()
    {
        if (pbPicker.getSelectedIndex() < 0)
        {
            return;
        }

        String pbData = getConfigValue(pbKeyForSelected());
        String[] details = decodeGhostDetails(pbData, " No Personal Best Yet.");
        FieldText(pbField, details[0]);
        updateThumbnail(pbThumbnail, pbData);
    }

    private void onLoadPersonalBestClicked()
    {
        if (plugin != null && plugin.isInTrial())
        {
            Status("Can't load a saved run while a trial is active.");
            return;
        }

        String pbData = getConfigValue(pbKeyForSelected());
        if (pbData.isEmpty())
        {
            Status("No personal best recorded yet.");
            return;
        }

        ConfigValue("ghostData", pbData);
        PlaybackText(pbData);
        Status("Loaded personal best as the active ghost.");
    }

    private void onCopyPersonalBestClicked()
    {
        String pbData = getConfigValue(pbKeyForSelected());
        if (pbData.isEmpty())
        {
            Status("No personal best recorded yet.");
            return;
        }

        copyGhostAsImage(pbData, "Copied personal best.", "Failed to encode personal best.");
    }

    public void startErasePbConfirmationFlow()
    {
        SwingUtilities.invokeLater(() -> {
            int step1 = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to erase your Personal Best data?",
                    "Confirm Erase",
                    JOptionPane.YES_NO_OPTION
            );
            if (step1 != JOptionPane.YES_OPTION)
            {
                resetErasePbCheckbox();
                return;
            }

            int step2 = JOptionPane.showConfirmDialog(
                    this,
                    "You cannot get it back once it's gone, are you 100% sure?",
                    "Confirm Erase",
                    JOptionPane.YES_NO_OPTION
            );
            if (step2 != JOptionPane.YES_OPTION)
            {
                resetErasePbCheckbox();
                return;
            }

            String confirmationCode = generateConfirmationCode();
            boolean typedCorrectly = showTypedConfirmationDialog(confirmationCode);
            if (!typedCorrectly)
            {
                resetErasePbCheckbox();
                return;
            }

            erasePersonalBestData();
            resetErasePbCheckbox();
            Status("Personal best data erased.");
        });
    }

    private void resetErasePbCheckbox()
    {
        configManager.setConfiguration(config, "erasePbData", "false");
    }

    private static String generateConfirmationCode()
    {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CONFIRMATION_CODE_LENGTH; i++)
        {
            sb.append(CONFIRMATION_CODE_CHARS.charAt(random.nextInt(CONFIRMATION_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private boolean showTypedConfirmationDialog(String confirmationCode)
    {
        JDialog dialog = new JDialog((Frame) null, "Confirm Erase", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel instructionLabel = new JLabel("Please enter the following to erase data:");
        instructionLabel.setForeground(Color.WHITE);
        instructionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(instructionLabel);
        content.add(Box.createVerticalStrut(8));

        JLabel codeLabel = new JLabel(confirmationCode);
        codeLabel.setForeground(Color.WHITE);
        codeLabel.setFont(codeLabel.getFont().deriveFont(Font.BOLD, 16f));
        codeLabel.setHorizontalAlignment(JLabel.CENTER);
        codeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(codeLabel);
        content.add(Box.createVerticalStrut(10));

        JTextField input = new JTextField();
        input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        content.add(input);
        content.add(Box.createVerticalStrut(10));

        JPanel buttonRow = new JPanel(new GridLayout(1, 2, 8, 0));
        buttonRow.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton confirmButton = Button("Confirm");
        confirmButton.setEnabled(false);
        JButton cancelButton = Button("Cancel");

        buttonRow.add(confirmButton);
        buttonRow.add(cancelButton);
        content.add(buttonRow);

        dialog.setContentPane(content);

        boolean[] result = { false };

        input.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                check();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                check();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                check();
            }

            private void check()
            {
                confirmButton.setEnabled(input.getText().equals(confirmationCode));
            }
        });

        confirmButton.addActionListener(e -> {
            result[0] = true;
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> {
            result[0] = false;
            dialog.dispose();
        });

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        return result[0];
    }

    private void erasePersonalBestData()
    {
        for (int i = 0; i < pbTrialTypesOrdered.size(); i++)
        {
            TrialData.TrialType trialType = pbTrialTypesOrdered.get(i);
            TrialData.Rank rank = pbRanksOrdered.get(i);
            String pbKey = PB_KEY_PREFIX + trialType.name() + "_" + rank.name();
            configManager.unsetConfiguration(config, pbKey);
        }
        updatePersonalBestDisplay();
    }

    private void onFollowModeToggled()
    {
        if (suppressFollowModeListener)
        {
            return;
        }
        ConfigValue("followModeEnabled", String.valueOf(followModeCheckbox.isSelected()));
    }

    private void onPasteImageClicked()
    {
        if (plugin != null && plugin.isInTrial())
        {
            Status("Can't change the ghost recording while a trial is active.");
            return;
        }

        String encoded = decodeGhostFromClipboardImage();
        if (encoded == null)
        {
            return;
        }

        ConfigValue("ghostData", encoded);
        PlaybackText(encoded);
        Status("Loaded ghost.");
    }

    private void onPasteGhostToSlotClicked(int slot)
    {
        if (!confirmOverwriteIfNeeded(slot))
        {
            return;
        }

        String encoded = decodeGhostFromClipboardImage();
        if (encoded == null)
        {
            return;
        }

        ConfigValue("slot" + slot, encoded);
        updateSlotDisplay(slot, encoded);
        Status("Pasted ghost into slot " + slot + ".");
    }

    private String decodeGhostFromClipboardImage()
    {
        Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
        if (contents == null || !contents.isDataFlavorSupported(DataFlavor.imageFlavor))
        {
            Status("No image found on the clipboard.");
            return null;
        }

        try
        {
            Image image = (Image) contents.getTransferData(DataFlavor.imageFlavor);
            BufferedImage buffered = toBufferedImage(image);

            RecordingEncoder.DecodedGhost decoded = RecordingEncoder.decodeFromImage(buffered);
            return RecordingEncoder.encode(decoded.frames, decoded.events, decoded.trialdata);
        }
        catch (UnsupportedFlavorException | IOException e)
        {
            Status("Failed to read the clipboard image.");
            return null;
        }
        catch (Exception e)
        {
            Status("Pasted image doesn't contain valid ghost data.");
            return null;
        }
    }

    private static BufferedImage toBufferedImage(Image image)
    {
        if (image instanceof BufferedImage)
        {
            return (BufferedImage) image;
        }

        BufferedImage buffered = new BufferedImage(
                image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = buffered.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return buffered;
    }

    private void onSavePlaybackClicked()
    {
        String current = getConfigValue("ghostData");
        if (current.isEmpty())
        {
            Status("No recording in the playback field to save.");
            return;
        }

        int slot = (Integer) playbackSlotPicker.getSelectedItem();
        if (!confirmOverwriteIfNeeded(slot))
        {
            return;
        }

        ConfigValue("slot" + slot, current);
        updateSlotDisplay(slot, current);
        Status("Saved playback recording to slot " + slot + ".");
    }

    private void onGhostPlaybackDeleteClicked()
    {
        ConfigValue("ghostData", "");
        PlaybackText("");
        Status("Cleared active ghost recording.");
    }

    private JPanel buildOutputSection()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = Label("Last Recorded Run:");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(4));

        JPanel outputFieldRow = new JPanel(new BorderLayout(6, 0));
        outputFieldRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        outputFieldRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        outputFieldRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        TextField(outputField, false);
        outputFieldRow.add(outputField, BorderLayout.CENTER);

        lastRunThumbnail.setVerticalAlignment(JLabel.CENTER);
        lastRunThumbnail.setHorizontalAlignment(JLabel.CENTER);
        outputFieldRow.add(lastRunThumbnail, BorderLayout.EAST);

        panel.add(outputFieldRow);
        panel.add(Box.createVerticalStrut(6));

        JPanel saveRow = new JPanel(new BorderLayout(4, 0));
        saveRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        saveRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        saveRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JPanel slotSubPanel = new JPanel(new BorderLayout(2, 0));
        slotSubPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        slotSubPanel.add(Label("Slot:"), BorderLayout.WEST);

        for (int i = 1; i <= slots; i++)
        {
            slotPicker.addItem(i);
        }
        slotSubPanel.add(slotPicker, BorderLayout.CENTER);

        JButton saveButton = Button("Save to slot");
        saveButton.addActionListener(e -> Save());

        saveRow.add(slotSubPanel, BorderLayout.WEST);
        saveRow.add(saveButton, BorderLayout.CENTER);
        panel.add(saveRow);
        panel.add(Box.createVerticalStrut(4));

        JButton copyLastRunAsImageButton = Button("Copy");
        copyLastRunAsImageButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        copyLastRunAsImageButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        copyLastRunAsImageButton.addActionListener(e -> onCopyLastRunAsImageClicked());
        panel.add(copyLastRunAsImageButton);

        return panel;
    }

    private void onCopyLastRunAsImageClicked()
    {
        String current = getConfigValue("lastRecordedGhost");
        if (current.isEmpty())
        {
            Status("No recorded run to copy yet.");
            return;
        }

        copyGhostAsImage(current, "Copied last recorded run.", "Failed to encode the run.");
    }

    private void copyGhostAsImage(String ghostData, String successMessage, String failureMessage)
    {
        try
        {
            RecordingEncoder.DecodedGhost decoded = RecordingEncoder.decode(ghostData);
            BufferedImage image = RecordingEncoder.encodeToImage(decoded.frames, decoded.events, decoded.trialdata);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(image), null);
            Status(successMessage);
        }
        catch (Exception e)
        {
            Status(failureMessage);
        }
    }

    private static class ImageTransferable implements Transferable
    {
        private final Image image;

        ImageTransferable(Image image)
        {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors()
        {
            return new DataFlavor[] { DataFlavor.imageFlavor };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor)
        {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
        {
            if (!isDataFlavorSupported(flavor))
            {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }

    private JLabel buildStatusLabel()
    {
        statusLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setVerticalAlignment(JLabel.TOP);

        Dimension statusSize = new Dimension(0, 34);
        statusLabel.setPreferredSize(statusSize);
        statusLabel.setMinimumSize(statusSize);
        statusLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        return statusLabel;
    }

    private JLabel buildSeparatorLabel(String text)
    {
        JLabel label = Label(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private JPanel buildSlotRow(int slotNumber)
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
        row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JPanel labelRow = new JPanel(new BorderLayout(4, 0));
        labelRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        labelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        labelRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel label = Label("Slot " + slotNumber + ":");
        slotLabels[slotNumber - 1] = label;
        labelRow.add(label, BorderLayout.WEST);

        JButton pasteGhostButton = Button("Paste Ghost");
        pasteGhostButton.addActionListener(e -> onPasteGhostToSlotClicked(slotNumber));
        labelRow.add(pasteGhostButton, BorderLayout.EAST);

        row.add(labelRow);
        row.add(Box.createVerticalStrut(3));

        JPanel fieldRow = new JPanel(new BorderLayout(6, 0));
        fieldRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        fieldRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        fieldRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JTextField field = new JTextField();
        TextField(field, false);
        slotFields[slotNumber - 1] = field;
        fieldRow.add(field, BorderLayout.CENTER);

        JLabel thumbnail = new JLabel();
        thumbnail.setVerticalAlignment(JLabel.CENTER);
        thumbnail.setHorizontalAlignment(JLabel.CENTER);
        slotThumbnails[slotNumber - 1] = thumbnail;
        fieldRow.add(thumbnail, BorderLayout.EAST);

        row.add(fieldRow);
        row.add(Box.createVerticalStrut(3));

        JPanel buttonRow = new JPanel(new GridLayout(1, 3, 4, 0));
        buttonRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JButton loadButton = Button("Load");
        loadButton.addActionListener(e -> LoadSave(slotNumber));

        JButton copyButton = Button("Copy");
        copyButton.addActionListener(e -> CopySave(slotNumber));

        JButton deleteButton = Button("Delete");
        deleteButton.addActionListener(e -> DeleteSave(slotNumber));

        buttonRow.add(loadButton);
        buttonRow.add(copyButton);
        buttonRow.add(deleteButton);
        row.add(buttonRow);

        return row;
    }

    private String[] decodeGhostDetails(String ghostData)
    {
        return decodeGhostDetails(ghostData, " No Ghost Saved.");
    }

    private String[] decodeGhostDetails(String ghostData, String emptyMessage)
    {
        if (ghostData == null || ghostData.isEmpty())
        {
            return new String[] { emptyMessage, "" };
        }

        try
        {
            RecordingEncoder.DecodedGhost decoded = RecordingEncoder.decode(ghostData);
            TrialData metadata = decoded.trialdata;
            double preciseSeconds = (decoded.frames.size() - 1) * 0.6;
            String timeText = formatTime(preciseSeconds);
            String rankText = titleCase(metadata.rank.name());
            String fieldText = " " + metadata.trialType.abbreviation + " " + rankText + " " + timeText
                    + missingEventsTag(decoded.events);
            return new String[] { fieldText, metadata.username };
        }
        catch (Exception e)
        {
            return new String[] { " (invalid data)", "" };
        }
    }

    private void updateSlotDisplay(int slotNumber, String ghostData)
    {
        String[] details = decodeGhostDetails(ghostData);
        FieldText(slotFields[slotNumber - 1], details[0]);

        JLabel label = slotLabels[slotNumber - 1];
        String username = details[1];
        label.setText(username.isEmpty() ? "Slot " + slotNumber + ":" : "Slot " + slotNumber + ": " + username);

        updateSlotThumbnail(slotNumber, ghostData);
    }

    private void updatePlaybackDisplay(String ghostData)
    {
        String[] details = decodeGhostDetails(ghostData);
        FieldText(ghostPlaybackField, details[0]);

        String username = details[1];
        playbackLabel.setText(username.isEmpty() ? "Playback:" : "Playback: " + username);

        updateThumbnail(playbackThumbnail, ghostData);
    }

    private static String formatTrialTypeName(TrialData.TrialType trialType)
    {
        String[] words = trialType.name().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words)
        {
            if (word.isEmpty())
            {
                continue;
            }
            if (result.length() > 0)
            {
                result.append(" ");
            }
            result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase());
        }
        return result.toString();
    }

    private static String titleCase(String value)
    {
        if (value == null || value.isEmpty())
        {
            return value;
        }
        return value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();
    }

    private static String formatTime(double totalSeconds)
    {
        int minutes = (int) (totalSeconds / 60);
        double remainderSeconds = totalSeconds - (minutes * 60);
        return minutes + ":" + String.format("%04.1f", remainderSeconds);
    }

    private void TextField(JTextField field, boolean editable)
    {
        field.setEditable(editable);
        field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        field.setPreferredSize(new Dimension(0, 26));
    }

    private JLabel Label(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        return label;
    }

    private static JButton Button(String text)
    {
        JButton button = new JButton(text);
        button.setUI(new BasicButtonUI());
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
        button.setMargin(new Insets(2, 2, 2, 2));
        return button;
    }

    private void Save()
    {
        String current = getConfigValue("lastRecordedGhost");
        if (current.isEmpty())
        {
            Status("No recorded run to save yet.");
            return;
        }

        int slot = (Integer) slotPicker.getSelectedItem();
        if (!confirmOverwriteIfNeeded(slot))
        {
            return;
        }

        ConfigValue("slot" + slot, current);
        updateSlotDisplay(slot, current);
        Status("Saved to slot " + slot + ".");
    }

    private void LoadSave(int slot)
    {
        if (plugin != null && plugin.isInTrial())
        {
            Status("Can't load a saved run while a trial is active.");
            return;
        }

        String value = getConfigValue("slot" + slot);
        if (value.isEmpty())
        {
            Status("Slot " + slot + " is empty.");
            return;
        }

        ConfigValue("ghostData", value);
        PlaybackText(value);
        Status("Loaded slot " + slot + " as the active ghost.");
    }

    private void CopySave(int slot)
    {
        String value = getConfigValue("slot" + slot);
        if (value.isEmpty())
        {
            Status("Slot " + slot + " is empty.");
            return;
        }

        copyGhostAsImage(value, "Copied slot " + slot + ".", "Failed to encode slot " + slot + ".");
    }

    private void DeleteSave(int slot)
    {
        if (!confirmDeleteIfNeeded(slot))
        {
            return;
        }

        configManager.unsetConfiguration(config, "slot" + slot);
        updateSlotDisplay(slot, "");
        Status("Slot " + slot + " cleared.");
    }

    public void refresh()
    {
        SwingUtilities.invokeLater(() -> {
            PlaybackText(getConfigValue("ghostData"));

            suppressFollowModeListener = true;
            followModeCheckbox.setSelected(Boolean.parseBoolean(getConfigValue("followModeEnabled")));
            suppressFollowModeListener = false;

            syncTrialLockedState();

            String lastRun = getConfigValue("lastRecordedGhost");
            String[] lastRunDetails = decodeGhostDetails(lastRun);
            FieldText(outputField, lastRunDetails[0]);
            updateThumbnail(lastRunThumbnail, lastRun);

            for (int i = 1; i <= slots; i++)
            {
                String value = getConfigValue("slot" + i);
                updateSlotDisplay(i, value);
            }

            updatePersonalBestDisplay();
        });
    }

    public void updateTrialLockState()
    {
        SwingUtilities.invokeLater(this::syncTrialLockedState);
    }

    private void syncTrialLockedState()
    {
        boolean inTrial = plugin != null && plugin.isInTrial();
        playbackDeleteButton.setEnabled(!inTrial);
        pasteImageButton.setEnabled(!inTrial);
    }

    public void ExternalStatus(String message)
    {
        Status(message);
    }

    private void PlaybackText(String text)
    {
        updatePlaybackDisplay(text);
    }

    private void FieldText(JTextField field, String text)
    {
        field.setText(text);
        field.revalidate();
        field.repaint();
        revalidate();
        repaint();
    }

    private void updateThumbnail(JLabel thumbnail, String ghostData)
    {
        if (ghostData == null || ghostData.isEmpty())
        {
            thumbnail.setIcon(getEmptyThumbnailIcon());
            return;
        }

        try
        {
            RecordingEncoder.DecodedGhost decoded = RecordingEncoder.decode(ghostData);
            BufferedImage source = RecordingEncoder.encodeToImage(decoded.frames, decoded.events, decoded.trialdata);
            thumbnail.setIcon(new ImageIcon(scaleToFixedSize(source, THUMBNAIL_SIZE)));
        }
        catch (Exception e)
        {
            thumbnail.setIcon(getEmptyThumbnailIcon());
        }
    }

    private void updateSlotThumbnail(int slotNumber, String ghostData)
    {
        updateThumbnail(slotThumbnails[slotNumber - 1], ghostData);
    }

    private static BufferedImage scaleToFixedSize(BufferedImage source, int size)
    {
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        return scaled;
    }

    private static ImageIcon getEmptyThumbnailIcon()
    {
        if (emptyThumbnailIcon == null)
        {
            BufferedImage placeholder = new BufferedImage(THUMBNAIL_SIZE, THUMBNAIL_SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = placeholder.createGraphics();
            g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
            g.fillRect(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
            g.dispose();
            emptyThumbnailIcon = new ImageIcon(placeholder);
        }
        return emptyThumbnailIcon;
    }

    private boolean confirmOverwriteIfNeeded(int slot)
    {
        String existing = getConfigValue("slot" + slot);
        if (existing.isEmpty())
        {
            return true;
        }

        int result = JOptionPane.showConfirmDialog(
                this,
                "Overwrite Slot " + slot + "?",
                "Confirm Overwrite",
                JOptionPane.YES_NO_OPTION
        );
        return result == JOptionPane.YES_OPTION;
    }

    private boolean confirmDeleteIfNeeded(int slot)
    {
        String existing = getConfigValue("slot" + slot);
        if (existing.isEmpty())
        {
            return true;
        }

        int result = JOptionPane.showConfirmDialog(
                this,
                "Delete saved ghost in Slot " + slot + "?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION
        );
        return result == JOptionPane.YES_OPTION;
    }

    private String getConfigValue(String key)
    {
        String value = configManager.getConfiguration(config, key);
        return value == null ? "" : value;
    }

    private void ConfigValue(String key, String value)
    {
        configManager.setConfiguration(config, key, value);
    }

    private void Status(String message)
    {
        statusLabel.setText("<html><body style='width: 165px'>" + message + "</body></html>");
        statusLabel.revalidate();
        statusLabel.repaint();

        if (statusTimer != null)
        {
            statusTimer.stop();
        }

        statusTimer = new Timer(4000, e -> {
            statusLabel.setText(" ");
            statusLabel.revalidate();
            statusLabel.repaint();
        });
        statusTimer.setRepeats(false);
        statusTimer.start();
    }

    private static String missingEventsTag(List<Events> events)
    {
        boolean hasTrim = false;
        boolean hasMote = false;
        boolean hasHarvest = false;

        for (Events event : events)
        {
            switch (event.type)
            {
                case SailTrim:
                    hasTrim = true;
                    break;
                case MoteUsed:
                    hasMote = true;
                    break;
                case ExtractorHarvested:
                    hasHarvest = true;
                    break;
            }
        }

        StringBuilder missing = new StringBuilder();
        if (!hasTrim)
        {
            missing.append("T");
        }
        if (!hasMote)
        {
            if (missing.length() > 0)
            {
                missing.append("/");
            }
            missing.append("M");
        }
        if (!hasHarvest)
        {
            if (missing.length() > 0)
            {
                missing.append("/");
            }
            missing.append("E");
        }

        return missing.length() > 0 ? " (" + missing + ")" : "";
    }
}