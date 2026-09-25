package tiptoieditor.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import service.tttool.TttoolService;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Controls for creating a single start-code PDF for an inclusive OID range. */
public class RowCreateOidRangeTable {

    private final Label selectedDirectoryLabel;
    private final TextField startOidField;
    private final TextField endOidField;
    private final TttoolService tttoolService;
    private final Consumer<String> logger;
    private final Consumer<String> statusUpdater;
    private final WorkflowTaskManager taskManager;
    private final Supplier<TttoolService.OidTableSettings> oidTableSettingsSupplier;
    private File selectedDirectory;

    public RowCreateOidRangeTable(Stage stage, Button selectDirectoryButton, Label selectedDirectoryLabel,
            Button createPdfButton, TextField startOidField, TextField endOidField, TttoolService tttoolService,
            Consumer<String> logger, Consumer<String> statusUpdater, WorkflowTaskManager taskManager,
            Supplier<TttoolService.OidTableSettings> oidTableSettingsSupplier) {
        this.selectedDirectoryLabel = selectedDirectoryLabel;
        this.startOidField = startOidField;
        this.endOidField = endOidField;
        this.tttoolService = tttoolService;
        this.logger = logger;
        this.statusUpdater = statusUpdater;
        this.taskManager = taskManager;
        this.oidTableSettingsSupplier = oidTableSettingsSupplier;
        selectDirectoryButton.setOnAction(e -> selectDirectory(stage));
        createPdfButton.setOnAction(e -> runToolCreateOidRangeTable());
    }

    private void selectDirectory(Stage stage) {
        File folder = FolderSelectionDialog.chooseFolder(stage, selectedDirectory);
        if (folder != null) {
            setSelectedDirectory(folder);
            logger.accept("Selected OID table directory: " + folder.getAbsolutePath());
        }
    }

    public void setSelectedDirectory(File selectedDirectory) {
        this.selectedDirectory = selectedDirectory;
        selectedDirectoryLabel.setText(selectedDirectory.getName());
    }

    public void runToolCreateOidRangeTable() {
        runToolCreateOidRangeTable(selectedDirectory, null, null);
    }

    /** Creates the manually configured start-code range PDF in {@code outputDirectory}. */
    public void runToolCreateOidRangeTable(File outputDirectory, Runnable onComplete, Consumer<String> onFailure) {
        if (outputDirectory == null) {
            fail(onFailure, "Please select a directory first.");
            return;
        }

        int startOid;
        int endOid;
        try {
            startOid = parseOid(startOidField.getText(), "start");
            endOid = parseOid(endOidField.getText(), "end");
        } catch (IllegalArgumentException e) {
            fail(onFailure, e.getMessage());
            return;
        }
        if (startOid > endOid) {
            fail(onFailure, "The start OID must not be greater than the end OID.");
            return;
        }

        runToolCreateOidRangeTable(outputDirectory, startOid, endOid, "start-oid-table.pdf", onComplete, onFailure);
    }

    /** Creates a named OID range PDF without reading the manual range input fields. */
    public void runToolCreateOidRangeTable(File outputDirectory, int startOid, int endOid, String outputFileName,
            Runnable onComplete, Consumer<String> onFailure) {
        if (outputDirectory == null) {
            fail(onFailure, "Please select a directory first.");
            return;
        }
        if (startOid > endOid) {
            fail(onFailure, "The start OID must not be greater than the end OID.");
            return;
        }

        final TttoolService.OidTableSettings oidTableSettings;
        try {
            oidTableSettings = oidTableSettingsSupplier.get();
        } catch (IllegalArgumentException e) {
            fail(onFailure, "Could not create OID range table. Invalid PDF settings.");
            return;
        }

        Path outputPdf = outputDirectory.toPath().resolve(outputFileName);
        logger.accept("Creating OID range table: " + startOid + "-" + endOid + " in "
                + outputDirectory.getAbsolutePath());
        statusUpdater.accept("Creating OID range table...");
        taskManager.start("tttool-oid-range-table", () -> {
            try {
                String output = tttoolService.createOidRangeTable(startOid, endOid, outputPdf, oidTableSettings);
                Platform.runLater(() -> {
                    statusUpdater.accept("Created OID range table. Done!");
                    logger.accept(output.isBlank() ? "tttool finished successfully." : output);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Platform.runLater(() -> fail(onFailure, "OID range table creation cancelled."));
            } catch (Exception e) {
                Platform.runLater(() -> fail(onFailure, "Could not create OID range table: " + e.getMessage()));
            }
        });
    }

    private static int parseOid(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Please enter a " + label + " OID.");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Please enter a valid " + label + " OID.");
        }
    }

    private void fail(Consumer<String> onFailure, String message) {
        logger.accept(message);
        statusUpdater.accept(message);
        if (onFailure != null) {
            onFailure.accept(message);
        }
    }
}
