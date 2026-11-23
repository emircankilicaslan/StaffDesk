package com.staffdesk;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** İzin listesi süzgeçleri ve ekip takvimi (saat: 2035-01-01 Pazartesi; 17 Ocak resmi tatil). */
class IzinSuzgecVeTakvimTest extends ApiTestSupport {

    private long dept;
    private Emp mudur;
    private Emp ali;
    private Emp veli;

    private void hazirla() throws Exception {
        dept = newDepartment("Izin");
        mudur = newEmp("MANAGER", dept, "2015-01-01", "Müdür Kişi");
        ali = newEmp("EMPLOYEE", dept, "2018-01-01", "Ali Demir");
        veli = newEmp("EMPLOYEE", dept, "2018-01-01", "Veli Kaya");
    }

    /** Ali: yıllık 2035-01-08..01-10 (onaylı); Veli: mazeret 2035-01-22 (bekleyen), hastalık 2035-01-02 (reddedilecek). */
    private long[] veriYukle() throws Exception {
        long a = requestLeave(ali, "ANNUAL", "2035-01-08", "2035-01-10");
        call(post("/api/leaves/" + a + "/approve"), mudur.token(), null).andExpect(status().isOk());
        long v1 = requestLeave(veli, "EXCUSE", "2035-01-22", "2035-01-22");
        long v2 = requestLeave(veli, "SICK", "2035-01-02", "2035-01-02");
        call(post("/api/leaves/" + v2 + "/reject"), mudur.token(), Map.of("note", "Rapor yok")).andExpect(status().isOk());
        return new long[] {a, v1, v2};
    }

