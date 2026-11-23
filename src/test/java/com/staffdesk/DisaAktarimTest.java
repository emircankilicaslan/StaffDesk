package com.staffdesk;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;

/** Personel, demirbaş ve izin listelerinin Excel dışa aktarımı: yetki, süzgeç uyumu, içerik ve denetim kaydı. */
class DisaAktarimTest extends ApiTestSupport {

    private Workbook xlsx(String url, String token) throws Exception {
        MockHttpServletResponse res = call(get(url), token, null).andExpect(status().isOk()).andReturn().getResponse();
        return new XSSFWorkbook(new ByteArrayInputStream(res.getContentAsByteArray()));
    }

    private static List<String> column(Sheet sheet, int col) {
        List<String> values = new ArrayList<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            Cell cell = row == null ? null : row.getCell(col);
            values.add(cell == null || cell.getCellType() == CellType.BLANK ? "" : cell.toString());
        }
        return values;
    }

    private static List<String> header(Sheet sheet) {
        List<String> h = new ArrayList<>();
        sheet.getRow(0).forEach(c -> h.add(c.getStringCellValue()));
        return h;
    }

    // ---------------------------------------------------------------- yetki

    @ParameterizedTest(name = "{0} yalnız yöneticiye açık")
    @ValueSource(strings = {"/api/exports/employees.xlsx", "/api/exports/assets.xlsx", "/api/exports/leaves.xlsx"})
    void yalnizYoneticiIndirebilir(String url) throws Exception {
        long d = newDepartment("DisaYetki");
        Emp mudur = newEmp("MANAGER", d);
        Emp kisi = newEmp("EMPLOYEE", d);
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        call(get(url), kisi.token(), null).andExpect(status().isForbidden());
        call(get(url), mudur.token(), null).andExpect(status().isForbidden());
        call(get(url), admin, null).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("spreadsheetml")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
    }

    // ---------------------------------------------------------------- personel

    @Test
    void personelDosyasiBaslikVeSatirlariIcerir() throws Exception {
        long d = newDepartment("DisaPersonel");
        Emp e = newEmp("MANAGER", d, "2019-03-04", "Dışa Aktarılan Müdür");
        try (Workbook wb = xlsx("/api/exports/employees.xlsx?q=Dışa Aktarılan", admin)) {
            Sheet s = wb.getSheetAt(0);
            assertEquals("Personel", s.getSheetName());
            assertEquals(List.of("Sicil no", "Ad soyad", "E-posta", "Rol", "Unvan", "Birim", "İşe giriş", "Aktif"), header(s));
            assertEquals(1, s.getLastRowNum());
            assertEquals("Dışa Aktarılan Müdür", column(s, 1).get(0));
            assertEquals(e.email(), column(s, 2).get(0));
            assertEquals("Müdür", column(s, 3).get(0));
            assertTrue(column(s, 5).get(0).startsWith("DisaPersonel"));
            assertEquals("Evet", column(s, 7).get(0));
        }
    }

    @Test
    void personelDosyasiSuzgecleriUygular() throws Exception {
        long d = newDepartment("DisaSuzgec");
        Emp a = newEmp("EMPLOYEE", d, "2020-01-15", "Süzgeç Ahmet");
        newEmp("EMPLOYEE", d, "2020-01-15", "Süzgeç Burak");
        call(patch("/api/employees/" + a.id() + "/deactivate"), admin, null).andExpect(status().isOk());
        try (Workbook wb = xlsx("/api/exports/employees.xlsx?departmentId=" + d, admin)) {
            assertEquals(2, wb.getSheetAt(0).getLastRowNum());
        }
        try (Workbook wb = xlsx("/api/exports/employees.xlsx?departmentId=" + d + "&active=false", admin)) {
            Sheet s = wb.getSheetAt(0);
            assertEquals(1, s.getLastRowNum());
            assertEquals("Süzgeç Ahmet", column(s, 1).get(0));
            assertEquals("Hayır", column(s, 7).get(0));
        }
        try (Workbook wb = xlsx("/api/exports/employees.xlsx?departmentId=" + d + "&role=MANAGER", admin)) {
            assertEquals(0, wb.getSheetAt(0).getLastRowNum());
        }
    }

    @Test
    void personelDosyasiAdaGoreSiralidir() throws Exception {
        long d = newDepartment("DisaSira");
        newEmp("EMPLOYEE", d, "2020-01-15", "Sıra Zeynep");
        newEmp("EMPLOYEE", d, "2020-01-15", "Sıra Ali");
        try (Workbook wb = xlsx("/api/exports/employees.xlsx?departmentId=" + d, admin)) {
            assertEquals(List.of("Sıra Ali", "Sıra Zeynep"), column(wb.getSheetAt(0), 1));
        }
    }

    @Test
    void formulSayilanMetinlerDuzMetinOlarakYazilir() throws Exception {
        long d = newDepartment("DisaFormul");
        newEmp("EMPLOYEE", d, "2020-01-15", "=1+1 Formül");
        try (Workbook wb = xlsx("/api/exports/employees.xlsx?departmentId=" + d, admin)) {
            Cell cell = wb.getSheetAt(0).getRow(1).getCell(1);
            assertEquals(CellType.STRING, cell.getCellType());
            assertEquals("=1+1 Formül", cell.getStringCellValue());
        }
    }

    // ---------------------------------------------------------------- demirbaş

    @Test
    void demirbasDosyasiDetaylariVeZimmetliyiIcerir() throws Exception {
        long d = newDepartment("DisaDemirbas");
        Emp kisi = newEmp("EMPLOYEE", d, "2020-01-15", "Zimmet Sahibi");
        String ad = "Dışa Laptop " + SEQ.incrementAndGet();
        long id = newAsset(ad, "Bilgisayar", Map.of("serialNo", "SN-DISA-1", "purchaseDate", "2025-03-01",
                "purchasePrice", 12500.50, "warrantyEnd", "2035-02-01"));
        call(post("/api/assets/" + id + "/assign"), admin, Map.of("employeeId", kisi.id())).andExpect(status().isOk());
        try (Workbook wb = xlsx("/api/exports/assets.xlsx?q=" + "Dışa Laptop", admin)) {
            Sheet s = wb.getSheetAt(0);
            assertEquals(List.of("Demirbaş no", "Ad", "Kategori", "Durum", "Seri no", "Satın alma", "Fiyat (TL)",
                    "Garanti bitiş", "Zimmetli"), header(s));
            int satir = column(s, 1).indexOf(ad) + 1;
            assertTrue(satir > 0);
            Row r = s.getRow(satir);
            assertEquals("Bilgisayar", r.getCell(2).getStringCellValue());
            assertEquals("Zimmetli", r.getCell(3).getStringCellValue());
            assertEquals("SN-DISA-1", r.getCell(4).getStringCellValue());
            assertEquals(12500.50, r.getCell(6).getNumericCellValue(), 0.001);
            assertNotNull(r.getCell(7));
            assertEquals("Zimmet Sahibi", r.getCell(8).getStringCellValue());
        }
    }

    @Test
    void demirbasDosyasiBosAlanlariBosBirakirVeSuzer() throws Exception {
        String kat = "DisaKat" + SEQ.incrementAndGet();
        newAsset("Yalın " + SEQ.incrementAndGet(), kat, null);
        long diger = newAsset("Kayıt dışı " + SEQ.incrementAndGet(), kat, null);
        call(post("/api/assets/" + diger + "/retire"), admin, null).andExpect(status().isOk());
        try (Workbook wb = xlsx("/api/exports/assets.xlsx?category=" + kat, admin)) {
            assertEquals(2, wb.getSheetAt(0).getLastRowNum());
        }
        try (Workbook wb = xlsx("/api/exports/assets.xlsx?category=" + kat + "&status=RETIRED", admin)) {
            Sheet s = wb.getSheetAt(0);
            assertEquals(1, s.getLastRowNum());
            assertEquals("Kayıttan düşüldü", column(s, 3).get(0));
            assertEquals("", column(s, 4).get(0));
            assertEquals("", column(s, 8).get(0));
        }
    }

    @Test
    void gecersizSuzgecDegeri400() throws Exception {
        call(get("/api/exports/assets.xlsx?status=YOK"), admin, null).andExpect(status().isBadRequest());
        call(get("/api/exports/employees.xlsx?role=PATRON"), admin, null).andExpect(status().isBadRequest());
        call(get("/api/exports/leaves.xlsx?status=YOK"), admin, null).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- izin

    @Test
    void izinDosyasiTurDurumVeIsGunuIcerir() throws Exception {
        long d = newDepartment("DisaIzin");
        Emp mudur = newEmp("MANAGER", d);
        Emp kisi = newEmp("EMPLOYEE", d, "2020-01-15", "İzinli Kişi");
        long id = requestLeave(kisi, "EXCUSE", "2035-01-08", "2035-01-10");
        call(post("/api/leaves/" + id + "/approve"), mudur.token(), null).andExpect(status().isOk());
        requestLeave(kisi, "SICK", "2035-01-22", "2035-01-22");

        try (Workbook wb = xlsx("/api/exports/leaves.xlsx?status=ALL&employeeId=" + kisi.id(), admin)) {
            Sheet s = wb.getSheetAt(0);
            assertEquals(List.of("Personel", "Tür", "Başlangıç", "Bitiş", "İş günü", "Durum", "Karar notu"), header(s));
            assertEquals(2, s.getLastRowNum());
            // başlangıç tarihine göre azalan: önce 22 Ocak
            assertEquals("Hastalık izni", column(s, 1).get(0));
            assertEquals("Bekliyor", column(s, 5).get(0));
            assertEquals("Mazeret izni", column(s, 1).get(1));
            assertEquals("Onaylandı", column(s, 5).get(1));
            assertEquals(3.0, s.getRow(2).getCell(4).getNumericCellValue(), 0.001);
        }
    }

    @Test
    void izinDosyasiVarsayilanTumDurumlariVeSuzgecleri() throws Exception {
        long d = newDepartment("DisaIzinSuzgec");
        Emp kisi = newEmp("EMPLOYEE", d);
        long a = requestLeave(kisi, "EXCUSE", "2035-01-23", "2035-01-23");
        requestLeave(kisi, "EXCUSE", "2035-01-24", "2035-01-24");
        call(post("/api/leaves/" + a + "/cancel"), kisi.token(), null).andExpect(status().isOk());
        try (Workbook wb = xlsx("/api/exports/leaves.xlsx?employeeId=" + kisi.id(), admin)) {
            assertEquals(2, wb.getSheetAt(0).getLastRowNum());
        }
        try (Workbook wb = xlsx("/api/exports/leaves.xlsx?status=PENDING&employeeId=" + kisi.id(), admin)) {
            assertEquals(1, wb.getSheetAt(0).getLastRowNum());
        }
        try (Workbook wb = xlsx("/api/exports/leaves.xlsx?status=cancelled&employeeId=" + kisi.id(), admin)) {
            Sheet s = wb.getSheetAt(0);
            assertEquals(1, s.getLastRowNum());
            assertEquals("İptal edildi", column(s, 5).get(0));
        }
        try (Workbook wb = xlsx("/api/exports/leaves.xlsx?type=ANNUAL&employeeId=" + kisi.id(), admin)) {
            assertEquals(0, wb.getSheetAt(0).getLastRowNum());
        }
    }

    // ---------------------------------------------------------------- denetim

    @Test
    void herIndirmeDenetimKaydinaYazilir() throws Exception {
        long d = newDepartment("DisaDenetim");
        newEmp("EMPLOYEE", d);
        xlsx("/api/exports/employees.xlsx?departmentId=" + d, admin).close();
        xlsx("/api/exports/assets.xlsx?q=yokturbuyoktur", admin).close();
        xlsx("/api/exports/leaves.xlsx?employeeId=999999", admin).close();
        call(get("/api/audit?action=EXPORT&size=100"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].entityId", hasItem("personel")))
                .andExpect(jsonPath("$.content[*].entityId", hasItem("demirbaslar")))
                .andExpect(jsonPath("$.content[*].entityId", hasItem("izinler")))
                .andExpect(jsonPath("$.content[*].details", hasItem("1 satır")))
                .andExpect(jsonPath("$.content[*].details", hasItem("0 satır")));
    }

    @Test
    void bosSonucDa_gecerliDosyaUretir() throws Exception {
        try (Workbook wb = xlsx("/api/exports/assets.xlsx?q=hicbirseyeuymayanmetin", admin)) {
            Sheet s = wb.getSheetAt(0);
            assertEquals(0, s.getLastRowNum());
            assertFalse(header(s).isEmpty());
        }
    }
}
