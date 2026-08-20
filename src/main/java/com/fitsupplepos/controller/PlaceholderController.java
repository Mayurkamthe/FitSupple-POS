package com.fitsupplepos.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class PlaceholderController {

    @FXML private Label titleLabel;
    @FXML private Label messageLabel;

    public void configure(String moduleName) {
        titleLabel.setText(moduleName);
        messageLabel.setText(moduleName + " is scheduled for the next build phase and is not implemented yet. "
                + "The Dashboard and Owner Login are fully functional against the live SQLite database.");
    }
}