    private String liste(String sorgu, String token) throws Exception {
        return call(get("/api/leaves?" + sorgu), token, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ---------------------------------------------------------------- liste süzgeçleri

    @Test
    void varsayilanDurumBekleyenlerdir() throws Exception {
        hazirla();
        long[] k = veriYukle();
        call(get("/api/leaves"), mudur.token(), null)
                .andExpect(jsonPath("$.page.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is((int) k[1])));
    }

    @ParameterizedTest(name = "durum {0} -> {1} kayıt")
    @CsvSource({"PENDING,1", "APPROVED,1", "REJECTED,1", "CANCELLED,0", "ALL,3", "all,3", "approved,1"})
    void durumSuzgeci(String durum, int adet) throws Exception {
        hazirla();
        veriYukle();
        call(get("/api/leaves?status=" + durum), mudur.token(), null)
                .andExpect(jsonPath("$.page.totalElements", is(adet)));
    }

    @Test
    void gecersizDurumReddedilir() throws Exception {
        hazirla();
        call(get("/api/leaves?status=YOK"), mudur.token(), null).andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "tür {0} -> {1} kayıt")
    @CsvSource({"ANNUAL,1", "EXCUSE,1", "SICK,1"})
    void turSuzgeci(String tur, int adet) throws Exception {
        hazirla();
        veriYukle();
        call(get("/api/leaves?status=ALL&type=" + tur), mudur.token(), null)
                .andExpect(jsonPath("$.page.totalElements", is(adet)));
    }

    @Test
    void personelVeAdAramaSuzgeci() throws Exception {
        hazirla();
        veriYukle();
        call(get("/api/leaves?status=ALL&employeeId=" + veli.id()), mudur.token(), null)
                .andExpect(jsonPath("$.page.totalElements", is(2)));
        call(get("/api/leaves").param("status", "ALL").param("q", "DEMIR"), mudur.token(), null)
                .andExpect(jsonPath("$.page.totalElements", is(1)));
        call(get("/api/leaves").param("status", "ALL").param("q", "demir"), mudur.token(), null)
                .andExpect(jsonPath("$.page.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].employeeName", is("Ali Demir")));
        call(get("/api/leaves").param("status", "ALL").param("q", "%"), mudur.token(), null)
                .andExpect(jsonPath("$.page.totalElements", is(0)));
    }

    @Test
    void tarihAraligiCakisanIzinleriGetirir() throws Exception {
        hazirla();
        veriYukle();
        // Ali'nin izni 8-10 Ocak
        call(get("/api/leaves?status=ALL&from=2035-01-10&to=2035-01-10"), mudur.token(), null)
                .andExpect(jsonPath("$.page.totalElements", is(1)));
        call(get("/api/leaves?status=ALL&from=2035-01-11&to=2035-01-21"), mudur.token(), null)
                .andExpect(jsonPath("$.page.totalElements", is(0)));
        call(get("/api/leaves?status=ALL&from=2035-01-09"), mudur.token(), null)
                .andExpect(jsonPath("$.page.totalElements", is(2))); // Ali (8-10) ve Veli (22)
        call(get("/api/leaves?status=ALL&to=2035-01-05"), mudur.token(), null)
                .andExpect(jsonPath("$.page.totalElements", is(1))); // yalnız Veli'nin 2 Ocak hastalığı
    }

    @Test
    void tarihAraligiTersseReddedilir() throws Exception {
        hazirla();
        call(get("/api/leaves?from=2035-02-01&to=2035-01-01"), mudur.token(), null).andExpect(status().isBadRequest());
        call(get("/api/leaves?from=2035-13-01"), mudur.token(), null).andExpect(status().isBadRequest());
    }

    @Test
    void siralamaEnYeniBaslangicUstte() throws Exception {
        hazirla();
        veriYukle();
        String ham = liste("status=ALL", mudur.token());
        java.util.List<String> baslangiclar = com.jayway.jsonpath.JsonPath.read(ham, "$.content[*].startDate");
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("2035-01-22", "2035-01-08", "2035-01-02"), baslangiclar);
    }

    @Test
    void mudurYalnizcaKendiBirimininIzinleriniGorurSuzgecBileAsamaz() throws Exception {
        hazirla();
        veriYukle();
        long baska = newDepartment("BaskaIzin");
        Emp yabanci = newEmp("EMPLOYEE", baska);
        requestLeave(yabanci, "EXCUSE", "2035-01-23", "2035-01-23");
        call(get("/api/leaves?status=ALL"), mudur.token(), null).andExpect(jsonPath("$.page.totalElements", is(3)));
        call(get("/api/leaves?status=ALL&employeeId=" + yabanci.id()), mudur.token(), null)
                .andExpect(jsonPath("$.page.totalElements", is(0)));
        call(get("/api/leaves?status=ALL&employeeId=" + yabanci.id()), admin, null)
                .andExpect(jsonPath("$.page.totalElements", is(1)));
    }

    @Test
    void personelTumListeyiGoremez() throws Exception {
        hazirla();
        call(get("/api/leaves"), ali.token(), null).andExpect(status().isForbidden());
        call(get("/api/leaves?status=ALL"), ali.token(), null).andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "sayfa {0} boyut {1} -> {2}")
    @CsvSource({"-1,10,400", "0,0,400", "0,101,400", "0,50,200"})
    void sayfalamaSiniri(int sayfa, int boyut, int beklenen) throws Exception {
        call(get("/api/leaves?status=ALL&page=" + sayfa + "&size=" + boyut), admin, null).andExpect(status().is(beklenen));
    }

    // ---------------------------------------------------------------- ekip takvimi

    @Test
    void takvimBirimdekiIzinleriGosterirGerekceVermez() throws Exception {
        hazirla();
        veriYukle();
        String ham = call(get("/api/leaves/calendar?month=2035-01"), mudur.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))) // Ali onaylı, Veli bekleyen (reddedilen takvimde yok)
                .andExpect(jsonPath("$[0].employeeName", is("Ali Demir")))
                .andExpect(jsonPath("$[0].status", is("APPROVED")))
                .andExpect(jsonPath("$[0].startDate", is("2035-01-08")))
                .andExpect(jsonPath("$[1].employeeName", is("Veli Kaya")))
                .andExpect(jsonPath("$[1].status", is("PENDING")))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(ham.contains("reason"));
        org.junit.jupiter.api.Assertions.assertFalse(ham.contains("Rapor yok"));
    }

    @Test
    void personelTakvimdeYalnizcaBirimininOnayliIzinlerineBakar() throws Exception {
        hazirla();
        veriYukle();
        call(get("/api/leaves/calendar?month=2035-01"), veli.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employeeName", is("Ali Demir")))
                .andExpect(jsonPath("$[0].status", is("APPROVED")))
                .andExpect(jsonPath("$[0].department", is(
                        (String) read(call(get("/api/employees/" + ali.id()), admin, null), "$.department"))));
    }

    @Test
    void takvimBaskaBirimiGostermezYoneticiHepsiniGorur() throws Exception {
        hazirla();
        veriYukle();
        long baska = newDepartment("TakvimBaska");
        Emp yabanci = newEmp("EMPLOYEE", baska, "2019-01-01", "Yabancı Kişi");
        long id = requestLeave(yabanci, "EXCUSE", "2035-01-24", "2035-01-24");
        call(post("/api/leaves/" + id + "/approve"), admin, null).andExpect(status().isOk());

        call(get("/api/leaves/calendar?month=2035-01"), ali.token(), null)
                .andExpect(jsonPath("$[*].employeeName", not(hasItem("Yabancı Kişi"))));
        call(get("/api/leaves/calendar?month=2035-01"), admin, null)
                .andExpect(jsonPath("$[*].employeeName", hasItem("Yabancı Kişi")))
                .andExpect(jsonPath("$[*].employeeName", hasItem("Ali Demir")));
    }

    @Test
    void ayaSiniranIzinHerIkiAydaGorunur() throws Exception {
        hazirla();
        // 2035-01-30 Salı .. 2035-02-02 Cuma
        long id = requestLeave(ali, "EXCUSE", "2035-01-30", "2035-02-02");
        call(post("/api/leaves/" + id + "/approve"), mudur.token(), null).andExpect(status().isOk());
        call(get("/api/leaves/calendar?month=2035-01"), mudur.token(), null)
                .andExpect(jsonPath("$[*].endDate", hasItem("2035-02-02")));
        call(get("/api/leaves/calendar?month=2035-02"), mudur.token(), null)
                .andExpect(jsonPath("$[*].startDate", hasItem("2035-01-30")));
        call(get("/api/leaves/calendar?month=2035-03"), mudur.token(), null)
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ayVerilmezseSaatinAyiKullanilir() throws Exception {
        hazirla();
        long id = requestLeave(ali, "EXCUSE", "2035-01-15", "2035-01-15");
        call(post("/api/leaves/" + id + "/approve"), mudur.token(), null).andExpect(status().isOk());
        call(get("/api/leaves/calendar"), mudur.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startDate", is("2035-01-15")));
    }

    @ParameterizedTest(name = "geçersiz ay [{0}]")
    @ValueSource(strings = {"2035-13", "2035", "ocak", "2035-1", "2035-01-01"})
    void gecersizAyReddedilir(String ay) throws Exception {
        call(get("/api/leaves/calendar").param("month", ay), admin, null).andExpect(status().isBadRequest());
    }

    @Test
    void takvimOturumsuzReddedilirBirimsizPersonelYalnizKendiniGorur() throws Exception {
        mvc.perform(get("/api/leaves/calendar")).andExpect(status().isUnauthorized());
        Emp tek = newEmp("EMPLOYEE", null, "2020-01-01", "Birimsiz Kişi");
        long id = requestLeave(tek, "EXCUSE", "2035-01-25", "2035-01-25");
        call(post("/api/leaves/" + id + "/approve"), admin, null).andExpect(status().isOk());
        call(get("/api/leaves/calendar?month=2035-01"), tek.token(), null)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].department", nullValue()))
                .andExpect(jsonPath("$[0].employeeName", is("Birimsiz Kişi")));
    }
}
