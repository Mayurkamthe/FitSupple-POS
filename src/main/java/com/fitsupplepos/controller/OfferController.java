package com.fitsupplepos.controller;

import com.fitsupplepos.model.Offer;
import com.fitsupplepos.service.OfferService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class OfferController {

    private static final Logger log = LoggerFactory.getLogger(OfferController.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    @FXML private TableView<Offer> offerTable;
    @FXML private TableColumn<Offer, String> nameCol;
    @FXML private TableColumn<Offer, String> typeCol;
    @FXML private TableColumn<Offer, String> targetCol;
    @FXML private TableColumn<Offer, String> valueCol;
    @FXML private TableColumn<Offer, String> periodCol;
    @FXML private TableColumn<Offer, String> statusCol;
    @FXML private TableColumn<Offer, Void> actionsCol;

    private final OfferService offerService = new OfferService();

    @FXML
    public void initialize() {
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getOfferType().name().replace('_', ' ')));
        targetCol.setCellValueFactory(c -> new SimpleStringProperty(target(c.getValue())));
        valueCol.setCellValueFactory(c -> new SimpleStringProperty(value(c.getValue())));
        periodCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStartDate().format(DATE_FMT) + " → " + c.getValue().getEndDate().format(DATE_FMT)));
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isCurrentlyValid() ? "Active" : "Inactive"));

        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button deactivateBtn = new Button("Deactivate");
            private final HBox box = new HBox(6, editBtn, deactivateBtn);
            {
                editBtn.getStyleClass().add("btn-secondary");
                deactivateBtn.getStyleClass().add("btn-secondary");
                editBtn.setOnAction(e -> openEditDialog(getTableView().getItems().get(getIndex())));
                deactivateBtn.setOnAction(e -> {
                    Offer offer = getTableView().getItems().get(getIndex());
                    offerService.deactivate(offer.getId());
                    loadData();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        loadData();
    }

    private String target(Offer offer) {
        return switch (offer.getOfferType()) {
            case PERCENTAGE_DISCOUNT, FIXED_DISCOUNT -> switch (offer.getScope()) {
                case PRODUCT -> offer.getProduct() != null ? offer.getProduct().getProductName() : "-";
                case CATEGORY -> offer.getCategory() != null ? offer.getCategory().getDisplayName() : "-";
                case ALL -> "All Products";
                case null -> "-";
            };
            case COUPON -> "Coupon: " + offer.getCouponCode();
            case BUY_X_GET_Y -> (offer.getBuyProduct() != null ? offer.getBuyProduct().getProductName() : "-")
                    + " → " + (offer.getGetProduct() != null ? offer.getGetProduct().getProductName() : "-");
            case CUSTOMER_SPECIFIC -> offer.getCustomer() != null ? offer.getCustomer().getName() : "-";
        };
    }

    private String value(Offer offer) {
        return switch (offer.getOfferType()) {
            case PERCENTAGE_DISCOUNT -> offer.getDiscountPercent() + "%";
            case FIXED_DISCOUNT -> "₹" + offer.getDiscountFixed();
            case COUPON -> offer.getDiscountPercent() != null ? offer.getDiscountPercent() + "%" : "₹" + offer.getDiscountFixed();
            case BUY_X_GET_Y -> "Buy " + offer.getBuyQuantity() + " Get " + offer.getGetQuantity();
            case CUSTOMER_SPECIFIC -> offer.getDiscountPercent() != null ? offer.getDiscountPercent() + "%" : "₹" + offer.getDiscountFixed();
        };
    }

    private void loadData() {
        List<Offer> offers = offerService.listAll();
        offerTable.setItems(FXCollections.observableArrayList(offers));
    }

    @FXML
    private void handleAdd() {
        openEditDialog(null);
    }

    private void openEditDialog(Offer offer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/offer_edit.fxml"));
            Parent root = loader.load();
            OfferEditController controller = loader.getController();
            controller.configure(offer);
            controller.setOnSaved(this::loadData);

            Stage dialog = new Stage();
            dialog.setTitle(offer == null ? "Add Offer" : "Edit Offer");
            dialog.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root, 720, 780);
            var css = getClass().getResource("/css/theme.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            scene.setOnKeyPressed(ke -> { if (ke.getCode() == javafx.scene.input.KeyCode.ESCAPE) dialog.close(); });
            dialog.setScene(scene);
            dialog.showAndWait();
        } catch (Exception e) {
            log.error("Failed to open offer edit dialog", e);
            new Alert(Alert.AlertType.ERROR, "Could not open the offer form.").showAndWait();
        }
    }
}
