package com.fitsupplepos.service;

import com.fitsupplepos.config.AppPaths;
import com.fitsupplepos.model.*;
import com.fitsupplepos.model.enums.BillingMode;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;

/**
 * Generates the customer-facing invoice as a PDF — both a full A4 tax invoice and a
 * narrow thermal-receipt-style layout — pulling shop branding from InvoiceSetting and
 * GSTIN from GstSetting so nothing about the shop is hardcoded in this class.
 */
public class InvoiceService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 20, Font.BOLD);
    private static final Font SHOP_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Color HEADER_BG = new Color(15, 31, 61);

    private final InvoiceSettingRepo invoiceSettingRepo = new InvoiceSettingRepo();

    /** Thin read helper so this service doesn't depend on the Purchase-flavoured InvoiceSettingDao. */
    private static class InvoiceSettingRepo {
        InvoiceSetting get() {
            return com.fitsupplepos.config.SessionManager.withSession(
                    session -> session.get(InvoiceSetting.class, 1L));
        }
        GstSetting getGst() {
            return com.fitsupplepos.config.SessionManager.withSession(
                    session -> session.get(GstSetting.class, 1L));
        }
    }

    /** Ensures the invoices output directory exists and returns it. */
    public File resolveInvoiceDir() throws IOException {
        File dir = AppPaths.invoiceDir().toFile();
        if (!dir.exists()) {
            Files.createDirectories(dir.toPath());
        }
        return dir;
    }

    public File generateA4Invoice(Sale sale) throws IOException {
        File dir = resolveInvoiceDir();
        File file = new File(dir, sale.getInvoiceNumber() + "_A4.pdf");
        writeA4Invoice(sale, file);
        return file;
    }

    public File generateThermalReceipt(Sale sale) throws IOException {
        File dir = resolveInvoiceDir();
        File file = new File(dir, sale.getInvoiceNumber() + "_Thermal.pdf");
        writeThermalReceipt(sale, file);
        return file;
    }

    private void writeA4Invoice(Sale sale, File file) throws IOException {
        InvoiceSetting invoiceSetting = invoiceSettingRepo.get();
        GstSetting gstSetting = invoiceSettingRepo.getGst();
        boolean gstMode = sale.getBillingMode() == BillingMode.GST;

        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        try (FileOutputStream out = new FileOutputStream(file)) {
            PdfWriter.getInstance(document, out);
            document.open();

            if (invoiceSetting != null && invoiceSetting.getLogoPath() != null && !invoiceSetting.getLogoPath().isBlank()) {
                try {
                    Image logo = Image.getInstance(invoiceSetting.getLogoPath());
                    logo.scaleToFit(90, 90);
                    document.add(logo);
                } catch (Exception ignored) {
                    // Logo optional — invoice still generates without it.
                }
            }

            Paragraph shopName = new Paragraph(
                    invoiceSetting != null ? invoiceSetting.getShopName() : "FitSupple Nutrition Store", TITLE_FONT);
            document.add(shopName);

            if (invoiceSetting != null) {
                if (invoiceSetting.getAddress() != null) document.add(new Paragraph(invoiceSetting.getAddress(), SHOP_FONT));
                StringBuilder contact = new StringBuilder();
                if (invoiceSetting.getPhone() != null) contact.append("Phone: ").append(invoiceSetting.getPhone()).append("  ");
                if (invoiceSetting.getEmail() != null) contact.append("Email: ").append(invoiceSetting.getEmail());
                if (!contact.isEmpty()) document.add(new Paragraph(contact.toString(), SHOP_FONT));
            }
            if (gstMode && gstSetting != null && gstSetting.getGstin() != null) {
                document.add(new Paragraph("GSTIN: " + gstSetting.getGstin(), SHOP_FONT));
            }

            document.add(new Paragraph(" "));
            Paragraph invoiceTitle = new Paragraph(gstMode ? "TAX INVOICE" : "RETAIL INVOICE", BOLD_FONT);
            document.add(invoiceTitle);

            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingBefore(6);
            metaTable.setSpacingAfter(10);
            addPlainCell(metaTable, "Invoice #: " + sale.getInvoiceNumber());
            addPlainCell(metaTable, "Date: " + sale.getSaleDate().format(DATE_FMT));
            String customerLine = sale.getCustomer() != null
                    ? "Customer: " + sale.getCustomer().getName() + " (" + sale.getCustomer().getMobile() + ")"
                    : "Customer: Walk-in Customer";
            addPlainCell(metaTable, customerLine);
            String paymentLine = sale.getPayments().isEmpty() ? "Payment: -"
                    : "Payment: " + sale.getPayments().get(0).getMethod();
            addPlainCell(metaTable, paymentLine);
            document.add(metaTable);

            PdfPTable itemsTable = gstMode ? new PdfPTable(new float[]{26, 10, 8, 8, 10, 8, 8, 8, 14})
                    : new PdfPTable(new float[]{40, 15, 15, 15, 15});
            itemsTable.setWidthPercentage(100);

            if (gstMode) {
                addHeaderCell(itemsTable, "Product");
                addHeaderCell(itemsTable, "HSN");
                addHeaderCell(itemsTable, "Qty");
                addHeaderCell(itemsTable, "Rate");
                addHeaderCell(itemsTable, "Discount");
                addHeaderCell(itemsTable, "GST%");
                addHeaderCell(itemsTable, "CGST");
                addHeaderCell(itemsTable, "SGST");
                addHeaderCell(itemsTable, "Total");
            } else {
                addHeaderCell(itemsTable, "Product");
                addHeaderCell(itemsTable, "Qty");
                addHeaderCell(itemsTable, "Rate");
                addHeaderCell(itemsTable, "Discount");
                addHeaderCell(itemsTable, "Total");
            }

            for (SaleItem item : sale.getItems()) {
                if (gstMode) {
                    addCell(itemsTable, item.getProduct().getProductName());
                    addCell(itemsTable, nullSafe(item.getProduct().getHsnCode()));
                    addCell(itemsTable, String.valueOf(item.getQuantity()));
                    addCell(itemsTable, money(item.getRate()));
                    addCell(itemsTable, money(item.getDiscountAmount()));
                    addCell(itemsTable, item.getGstRate() + "%");
                    addCell(itemsTable, money(item.getCgstAmount()));
                    addCell(itemsTable, money(item.getSgstAmount()));
                    addCell(itemsTable, money(item.getLineTotal()));
                } else {
                    addCell(itemsTable, item.getProduct().getProductName());
                    addCell(itemsTable, String.valueOf(item.getQuantity()));
                    addCell(itemsTable, money(item.getRate()));
                    addCell(itemsTable, money(item.getDiscountAmount()));
                    addCell(itemsTable, money(item.getLineTotal()));
                }
            }
            document.add(itemsTable);

            document.add(new Paragraph(" "));
            PdfPTable totalsTable = new PdfPTable(2);
            totalsTable.setWidthPercentage(45);
            totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addTotalsRow(totalsTable, "Subtotal", money(sale.getSubtotal()));
            addTotalsRow(totalsTable, "Discount", money(sale.getDiscountAmount()));
            addTotalsRow(totalsTable, "Taxable Amount", money(sale.getTaxableAmount()));
            if (gstMode) {
                addTotalsRow(totalsTable, "CGST", money(sale.getCgstAmount()));
                addTotalsRow(totalsTable, "SGST", money(sale.getSgstAmount()));
                if (sale.getIgstAmount() != null && sale.getIgstAmount().signum() > 0) {
                    addTotalsRow(totalsTable, "IGST", money(sale.getIgstAmount()));
                }
            }
            addTotalsRow(totalsTable, "Round Off", money(sale.getRoundOff()));
            addTotalsRow(totalsTable, "Grand Total", money(sale.getGrandTotal()));
            document.add(totalsTable);

            document.add(new Paragraph(" "));
            String footerNote = invoiceSetting != null && invoiceSetting.getInvoiceFooterNote() != null
                    ? invoiceSetting.getInvoiceFooterNote() : "Thank you for shopping with us!";
            document.add(new Paragraph(footerNote, SHOP_FONT));
        } finally {
            if (document.isOpen()) document.close();
        }
    }

    private void writeThermalReceipt(Sale sale, File file) throws IOException {
        InvoiceSetting invoiceSetting = invoiceSettingRepo.get();
        boolean gstMode = sale.getBillingMode() == BillingMode.GST;

        // 80mm thermal width ≈ 226 points. Height grows with the page count OpenPDF needs.
        Rectangle pageSize = new Rectangle(226, 800);
        Document document = new Document(pageSize, 8, 8, 8, 8);
        try (FileOutputStream out = new FileOutputStream(file)) {
            PdfWriter.getInstance(document, out);
            document.open();

            Font small = new Font(Font.HELVETICA, 8, Font.NORMAL);
            Font smallBold = new Font(Font.HELVETICA, 9, Font.BOLD);

            Paragraph shopName = new Paragraph(
                    invoiceSetting != null ? invoiceSetting.getShopName() : "FitSupple Nutrition Store", smallBold);
            shopName.setAlignment(Element.ALIGN_CENTER);
            document.add(shopName);

            if (invoiceSetting != null && invoiceSetting.getPhone() != null) {
                Paragraph phone = new Paragraph(invoiceSetting.getPhone(), small);
                phone.setAlignment(Element.ALIGN_CENTER);
                document.add(phone);
            }

            document.add(new Paragraph(" ", small));
            document.add(new Paragraph("Invoice: " + sale.getInvoiceNumber(), small));
            document.add(new Paragraph("Date: " + sale.getSaleDate().format(DATE_FMT), small));
            document.add(new Paragraph("--------------------------------", small));

            for (SaleItem item : sale.getItems()) {
                document.add(new Paragraph(item.getProduct().getProductName(), small));
                String line = item.getQuantity() + " x " + money(item.getRate()) + " = " + money(item.getLineTotal());
                document.add(new Paragraph(line, small));
            }

            document.add(new Paragraph("--------------------------------", small));
            document.add(new Paragraph("Subtotal: " + money(sale.getSubtotal()), small));
            document.add(new Paragraph("Discount: " + money(sale.getDiscountAmount()), small));
            if (gstMode) {
                document.add(new Paragraph("CGST: " + money(sale.getCgstAmount()), small));
                document.add(new Paragraph("SGST: " + money(sale.getSgstAmount()), small));
            }
            document.add(new Paragraph("Round Off: " + money(sale.getRoundOff()), small));
            document.add(new Paragraph("TOTAL: " + money(sale.getGrandTotal()), smallBold));
            document.add(new Paragraph(" ", small));

            Paragraph thanks = new Paragraph("Thank you! Visit again.", small);
            thanks.setAlignment(Element.ALIGN_CENTER);
            document.add(thanks);
        } finally {
            if (document.isOpen()) document.close();
        }
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, HEADER_FONT));
        cell.setBackgroundColor(HEADER_BG);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, CELL_FONT));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addPlainCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, CELL_FONT));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(3);
        table.addCell(cell);
    }

    private void addTotalsRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, CELL_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, CELL_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private String money(java.math.BigDecimal value) {
        return "Rs " + (value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private String nullSafe(String s) {
        return s == null ? "-" : s;
    }
}
