package tiptoieditor.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import service.yaml.GenerateYamlService;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Controls for selecting an album folder and generating its YAML files.
 */
public class RowCreateYaml {

    private final Label selectedAlbumFolderLabel;
    private final Supplier<String> productIdSupplier;
    private final Supplier<String> albumTitleSupplier;
    private final Supplier<GenerateYamlService.ScriptCodeSettings> scriptCodeSettingsSupplier;
    private final Consumer<File> yamlFileConsumer;
    private final Consumer<String> logger;
    private final Consumer<String> statusUpdater;
    private final WorkflowTaskManager taskManager;
    private File selectedAlbumFolder;
    private String cancelText = "YAML creation cancelled.";

    public RowCreateYaml(Stage stage, Button selectAlbumFolderButton, Label selectedAlbumFolderLabel,
            Button createYamlButton, Supplier<String> productIdSupplier, Supplier<String> albumTitleSupplier,
            Supplier<GenerateYamlService.ScriptCodeSettings> scriptCodeSettingsSupplier,
            Consumer<File> yamlFileConsumer, Consumer<String> logger,
            Consumer<String> statusUpdater, WorkflowTaskManager taskManager) {
        this.selectedAlbumFolderLabel = selectedAlbumFolderLabel;
        this.productIdSupplier = productIdSupplier;
        this.albumTitleSupplier = albumTitleSupplier;
        this.scriptCodeSettingsSupplier = scriptCodeSettingsSupplier;
        this.yamlFileConsumer = yamlFileConsumer;
        this.logger = logger;
        this.statusUpdater = statusUpdater;
        this.taskManager = taskManager;
        selectAlbumFolderButton.setOnAction(e -> selectAlbumFolder(stage));
        createYamlButton.setOnAction(e -> runToolCreateYaml());
    }

    private void selectAlbumFolder(Stage stage) {
        File folder = FolderSelectionDialog.chooseFolder(stage, selectedAlbumFolder);
        if (folder != null) {
            setSelectedAlbumFolder(folder);
            logger.accept("Selected album folder: " + folder.getAbsolutePath());
        }
    }

    public File getSelectedAlbumFolder() {
        return selectedAlbumFolder;
    }

    public void setSelectedAlbumFolder(File selectedAlbumFolder) {
        this.selectedAlbumFolder = selectedAlbumFolder;
        selectedAlbumFolderLabel.setText(selectedAlbumFolder.getName());
    }

    public void runToolCreateYaml() {
        runToolCreateYaml(null);
    }

    /**
     * Generates YAML in the background and invokes {@code onComplete} on the JavaFX
     * thread.
     */
    public void runToolCreateYaml(Consumer<File> onComplete) {
        runToolCreateYaml(onComplete, null);
    }

    /** Generates YAML and reports a failed workflow step to {@code onFailure}. */
    public void runToolCreateYaml(Consumer<File> onComplete, Consumer<String> onFailure) {
        String productIdText = productIdSupplier.get().trim();
        if (productIdText.isEmpty()) {
            logger.accept("Please enter a product ID.");
            statusUpdater.accept("Please enter a product ID.");
            notifyFailure(onFailure, "Please enter a product ID.");
            return;
        } else if (selectedAlbumFolder == null) {
            logger.accept("Please select a folder first.");
            statusUpdater.accept("Please select a folder first.");
            notifyFailure(onFailure, "Please select a folder first.");
            return;
        }

        int productId;
        GenerateYamlService.ScriptCodeSettings scriptCodeSettings;
        try {
            productId = Integer.parseInt(productIdText);
            scriptCodeSettings = scriptCodeSettingsSupplier.get();
        } catch (NumberFormatException e) {
            logger.accept("Please enter a valid product ID.");
            statusUpdater.accept("Please enter a valid product ID.");
            notifyFailure(onFailure, "Please enter a valid product ID.");
            return;
        } catch (IllegalArgumentException e) {
            logger.accept(e.getMessage());
            statusUpdater.accept("Please enter valid chapter OID settings.");
            notifyFailure(onFailure, "Please enter valid chapter OID settings.");
            return;
        }
        File albumFolder = selectedAlbumFolder;
        statusUpdater.accept("Creating Yaml...");
        taskManager.start("yaml-create", () -> {
            try {
                logger.accept("Creating YAML...");
                GenerateYamlService.GeneratedYamlFiles generatedFiles = new GenerateYamlService()
                        .generate(productId, albumFolder, albumTitleSupplier.get(), scriptCodeSettings);
                if (Thread.currentThread().isInterrupted()) {
                    logger.accept(cancelText);
                    statusUpdater.accept(cancelText);
                    notifyFailure(onFailure, cancelText);
                    return;
                }
                File yamlFile = generatedFiles.yamlFile().toFile();
                logger.accept("YAML created: " + yamlFile.getAbsolutePath());
                Platform.runLater(() -> {
                    yamlFileConsumer.accept(yamlFile);
                    statusUpdater.accept("Created Yaml. Done!");
                    if (onComplete != null) {
                        onComplete.accept(yamlFile);
                    }
                });
            } catch (Exception e) {
                logger.accept("Could not create YAML: " + e.getMessage());
                statusUpdater.accept("YAML creation failed.");
                notifyFailure(onFailure, "YAML creation failed.");
            }
        });
    }

    private static void notifyFailure(Consumer<String> onFailure, String message) {
        if (onFailure != null) {
            Platform.runLater(() -> onFailure.accept(message));
        }
    }
}
