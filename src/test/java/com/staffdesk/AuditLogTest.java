package com.staffdesk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Denetim kaydı (kim, ne zaman, ne yaptı) ve Excel çıktıları: gerçek Spring bağlamı, Flyway ve JWT ile. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditLogTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String PASSWORD = "Parola-12345";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = login("admin@test.local", "admin-password-123");
    }

    // ---------------------------------------------------------------- yardımcılar

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private ResultActions call(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(req);
    }

    private long idOf(ResultActions r) throws Exception {
        Number n = JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
        return n.longValue();
    }

    private long createDepartment(String name) throws Exception {
        return idOf(call(post("/api/departments"), admin, Map.of("name", name)).andExpect(status().isCreated()));
    }

    /** Personel oluşturur: [id, e-posta, token]. */
    private Object[] newEmployee(String role, long departmentId) throws Exception {
        int n = SEQ.incrementAndGet();
        String email = "denetim" + n + "@test.local";
        long id = idOf(call(post("/api/employees"), admin, Map.of("registrationNo", "D-" + n, "fullName", "Denetim " + n,
                "email", email, "password", PASSWORD, "role", role, "departmentId", departmentId, "hireDate", "2010-01-04"))
                .andExpect(status().isCreated()));
        return new Object[] {id, email, login(email, PASSWORD)};
    }

    private List<JsonNode> entries(String query) throws Exception {
        String body = call(get("/api/audit?size=100" + query), admin, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<JsonNode> list = new ArrayList<>();
        json.readTree(body).get("content").forEach(list::add);
        return list;
    }

    private static JsonNode only(List<JsonNode> list, String entityId) {
        List<JsonNode> hit = list.stream().filter(n -> entityId.equals(n.path("entityId").asText())).toList();
        assertEquals(1, hit.size(), "beklenen tek kayıt: " + hit);
        return hit.get(0);
    }

    private Workbook xlsx(String url, String token) throws Exception {
        var res = call(get(url), token, null).andExpect(status().isOk()).andReturn().getResponse();
        assertTrue(res.getContentType().startsWith(XLSX), res.getContentType());
        return new XSSFWorkbook(new ByteArrayInputStream(res.getContentAsByteArray()));
    }

    // ---------------------------------------------------------------- testler

    @Test
    void yoneticiIslemleriKullaniciVeZamanlaKaydedilir() throws Exception {
        long dept = createDepartment("Denetim-Birim-" + SEQ.incrementAndGet());
        Object[] emp = newEmployee("EMPLOYEE", dept);
        long assetId = idOf(call(post("/api/assets"), admin, Map.of("assetTag", "DNT-" + SEQ.incrementAndGet(),
                "name", "Dizüstü", "category", "BT")).andExpect(status().isCreated()));
        call(post("/api/assets/" + assetId + "/assign"), admin, Map.of("employeeId", emp[0])).andExpect(status().isOk());
        call(post("/api/assets/" + assetId + "/return"), admin, Map.of("note", "tamam")).andExpect(status().isOk());
        call(post("/api/assets/" + assetId + "/retire"), admin, null).andExpect(status().isOk());
        call(patch("/api/employees/" + emp[0] + "/deactivate"), admin, null).andExpect(status().isOk());

        JsonNode created = only(entries("&action=DEPARTMENT_CREATED"), String.valueOf(dept));
        assertEquals("admin@test.local", created.get("actor").asText());
        assertEquals("ADMIN", created.get("actorRole").asText());
        assertNotNull(created.get("occurredAt").asText());

        for (String action : List.of("ASSET_ASSIGNED", "ASSET_RETURNED", "ASSET_RETIRED")) {
            assertEquals("admin@test.local", only(entries("&action=" + action), String.valueOf(assetId)).get("actor").asText());
        }
        assertTrue(only(entries("&action=ASSET_ASSIGNED"), String.valueOf(assetId)).get("details").asText().contains(emp[1].toString()));
        only(entries("&action=EMPLOYEE_CREATED"), String.valueOf(emp[0]));
        only(entries("&action=EMPLOYEE_DEACTIVATED"), String.valueOf(emp[0]));
    }

    @Test
    void izinAkisiTalepEdenVeKararVerenAdlaKaydedilir() throws Exception {
        long dept = createDepartment("Denetim-Izin-" + SEQ.incrementAndGet());
        Object[] emp = newEmployee("EMPLOYEE", dept);
        String token = (String) emp[2];

        long leaveId = idOf(call(post("/api/leaves"), token, Map.of("type", "ANNUAL", "startDate", "2035-03-05",
                "endDate", "2035-03-06")).andExpect(status().isCreated()));
        call(post("/api/leaves/" + leaveId + "/approve"), admin, Map.of("note", "uygun")).andExpect(status().isOk());
        long second = idOf(call(post("/api/leaves"), token, Map.of("type", "EXCUSE", "startDate", "2035-03-12",
                "endDate", "2035-03-12")).andExpect(status().isCreated()));
        call(post("/api/leaves/" + second + "/cancel"), token, null).andExpect(status().isOk());
        long third = idOf(call(post("/api/leaves"), token, Map.of("type", "EXCUSE", "startDate", "2035-03-19",
                "endDate", "2035-03-19")).andExpect(status().isCreated()));
        call(post("/api/leaves/" + third + "/reject"), admin, Map.of("note", "yoğun dönem")).andExpect(status().isOk());

        JsonNode req = only(entries("&action=LEAVE_REQUESTED"), String.valueOf(leaveId));
        assertEquals(emp[1], req.get("actor").asText());
        assertEquals("EMPLOYEE", req.get("actorRole").asText());
        assertTrue(req.get("details").asText().contains("2 iş günü"));
        assertEquals("admin@test.local", only(entries("&action=LEAVE_APPROVED"), String.valueOf(leaveId)).get("actor").asText());
        assertEquals(emp[1], only(entries("&action=LEAVE_CANCELLED"), String.valueOf(second)).get("actor").asText());
        JsonNode rejected = only(entries("&action=LEAVE_REJECTED"), String.valueOf(third));
        assertEquals("yoğun dönem", rejected.get("details").asText());
    }

    @Test
    void girisVeBasarisizGirisKaydedilirParolaKayidaGirmez() throws Exception {
        String unknown = "yok" + SEQ.incrementAndGet() + "@test.local";
        String secret = "cok-gizli-yanlis-parola-9";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", unknown, "password", secret))))
                .andExpect(status().isUnauthorized());

        String raw = call(get("/api/audit?size=100&actor=" + unknown), admin, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode items = json.readTree(raw).get("content");
        assertEquals(1, items.size());
        assertEquals("LOGIN_FAILED", items.get(0).get("action").asText());
        assertFalse(raw.contains(secret));

        // Başarılı giriş: yönetici girişi kaydedilir (setUp'taki giriş dahil en az bir kayıt).
        assertFalse(entries("&action=LOGIN&actor=admin@test.local").isEmpty());
        assertFalse(call(get("/api/audit?size=100"), admin, null).andReturn().getResponse().getContentAsString()
                .contains("admin-password-123"));
    }

    @Test
    void denetimUclariYalnizcaYoneticiyeAcik() throws Exception {
        long dept = createDepartment("Denetim-Yetki-" + SEQ.incrementAndGet());
        String emp = (String) newEmployee("EMPLOYEE", dept)[2];
        String manager = (String) newEmployee("MANAGER", dept)[2];

        for (String url : List.of("/api/audit", "/api/audit/export.xlsx", "/api/reports/leave-usage.xlsx")) {
            call(get(url), emp, null).andExpect(status().isForbidden());
            call(get(url), manager, null).andExpect(status().isForbidden());
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void suzgecSayfalamaVeDogrulama() throws Exception {
        call(get("/api/audit?size=0"), admin, null).andExpect(status().isBadRequest());
        call(get("/api/audit?size=101"), admin, null).andExpect(status().isBadRequest());
        call(get("/api/audit?page=-1"), admin, null).andExpect(status().isBadRequest());
        call(get("/api/audit?from=2035-02-01&to=2035-01-01"), admin, null).andExpect(status().isBadRequest());
        call(get("/api/audit?from=bozuk-tarih"), admin, null).andExpect(status().isBadRequest());

        String none = call(get("/api/audit?actor=kimse-yok@test.local"), admin, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(0, json.readTree(none).get("page").get("totalElements").asInt());

        String future = call(get("/api/audit?from=2999-01-01"), admin, null).andReturn().getResponse().getContentAsString();
        assertEquals(0, json.readTree(future).get("page").get("totalElements").asInt());

        String today = call(get("/api/audit?size=1&from=2000-01-01&to=2999-12-31"), admin, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, json.readTree(today).get("content").size());
        assertTrue(json.readTree(today).get("page").get("totalElements").asInt() >= 1);
    }

    @Test
    void excelCiktilari_FormulEnjeksiyonuCalismaz() throws Exception {
        String formula = "=HYPERLINK(\"http://kotu.example\",\"tikla\")";
        long dept = createDepartment(formula);

        try (Workbook audit = xlsx("/api/audit/export.xlsx", admin)) {
            Sheet sheet = audit.getSheetAt(0);
            assertEquals("Kullanıcı", sheet.getRow(0).getCell(2).getStringCellValue());
            boolean found = false;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                var cell = sheet.getRow(r).getCell(7); // Ayrıntı = birim adı
                if (cell != null && cell.getCellType() == CellType.STRING && cell.getStringCellValue().equals(formula)) {
                    found = true;
                }
                if (cell != null) assertTrue(cell.getCellType() != CellType.FORMULA);
            }
            assertTrue(found, "formül benzeri metin düz metin olarak yazılmalı (birim " + dept + ")");
        }

        try (Workbook report = xlsx("/api/reports/leave-usage.xlsx?year=2035", admin)) {
            Sheet sheet = report.getSheetAt(0);
            assertEquals("Birim", sheet.getRow(0).getCell(0).getStringCellValue());
            boolean found = false;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                var cell = sheet.getRow(r).getCell(0);
                assertTrue(cell.getCellType() != CellType.FORMULA);
                if (cell.getStringCellValue().equals(formula)) found = true;
            }
            assertTrue(found);
        }
    }
}
