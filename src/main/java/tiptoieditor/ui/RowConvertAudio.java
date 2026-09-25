package tiptoieditor.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import service.audio.AudioConvertService;
import service.audio.AudioCopyService;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Controls for selecting and converting an audio folder.
 */
public class RowConvertAudio {

    private final Label selectedAudioFolderLabel;
    private final AudioCopyService audioCopyService;
    private final AudioConvertService audioConvertService;
    private final Supplier<String> albumNameSupplier;
    private final Consumer<String> logger;
    private final Consumer<String> statusUpdater;
    private final WorkflowTaskManager taskManager;
    private String cancelText = "Audio preparation cancelled.";
    private Consumer<File> selectedAudioFolderConsumer = folder -> {
    };
    private Consumer<File> audioPreparedConsumer = folder -> {
    };
    private File selectedAudioFolder;

    public RowConvertAudio(Stage stage, Button selectAudioFolderButton, Label selectedAudioFolderLabel,
            Button convertButton, AudioCopyService audioCopyService,
            AudioConvertService audioConvertService, Supplier<String> albumNameSupplier, Consumer<String> logger,
            Consumer<String> statusUpdater, WorkflowTaskManager taskManager) {
        this.selectedAudioFolderLabel = selectedAudioFolderLabel;
        this.audioCopyService = audioCopyService;
        this.audioConvertService = audioConvertService;
        this.albumNameSupplier = albumNameSupplier;
        this.logger = logger;
        this.statusUpdater = statusUpdater;
        this.taskManager = taskManager;
        selectAudioFolderButton.setOnAction(e -> selectAudioFolder(stage));
        convertButton.setOnAction(e -> runToolCopyAndConvert());
    }

    public void selectAudioFolder(Stage stage) {
        File folder = FolderSelectionDialog.chooseFolder(stage, selectedAudioFolder);
        if (folder != null) {
            setSelectedAudioFolder(folder);
            logger.accept("Selected audio folder: " + folder.getAbsolutePath());
        }
    }

    public void runToolCopyAndConvert() {
        runToolCopyAndConvert(null);
    }

    public void runToolCopyAndConvert(Consumer<File> onComplete) {
        runToolCopyAndConvert(onComplete, null);
    }

    /** Prepares source audio and reports a failed workflow step to {@code onFailure}. */
    public void runToolCopyAndConvert(Consumer<File> onComplete, Consumer<String> onFailure) {
        if (selectedAudioFolder == null) {
            logger.accept("Please select a folder first.");
            notifyFailure(onFailure, "Please select a folder first.");
            return;
        }

        File sourceAudioFolder = selectedAudioFolder;
        String albumName = albumNameSupplier.get();
        String resolvedAlbumName = albumName == null || albumName.isBlank() ? "tttoolAlbum" : albumName.trim();

        logger.accept("Preparing folder structure for album: " + resolvedAlbumName);
        statusUpdater.accept("Prepping audio fiiles...");

        taskManager.start("audio-convert", () -> {
            try {
                File audioFolder = audioCopyService.prepareAudioFolder(sourceAudioFolder, resolvedAlbumName);
                if (Thread.currentThread().isInterrupted()) {
                    logger.accept(cancelText);
                    statusUpdater.accept(cancelText);
                    notifyFailure(onFailure, cancelText);
                    return;
                }
                logger.accept("Audio copied to: " + audioFolder.getAbsolutePath());
                processAudioFolder(audioFolder, onComplete, onFailure);
            } catch (Exception e) {
                logger.accept("Could not prepare audio: " + e.getMessage());
                statusUpdater.accept("Audio preparation failed.");
                notifyFailure(onFailure, "Audio preparation failed.");
            }
        });
    }

    /** Prepares audio from a separate source folder while keeping the album beside {@code albumLocationFolder}. */
    public void runToolCopyAndConvertFromSource(File sourceAudioFolder, File albumLocationFolder,
            Consumer<File> onComplete, Consumer<String> onFailure) {
        String albumName = albumNameSupplier.get();
        String resolvedAlbumName = albumName == null || albumName.isBlank() ? "tttoolAlbum" : albumName.trim();

        logger.accept("Preparing folder structure for album: " + resolvedAlbumName);
        statusUpdater.accept("Prepping audio fiiles...");
        taskManager.start("audio-convert", () -> {
            try {
                File audioFolder = audioCopyService.prepareAudioFolder(sourceAudioFolder, albumLocationFolder,
                        resolvedAlbumName);
                if (Thread.currentThread().isInterrupted()) {
                    logger.accept(cancelText);
                    statusUpdater.accept(cancelText);
                    notifyFailure(onFailure, cancelText);
                    return;
                }
                logger.accept("Audio copied to: " + audioFolder.getAbsolutePath());
                processAudioFolder(audioFolder, onComplete, onFailure);
            } catch (Exception e) {
                logger.accept("Could not prepare audio: " + e.getMessage());
                statusUpdater.accept("Audio preparation failed.");
                notifyFailure(onFailure, "Audio preparation failed.");
            }
        });
    }

