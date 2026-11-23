package com.staffdesk;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Rol bazlı özet panosu: kapsam (ALL / DEPARTMENT / SELF) ve hesaplanan sayılar. Saat: 2035-01-01 Pazartesi. */
class PanoTest extends ApiTestSupport {

    @Test
    void oturumsuzReddedilir() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void mudurYalnizcaKendiBiriminiGorur() throws Exception {
        long dept = newDepartment("Pano");
        Emp mudur = newEmp("MANAGER", dept, "2015-01-01", "Pano Müdürü");
        Emp a = newEmp("EMPLOYEE", dept);
        Emp b = newEmp("EMPLOYEE", dept);
        // başka birimdeki hareket müdürün özetini etkilemez
        long baska = newDepartment("PanoBaska");
        Emp yabanci = newEmp("EMPLOYEE", baska);
        requestLeave(yabanci, "EXCUSE", "2035-01-23", "2035-01-23");

        call(get("/api/dashboard"), mudur.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope", is("DEPARTMENT")))
                .andExpect(jsonPath("$.activeEmployees", is(3)))
                .andExpect(jsonPath("$.onLeaveToday", is(0)))
                .andExpect(jsonPath("$.pendingLeaves", is(0)))
                .andExpect(jsonPath("$.assetsAvailable", is(0)))
                .andExpect(jsonPath("$.monthly", hasSize(6)));
        org.junit.jupiter.api.Assertions.assertNotEquals(a.id(), b.id());
    }

    @Test
    void bugunIzinliVeBekleyenSayilariHesaplanir() throws Exception {
        long dept = newDepartment("PanoIzin");
        Emp mudur = newEmp("MANAGER", dept);
        Emp a = newEmp("EMPLOYEE", dept);
        Emp b = newEmp("EMPLOYEE", dept);
        // a: bugün (1 Ocak 2035 Pzt) başlayan, onaylı izin; b: ileri tarihli bekleyen talep
        long izin = requestLeave(a, "EXCUSE", "2035-01-01", "2035-01-02");
        call(post("/api/leaves/" + izin + "/approve"), mudur.token(), null).andExpect(status().isOk());
        requestLeave(b, "EXCUSE", "2035-01-24", "2035-01-24");

        call(get("/api/dashboard"), mudur.token(), null)
                .andExpect(jsonPath("$.onLeaveToday", is(1)))
                .andExpect(jsonPath("$.pendingLeaves", is(1)));
        // iptal edilen talep bekleyenlerden düşer
        long ikinci = requestLeave(b, "EXCUSE", "2035-01-25", "2035-01-25");
        call(get("/api/dashboard"), mudur.token(), null).andExpect(jsonPath("$.pendingLeaves", is(2)));
        call(post("/api/leaves/" + ikinci + "/cancel"), b.token(), null).andExpect(status().isOk());
        call(get("/api/dashboard"), mudur.token(), null).andExpect(jsonPath("$.pendingLeaves", is(1)));
    }

    @Test
    void aylikOzetOnayliIzinleriBaslangicAyinaGoreSayar() throws Exception {
        long dept = newDepartment("PanoAylik");
        Emp mudur = newEmp("MANAGER", dept);
        Emp a = newEmp("EMPLOYEE", dept);
        long id = requestLeave(a, "EXCUSE", "2035-01-08", "2035-01-10"); // 3 iş günü
        call(post("/api/leaves/" + id + "/approve"), mudur.token(), null).andExpect(status().isOk());
        requestLeave(a, "EXCUSE", "2035-01-29", "2035-01-30"); // bekleyen: aylık özete girmez

        call(get("/api/dashboard"), mudur.token(), null)
                .andExpect(jsonPath("$.monthly", hasSize(6)))
                .andExpect(jsonPath("$.monthly[0].month", is("2034-08")))
                .andExpect(jsonPath("$.monthly[5].month", is("2035-01")))
                .andExpect(jsonPath("$.monthly[5].requests", is(1)))
                .andExpect(jsonPath("$.monthly[5].businessDays", is(3)))
                .andExpect(jsonPath("$.monthly[4].requests", is(0)));
    }

    @Test
    void izinTurDagilimiYalnizOnayliVeBuYildir() throws Exception {
        long dept = newDepartment("PanoTur");
        Emp mudur = newEmp("MANAGER", dept);
        Emp a = newEmp("EMPLOYEE", dept);
        long e1 = requestLeave(a, "EXCUSE", "2035-01-08", "2035-01-08");
        long e2 = requestLeave(a, "EXCUSE", "2035-01-09", "2035-01-09");
        long s1 = requestLeave(a, "SICK", "2035-01-10", "2035-01-10");
        requestLeave(a, "ANNUAL", "2035-01-11", "2035-01-11"); // bekleyen: sayılmaz
        for (long id : new long[] {e1, e2, s1}) {
            call(post("/api/leaves/" + id + "/approve"), mudur.token(), null).andExpect(status().isOk());
        }
        call(get("/api/dashboard"), mudur.token(), null)
                .andExpect(jsonPath("$.leaveByType", hasSize(2)))
                .andExpect(jsonPath("$.leaveByType[?(@.label=='EXCUSE')].count", hasItem(2)))
                .andExpect(jsonPath("$.leaveByType[?(@.label=='SICK')].count", hasItem(1)));
    }

    @Test
    void personelYalnizKendiVerisiniGorur() throws Exception {
        long dept = newDepartment("PanoKendi");
        Emp mudur = newEmp("MANAGER", dept);
        Emp a = newEmp("EMPLOYEE", dept);
        Emp b = newEmp("EMPLOYEE", dept);
        long izin = requestLeave(a, "EXCUSE", "2035-01-01", "2035-01-01");
        call(post("/api/leaves/" + izin + "/approve"), mudur.token(), null).andExpect(status().isOk());
        requestLeave(b, "EXCUSE", "2035-01-26", "2035-01-26");

        call(get("/api/dashboard"), a.token(), null)
                .andExpect(jsonPath("$.scope", is("SELF")))
                .andExpect(jsonPath("$.activeEmployees", is(1)))
                .andExpect(jsonPath("$.onLeaveToday", is(1)))
                .andExpect(jsonPath("$.pendingLeaves", is(0)));
        call(get("/api/dashboard"), b.token(), null)
                .andExpect(jsonPath("$.scope", is("SELF")))
                .andExpect(jsonPath("$.onLeaveToday", is(0)))
                .andExpect(jsonPath("$.pendingLeaves", is(1)));
    }

    @Test
    void personelinZimmetSayisiKendisininDir() throws Exception {
        long dept = newDepartment("PanoZimmet");
        Emp a = newEmp("EMPLOYEE", dept);
        Emp b = newEmp("EMPLOYEE", dept);
        long x = newAsset("Zimmet x " + SEQ.incrementAndGet(), "Z", null);
        long y = newAsset("Zimmet y " + SEQ.incrementAndGet(), "Z", null);
        call(post("/api/assets/" + x + "/assign"), admin, Map.of("employeeId", a.id())).andExpect(status().isOk());
        call(post("/api/assets/" + y + "/assign"), admin, Map.of("employeeId", a.id())).andExpect(status().isOk());
        call(get("/api/dashboard"), a.token(), null).andExpect(jsonPath("$.assetsAssigned", is(2)))
                .andExpect(jsonPath("$.assetsAvailable", is(0)));
        call(get("/api/dashboard"), b.token(), null).andExpect(jsonPath("$.assetsAssigned", is(0)));
        call(post("/api/assets/" + x + "/return"), admin, null).andExpect(status().isOk());
        call(get("/api/dashboard"), a.token(), null).andExpect(jsonPath("$.assetsAssigned", is(1)));
    }

    @Test
    void yoneticiTumKurumuVeDemirbaslariGorur() throws Exception {
        long dept = newDepartment("PanoYonetici");
        newEmp("EMPLOYEE", dept);
        String kat = "PanoKat" + SEQ.incrementAndGet();
        long a = newAsset("Pano A", kat, Map.of("warrantyEnd", "2035-02-01"));
        newAsset("Pano B", kat, null);
        long c = newAsset("Pano C", kat, null);
        call(post("/api/assets/" + c + "/retire"), admin, null).andExpect(status().isOk());

        call(get("/api/dashboard"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope", is("ALL")))
                .andExpect(jsonPath("$.activeEmployees", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.assetsAvailable", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.assetsRetired", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.warrantyExpiring", greaterThanOrEqualTo(1)))
                // kategori dağılımı kayıttan düşülenleri saymaz: 2 kayıt
                .andExpect(jsonPath("$.assetsByCategory[?(@.label=='" + kat + "')].count", hasItem(2)));
        org.junit.jupiter.api.Assertions.assertTrue(a > 0);
    }

    @Test
    void demirbasSayilariIslemlerleDegisir() throws Exception {
        long dept = newDepartment("PanoDeg");
        Emp kisi = newEmp("EMPLOYEE", dept);
        long once = ((Number) read(call(get("/api/dashboard"), admin, null), "$.assetsAssigned")).longValue();
        long id = newAsset("Sayaç " + SEQ.incrementAndGet(), "Sayaç", null);
        call(post("/api/assets/" + id + "/assign"), admin, Map.of("employeeId", kisi.id())).andExpect(status().isOk());
        call(get("/api/dashboard"), admin, null).andExpect(jsonPath("$.assetsAssigned", is((int) (once + 1))));
        call(post("/api/assets/" + id + "/return"), admin, null).andExpect(status().isOk());
        call(get("/api/dashboard"), admin, null).andExpect(jsonPath("$.assetsAssigned", is((int) once)));
    }

    @Test
    void pasifPersonelAktifSayisindanDuserTekrarAktifEdilinceGeriGelir() throws Exception {
        long dept = newDepartment("PanoPasif");
        Emp mudur = newEmp("MANAGER", dept);
        Emp a = newEmp("EMPLOYEE", dept);
        call(get("/api/dashboard"), mudur.token(), null).andExpect(jsonPath("$.activeEmployees", is(2)));
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/employees/" + a.id() + "/deactivate"),
                admin, null).andExpect(status().isOk());
        call(get("/api/dashboard"), mudur.token(), null).andExpect(jsonPath("$.activeEmployees", is(1)));
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/employees/" + a.id() + "/activate"),
                admin, null).andExpect(status().isOk());
        call(get("/api/dashboard"), mudur.token(), null).andExpect(jsonPath("$.activeEmployees", is(2)));
    }

    @Test
    void birimsizMudurPersonelKapsaminaDuser() throws Exception {
        // Birimsiz bir MANAGER oluşturulamaz; birimsiz personel SELF kapsamındadır.
        Emp tek = newEmp("EMPLOYEE", null, "2020-01-01", "Birimsiz");
        call(get("/api/dashboard"), tek.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope", is("SELF")));
    }
}
