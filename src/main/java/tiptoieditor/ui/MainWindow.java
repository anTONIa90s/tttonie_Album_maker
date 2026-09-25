package tiptoieditor.ui;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import service.audio.AudioConvertService;
import service.audio.AudioCopyService;
import service.audio.AudioFileNameService;
import service.audio.AudioMergeService;
import service.tonie.TonieAudioExportService;
import service.tonie.TonieExportDestinationService;
import service.tttool.TttoolService;
import service.workflow.AlbumFolderWorkflowResolver;
import service.yaml.GenerateYamlService;
import org.controlsfx.control.StatusBar;
import org.controlsfx.control.ToggleSwitch;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class MainWindow {

        // The status bar background is defined by orange-theme.css. Inline styles here
        // are
        // intentionally limited to state-specific text colours so they do not override
        // it.
        private static final String STATUS_BASE_STYLE = "";
        private static final String STATUS_SUCCESS_STYLE = "-fx-text-fill: #2e7d32;";
        private static final String STATUS_ERROR_STYLE = "-fx-text-fill: #b3261e;";

        private TextArea outputArea;
        private StatusBar workflowStatusBar;
        private boolean manyDirectoriesMode;
        private boolean manyDirectoriesCancelled;
        private int manyDirectoriesStartingProductId;
        private GenerateYamlService.ScriptCodeSettings manyDirectoriesScriptCodeSettings;
        private VBox manyDirectoryResults;

        private TextField productNameField;
        private TextField productIdField;
        private ToggleSwitch appendProductIdToggle;
        private final ValidationSupport validationSupport = new ValidationSupport();
        private RowConvertAudio rowConvertAudio;
        private RowCreateYaml rowCreateYaml;
        private RowYamlToGme rowYamlToGme;
        private RowCreateOidTable rowCreateOidTable;
        private RowCreateOidRangeTable rowCreateOidRangeTable;
        private RowExportTonieAudio rowExportTonieAudio;
        private AlbumWorkflowContinuation albumWorkflowContinuation;
        private TextField startOidField;
        private TextField endOidField;
        private TextField firstScriptCodeField;
        private TextField scriptCodeTracksField;
        private TextField oidTableDpiField;
        private TextField oidTablePixelSizeField;
        private TextField oidTableCodeDimField;

        private final WorkflowTaskManager taskManager = new WorkflowTaskManager();
        private final TttoolService tttoolService = new TttoolService(taskManager);
        private final AudioCopyService audioCopyService = new AudioCopyService();
        private final AudioConvertService audioConvertService = new AudioConvertService(this::log, taskManager);
        private final AudioMergeService audioMergeService = new AudioMergeService(this::log, taskManager);
        private final AudioFileNameService audioFileNameService = new AudioFileNameService();
        private final TonieAudioExportService tonieAudioExportService = new TonieAudioExportService();
        private final TonieExportDestinationService tonieExportDestinationService = new TonieExportDestinationService();
        private final AlbumFolderWorkflowResolver workflowResolver = new AlbumFolderWorkflowResolver(
                        tonieAudioExportService);

        public void show(Stage stage) {
                Label title = new Label("TTTool GUI");
                title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

                productNameField = new TextField();
                productNameField.setPromptText("Album name (default: tttoolAlbum)");

                productIdField = new TextField();
                productIdField.setTextFormatter(
                                new TextFormatter<>(
                                                change -> change.getControlNewText().matches("\\d*") ? change : null));
                productIdField.setPrefColumnCount(5);
                productIdField.setPromptText("Product ID");
                validationSupport.registerValidator(productIdField,
                                Validator.createPredicateValidator(MainWindow::isValidProductId,
                                                "Enter a Product ID between 0 and " + Integer.MAX_VALUE + "."));

                appendProductIdToggle = new ToggleSwitch();
                appendProductIdToggle.setTooltip(new javafx.scene.control.Tooltip(
                                "will add product ID to album name"));

                Button selectDirectoryButton = new Button("Select Directory");
                selectDirectoryButton.setPrefHeight(40);
                Label selectedFolderLabel = new Label("No folder selected");
                selectedFolderLabel.setStyle("-fx-text-fill: gray;");
                Label productNameLabel = new Label("Album Name");
                productNameLabel.setStyle("-fx-font-weight: bold;");
                Label productIdLabel = new Label("Product ID");
                productIdLabel.setStyle("-fx-font-weight: bold;");
                VBox productNameControl = new VBox(4, productNameLabel, productNameField);
                VBox productIdControl = new VBox(4, productIdLabel, productIdField);
                VBox appendProductIdControl = new VBox(4, new Label("append"), appendProductIdToggle);
                appendProductIdControl.setAlignment(Pos.BOTTOM_LEFT);
                HBox productDetailsGroup = new HBox(10, productIdControl, appendProductIdControl);
                productDetailsGroup.setAlignment(Pos.BOTTOM_RIGHT);
                VBox selectDirectoryControl = new VBox(selectDirectoryButton);
                selectDirectoryControl.setAlignment(Pos.BOTTOM_LEFT);
                HBox inputControlsRow = new HBox(10, selectDirectoryControl, productNameControl, productDetailsGroup);
                inputControlsRow.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(productNameControl, Priority.ALWAYS);
                productNameControl.setMaxWidth(Double.MAX_VALUE);
                VBox rowInput = new VBox(4, selectedFolderLabel, inputControlsRow);

                Button runButton = new Button("Run tttool");
                runButton.setPrefHeight(50);
                Button cancelButton = new Button("Cancel");
                cancelButton.setVisible(false);
                cancelButton.setManaged(false);
                HBox runRow = new HBox(10, runButton, cancelButton);
                runRow.setMaxWidth(Double.MAX_VALUE);
                runButton.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(runButton, Priority.ALWAYS);

                Button selectAudioFolderButton = new Button("Select Audio Folder");
                Label selectedAudioFolderLabel = new Label("No folder selected");
                Button convertAudioButton = new Button("Convert Audio");

                Button selectAudioMergeFolderButton = new Button("Select Audio Folder");
                Label selectedAudioMergeFolderLabel = new Label("No folder selected");
                ComboBox<AudioMergeService.OutputFormat> outputFormatComboBox = new ComboBox<>(
                                FXCollections.observableArrayList(AudioMergeService.OutputFormat.values()));
                outputFormatComboBox.getStyleClass().add("format-dropdown");
                outputFormatComboBox.setValue(AudioMergeService.OutputFormat.OGG);
                TextField minimumLengthField = new TextField("5");
                minimumLengthField.setPrefColumnCount(4);
                minimumLengthField.setPromptText("Minimum Length");
                Button mergeAudiosButton = new Button("Merge Audios");

                Button selectAlbumFolderButton = new Button("Select Album Folder");
                Label selectedAlbumFolderLabel = new Label("No folder selected");
                Button createYamlButton = new Button("Create YAML");

                Button selectYamlFileButton = new Button("Select YAML file");
                Label selectedYamlFileLabel = new Label("No YAML file selected");
                Button createGmeButton = new Button("Create GME");

                Button selectOidTableFolderButton = new Button("Select Album Folder");
                Label selectedOidTableFolderLabel = new Label("No folder selected");
                Button createOidTableButton = new Button("Create OID Table");

                Button selectOidRangeTableDirectoryButton = new Button("Select Directory");
                Label selectedOidRangeTableDirectoryLabel = new Label("No folder selected");
                Button createOidRangeTableButton = new Button("Create PDF");
                startOidField = createOidField("Start OID");
                endOidField = createOidField("End OID");
                HBox oidRangeFields = new HBox(10, new Label("Start OID"), startOidField,
                                new Label("End OID"), endOidField);
                oidRangeFields.setAlignment(Pos.CENTER_LEFT);

                firstScriptCodeField = createOidField("First OID");
                firstScriptCodeField.setText(Integer.toString(GenerateYamlService.FIRST_SCRIPT_CODE));
                scriptCodeTracksField = createOidField("Number of Chapters");
                scriptCodeTracksField.setText(Integer.toString(GenerateYamlService.SCRIPT_CODE_TRACKS));

                oidTableDpiField = createOidField("DPI");
                oidTableDpiField.setText(Integer.toString(TttoolService.OidTableSettings.DEFAULT.dpi()));
                oidTablePixelSizeField = createOidField("Pixel Size");
                oidTablePixelSizeField.setText(Integer.toString(TttoolService.OidTableSettings.DEFAULT.pixelSize()));
                oidTableCodeDimField = createOidField("Code dimensions");
                oidTableCodeDimField.setText(Integer.toString(TttoolService.OidTableSettings.DEFAULT.codeDim()));

                Label chapterSettingsLabel = new Label("Chapter settings");
                chapterSettingsLabel.setStyle("-fx-font-weight: bold;");
                VBox chapterOidSettingsColumn = new VBox(8, chapterSettingsLabel,
                                createSettingsInputRow("First OID", firstScriptCodeField),
                                createSettingsInputRow("Number of Chapters", scriptCodeTracksField));

                Label pdfSettingsLabel = new Label("PDF settings");
                pdfSettingsLabel.setStyle("-fx-font-weight: bold;");
                VBox pdfOidSettingsColumn = new VBox(8, pdfSettingsLabel,
                                createSettingsInputRow("DPI", oidTableDpiField),
                                createSettingsInputRow("Pixel Size", oidTablePixelSizeField),
                                createSettingsInputRow("Code dimensions", oidTableCodeDimField));

                chapterOidSettingsColumn.setMaxWidth(Double.MAX_VALUE);
                pdfOidSettingsColumn.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(chapterOidSettingsColumn, Priority.ALWAYS);
                HBox.setHgrow(pdfOidSettingsColumn, Priority.ALWAYS);
                Separator settingsDivider = new Separator(Orientation.VERTICAL);
                settingsDivider.setMaxHeight(Double.MAX_VALUE);
                HBox oidSettingsFields = new HBox(20, chapterOidSettingsColumn, settingsDivider,
                                pdfOidSettingsColumn);
                oidSettingsFields.setAlignment(Pos.TOP_LEFT);
                oidSettingsFields.setMaxWidth(Double.MAX_VALUE);

                Button selectTonieFileButton = new Button("Select Tonie File");
                Label selectedTonieFileLabel = new Label("No file selected");
                Button exportTonieAudioButton = new Button("Export OGG");

                Button selectGmeFolderButton = new Button("Select GME Folder");
                Label selectedGmeFolderLabel = new Label("No folder selected");
                Button listGmeProductIdsButton = new Button("List Product IDs");
                ProgressIndicator listGmeProductIdsSpinner = new ProgressIndicator();
                listGmeProductIdsSpinner.setPrefSize(16, 16);
                listGmeProductIdsSpinner.setMaxSize(16, 16);
                listGmeProductIdsSpinner.setVisible(false);
                ObservableList<RowListGmeProductIds.ProductIdTableRow> productIdRows = FXCollections
                                .observableArrayList();
                TableView<RowListGmeProductIds.ProductIdTableRow> productIdTable = createProductIdTable(productIdRows);

                rowConvertAudio = new RowConvertAudio(stage, selectAudioFolderButton, selectedAudioFolderLabel,
                                convertAudioButton, audioCopyService, audioConvertService,
                                this::getAlbumName, this::log, this::setWorkflowStatus, taskManager);
                new RowMergeAudios(stage, selectAudioMergeFolderButton, selectedAudioMergeFolderLabel,
                                outputFormatComboBox, minimumLengthField, mergeAudiosButton, audioMergeService, this::log,
                                this::setWorkflowStatus, taskManager, this::getAlbumName,
                                rowConvertAudio::setSelectedAudioFolder, this::showMergeAudioResult);
                selectDirectoryButton.setOnAction(e -> rowConvertAudio.selectAudioFolder(stage));
                rowYamlToGme = new RowYamlToGme(stage, selectYamlFileButton, selectedYamlFileLabel,
                                createGmeButton, tttoolService, audioFileNameService, this::log,
                                this::setWorkflowStatus, taskManager);
                rowCreateOidTable = new RowCreateOidTable(stage, selectOidTableFolderButton,
                                selectedOidTableFolderLabel, createOidTableButton, tttoolService, this::log,
                                this::setWorkflowStatus, taskManager, this::getOidTableSettings);
                rowCreateOidRangeTable = new RowCreateOidRangeTable(stage, selectOidRangeTableDirectoryButton,
                                selectedOidRangeTableDirectoryLabel, createOidRangeTableButton, startOidField,
                                endOidField, tttoolService, this::log, this::setWorkflowStatus, taskManager,
                                this::getOidTableSettings);
                rowYamlToGme.setOnSelectedYamlFile(
                                yamlFile -> rowCreateOidTable.setSelectedAlbumFolder(yamlFile.getParentFile()));
                rowCreateYaml = new RowCreateYaml(stage, selectAlbumFolderButton, selectedAlbumFolderLabel,
                                createYamlButton, productIdField::getText, this::getMetadataName,
                                this::getCurrentScriptCodeSettings,
                                rowYamlToGme::setSelectedYamlFile, this::log, this::setWorkflowStatus, taskManager);
                createYamlButton.addEventFilter(ActionEvent.ACTION,
                                event -> validationSupport.initInitialDecoration());
                rowConvertAudio.setOnAudioPrepared(audioFolder -> {
                        File albumFolder = audioFolder.getParentFile();
                        rowConvertAudio.setSelectedAudioFolder(albumFolder);
                        rowCreateYaml.setSelectedAlbumFolder(albumFolder);
                });
                rowExportTonieAudio = new RowExportTonieAudio(stage, selectTonieFileButton, selectedTonieFileLabel,
                                exportTonieAudioButton, tonieAudioExportService, tonieExportDestinationService,
                                this::getAlbumName, this::log, rowConvertAudio::setSelectedAudioFolder,
                                this::setWorkflowStatus, taskManager);
                albumWorkflowContinuation = new AlbumWorkflowContinuation(rowCreateYaml, rowYamlToGme,
                                rowCreateOidTable,
                                rowExportTonieAudio,
                                rowConvertAudio::getSelectedAudioFolder, workflowResolver, this::log,
                                this::getMetadataName, this::setWorkflowStatus);
                rowConvertAudio.setOnSelectedAudioFolder(folder -> {
                        selectedFolderLabel.setText(folder.getName());
                        productNameField.setText(albumNameFromFolderName(folder.getName()));
                        albumWorkflowContinuation.updateSelectedFolderControls();
                });
                new RowListGmeProductIds(stage, selectGmeFolderButton, selectedGmeFolderLabel,
                                listGmeProductIdsButton, listGmeProductIdsSpinner, tttoolService, productIdRows,
                                this::log, this::setWorkflowStatus, taskManager);

                ExpandableSubActions exportToniePane = new ExpandableSubActions(
                                "Only export Tonie audio", selectTonieFileButton, selectedTonieFileLabel,
                                exportTonieAudioButton);
                ExpandableSubActions prepAudioPane = new ExpandableSubActions(
                                "Only prep audio", selectAudioFolderButton, selectedAudioFolderLabel,
                                convertAudioButton);
                HBox minimumLengthControl = new HBox(5, minimumLengthField, new Label("min"));
                minimumLengthControl.setAlignment(Pos.CENTER_LEFT);
                Label outputFormatLabel = new Label("Output Format");
                outputFormatLabel.setStyle("-fx-font-weight: bold;");
                Label minimumLengthLabel = new Label("Minimum Length");
                minimumLengthLabel.setStyle("-fx-font-weight: bold;");
                GridPane audioMergeControls = new GridPane();
                audioMergeControls.setHgap(10);
                audioMergeControls.setVgap(5);
                audioMergeControls.add(selectAudioMergeFolderButton, 0, 0);
                audioMergeControls.add(selectedAudioMergeFolderLabel, 1, 0);
                audioMergeControls.add(mergeAudiosButton, 2, 0);
                audioMergeControls.add(outputFormatLabel, 0, 1);
                audioMergeControls.add(minimumLengthLabel, 1, 1);
                audioMergeControls.add(outputFormatComboBox, 0, 2);
                audioMergeControls.add(minimumLengthControl, 1, 2);
                GridPane.setMargin(outputFormatLabel, new Insets(5, 0, 0, 0));
                GridPane.setMargin(minimumLengthLabel, new Insets(5, 0, 0, 0));
                GridPane.setHgrow(selectedAudioMergeFolderLabel, Priority.ALWAYS);
                audioMergeControls.setMaxWidth(Double.MAX_VALUE);
                ExpandableSubActions combineAudiosPane = new ExpandableSubActions("Only combine audios",
                                audioMergeControls);
                ExpandableSubActions createYamlPane = new ExpandableSubActions(
                                "Only create YAML", selectAlbumFolderButton, selectedAlbumFolderLabel,
                                createYamlButton);
                ExpandableSubActions chapterOidSettingsPane = new ExpandableSubActions("OID settings",
                                oidSettingsFields);
                ExpandableSubActions createGmePane = new ExpandableSubActions(
                                "Only create GME", selectYamlFileButton, selectedYamlFileLabel,
                                createGmeButton);
                ExpandableSubActions createOidTablePane = new ExpandableSubActions(
                                "Only create OID table", selectOidTableFolderButton, selectedOidTableFolderLabel,
                                createOidTableButton);
                ExpandableSubActions createOidRangeTablePane = new ExpandableSubActions(
                                "Create PDF with many startcodes", selectOidRangeTableDirectoryButton,
                                selectedOidRangeTableDirectoryLabel, createOidRangeTableButton, oidRangeFields);
                ExpandableSubActions listGmeProductIdsPane = new ExpandableSubActions(
                                "List GME Product IDs", selectGmeFolderButton, selectedGmeFolderLabel,
                                listGmeProductIdsButton, productIdTable, listGmeProductIdsSpinner);
                Accordion workflowPanes = new Accordion(chapterOidSettingsPane, exportToniePane, prepAudioPane,
                                combineAudiosPane,
                                createYamlPane, createGmePane, createOidTablePane, listGmeProductIdsPane);
                List<TitledPane> singleDirectoryPanes = List.of(chapterOidSettingsPane, exportToniePane, prepAudioPane,
                                combineAudiosPane,
                                createYamlPane, createGmePane, createOidTablePane, listGmeProductIdsPane);

                workflowStatusBar = new StatusBar();
                workflowStatusBar.setMinHeight(40);
                workflowStatusBar.setPrefHeight(40);
                workflowStatusBar.setMaxHeight(40);
                workflowStatusBar.setProgress(0);
                workflowStatusBar.setText("");
                workflowStatusBar.setStyle(STATUS_BASE_STYLE);
                ToggleButton singleDirectoryButton = new ToggleButton("Single directory");
                ToggleButton manyDirectoriesButton = new ToggleButton("Many directories");
                singleDirectoryButton.getStyleClass().addAll("directory-mode-toggle", "directory-mode-toggle-left");
                manyDirectoriesButton.getStyleClass().addAll("directory-mode-toggle", "directory-mode-toggle-right");
                ToggleGroup directoryModeGroup = new ToggleGroup();
                singleDirectoryButton.setToggleGroup(directoryModeGroup);
                manyDirectoriesButton.setToggleGroup(directoryModeGroup);
                singleDirectoryButton.setSelected(true);
                HBox directoryModeButtons = new HBox(0, singleDirectoryButton, manyDirectoriesButton);
                directoryModeButtons.getStyleClass().add("directory-mode-tabs");
                directoryModeButtons.setMaxWidth(Double.MAX_VALUE);
                singleDirectoryButton.setMaxWidth(Double.MAX_VALUE);
                manyDirectoriesButton.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(singleDirectoryButton, Priority.ALWAYS);
                HBox.setHgrow(manyDirectoriesButton, Priority.ALWAYS);
                manyDirectoryResults = new VBox(4);
                directoryModeGroup.selectedToggleProperty().addListener((observable, oldValue, selectedToggle) -> {
                        if (selectedToggle == null) {
                                singleDirectoryButton.setSelected(true);
                                return;
                        }
                        boolean useManyDirectories = selectedToggle == manyDirectoriesButton;
                        updateDirectoryMode(useManyDirectories, productNameControl, productIdLabel,
                                        selectDirectoryControl,
                                        productDetailsGroup, runButton, workflowPanes, singleDirectoryPanes,
                                        List.<TitledPane>of(chapterOidSettingsPane, createOidRangeTablePane,
                                                        listGmeProductIdsPane));
                });
                VBox buttonBox = new VBox(10, directoryModeButtons, rowInput, runRow, workflowStatusBar, workflowPanes,
                                manyDirectoryResults);

                outputArea = new TextArea();
                outputArea.setEditable(false);

                TitledPane logPane = new TitledPane("Logs", outputArea);
                logPane.setExpanded(false);
                logPane.setMaxHeight(Double.MAX_VALUE);

                BorderPane root = new BorderPane();
                root.setPadding(new Insets(10));
                VBox topContent = new VBox(10, title, buttonBox);
                ScrollPane contentScrollPane = new ScrollPane(topContent);
                contentScrollPane.setFitToWidth(true);
                contentScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                contentScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                contentScrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
                root.setCenter(contentScrollPane);
                root.setBottom(logPane);
                BorderPane.setMargin(contentScrollPane, new Insets(0, 0, 10, 0));

                runButton.setOnAction(e -> runTool());
                cancelButton.setOnAction(e -> cancelRunningTasks());
                taskManager.setOnRunningChanged(isRunning -> javafx.application.Platform.runLater(() -> {
                        cancelButton.setVisible(isRunning);
                        cancelButton.setManaged(isRunning);
                }));

                Scene scene = new Scene(root, 500, 600);
                scene.getStylesheets().add(MainWindow.class.getResource("orange-theme.css").toExternalForm());
                stage.setTitle("TTTool Album Creator");
                stage.getIcons().add(new Image(MainWindow.class.getResourceAsStream("images/tttonie_icon.png")));
                stage.setScene(scene);
                stage.show();
                appendProductIdToggle.setPrefWidth(appendProductIdToggle.minWidth(-1));

                javafx.application.Platform.runLater(root::requestFocus);
        }

        private void runTool() {
                if (manyDirectoriesMode) {
                        runToolForManyDirectories();
                        return;
                }
                File selectedFolder = rowConvertAudio.getSelectedAudioFolder();
                if (selectedFolder == null) {
                        log("Please select a folder first.");
                        setWorkflowStatus("Please select a folder first.");
                        return;
                }

                AlbumFolderWorkflowResolver.WorkflowResolution resolution = workflowResolver.resolve(selectedFolder);
                if (requiresProductId(resolution) && validationSupport.isInvalid()) {
                        validationSupport.initInitialDecoration();
                        log("Please enter a valid product ID.");
                        setWorkflowStatus("Please enter a valid product ID.");
                        return;
                }
                switch (resolution.workflow()) {
                        case EXPORT_TONIE_AUDIO -> {
                                File exportFolder;
                                try {
                                        exportFolder = tonieExportDestinationService.createExportFolder(
                                                        resolution.tonieFile(), getAlbumName());
                                } catch (java.io.IOException e) {
                                        log("Could not create Tonie export folder: " + e.getMessage());
                                        setWorkflowStatus("Tonie audio export failed.");
                                        return;
                                }
                                rowExportTonieAudio.runToolExportAudio(resolution.tonieFile(), exportFolder,
                                                exportedAlbumFolder -> {
                                                        rowConvertAudio.setSelectedAudioFolder(exportedAlbumFolder);
                                                        rowConvertAudio.runToolCopyAndConvertForExistingAlbum(
                                                                        exportedAlbumFolder,
                                                                        albumWorkflowContinuation::continueFromAudioFolder);
                                                });
                        }
                        case PROCESS_AUDIO -> rowConvertAudio
                                        .runToolCopyAndConvert(albumWorkflowContinuation::continueFromAudioFolder);
                        case EXISTING_ALBUM -> albumWorkflowContinuation.continueFromExistingAlbum(selectedFolder,
                                        resolution.yamlFile());
                        case UNSUPPORTED -> {
                                String logMessage = "Selected folder contains no Tonie file, MP3/OGG files, or audio directory.";
                                log(logMessage);
                                setWorkflowStatus("Could not run workflow: " + logMessage);
                        }
                }
        }

        private void updateDirectoryMode(boolean useManyDirectories, VBox productNameControl, Label productIdLabel,
                        VBox selectDirectoryControl, HBox productDetailsGroup, Button runButton,
                        Accordion workflowPanes, List<TitledPane> singleDirectoryPanes,
                        List<TitledPane> manyDirectoryPanes) {
                manyDirectoriesMode = useManyDirectories;
                if (!useManyDirectories) {
                        manyDirectoriesScriptCodeSettings = null;
                }
                productNameControl.setVisible(!useManyDirectories);
                productNameControl.setManaged(!useManyDirectories);
                productIdLabel.setText(useManyDirectories ? "Starting Product ID" : "Product ID");
                runButton.setText(useManyDirectories ? "Run tttool over many directories" : "Run tttool");
                HBox.setHgrow(selectDirectoryControl, useManyDirectories ? Priority.ALWAYS : Priority.NEVER);
                productDetailsGroup.setAlignment(Pos.BOTTOM_RIGHT);

                workflowPanes.getPanes().setAll(useManyDirectories
                                ? manyDirectoryPanes
                                : singleDirectoryPanes);
                workflowPanes.setExpandedPane(null);
                if (!useManyDirectories) {
                        manyDirectoryResults.getChildren().clear();
                }
        }

        private void runToolForManyDirectories() {
                File mainDirectory = rowConvertAudio.getSelectedAudioFolder();
                if (mainDirectory == null) {
                        log("Please select a folder first.");
                        setWorkflowStatus("Please select a folder first.");
                        return;
                }
                if (validationSupport.isInvalid()) {
                        validationSupport.initInitialDecoration();
                        log("Please enter a valid starting product ID.");
                        setWorkflowStatus("Please enter a valid starting product ID.");
                        return;
                }

                File[] children = mainDirectory.listFiles(File::isDirectory);
                if (children == null || children.length == 0) {
                        log("The selected directory contains no subdirectories.");
                        setWorkflowStatus("The selected directory contains no subdirectories.");
                        return;
                }
                List<File> folders = Arrays.stream(children)
                                .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                                .toList();
                boolean containsTonieFile = folders.stream()
                                .map(workflowResolver::resolve)
                                .anyMatch(resolution -> resolution
                                                .workflow() == AlbumFolderWorkflowResolver.Workflow.EXPORT_TONIE_AUDIO);
                if (containsTonieFile && !rowExportTonieAudio.confirmExportOnce()) {
                        setWorkflowStatus("Tonie audio export cancelled.");
                        return;
                }
                try {
                        manyDirectoriesScriptCodeSettings = getScriptCodeSettings();
                } catch (IllegalArgumentException e) {
                        log(e.getMessage());
                        setWorkflowStatus("Please enter valid chapter OID settings.");
                        return;
                }
                int startingProductId = Integer.parseInt(productIdField.getText());
                if ((long) startingProductId + folders.size() - 1 > Integer.MAX_VALUE) {
                        log("The product ID range exceeds " + Integer.MAX_VALUE + ".");
                        setWorkflowStatus("The product ID range is too large.");
                        return;
                }

                manyDirectoryResults.getChildren().clear();
                manyDirectoriesCancelled = false;
                manyDirectoriesStartingProductId = startingProductId;
                runNextDirectory(mainDirectory, folders, 0, startingProductId);
        }

        private void runNextDirectory(File mainDirectory, List<File> folders, int folderIndex, int productId) {
                if (manyDirectoriesCancelled) {
                        setWorkflowStatus("Many-directories workflow cancelled.");
                        return;
                }
                if (folderIndex >= folders.size()) {
                        rowConvertAudio.setSelectedAudioFolder(mainDirectory);
                        createBatchOidTables(mainDirectory, folders.size());
                        return;
                }

                File folder = folders.get(folderIndex);
                productIdField.setText(Integer.toString(productId));
                rowConvertAudio.setSelectedAudioFolder(folder);
                AlbumFolderWorkflowResolver.WorkflowResolution resolution = workflowResolver.resolve(folder);
                if (resolution.workflow() == AlbumFolderWorkflowResolver.Workflow.UNSUPPORTED) {
                        String message = "Skipped " + folder.getName()
                                        + ": no Tonie file, MP3/OGG files, or audio directory found.";
                        log(message);
                        setWorkflowStatus(message);
                        runNextDirectory(mainDirectory, folders, folderIndex + 1, productId + 1);
                        return;
                }

                String albumName = getAlbumName().isBlank() ? "tttoolAlbum" : getAlbumName();
                runWorkflowForDirectory(folder, resolution, () -> copyGmeFilesAndContinue(mainDirectory, folder,
                                albumName, productId, folders, folderIndex),
                                failure -> continueAfterDirectoryFailure(mainDirectory, folders, folderIndex, productId,
                                                folder, failure));
        }

        private void runWorkflowForDirectory(File folder, AlbumFolderWorkflowResolver.WorkflowResolution resolution,
                        Runnable onComplete, Consumer<String> onFailure) {
                switch (resolution.workflow()) {
                        case EXPORT_TONIE_AUDIO -> {
                                File exportFolder;
                                try {
                                        exportFolder = tonieExportDestinationService.createExportFolder(
                                                        resolution.tonieFile(), getAlbumName());
                                } catch (IOException e) {
                                        log("Could not create Tonie export folder: " + e.getMessage());
                                        setWorkflowStatus("Tonie audio export failed.");
                                        onFailure.accept("Tonie audio export failed.");
                                        return;
                                }
                                rowExportTonieAudio.runToolExportAudio(resolution.tonieFile(), exportFolder,
                                                exportedAlbumFolder -> {
                                                        rowConvertAudio.setSelectedAudioFolder(exportedAlbumFolder);
                                                        rowConvertAudio.runToolCopyAndConvertForExistingAlbum(
                                                                        exportedAlbumFolder,
                                                                        audioFolder -> albumWorkflowContinuation
                                                                                        .continueFromAudioFolder(
                                                                                                        audioFolder,
                                                                                                        onComplete,
                                                                                                        onFailure),
                                                                        onFailure);
                                                }, onFailure, false);
                        }
                        case PROCESS_AUDIO -> rowConvertAudio.runToolCopyAndConvert(
                                        audioFolder -> albumWorkflowContinuation.continueFromAudioFolder(audioFolder,
                                                        onComplete, onFailure),
                                        onFailure);
                        case EXISTING_ALBUM -> albumWorkflowContinuation.continueFromExistingAlbum(folder,
                                        resolution.yamlFile(), onComplete, onFailure);
                        case UNSUPPORTED -> throw new IllegalArgumentException("Unsupported folder workflow");
                }
        }

        private void copyGmeFilesAndContinue(File mainDirectory, File sourceDirectory, String albumName, int productId,
                        List<File> folders, int folderIndex) {
                taskManager.start("copy-gme-files", () -> {
                        try (Stream<java.nio.file.Path> files = Files.walk(sourceDirectory.toPath())) {
                                List<java.nio.file.Path> gmeFiles = files
                                                .filter(Files::isRegularFile)
                                                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                                                                .endsWith(".gme"))
                                                .toList();
                                for (java.nio.file.Path gmeFile : gmeFiles) {
                                        Files.copy(gmeFile, mainDirectory.toPath().resolve(gmeFile.getFileName()),
                                                        StandardCopyOption.REPLACE_EXISTING);
                                }
                                javafx.application.Platform.runLater(() -> {
                                        Label success = new Label("Done! Created " + albumName + " with Product ID "
                                                        + productId);
                                        success.setStyle(STATUS_SUCCESS_STYLE);
                                        success.setWrapText(true);
                                        manyDirectoryResults.getChildren().add(success);
                                        setWorkflowStatus(success.getText());
                                        runNextDirectory(mainDirectory, folders, folderIndex + 1, productId + 1);
                                });
                        } catch (IOException e) {
                                log("Could not copy GME file(s) from " + sourceDirectory.getName() + ": "
                                                + e.getMessage());
                                setWorkflowStatus("Could not copy GME file(s).");
                                javafx.application.Platform.runLater(() -> continueAfterDirectoryFailure(mainDirectory,
                                                folders, folderIndex, productId, sourceDirectory,
                                                "Could not copy GME file(s)."));
                        }
                });
        }

        private void continueAfterDirectoryFailure(File mainDirectory, List<File> folders, int folderIndex,
                        int productId, File folder, String failure) {
                Label result = new Label("Failed " + folder.getName() + ": " + failure);
                result.setStyle(STATUS_ERROR_STYLE);
                result.setWrapText(true);
                manyDirectoryResults.getChildren().add(result);
                runNextDirectory(mainDirectory, folders, folderIndex + 1, productId + 1);
        }

        private void addOidRangeTableFailure(String failure) {
                Label result = new Label("Failed to create OID range table: " + failure);
                result.setStyle(STATUS_ERROR_STYLE);
                result.setWrapText(true);
                manyDirectoryResults.getChildren().add(result);
        }

        private void createBatchOidTables(File mainDirectory, int albumCount) {
                int startOid = manyDirectoriesStartingProductId;
                int endOid = startOid + albumCount - 1;
                GenerateYamlService.ScriptCodeSettings scriptCodeSettings = manyDirectoriesScriptCodeSettings;
                rowCreateOidRangeTable.runToolCreateOidRangeTable(mainDirectory, startOid, endOid,
                                "start-oid-table.pdf",
                                () -> rowCreateOidRangeTable.runToolCreateOidRangeTable(mainDirectory,
                                                scriptCodeSettings.firstScriptCode(),
                                                scriptCodeSettings.firstScriptCode()
                                                                + scriptCodeSettings.scriptCodeTracks() - 1,
                                                "chapters-oid-table.pdf",
                                                () -> {
                                                        manyDirectoriesScriptCodeSettings = null;
                                                        setWorkflowStatus("Done! All directories processed.");
                                                },
                                                this::addOidRangeTableFailure),
                                this::addOidRangeTableFailure);
        }

        private TableView<RowListGmeProductIds.ProductIdTableRow> createProductIdTable(
                        ObservableList<RowListGmeProductIds.ProductIdTableRow> productIdRows) {
                TableColumn<RowListGmeProductIds.ProductIdTableRow, String> gmeColumn = new TableColumn<>("gme");
                gmeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().gme()));
                gmeColumn.setPrefWidth(300);

                TableColumn<RowListGmeProductIds.ProductIdTableRow, Integer> productIdColumn = new TableColumn<>(
                                "Product ID");
                productIdColumn.setCellValueFactory(
                                cell -> new SimpleIntegerProperty(cell.getValue().productId()).asObject());
                productIdColumn.setPrefWidth(130);

                TableView<RowListGmeProductIds.ProductIdTableRow> table = new TableView<>(productIdRows);
                table.getColumns().add(gmeColumn);
                table.getColumns().add(productIdColumn);
                table.getSortOrder().add(gmeColumn);
                table.setPlaceholder(new Label("No Product IDs listed yet"));
                table.setPrefHeight(200);
                table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_NEXT_COLUMN);
                return table;
        }

        private String getAlbumName() {
                return productNameField.getText().trim();
        }

        private String getMetadataName() {
                String productName = getAlbumName();
                if (!appendProductIdToggle.isSelected()) {
                        return productName;
                }

                String productId = productIdField.getText().trim();
                if (productId.isEmpty()) {
                        return productName;
                }
                return (productName.isEmpty() ? "tttoolAlbum" : productName) + "_" + productId;
        }

        private static boolean isValidProductId(String productId) {
                if (productId == null || productId.isBlank()) {
                        return false;
                }
                try {
                        Integer.parseInt(productId);
                        return true;
                } catch (NumberFormatException e) {
                        return false;
                }
        }

        private static TextField createOidField(String promptText) {
                TextField field = new TextField();
                field.setPromptText(promptText);
                field.setPrefColumnCount(6);
                field.setTextFormatter(new TextFormatter<>(
                                change -> change.getControlNewText().matches("\\d*") ? change : null));
                return field;
        }

        private void showMergeAudioResult(String message) {
                Label result = new Label(message);
                result.setStyle(STATUS_SUCCESS_STYLE);
                result.setWrapText(true);
                manyDirectoryResults.getChildren().setAll(result);
        }

        private static HBox createSettingsInputRow(String labelText, TextField field) {
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                HBox row = new HBox(10, new Label(labelText), spacer, field);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setMaxWidth(Double.MAX_VALUE);
                return row;
        }

        private GenerateYamlService.ScriptCodeSettings getScriptCodeSettings() {
                String firstScriptCode = firstScriptCodeField.getText();
                String scriptCodeTracks = scriptCodeTracksField.getText();
                if (firstScriptCode.isBlank() || scriptCodeTracks.isBlank()) {
                        throw new IllegalArgumentException("Please enter both chapter OID settings.");
                }
                try {
                        return new GenerateYamlService.ScriptCodeSettings(Integer.parseInt(firstScriptCode),
                                        Integer.parseInt(scriptCodeTracks));
                } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Please enter valid chapter OID settings.", e);
                }
        }

        private GenerateYamlService.ScriptCodeSettings getCurrentScriptCodeSettings() {
                return manyDirectoriesMode && manyDirectoriesScriptCodeSettings != null
                                ? manyDirectoriesScriptCodeSettings
                                : getScriptCodeSettings();
        }

        private TttoolService.OidTableSettings getOidTableSettings() {
                try {
                        return new TttoolService.OidTableSettings(
                                        Integer.parseInt(oidTableDpiField.getText()),
                                        Integer.parseInt(oidTablePixelSizeField.getText()),
                                        Integer.parseInt(oidTableCodeDimField.getText()));
                } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Please enter valid OID table PDF settings.", e);
                }
        }

        private static boolean requiresProductId(AlbumFolderWorkflowResolver.WorkflowResolution resolution) {
                return resolution.workflow() == AlbumFolderWorkflowResolver.Workflow.EXPORT_TONIE_AUDIO
                                || resolution.workflow() == AlbumFolderWorkflowResolver.Workflow.PROCESS_AUDIO
                                || (resolution.workflow() == AlbumFolderWorkflowResolver.Workflow.EXISTING_ALBUM
                                                && resolution.yamlFile() == null);
        }

        private static String albumNameFromFolderName(String folderName) {
                if (folderName.endsWith("_album")) {
                        return folderName.substring(0, folderName.length() - "_album".length());
                }
                if (folderName.endsWith("_export")) {
                        return folderName.substring(0, folderName.length() - "_export".length());
                }
                return folderName;
        }

        private void log(String message) {
                Runnable appendMessage = () -> outputArea.appendText(message + "\n");
                if (javafx.application.Platform.isFxApplicationThread()) {
                        appendMessage.run();
                } else {
                        javafx.application.Platform.runLater(appendMessage);
                }
        }

        private void cancelRunningTasks() {
                if (manyDirectoriesMode) {
                        manyDirectoriesCancelled = true;
                }
                taskManager.cancelAll();
                log("Cancellation requested.");
        }

        private void setWorkflowStatus(String message) {
                Runnable updateStatus = () -> {
                        boolean isRunning = message != null && message.endsWith("...");
                        workflowStatusBar.setProgress(isRunning ? -1 : 0);
                        workflowStatusBar.setText(message == null ? "" : message);
                        workflowStatusBar.setStyle(statusStyle(message, isRunning));
                };
                if (javafx.application.Platform.isFxApplicationThread()) {
                        updateStatus.run();
                } else {
                        javafx.application.Platform.runLater(updateStatus);
                }
        }

        private static String statusStyle(String message, boolean isRunning) {
                if (isRunning || message == null) {
                        return STATUS_BASE_STYLE;
                }

                String normalizedMessage = message.toLowerCase(Locale.ROOT);
                if (normalizedMessage.contains("failed")
                                || normalizedMessage.contains("could not")
                                || normalizedMessage.contains("please ")
                                || normalizedMessage.contains("cancelled")
                                || normalizedMessage.contains("skipped")) {
                        return STATUS_ERROR_STYLE;
                }
                return normalizedMessage.contains("done!") ? STATUS_SUCCESS_STYLE : STATUS_BASE_STYLE;
        }
}