    /**
     * Processes an existing album audio directory and then continues on the JavaFX
     * thread.
     */
    public void runToolProcessAudio(File audioFolder, Consumer<File> onComplete) {
        statusUpdater.accept("Prepping audio fiiles...");
        taskManager.start("audio-process", () -> processAudioFolder(audioFolder, onComplete, null));
    }

    /**
     * Copies an existing album's exported audio into {@code audio} and processes
     * it.
     */
    public void runToolCopyAndConvertForExistingAlbum(File albumFolder, Consumer<File> onComplete) {
        runToolCopyAndConvertForExistingAlbum(albumFolder, onComplete, null);
    }

    /** Copies an existing album's audio and reports a failed workflow step to {@code onFailure}. */
    public void runToolCopyAndConvertForExistingAlbum(File albumFolder, Consumer<File> onComplete,
            Consumer<String> onFailure) {
        runToolCopyAndConvertForExistingAlbum(albumFolder, albumFolder, onComplete, onFailure);
    }

    /** Copies audio from {@code sourceFolder} into an existing album and processes it. */
    public void runToolCopyAndConvertForExistingAlbum(File albumFolder, File sourceFolder, Consumer<File> onComplete,
            Consumer<String> onFailure) {
        statusUpdater.accept("Prepping audio fiiles...");
        taskManager.start("audio-convert", () -> {
            try {
                File audioFolder = audioCopyService.prepareAudioFolderForExistingAlbum(albumFolder, sourceFolder);
                if (Thread.currentThread().isInterrupted()) {
                    logger.accept(cancelText);
                    statusUpdater.accept(cancelText);
                    notifyFailure(onFailure, cancelText);
                    return;
                }
                logger.accept("Audio copied to: " + audioFolder.getAbsolutePath());
                processAudioFolder(audioFolder, onComplete, onFailure);
            } catch (Exception e) {
                logger.accept("Could not prepare audio: " + e.getMessage());
                statusUpdater.accept("Audio preparation failed.");
                notifyFailure(onFailure, "Audio preparation failed.");
            }
        });
    }

    private void processAudioFolder(File audioFolder, Consumer<File> onComplete, Consumer<String> onFailure) {
        logger.accept("Processing audio...");
        try {
            audioConvertService.processFolder(audioFolder);
        } catch (Exception e) {
            logger.accept("Could not prepare audio: " + e.getMessage());
            statusUpdater.accept("Audio preparation failed.");
            notifyFailure(onFailure, "Audio preparation failed.");
            return;
        }

        if (Thread.currentThread().isInterrupted()) {
            logger.accept(cancelText);
            statusUpdater.accept(cancelText);
            notifyFailure(onFailure, cancelText);
            return;
        }

        logger.accept("Audio successfully processed.");
        statusUpdater.accept("Prepped audio fiiles. Done!");
        Platform.runLater(() -> {
            audioPreparedConsumer.accept(audioFolder);
            if (onComplete != null) {
                onComplete.accept(audioFolder);
            }
        });
    }

    public File getSelectedAudioFolder() {
        return selectedAudioFolder;
    }

    public void setSelectedAudioFolder(File selectedAudioFolder) {
        this.selectedAudioFolder = selectedAudioFolder;
        selectedAudioFolderLabel.setText(selectedAudioFolder.getName());
        selectedAudioFolderConsumer.accept(selectedAudioFolder);
    }

    /** Registers a listener for audio-folder selections made by any workflow. */
    public void setOnSelectedAudioFolder(Consumer<File> selectedAudioFolderConsumer) {
        this.selectedAudioFolderConsumer = selectedAudioFolderConsumer;
    }

    /**
     * Registers a listener that receives the {@code audio} folder after preparation
     * is complete.
     */
    public void setOnAudioPrepared(Consumer<File> audioPreparedConsumer) {
        this.audioPreparedConsumer = audioPreparedConsumer;
    }

    private static void notifyFailure(Consumer<String> onFailure, String message) {
        if (onFailure != null) {
            Platform.runLater(() -> onFailure.accept(message));
        }
    }

}
