package com.staffdesk;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** Personel kaydı güncelleme, yeniden etkinleştirme ve kendi parolasını değiştirme. */
class HesapYonetimiTest extends ApiTestSupport {

    // ---------------------------------------------------------------- güncelleme

    @Test
    void adUnvanBirimVeRolGuncellenir() throws Exception {
        long d1 = newDepartment("Guncel1");
        long d2 = newDepartment("Guncel2");
        Emp e = newEmp("EMPLOYEE", d1, "2020-01-15", "Eski Ad");
        call(patch("/api/employees/" + e.id()), admin, Map.of("fullName", "  Yeni Ad  ", "title", "Uzman", "departmentId", d2, "role", "MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("Yeni Ad")))
                .andExpect(jsonPath("$.title", is("Uzman")))
                .andExpect(jsonPath("$.departmentId", is((int) d2)))
                .andExpect(jsonPath("$.role", is("MANAGER")));
    }

    @Test
    void bosAlanlarDegismezBosUnvanTemizlenir() throws Exception {
        long d = newDepartment("Kismi");
        Emp e = newEmp("EMPLOYEE", d, "2020-01-15", "Aynı Ad");
        call(patch("/api/employees/" + e.id()), admin, Map.of("title", "Başlık")).andExpect(status().isOk());
        call(patch("/api/employees/" + e.id()), admin, new HashMap<String, Object>())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("Aynı Ad")))
                .andExpect(jsonPath("$.title", is("Başlık")))
                .andExpect(jsonPath("$.departmentId", is((int) d)));
        call(patch("/api/employees/" + e.id()), admin, Map.of("title", " ")).andExpect(status().isOk())
                .andExpect(jsonPath("$.title", nullValue()));
    }

    @Test
    void mudurRoluBirimGerektirir() throws Exception {
        Emp birimsiz = newEmp("EMPLOYEE", null, "2020-01-15", "Birimsiz");
        call(patch("/api/employees/" + birimsiz.id()), admin, Map.of("role", "MANAGER")).andExpect(status().isConflict());
        long d = newDepartment("MudurBirim");
        call(patch("/api/employees/" + birimsiz.id()), admin, Map.of("role", "MANAGER", "departmentId", d))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role", is("MANAGER")));
    }

