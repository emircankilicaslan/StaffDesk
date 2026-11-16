package com.staffdesk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Excel üretimi için birim testleri. */
class XlsxWriterTest {

    private static Sheet open(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes)).getSheetAt(0);
    }

    private static List<List<Object>> rows(Object[]... rows) {
        List<List<Object>> list = new ArrayList<>();
        for (Object[] r : rows) list.add(Arrays.asList(r));
        return list;
    }

    @Test
    void baslikVeTipliDegerlerDogruYazilir() throws Exception {
        LocalDateTime at = LocalDateTime.of(2026, 11, 12, 10, 30, 15);
        Sheet s = open(XlsxWriter.build("Deneme", List.of("Ad", "Zaman", "Sayı", "Aktif"),
                rows(new Object[] {"Trafo 1", at, 5, true}, new Object[] {"Trafo 2", null, 7L, false})));

        assertEquals("Deneme", s.getSheetName());
        assertEquals("Zaman", s.getRow(0).getCell(1).getStringCellValue());
        assertTrue(s.getWorkbook().getFontAt(s.getRow(0).getCell(0).getCellStyle().getFontIndex()).getBold());
        assertEquals("Trafo 1", s.getRow(1).getCell(0).getStringCellValue());
        assertEquals(CellType.NUMERIC, s.getRow(1).getCell(1).getCellType());
        assertTrue(DateUtil.isCellDateFormatted(s.getRow(1).getCell(1)));
        assertEquals(at, s.getRow(1).getCell(1).getLocalDateTimeCellValue());
        assertEquals(5.0, s.getRow(1).getCell(2).getNumericCellValue());
        assertEquals("Evet", s.getRow(1).getCell(3).getStringCellValue());
        assertNull(s.getRow(2).getCell(1));
        assertEquals("Hayır", s.getRow(2).getCell(3).getStringCellValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+SUM(A1:A2)", "-2+3", "@cmd", "=HYPERLINK(\"http://kotu.example\",\"tikla\")"})
    void formulGibiGorunenMetinFormulOlarakCalismaz(String value) throws Exception {
        Sheet s = open(XlsxWriter.build("Deneme", List.of("Metin"), rows(new Object[] {value})));
        var cell = s.getRow(1).getCell(0);
        assertFalse(cell.getCellType() == CellType.FORMULA);
        assertEquals(CellType.STRING, cell.getCellType());
        assertEquals(value, cell.getStringCellValue());
    }

    @Test
    void bosListeYalnizcaBasligiIcerir() throws Exception {
        Sheet s = open(XlsxWriter.build("Bos", List.of("A", "B"), List.of()));
        assertEquals("A", s.getRow(0).getCell(0).getStringCellValue());
        assertEquals(0, s.getLastRowNum());
    }
}
