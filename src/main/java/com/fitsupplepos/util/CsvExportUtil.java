package com.fitsupplepos.util;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Generic CSV exporter that works against any TableView by reading its column headers
 * and cell values directly — no per-report export code needed. CSV opens natively in
 * Excel, which covers the "Export to Excel" requirement without adding a POI dependency
 * that isn't in the approved tech stack.
 */
public final class CsvExportUtil {

    private CsvExportUtil() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> void exportToCsv(TableView<T> table, File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            StringBuilder header = new StringBuilder();
            for (int i = 0; i < table.getColumns().size(); i++) {
                if (i > 0) header.append(',');
                header.append(escape(table.getColumns().get(i).getText()));
            }
            writer.println(header);

            for (T item : table.getItems()) {
                StringBuilder row = new StringBuilder();
                for (int i = 0; i < table.getColumns().size(); i++) {
                    if (i > 0) row.append(',');
                    TableColumn col = table.getColumns().get(i);
                    Object value = col.getCellObservableValue(item) != null
                            ? ((javafx.beans.value.ObservableValue) col.getCellObservableValue(item)).getValue()
                            : null;
                    row.append(escape(value == null ? "" : value.toString()));
                }
                writer.println(row);
            }
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n");
        String escaped = value.replace("\"", "\"\"");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
    }
}
