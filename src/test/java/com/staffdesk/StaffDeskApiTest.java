package com.staffdesk;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Uçtan uca testler: gerçek Spring bağlamı, Flyway migrasyonları (H2'de PostgreSQL kipi), JWT ve rol kontrolleri.
 * Saat 2035-01-01 (Pazartesi) olarak sabitlenir; böylece tarih kuralları zamandan bağımsız test edilir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffDeskApiTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2035-01-01T09:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String PASSWORD = "Parola-12345";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String admin;
    private long dept;

    @BeforeEach
    void setUp() throws Exception {
        admin = login("admin@test.local", "admin-password-123");
        dept = createDepartment("Birim-" + SEQ.incrementAndGet());
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

    /** Yeni personel oluşturur ve oturum token'ını döndürür: [id, token]. */
    private Object[] newEmployee(String role, long departmentId, String hireDate) throws Exception {
        int n = SEQ.incrementAndGet();
        String email = "kisi" + n + "@test.local";
        long id = idOf(call(post("/api/employees"), admin, Map.of("registrationNo", "S-" + n, "fullName", "Kişi " + n,
                "email", email, "password", PASSWORD, "role", role, "departmentId", departmentId, "hireDate", hireDate))
                .andExpect(status().isCreated()));
        return new Object[] {id, login(email, PASSWORD)};
    }

    private Map<String, Object> leave(String type, String start, String end) {
        return Map.of("type", type, "startDate", start, "endDate", end);
    }

    // ---------------------------------------------------------------- kimlik doğrulama ve yetki

    @Test
    void loginFailuresAndMissingTokenAreRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "admin@test.local", "password", "yanlis-sifre"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "yok@test.local", "password", "yanlis-sifre"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/leaves/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/leaves/mine").header("Authorization", "Bearer sahte.token.degeri"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    void employeesCannotUseAdminEndpoints() throws Exception {
        String emp = (String) newEmployee("EMPLOYEE", dept, "2020-01-15")[1];

        call(post("/api/departments"), emp, Map.of("name", "Yetkisiz")).andExpect(status().isForbidden());
        call(get("/api/employees"), emp, null).andExpect(status().isForbidden());
        call(get("/api/reports/leave-usage"), emp, null).andExpect(status().isForbidden());
        call(post("/api/assets"), emp, Map.of("assetTag", "X", "name", "X", "category", "X"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validationErrorsReturnProblemDetail() throws Exception {
        call(post("/api/employees"), admin, Map.of("registrationNo", "", "fullName", "A", "email", "bozuk",
                "password", "kisa", "role", "EMPLOYEE", "hireDate", "2020-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Geçersiz istek")))
                .andExpect(jsonPath("$.detail", containsString("email")))
                .andExpect(jsonPath("$.detail", containsString("password")));
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        int n = SEQ.incrementAndGet();
        Map<String, Object> body = Map.of("registrationNo", "D-" + n, "fullName", "Aynı Kişi", "email",
                "ayni" + n + "@test.local", "password", PASSWORD, "role", "EMPLOYEE", "hireDate", "2020-01-01");
        call(post("/api/employees"), admin, body).andExpect(status().isCreated());
        Map<String, Object> again = new java.util.HashMap<>(body);
        again.put("registrationNo", "D-" + n + "-2");
        call(post("/api/employees"), admin, again).andExpect(status().isConflict());
    }

    @Test
    void deactivatedEmployeeCannotLogin() throws Exception {
        int n = SEQ.incrementAndGet();
        long id = idOf(call(post("/api/employees"), admin, Map.of("registrationNo", "P-" + n, "fullName", "Pasif",
                "email", "pasif" + n + "@test.local", "password", PASSWORD, "role", "EMPLOYEE", "hireDate", "2020-01-01"))
                .andExpect(status().isCreated()));
        call(patch("/api/employees/" + id + "/deactivate"), admin, null).andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "pasif" + n + "@test.local", "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- izin akışı

    @Test
    void annualLeaveRulesAreEnforced() throws Exception {
        // 2026'da işe girdi: 2035'te 9 yıl -> 20 gün hak
        String emp = (String) newEmployee("EMPLOYEE", dept, "2026-01-15")[1];

        // 8–26 Ocak: 15 iş günü, 17 Ocak resmi tatil -> 14 gün
        call(post("/api/leaves"), emp, leave("ANNUAL", "2035-01-08", "2035-01-26"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessDays", is(14)))
                .andExpect(jsonPath("$.status", is("PENDING")));

        // çakışma
        call(post("/api/leaves"), emp, leave("EXCUSE", "2035-01-10", "2035-01-12"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("başka bir izin")));

        // bakiye: 20 - 14 = 6 gün kaldı; 10 günlük talep reddedilir, 5 günlük kabul edilir
        call(post("/api/leaves"), emp, leave("ANNUAL", "2035-01-29", "2035-02-09"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Yetersiz")));
        call(post("/api/leaves"), emp, leave("ANNUAL", "2035-01-29", "2035-02-02")).andExpect(status().isCreated());

        // sadece hafta sonu, geçmiş tarih, ters aralık
        call(post("/api/leaves"), emp, leave("ANNUAL", "2035-02-10", "2035-02-11")).andExpect(status().isConflict());
        call(post("/api/leaves"), emp, leave("ANNUAL", "2034-12-04", "2034-12-08")).andExpect(status().isConflict());
        call(post("/api/leaves"), emp, leave("ANNUAL", "2035-03-05", "2035-03-01")).andExpect(status().isConflict());

        call(get("/api/leaves/mine"), emp, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()", is(2)));
    }

    @Test
    void sickLeaveCanBeBackdatedWithinThirtyDaysOnly() throws Exception {
        String emp = (String) newEmployee("EMPLOYEE", dept, "2020-01-15")[1];

        call(post("/api/leaves"), emp, leave("SICK", "2034-12-27", "2034-12-28")).andExpect(status().isCreated());
        call(post("/api/leaves"), emp, leave("SICK", "2034-11-01", "2034-11-02")).andExpect(status().isConflict());
    }

    @Test
    void leaveBalanceReflectsEntitlementAndUsage() throws Exception {
        Object[] e = newEmployee("EMPLOYEE", dept, "2020-01-15"); // 15 yıl -> 30 gün
        String emp = (String) e[1];
        long id = (Long) e[0];

        long leaveId = idOf(call(post("/api/leaves"), emp, leave("ANNUAL", "2035-01-08", "2035-01-12"))
                .andExpect(status().isCreated()));

        call(get("/api/employees/" + id + "/leave-balance?year=2035"), emp, null)
                .andExpect(jsonPath("$.entitlement", is(30)))
                .andExpect(jsonPath("$.pending", is(5)))
                .andExpect(jsonPath("$.used", is(0)))
                .andExpect(jsonPath("$.remaining", is(25)));

        call(post("/api/leaves/" + leaveId + "/approve"), admin, null).andExpect(status().isOk());

        call(get("/api/employees/" + id + "/leave-balance?year=2035"), emp, null)
                .andExpect(jsonPath("$.pending", is(0)))
                .andExpect(jsonPath("$.used", is(5)))
                .andExpect(jsonPath("$.remaining", is(25)));
    }

    @Test
    void approvalRulesAndScoping() throws Exception {
        long otherDept = createDepartment("Diğer-" + SEQ.incrementAndGet());
        Object[] emp = newEmployee("EMPLOYEE", dept, "2020-01-15");
        String empToken = (String) emp[1];
        String manager = (String) newEmployee("MANAGER", dept, "2018-01-15")[1];
        String foreignManager = (String) newEmployee("MANAGER", otherDept, "2018-01-15")[1];

        long leaveId = idOf(call(post("/api/leaves"), empToken, leave("ANNUAL", "2035-01-22", "2035-01-23"))
                .andExpect(status().isCreated()));

        // çalışan onaylayamaz; başka birimin müdürü onaylayamaz
        call(post("/api/leaves/" + leaveId + "/approve"), empToken, null).andExpect(status().isForbidden());
        call(post("/api/leaves/" + leaveId + "/approve"), foreignManager, null).andExpect(status().isForbidden());

        // müdür yalnızca kendi birimindeki bekleyenleri listeler
        call(get("/api/leaves?status=PENDING"), manager, null)
                .andExpect(jsonPath("$.content[*].id", hasItem((int) leaveId)));
        call(get("/api/leaves?status=PENDING"), foreignManager, null)
                .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.not(hasItem((int) leaveId))));

        call(post("/api/leaves/" + leaveId + "/approve"), manager, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("APPROVED")));
        // ikinci karar ve onaylanmış izni iptal: iş kuralı ihlali
        call(post("/api/leaves/" + leaveId + "/reject"), manager, null).andExpect(status().isConflict());
        call(post("/api/leaves/" + leaveId + "/cancel"), empToken, null).andExpect(status().isConflict());
    }

    @Test
    void managerCannotDecideOwnRequestAndOnlyOwnerCancels() throws Exception {
        String manager = (String) newEmployee("MANAGER", dept, "2018-01-15")[1];
        String emp = (String) newEmployee("EMPLOYEE", dept, "2020-01-15")[1];
        String stranger = (String) newEmployee("EMPLOYEE", dept, "2020-01-15")[1];

        long mine = idOf(call(post("/api/leaves"), manager, leave("EXCUSE", "2035-01-22", "2035-01-22"))
                .andExpect(status().isCreated()));
        call(post("/api/leaves/" + mine + "/approve"), manager, null).andExpect(status().isForbidden());
        call(post("/api/leaves/" + mine + "/approve"), admin, null).andExpect(status().isOk());

        long other = idOf(call(post("/api/leaves"), emp, leave("EXCUSE", "2035-01-23", "2035-01-23"))
                .andExpect(status().isCreated()));
        call(post("/api/leaves/" + other + "/cancel"), stranger, null).andExpect(status().isForbidden());
        call(post("/api/leaves/" + other + "/cancel"), emp, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    void paginationParametersAreValidated() throws Exception {
        call(get("/api/employees?size=1000"), admin, null).andExpect(status().isBadRequest());
        call(get("/api/employees?page=-1"), admin, null).andExpect(status().isBadRequest());
        call(get("/api/leaves?status=BOGUS"), admin, null).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- zimmet

    @Test
    void assetAssignmentLifecycle() throws Exception {
        Object[] a = newEmployee("EMPLOYEE", dept, "2020-01-15");
        Object[] b = newEmployee("EMPLOYEE", dept, "2020-01-15");
        int n = SEQ.incrementAndGet();
        long assetId = idOf(call(post("/api/assets"), admin,
                Map.of("assetTag", "DMB-" + n, "name", "Dizüstü Bilgisayar", "category", "BİLGİSAYAR"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status", is("AVAILABLE"))));

        // aynı demirbaş numarası tekrar kaydedilemez
        call(post("/api/assets"), admin, Map.of("assetTag", "DMB-" + n, "name", "X", "category", "X"))
                .andExpect(status().isConflict());

        call(post("/api/assets/" + assetId + "/assign"), admin, Map.of("employeeId", a[0], "note", "Teslim alındı"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.returnedAt").doesNotExist());
        // zimmetliyken ikinci kişiye verilemez, kayıttan düşülemez
        call(post("/api/assets/" + assetId + "/assign"), admin, Map.of("employeeId", b[0])).andExpect(status().isConflict());
        call(post("/api/assets/" + assetId + "/retire"), admin, null).andExpect(status().isConflict());

        // personel kendi zimmetini görür, başkasınınkini göremez
        call(get("/api/employees/" + a[0] + "/assets"), (String) a[1], null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()", is(1)));
        call(get("/api/employees/" + a[0] + "/assets"), (String) b[1], null).andExpect(status().isForbidden());

        // iade sonrası başkasına verilebilir; geçmiş korunur
        call(post("/api/assets/" + assetId + "/return"), admin, Map.of("note", "Hasarsız iade"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.returnedAt").exists());
        call(post("/api/assets/" + assetId + "/return"), admin, null).andExpect(status().isConflict());
        call(post("/api/assets/" + assetId + "/assign"), admin, Map.of("employeeId", b[0])).andExpect(status().isOk());
        call(get("/api/employees/" + a[0] + "/assets"), admin, null)
                .andExpect(jsonPath("$[0].returnedAt").exists())
                .andExpect(jsonPath("$[0].note", is("Hasarsız iade")));
    }

    @Test
    void retiredAssetCannotBeAssigned() throws Exception {
        long emp = (Long) newEmployee("EMPLOYEE", dept, "2020-01-15")[0];
        long assetId = idOf(call(post("/api/assets"), admin,
                Map.of("assetTag", "DMB-" + SEQ.incrementAndGet(), "name", "Eski Yazıcı", "category", "YAZICI"))
                .andExpect(status().isCreated()));

        call(post("/api/assets/" + assetId + "/retire"), admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("RETIRED")));
        call(post("/api/assets/" + assetId + "/assign"), admin, Map.of("employeeId", emp)).andExpect(status().isConflict());
        call(post("/api/assets/999999/assign"), admin, Map.of("employeeId", emp)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- rapor

    @Test
    void leaveUsageReportAggregatesPerDepartment() throws Exception {
        String deptName = "Rapor-" + SEQ.incrementAndGet();
        long reportDept = createDepartment(deptName);
        String e1 = (String) newEmployee("EMPLOYEE", reportDept, "2020-01-15")[1];
        String e2 = (String) newEmployee("EMPLOYEE", reportDept, "2020-01-15")[1];

        long approved = idOf(call(post("/api/leaves"), e1, leave("ANNUAL", "2035-01-08", "2035-01-12"))
                .andExpect(status().isCreated())); // 5 gün
        call(post("/api/leaves/" + approved + "/approve"), admin, null).andExpect(status().isOk());
        call(post("/api/leaves"), e2, leave("ANNUAL", "2035-01-22", "2035-01-23")).andExpect(status().isCreated()); // bekleyen

        String path = "$[?(@.department == '" + deptName + "')]";
        call(get("/api/reports/leave-usage?year=2035"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".employees", hasItem(2)))
                .andExpect(jsonPath(path + ".annualDaysUsed", hasItem(5)))
                .andExpect(jsonPath(path + ".pendingRequests", hasItem(1)));
    }
}
