package com.staffdesk.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Excel (.xlsx) üretir. Metin hücreleri her zaman düz metin yazılır ({@code setCellValue(String)} formül
 * ayrıştırmaz); böylece "=..." ile başlayan kullanıcı girdisi Excel'de formül olarak çalışmaz.
 */
public final class XlsxWriter {

    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private XlsxWriter() {}

    public static byte[] build(String sheetName, List<String> headers, List<List<Object>> rows) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet(sheetName);
            CreationHelper helper = book.getCreationHelper();

            Font bold = book.createFont();
            bold.setBold(true);
            CellStyle headStyle = book.createCellStyle();
            headStyle.setFont(bold);
            CellStyle dateTime = book.createCellStyle();
            dateTime.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            CellStyle date = book.createCellStyle();
            date.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));

            Row head = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = head.createCell(c);
                cell.setCellValue(headers.get(c));
                cell.setCellStyle(headStyle);
            }
            int r = 1;
            for (List<Object> row : rows) {
                Row line = sheet.createRow(r++);
                for (int c = 0; c < row.size(); c++) {
                    Object v = row.get(c);
                    if (v == null) {
                        continue;
                    }
                    Cell cell = line.createCell(c);
                    if (v instanceof LocalDateTime t) {
                        cell.setCellValue(t);
                        cell.setCellStyle(dateTime);
                    } else if (v instanceof LocalDate d) {
                        cell.setCellValue(d);
                        cell.setCellStyle(date);
                    } else if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else if (v instanceof Boolean b) {
                        cell.setCellValue(b ? "Evet" : "Hayır");
                    } else {
                        cell.setCellValue(v.toString());
                    }
                }
            }
            sheet.createFreezePane(0, 1);
            if (!rows.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(0, rows.size(), 0, headers.size() - 1));
            }
            for (int c = 0; c < headers.size(); c++) {
                sheet.setColumnWidth(c, Math.min(Math.max(headers.get(c).length() + 4, 14), 50) * 256);
            }
            book.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