    @Test
    void yetkisizVeGecersizGuncellemeler() throws Exception {
        long d = newDepartment("GecersizGunc");
        Emp e = newEmp("EMPLOYEE", d);
        Emp mudur = newEmp("MANAGER", d);
        call(patch("/api/employees/" + e.id()), mudur.token(), Map.of("title", "x")).andExpect(status().isForbidden());
        call(patch("/api/employees/" + e.id()), e.token(), Map.of("title", "x")).andExpect(status().isForbidden());
        call(patch("/api/employees/999999"), admin, Map.of("title", "x")).andExpect(status().isNotFound());
        call(patch("/api/employees/" + e.id()), admin, Map.of("departmentId", 999999)).andExpect(status().isNotFound());
        call(patch("/api/employees/" + e.id()), admin, Map.of("fullName", "  ")).andExpect(status().isConflict());
        call(patch("/api/employees/" + e.id()), admin, Map.of("fullName", "a".repeat(151))).andExpect(status().isBadRequest());
        call(patch("/api/employees/" + e.id()), admin, Map.of("role", "PATRON")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/employees/" + e.id()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sonAktifYoneticininRoluDusurulemezDigerYoneticiVarsaDusurulur() throws Exception {
        // Testlerde başka yöneticiler de oluşturulabildiği için aktif yönetici sayısı kontrol edilir.
        Emp ikinci = newEmp("ADMIN", null, "2018-01-01", "İkinci Yönetici");
        call(patch("/api/employees/" + ikinci.id()), admin, Map.of("role", "EMPLOYEE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role", is("EMPLOYEE")));
        // yeniden yöneticiye yükseltilebilir
        call(patch("/api/employees/" + ikinci.id()), admin, Map.of("role", "ADMIN")).andExpect(status().isOk());
    }

    @Test
    void guncellemeDenetimKaydinaYazilir() throws Exception {
        long d = newDepartment("GuncDenetim");
        Emp e = newEmp("EMPLOYEE", d);
        call(patch("/api/employees/" + e.id()), admin, Map.of("title", "Denetimli")).andExpect(status().isOk());
        call(get("/api/audit?action=EMPLOYEE_UPDATED&size=100"), admin, null)
                .andExpect(jsonPath("$.content[*].entityId", hasItem(String.valueOf(e.id()))));
    }

    // ---------------------------------------------------------------- etkinleştirme

    @Test
    void pasifPersonelYenidenEtkinlestirilinceGirisYapabilir() throws Exception {
        long d = newDepartment("Etkin");
        Emp e = newEmp("EMPLOYEE", d);
        call(patch("/api/employees/" + e.id() + "/deactivate"), admin, null).andExpect(status().isOk());
        call(get("/api/employees/me"), e.token(), null).andExpect(status().isUnauthorized());

        call(patch("/api/employees/" + e.id() + "/activate"), admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.active", is(true)));
        call(get("/api/employees/me"), e.token(), null).andExpect(status().isOk());
        login(e.email(), PASSWORD);
        call(get("/api/audit?action=EMPLOYEE_ACTIVATED&size=100"), admin, null)
                .andExpect(jsonPath("$.content[*].entityId", hasItem(String.valueOf(e.id()))));
    }

    @Test
    void etkinlestirmeYalnizYoneticiyeAciktirOlmayanKayit404() throws Exception {
        long d = newDepartment("EtkinYetki");
        Emp e = newEmp("EMPLOYEE", d);
        Emp mudur = newEmp("MANAGER", d);
        call(patch("/api/employees/" + e.id() + "/activate"), mudur.token(), null).andExpect(status().isForbidden());
        call(patch("/api/employees/" + e.id() + "/activate"), e.token(), null).andExpect(status().isForbidden());
        call(patch("/api/employees/999999/activate"), admin, null).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- parola değiştirme

    private Map<String, Object> parola(String mevcut, String yeni) {
        return Map.of("currentPassword", mevcut, "newPassword", yeni);
    }

    @Test
    void parolaDegistirilinceEskisiyleGirilemezYenisiyleGirilir() throws Exception {
        Emp e = newEmp("EMPLOYEE", null);
        call(post("/api/auth/change-password"), e.token(), parola(PASSWORD, "Yeni-Parola-9876")).andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", e.email(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
        String yeniToken = login(e.email(), "Yeni-Parola-9876");
        call(get("/api/employees/me"), yeniToken, null).andExpect(status().isOk());
    }

    @Test
    void yanlisMevcutParolaReddedilirParolaDegismez() throws Exception {
        Emp e = newEmp("EMPLOYEE", null);
        call(post("/api/auth/change-password"), e.token(), parola("Yanlis-Parola-1", "Yeni-Parola-9876"))
                .andExpect(status().isConflict());
        login(e.email(), PASSWORD); // eski parola hâlâ geçerli
    }

    @Test
    void yeniParolaMevcutlaAyniOlamaz() throws Exception {
        Emp e = newEmp("EMPLOYEE", null);
        call(post("/api/auth/change-password"), e.token(), parola(PASSWORD, PASSWORD)).andExpect(status().isConflict());
    }

    @ParameterizedTest(name = "geçersiz yeni parola [{0}]")
    @ValueSource(strings = {"kisa", "1234567", ""})
    void kisaYeniParolaReddedilir(String yeni) throws Exception {
        Emp e = newEmp("EMPLOYEE", null);
        call(post("/api/auth/change-password"), e.token(), parola(PASSWORD, yeni)).andExpect(status().isBadRequest());
    }

    @Test
    void parolaUzunlukUstSiniri() throws Exception {
        Emp e = newEmp("EMPLOYEE", null);
        call(post("/api/auth/change-password"), e.token(), parola(PASSWORD, "p".repeat(73))).andExpect(status().isBadRequest());
        call(post("/api/auth/change-password"), e.token(), parola(PASSWORD, "p".repeat(72))).andExpect(status().isNoContent());
    }

    @Test
    void eksikAlanlarVeOturumsuzIstek() throws Exception {
        Emp e = newEmp("EMPLOYEE", null);
        call(post("/api/auth/change-password"), e.token(), Map.of("newPassword", "Yeni-Parola-9876")).andExpect(status().isBadRequest());
        call(post("/api/auth/change-password"), e.token(), Map.of("currentPassword", PASSWORD)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(parola(PASSWORD, "Yeni-Parola-9876"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void parolaDegisikligiDenetimKaydinaYazilirParolaIcermez() throws Exception {
        Emp e = newEmp("EMPLOYEE", null);
        String yeni = "Gizli-Yeni-Parola-55";
        call(post("/api/auth/change-password"), e.token(), parola(PASSWORD, yeni)).andExpect(status().isNoContent());
        String ham = call(get("/api/audit?action=PASSWORD_CHANGED&size=100"), admin, null)
                .andExpect(jsonPath("$.content[*].entityId", hasItem(String.valueOf(e.id()))))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(ham.contains(yeni));
        org.junit.jupiter.api.Assertions.assertFalse(ham.contains(PASSWORD));
    }

    @ParameterizedTest(name = "{0} kendi parolasını değiştirebilir")
    @CsvSource({"EMPLOYEE", "MANAGER", "ADMIN"})
    void tumRollerKendiParolasiniDegistirir(String rol) throws Exception {
        long d = newDepartment("ParolaRol");
        Emp e = newEmp(rol, "ADMIN".equals(rol) ? null : d);
        call(post("/api/auth/change-password"), e.token(), parola(PASSWORD, "Rol-Yeni-Parola-1")).andExpect(status().isNoContent());
        login(e.email(), "Rol-Yeni-Parola-1");
    }
}
