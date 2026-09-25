package tiptoieditor.ui;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import service.audio.AudioMergeService;

import java.io.File;
import java.io.InterruptedIOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Controls for combining source audio files without starting the album workflow. */
public class RowMergeAudios {

    private final Label selectedAudioFolderLabel;
    private final ComboBox<AudioMergeService.OutputFormat> outputFormatComboBox;
    private final TextField minimumLengthField;
    private final AudioMergeService audioMergeService;
    private final Consumer<String> logger;
    private final Consumer<String> statusUpdater;
    private final WorkflowTaskManager taskManager;
    private final Supplier<String> albumNameSupplier;
    private final Consumer<File> mainFolderSelectionConsumer;
    private final Consumer<String> mergeResultConsumer;
    private File selectedAudioFolder;

    public RowMergeAudios(Stage stage, Button selectAudioFolderButton, Label selectedAudioFolderLabel,
            ComboBox<AudioMergeService.OutputFormat> outputFormatComboBox, TextField minimumLengthField,
            Button mergeAudiosButton, AudioMergeService audioMergeService,
            Consumer<String> logger, Consumer<String> statusUpdater, WorkflowTaskManager taskManager,
            Supplier<String> albumNameSupplier, Consumer<File> mainFolderSelectionConsumer,
            Consumer<String> mergeResultConsumer) {
        this.selectedAudioFolderLabel = selectedAudioFolderLabel;
        this.outputFormatComboBox = outputFormatComboBox;
        this.minimumLengthField = minimumLengthField;
        this.audioMergeService = audioMergeService;
        this.logger = logger;
        this.statusUpdater = statusUpdater;
        this.taskManager = taskManager;
        this.albumNameSupplier = albumNameSupplier;
        this.mainFolderSelectionConsumer = mainFolderSelectionConsumer;
        this.mergeResultConsumer = mergeResultConsumer;
        selectAudioFolderButton.setOnAction(e -> selectAudioFolder(stage));
        mergeAudiosButton.setOnAction(e -> mergeAudios());
    }

    private void selectAudioFolder(Stage stage) {
        File folder = FolderSelectionDialog.chooseFolder(stage, selectedAudioFolder);
        if (folder != null) {
            selectedAudioFolder = folder;
            selectedAudioFolderLabel.setText(folder.getName());
            mainFolderSelectionConsumer.accept(folder);
            logger.accept("Selected audio folder for merging: " + folder.getAbsolutePath());
        }
    }

    private void mergeAudios() {
        if (selectedAudioFolder == null) {
            logAndSetStatus("Please select an audio folder first.");
            return;
        }

        double minimumLength;
        try {
            minimumLength = Double.parseDouble(minimumLengthField.getText().trim());
            if (!Double.isFinite(minimumLength) || minimumLength <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            logAndSetStatus("Minimum length must be a number greater than zero.");
            return;
        }

        File sourceFolder = selectedAudioFolder;
        String albumName = albumNameSupplier.get();
        AudioMergeService.OutputFormat outputFormat = outputFormatComboBox.getValue();
        if (outputFormat == null) {
            logAndSetStatus("Please select an output format.");
            return;
        }
        statusUpdater.accept("Merging audios...");
        taskManager.start("audio-merge", () -> {
            try {
                AudioMergeService.MergeResult result = audioMergeService.mergeAudioFiles(sourceFolder, minimumLength,
                                outputFormat, albumName);
                String displayAlbumName = albumName == null || albumName.isBlank()
                                ? sourceFolder.getName()
                                : albumName.trim();
                String message = "Created " + result.createdTrackCount() + " longer tracks out of "
                                + result.originalTrackCount() + " for " + displayAlbumName + ".";
                javafx.application.Platform.runLater(() -> mergeResultConsumer.accept(message));
                statusUpdater.accept("Audios merged. Done!");
            } catch (InterruptedIOException e) {
                logger.accept("Audio merging cancelled.");
                statusUpdater.accept("Audio merging cancelled.");
            } catch (Exception e) {
                logger.accept("Could not merge audios: " + e.getMessage());
                statusUpdater.accept("Audio merging failed.");
            }
        });
    }

    private void logAndSetStatus(String message) {
        logger.accept(message);
        statusUpdater.accept(message);
    }
}
