package com.fitsupplepos.controller;

import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Customer;
import com.fitsupplepos.model.WhatsAppCampaign;
import com.fitsupplepos.model.WhatsAppMessage;
import com.fitsupplepos.model.WhatsAppTemplate;
import com.fitsupplepos.model.enums.CampaignAudience;
import com.fitsupplepos.model.enums.WhatsAppMessagePurpose;
import com.fitsupplepos.model.enums.WhatsAppMessageStatus;
import com.fitsupplepos.service.CustomerService;
import com.fitsupplepos.service.WhatsAppCampaignService;
import com.fitsupplepos.service.WhatsAppService;
import com.fitsupplepos.service.WhatsAppTemplateService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class WhatsAppController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppController.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    @FXML private Label configStatusLabel;

    // Send tab
    @FXML private ComboBox<Customer> sendCustomerCombo;
    @FXML private ComboBox<WhatsAppTemplate> sendTemplateCombo;
    @FXML private TextArea sendTextArea;
    @FXML private Label sendErrorLabel;
    @FXML private TableView<WhatsAppMessage> messageTable;
    @FXML private TableColumn<WhatsAppMessage, String> msgDateCol;
    @FXML private TableColumn<WhatsAppMessage, String> msgRecipientCol;
    @FXML private TableColumn<WhatsAppMessage, String> msgTypeCol;
    @FXML private TableColumn<WhatsAppMessage, String> msgPurposeCol;
    @FXML private TableColumn<WhatsAppMessage, String> msgStatusCol;
    @FXML private TableColumn<WhatsAppMessage, String> msgErrorCol;

    // Templates tab
    @FXML private TableView<WhatsAppTemplate> templateTable;
    @FXML private TableColumn<WhatsAppTemplate, String> tplNameCol;
    @FXML private TableColumn<WhatsAppTemplate, String> tplLanguageCol;
    @FXML private TableColumn<WhatsAppTemplate, String> tplCategoryCol;
    @FXML private TableColumn<WhatsAppTemplate, String> tplBodyCol;
    @FXML private TableColumn<WhatsAppTemplate, Void> tplActionsCol;

    // Campaigns tab
    @FXML private TextField campaignNameField;
    @FXML private ComboBox<CampaignAudience> campaignAudienceCombo;
    @FXML private ComboBox<WhatsAppTemplate> campaignTemplateCombo;
    @FXML private TextArea campaignBodyField;
    @FXML private Label campaignErrorLabel;
    @FXML private TableView<WhatsAppCampaign> campaignTable;
    @FXML private TableColumn<WhatsAppCampaign, String> campNameCol;
    @FXML private TableColumn<WhatsAppCampaign, String> campAudienceCol;
    @FXML private TableColumn<WhatsAppCampaign, String> campStatusCol;
    @FXML private TableColumn<WhatsAppCampaign, String> campRecipientsCol;
    @FXML private TableColumn<WhatsAppCampaign, String> campSentCol;
    @FXML private TableColumn<WhatsAppCampaign, String> campFailedCol;
    @FXML private TableColumn<WhatsAppCampaign, Void> campActionsCol;

    private final WhatsAppService whatsAppService = new WhatsAppService();
    private final WhatsAppTemplateService templateService = new WhatsAppTemplateService();
    private final WhatsAppCampaignService campaignService = new WhatsAppCampaignService();
    private final CustomerService customerService = new CustomerService();

    @FXML
    public void initialize() {
        configStatusLabel.setText(whatsAppService.isConfigured()
                ? "WhatsApp Cloud API is configured and ready to send."
                : "WhatsApp is not configured yet. Set whatsapp.accessToken and whatsapp.phoneNumberId "
                        + "in the external config file (see Settings screen for the exact path), then restart the app.");

        setupSendTab();
        setupTemplatesTab();
        setupCampaignsTab();
    }

    // ---------------- Send tab ----------------

    private void setupSendTab() {
        sendCustomerCombo.setItems(FXCollections.observableArrayList(customerService.listAll()));
        sendCustomerCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Customer c) { return c == null ? "" : c.getName() + " (" + c.getMobile() + ")"; }
            @Override public Customer fromString(String s) { return null; }
        });

        sendTemplateCombo.setItems(FXCollections.observableArrayList(templateService.listActive()));
        sendTemplateCombo.setConverter(templateConverter());

        msgDateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSentAt().format(DATE_FMT)));
        msgRecipientCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRecipientNumber()));
        msgTypeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMessageType().name()));
        msgPurposeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPurpose().name()));
        msgStatusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().name()));
        msgErrorCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getErrorMessage() == null ? "" : c.getValue().getErrorMessage()));

        refreshMessages();
    }

    private StringConverter<WhatsAppTemplate> templateConverter() {
        return new StringConverter<>() {
            @Override public String toString(WhatsAppTemplate t) { return t == null ? "(none — use text)" : t.getTemplateName() + " [" + t.getLanguage() + "]"; }
            @Override public WhatsAppTemplate fromString(String s) { return null; }
        };
    }

    private void refreshMessages() {
        messageTable.setItems(FXCollections.observableArrayList(whatsAppService.recentMessages(100)));
    }

    @FXML
    private void handleClearTemplate() {
        sendTemplateCombo.setValue(null);
    }

    @FXML
    private void handleSend() {
        try {
            Customer customer = sendCustomerCombo.getValue();
            if (customer == null) {
                throw new BusinessException("Select a customer to send to.");
            }
            WhatsAppTemplate template = sendTemplateCombo.getValue();
            if (template != null) {
                whatsAppService.sendTemplate(customer, template, List.of(), WhatsAppMessagePurpose.OTHER);
            } else {
                String text = sendTextArea.getText();
                if (text == null || text.isBlank()) {
                    throw new BusinessException("Enter a message or select a template.");
                }
                whatsAppService.sendText(customer, text, WhatsAppMessagePurpose.OTHER);
            }
            sendTextArea.clear();
            hideError(sendErrorLabel);
            refreshMessages();
        } catch (BusinessException e) {
            showError(sendErrorLabel, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message", e);
            showError(sendErrorLabel, "Unexpected error while sending.");
        }
    }

    // ---------------- Templates tab ----------------

    private void setupTemplatesTab() {
        tplNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTemplateName()));
        tplLanguageCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getLanguage()));
        tplCategoryCol.setCellValueFactory(c -> new SimpleStringProperty(nullSafe(c.getValue().getCategory())));
        tplBodyCol.setCellValueFactory(c -> new SimpleStringProperty(nullSafe(c.getValue().getBodyText())));

        tplActionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final HBox box = new HBox(6, editBtn);
            {
                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setOnAction(e -> openTemplateDialog(getTableView().getItems().get(getIndex())));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        refreshTemplates();
    }

    private void refreshTemplates() {
        List<WhatsAppTemplate> templates = templateService.listActive();
        templateTable.setItems(FXCollections.observableArrayList(templates));
        sendTemplateCombo.setItems(FXCollections.observableArrayList(templates));
        campaignTemplateCombo.setItems(FXCollections.observableArrayList(templates));
    }

    @FXML
    private void handleAddTemplate() {
        openTemplateDialog(null);
    }

    private void openTemplateDialog(WhatsAppTemplate template) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/whatsapp_template_edit.fxml"));
            Parent root = loader.load();
            WhatsAppTemplateEditController controller = loader.getController();
            controller.configure(template);
            controller.setOnSaved(this::refreshTemplates);

            Stage dialog = new Stage();
            dialog.setTitle(template == null ? "Add Template" : "Edit Template");
            dialog.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root, 460, 560);
            var css = getClass().getResource("/css/theme.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            dialog.setScene(scene);
            scene.setOnKeyPressed(ke -> { if (ke.getCode() == javafx.scene.input.KeyCode.ESCAPE) dialog.close(); });
            dialog.showAndWait();
        } catch (Exception e) {
            log.error("Failed to open template dialog", e);
            new Alert(Alert.AlertType.ERROR, "Could not open the template form.").showAndWait();
        }
    }

    // ---------------- Campaigns tab ----------------

    private void setupCampaignsTab() {
        campaignAudienceCombo.setItems(FXCollections.observableArrayList(CampaignAudience.values()));
        campaignTemplateCombo.setConverter(templateConverter());

        campNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        campAudienceCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAudience().name()));
        campStatusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        campRecipientsCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getRecipients().size())));
        campSentCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(countStatus(c.getValue(), WhatsAppMessageStatus.SENT))));
        campFailedCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(countStatus(c.getValue(), WhatsAppMessageStatus.FAILED))));

        campActionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button sendBtn = new Button("Send");
            private final HBox box = new HBox(sendBtn);
            {
                sendBtn.getStyleClass().add("btn-primary");
                sendBtn.setOnAction(e -> {
                    WhatsAppCampaign campaign = getTableView().getItems().get(getIndex());
                    handleSendCampaign(campaign);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    WhatsAppCampaign campaign = getTableView().getItems().get(getIndex());
                    sendBtn.setDisable(!"DRAFT".equals(campaign.getStatus()));
                    setGraphic(box);
                }
            }
        });

        refreshCampaigns();
    }

    private long countStatus(WhatsAppCampaign campaign, WhatsAppMessageStatus status) {
        return campaign.getRecipients().stream().filter(r -> r.getStatus() == status).count();
    }

    private void refreshCampaigns() {
        campaignTable.setItems(FXCollections.observableArrayList(campaignService.listCampaigns()));
    }

    @FXML
    private void handleCreateCampaign() {
        try {
            String name = campaignNameField.getText();
            CampaignAudience audience = campaignAudienceCombo.getValue();
            if (audience == null) {
                throw new BusinessException("Select an audience.");
            }
            WhatsAppTemplate template = campaignTemplateCombo.getValue();
            String body = campaignBodyField.getText();

            campaignService.createCampaign(name, audience, template, body);
            campaignNameField.clear();
            campaignAudienceCombo.setValue(null);
            campaignTemplateCombo.setValue(null);
            campaignBodyField.clear();
            hideError(campaignErrorLabel);
            refreshCampaigns();
        } catch (BusinessException e) {
            showError(campaignErrorLabel, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create campaign", e);
            showError(campaignErrorLabel, "Unexpected error while creating the campaign.");
        }
    }

    private void handleSendCampaign(WhatsAppCampaign campaign) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Send \"" + campaign.getName() + "\" to " + campaign.getRecipients().size() + " opted-in customer(s) now?",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    campaignService.sendCampaign(campaign.getId());
                    refreshCampaigns();
                    refreshMessages();
                } catch (BusinessException e) {
                    new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
                } catch (Exception e) {
                    log.error("Failed to send campaign", e);
                    new Alert(Alert.AlertType.ERROR, "Unexpected error while sending the campaign.").showAndWait();
                }
            }
        });
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private void showError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void hideError(Label label) {
        label.setVisible(false);
        label.setManaged(false);
    }
}
