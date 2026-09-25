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
    private Runnable mergeRequestedAction;

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
        this.mergeRequestedAction = this::mergeSelectedFolder;
        selectAudioFolderButton.setOnAction(e -> selectAudioFolder(stage));
        mergeAudiosButton.setOnAction(e -> mergeRequestedAction.run());
    }

    private void selectAudioFolder(Stage stage) {
        File folder = FolderSelectionDialog.chooseFolder(stage, selectedAudioFolder);
        if (folder != null) {
            setSelectedAudioFolder(folder);
            mainFolderSelectionConsumer.accept(folder);
            logger.accept("Selected audio folder for merging: " + folder.getAbsolutePath());
        }
    }

    /** Overrides the default selected-folder merge action, for example in many-directory mode. */
    public void setOnMergeRequested(Runnable mergeRequestedAction) {
        this.mergeRequestedAction = mergeRequestedAction;
    }

    /** Starts a merge for the selected folder using the format selected in the UI. */
    public void mergeSelectedFolder() {
        if (selectedAudioFolder == null) {
            logAndSetStatus("Please select an audio folder first.");
            return;
        }
        AudioMergeService.OutputFormat outputFormat = outputFormatComboBox.getValue();
        if (outputFormat == null) {
            logAndSetStatus("Please select an output format.");
            return;
        }
        String albumName = albumNameSupplier.get();
        runToolMergeAudios(selectedAudioFolder, albumName, outputFormat,
                result -> mergeResultConsumer.accept(mergeResultMessage(result, selectedAudioFolder, albumName)), null);
    }

    /** Runs the merge step for the main workflow, always producing OGG files. */
    public void runToolMergeAudiosForWorkflow(File sourceFolder, String albumName, Consumer<File> onComplete,
            Consumer<String> onFailure) {
        runToolMergeAudios(sourceFolder, albumName, AudioMergeService.OutputFormat.OGG, result -> {
            mergeResultConsumer.accept(mergeResultMessage(result, sourceFolder, albumName));
            if (onComplete != null) {
                onComplete.accept(result.outputFolder());
            }
        }, onFailure);
    }

    /** Runs a merge with the current minimum-length field and reports its result on the JavaFX thread. */
    public void runToolMergeAudios(File sourceFolder, String albumName, AudioMergeService.OutputFormat outputFormat,
            Consumer<AudioMergeService.MergeResult> onComplete, Consumer<String> onFailure) {
        runToolMergeAudios(sourceFolder, albumName, outputFormat, null, onComplete, onFailure);
    }

    /** Runs a merge with the current minimum-length field into an optional custom output folder. */
    public void runToolMergeAudios(File sourceFolder, String albumName, AudioMergeService.OutputFormat outputFormat,
            File outputFolder, Consumer<AudioMergeService.MergeResult> onComplete, Consumer<String> onFailure) {
        double minimumLength;
        try {
            minimumLength = getMinimumLength();
        } catch (IllegalArgumentException e) {
            logAndSetStatus(e.getMessage());
            if (onFailure != null) {
                onFailure.accept(e.getMessage());
            }
            return;
        }
        statusUpdater.accept("Merging audios...");
        taskManager.start("audio-merge", () -> {
            try {
                AudioMergeService.MergeResult result = outputFolder == null
                                ? audioMergeService.mergeAudioFiles(sourceFolder, minimumLength, outputFormat,
                                                albumName)
                                : audioMergeService.mergeAudioFiles(sourceFolder, minimumLength, outputFormat,
                                                albumName, outputFolder);
                statusUpdater.accept("Audios merged. Done!");
                if (onComplete != null) {
                    javafx.application.Platform.runLater(() -> onComplete.accept(result));
                }
            } catch (InterruptedIOException e) {
                logger.accept("Audio merging cancelled.");
                statusUpdater.accept("Audio merging cancelled.");
                notifyFailure(onFailure, "Audio merging cancelled.");
            } catch (Exception e) {
                logger.accept("Could not merge audios: " + e.getMessage());
                statusUpdater.accept("Audio merging failed.");
                notifyFailure(onFailure, "Audio merging failed.");
            }
        });
    }

    public File getSelectedAudioFolder() {
        return selectedAudioFolder;
    }

    public AudioMergeService.OutputFormat getSelectedOutputFormat() {
        return outputFormatComboBox.getValue();
    }

    public void setSelectedAudioFolder(File selectedAudioFolder) {
        this.selectedAudioFolder = selectedAudioFolder;
        selectedAudioFolderLabel.setText(selectedAudioFolder.getName());
    }

    private double getMinimumLength() {
        try {
            double minimumLength = Double.parseDouble(minimumLengthField.getText().trim());
            if (!Double.isFinite(minimumLength) || minimumLength <= 0) {
                throw new NumberFormatException();
            }
            return minimumLength;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Minimum length must be a number greater than zero.");
        }
    }

    public static String mergeResultMessage(AudioMergeService.MergeResult result, File sourceFolder,
            String albumName) {
        String displayAlbumName = albumName == null || albumName.isBlank()
                ? sourceFolder.getName()
                : albumName.trim();
        return "Created " + result.createdTrackCount() + " longer tracks out of "
                + result.originalTrackCount() + " for " + displayAlbumName + ".";
    }

    private static void notifyFailure(Consumer<String> onFailure, String message) {
        if (onFailure != null) {
            javafx.application.Platform.runLater(() -> onFailure.accept(message));
        }
    }

    private void logAndSetStatus(String message) {
        logger.accept(message);
        statusUpdater.accept(message);
    }
}
