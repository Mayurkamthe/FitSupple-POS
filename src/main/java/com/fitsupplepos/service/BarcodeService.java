package com.fitsupplepos.service;

import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Product;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Generates Code128 barcode images for products (using their SKU/barcode value) and
 * printable label sheets. USB barcode scanners themselves need no special integration —
 * they act as keyboard input, which the POS Billing barcode field already accepts.
 */
public class BarcodeService {

    public BufferedImage generateBarcodeImage(String data, int width, int height) {
        if (data == null || data.isBlank()) {
            throw new BusinessException("This product has no SKU or barcode value to encode.");
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 4);
            BitMatrix matrix = new Code128Writer().encode(data, BarcodeFormat.CODE_128, width, height, hints);
            return MatrixToImageWriter.toBufferedImage(matrix);
        } catch (WriterException e) {
            throw new BusinessException("Could not generate barcode: " + e.getMessage());
        }
    }

    /** Generates a simple grid label sheet (barcode + product name + price) ready to print and cut. */
    public void generateLabelSheet(File outputFile, List<Product> products, int copiesPerProduct) throws IOException {
        Document document = new Document(PageSize.A4, 20, 20, 20, 20);
        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            PdfWriter.getInstance(document, out);
            document.open();

            Font nameFont = new Font(Font.HELVETICA, 8, Font.BOLD);
            Font priceFont = new Font(Font.HELVETICA, 8, Font.NORMAL);

            com.lowagie.text.pdf.PdfPTable grid = new com.lowagie.text.pdf.PdfPTable(3);
            grid.setWidthPercentage(100);

            for (Product product : products) {
                String code = product.getBarcode() != null && !product.getBarcode().isBlank()
                        ? product.getBarcode() : product.getSku();
                if (code == null || code.isBlank()) continue;

                for (int i = 0; i < copiesPerProduct; i++) {
                    BufferedImage barcodeImg = generateBarcodeImage(code, 220, 70);
                    ByteArrayOutputStream imgBytes = new ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(barcodeImg, "png", imgBytes);
                    Image pdfImage = Image.getInstance(imgBytes.toByteArray());
                    pdfImage.scaleToFit(150, 50);

                    com.lowagie.text.pdf.PdfPCell cell = new com.lowagie.text.pdf.PdfPCell();
                    cell.setPadding(6);
                    cell.addElement(new Paragraph(product.getProductName(), nameFont));
                    cell.addElement(pdfImage);
                    cell.addElement(new Paragraph("MRP: Rs " + product.getMrp(), priceFont));
                    grid.addCell(cell);
                }
            }
            document.add(grid);
        } finally {
            if (document.isOpen()) document.close();
        }
    }
}
