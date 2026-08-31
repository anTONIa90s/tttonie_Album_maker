package tiptoieditor.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * A collapsed-by-default titled pane with padded content.
 */
public class ExpandableSubActions extends TitledPane {

    private final Button loadButton;
    private final Label selectedItemLabel;
    private final Button actionButton;
    private final Node output;

    /** Creates a collapsed-by-default settings pane with padded content. */
    public ExpandableSubActions(String title, Node content) {
        super(title, createPaddedContent(content));
        this.loadButton = null;
        this.selectedItemLabel = null;
        this.actionButton = null;
        this.output = content;
        setExpanded(false);
    }

    public ExpandableSubActions(String title, Button loadButton, Label selectedItemLabel,
                                Button actionButton) {
        this(title, loadButton, selectedItemLabel, actionButton, null);
    }

    /**
     * Creates an action pane with an optional output row below its controls.
     */
    public ExpandableSubActions(String title, Button loadButton, Label selectedItemLabel,
                                Button actionButton, Node output) {
        this(title, loadButton, selectedItemLabel, actionButton, output, null);
    }

    /** Creates an action pane with an optional indicator immediately before its action button. */
    public ExpandableSubActions(String title, Button loadButton, Label selectedItemLabel,
                                Button actionButton, Node output, Node actionIndicator) {
        super(title, createPaddedContent(loadButton, selectedItemLabel, actionButton, output, actionIndicator));
        this.loadButton = loadButton;
        this.selectedItemLabel = selectedItemLabel;
        this.actionButton = actionButton;
        this.output = output;
        setExpanded(false);
    }

    private static VBox createPaddedContent(Button loadButton, Label selectedItemLabel,
                                            Button actionButton, Node output, Node actionIndicator) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controls = new HBox(10, loadButton, selectedItemLabel, spacer);
        if (actionIndicator != null) {
            controls.getChildren().add(actionIndicator);
        }
        controls.getChildren().add(actionButton);
        controls.setMaxWidth(Double.MAX_VALUE);

        VBox contentBox = new VBox(10, controls);
        if (output != null) {
            contentBox.getChildren().add(output);
        }
        contentBox.setPadding(new Insets(10));
        return contentBox;
    }

    private static VBox createPaddedContent(Node content) {
        VBox contentBox = new VBox(10, content);
        contentBox.setPadding(new Insets(10));
        return contentBox;
    }

    public Button getLoadButton() {
        return loadButton;
    }

    public Label getSelectedItemLabel() {
        return selectedItemLabel;
    }

    public Button getActionButton() {
        return actionButton;
    }

    public Node getOutput() {
        return output;
    }
}
