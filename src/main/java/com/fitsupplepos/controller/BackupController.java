package com.fitsupplepos.controller;

import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.service.BackupService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class BackupController {

    private static final Logger log = LoggerFactory.getLogger(BackupController.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

    @FXML private Label statusLabel;
    @FXML private TableView<File> backupTable;
    @FXML private TableColumn<File, String> fileNameCol;
    @FXML private TableColumn<File, String> dateCol;
    @FXML private TableColumn<File, String> sizeCol;

    private final BackupService backupService = new BackupService();

    @FXML
    public void initialize() {
        fileNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(
                Instant.ofEpochMilli(c.getValue().lastModified()).atZone(ZoneId.systemDefault()).format(DATE_FMT)));
        sizeCol.setCellValueFactory(c -> new SimpleStringProperty(formatSize(c.getValue().length())));

        refreshList();
    }

    private void refreshList() {
        List<File> backups = backupService.listBackups();
        backupTable.setItems(FXCollections.observableArrayList(backups));
    }

    @FXML
    private void handleRefresh() {
        refreshList();
    }

    @FXML
    private void handleBackupNow() {
        try {
            File backup = backupService.createBackup();
            statusLabel.setText("Backup created: " + backup.getName());
            refreshList();
        } catch (BusinessException e) {
            statusLabel.setText("Error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Backup failed", e);
            statusLabel.setText("Unexpected error while creating backup.");
        }
    }

    @FXML
    private void handleRestore() {
        File selected = backupTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a backup from the list first.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Restore from \"" + selected.getName() + "\"?\n\n"
                        + "A safety backup of the CURRENT database will be created automatically before restoring. "
                        + "This action will replace all current data.",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Restore");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    backupService.restoreBackup(selected);
                    statusLabel.setText("Database restored from " + selected.getName()
                            + ". A safety backup of the previous data was created automatically.");
                    refreshList();
                } catch (BusinessException e) {
                    statusLabel.setText("Error: " + e.getMessage());
                } catch (Exception e) {
                    log.error("Restore failed", e);
                    statusLabel.setText("Unexpected error while restoring.");
                }
            }
        });
    }

    @FXML
    private void handleExportData() {
        try {
            File exportDir = backupService.exportData();
            statusLabel.setText("Data exported to: " + exportDir.getAbsolutePath());
        } catch (BusinessException e) {
            statusLabel.setText("Error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Export failed", e);
            statusLabel.setText("Unexpected error while exporting data.");
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
