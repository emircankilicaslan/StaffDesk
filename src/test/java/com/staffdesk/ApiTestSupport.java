package com.staffdesk;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Uçtan uca testlerin ortak tabanı: gerçek Spring bağlamı, Flyway migrasyonları (H2'de PostgreSQL kipi), JWT.
 * Saat 2035-01-01 (Pazartesi) 09:00 olarak sabitlenir; tarih kuralları zamandan bağımsız sınanır.
 * Test takvimi: yalnızca 2034 ve 2035 için tatil tanımlıdır (2035-01-17 Çarşamba resmi tatil).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSupport.FixedClock.class)
abstract class ApiTestSupport {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2035-01-01T09:00:00Z"), ZoneOffset.UTC);
        }
    }

    static final AtomicInteger SEQ = new AtomicInteger(1000);
    static final String PASSWORD = "Parola-12345";

    /** Test için oluşturulan personel: kimlik, e-posta ve oturum anahtarı. */
    record Emp(long id, String email, String token) {}

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    String admin;

    @BeforeEach
    void loginAdmin() throws Exception {
        admin = login("admin@test.local", "admin-password-123");
    }

    String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    ResultActions call(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(req);
    }

    long idOf(ResultActions r) throws Exception {
        Number n = JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
        return n.longValue();
    }

    <T> T read(ResultActions r, String path) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), path);
    }

    long newDepartment(String prefix) throws Exception {
        return idOf(call(post("/api/departments"), admin,
                Map.of("name", prefix + "-" + SEQ.incrementAndGet())).andExpect(status().isCreated()));
    }

    /** Yeni personel oluşturur ve oturum açar. name boşsa "Kişi n" kullanılır. */
    Emp newEmp(String role, Long departmentId, String hireDate, String name) throws Exception {
        int n = SEQ.incrementAndGet();
        String email = "kisi" + n + "@test.local";
        Map<String, Object> body = new HashMap<>();
        body.put("registrationNo", "T-" + n);
        body.put("fullName", name == null ? "Kişi " + n : name);
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("role", role);
        body.put("hireDate", hireDate);
        if (departmentId != null) {
            body.put("departmentId", departmentId);
        }
        long id = idOf(call(post("/api/employees"), admin, body).andExpect(status().isCreated()));
        return new Emp(id, email, login(email, PASSWORD));
    }

    Emp newEmp(String role, Long departmentId) throws Exception {
        return newEmp(role, departmentId, "2020-01-15", null);
    }

    Map<String, Object> leave(String type, String start, String end) {
        return Map.of("type", type, "startDate", start, "endDate", end);
    }

    /** Talep açar, [id] döndürür. */
    long requestLeave(Emp e, String type, String start, String end) throws Exception {
        return idOf(call(post("/api/leaves"), e.token(), leave(type, start, end)).andExpect(status().isCreated()));
    }

    long newAsset(String name, String category, Map<String, Object> extra) throws Exception {
        int n = SEQ.incrementAndGet();
        Map<String, Object> body = new HashMap<>(Map.of("assetTag", "DB-" + n, "name", name, "category", category));
        if (extra != null) {
            body.putAll(extra);
        }
        return idOf(call(post("/api/assets"), admin, body).andExpect(status().isCreated()));
    }
}
