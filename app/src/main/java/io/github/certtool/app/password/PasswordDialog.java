package io.github.certtool.app.password;

import java.util.Objects;
import java.util.function.Predicate;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Modal dialog that prompts the user for a single password. Returns the entered value as a
 * {@code char[]} that the caller MUST zero after use, or {@code null} if the user cancelled.
 *
 * <p>The dialog is intentionally minimal — no validation beyond an optional predicate the caller
 * supplies (e.g., "non-empty for BCFKS targets"). The dialog never logs the entered value.
 */
public final class PasswordDialog {

    private final String title;
    private final String header;
    private final Predicate<char[]> validator;
    private final Window owner;

    public PasswordDialog(String title, String header, Window owner) {
        this(title, header, owner, pwd -> true);
    }

    public PasswordDialog(
            String title, String header, Window owner, Predicate<char[]> validator) {
        this.title = Objects.requireNonNull(title, "title");
        this.header = Objects.requireNonNull(header, "header");
        this.owner = owner;
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /**
     * Shows the dialog and returns the entered password, or {@code null} if the user cancelled.
     * The dialog itself never logs or retains the password; the caller owns its lifecycle.
     */
    public char[] showAndWait() {
        Dialog<char[]> dlg = new Dialog<>();
        dlg.setTitle(title);
        dlg.setHeaderText(header);
        if (owner != null) {
            dlg.initOwner(owner);
        }
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                // PasswordField.getText() returns String; convert defensively.
                char[] result = pwdField.getText().toCharArray();
                // Wipe the PasswordField's internal buffer immediately.
                pwdField.clear();
                return validator.test(result) ? result : null;
            }
            return null;
        });

        Label prompt = new Label("Password:");
        pwdField = new PasswordField();
        pwdField.setPromptText("Enter password");
        pwdField.setPrefWidth(280);
        VBox root = new VBox(8, prompt, pwdField);
        dlg.getDialogPane().setContent(root);

        // Disable OK until non-empty (the most common "obvious" check; full validation runs on OK).
        dlg.getDialogPane().lookupButton(ButtonType.OK).setDisable(true);
        pwdField.textProperty().addListener((obs, oldV, newV) -> {
            dlg.getDialogPane().lookupButton(ButtonType.OK).setDisable(newV == null || newV.isEmpty());
        });

        // Returned value lives only in the resultConverter closure; we never store it on `this`.
        return dlg.showAndWait().orElse(null);
    }

    // Field declared here so the resultConverter lambda can reach it. Cleared on OK.
    private PasswordField pwdField;

    /** Convenience: shows a one-shot password dialog anchored to {@code owner}. */
    public static char[] show(Window owner, String title, String header) {
        return new PasswordDialog(title, header, owner).showAndWait();
    }
}