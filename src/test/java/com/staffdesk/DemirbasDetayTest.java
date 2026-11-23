package com.staffdesk;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Demirbaş ayrıntıları (seri no, satın alma, garanti), arama/süzgeç, güncelleme ve zimmetli kişi bilgisi. */
class DemirbasDetayTest extends ApiTestSupport {

    private Map<String, Object> ayrinti(String seri, String alim, String fiyat, String garanti) {
        Map<String, Object> m = new HashMap<>();
        m.put("serialNo", seri);
        m.put("purchaseDate", alim);
        m.put("purchasePrice", fiyat == null ? null : new java.math.BigDecimal(fiyat));
        m.put("warrantyEnd", garanti);
        return m;
    }

    @Test
    void ayrintiliDemirbasOlusturulurVeGarantiGunuHesaplanir() throws Exception {
        long id = newAsset("Dizüstü Bilgisayar", "Bilgisayar", ayrinti("SN-ABC-1", "2025-06-01", "42500.50", "2035-01-31"));
        // saat 2035-01-01: garanti bitişine 30 gün
        call(get("/api/assets?q=SN-ABC-1"), admin, null)
                .andExpect(jsonPath("$.page.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is((int) id)))
                .andExpect(jsonPath("$.content[0].serialNo", is("SN-ABC-1")))
                .andExpect(jsonPath("$.content[0].purchaseDate", is("2025-06-01")))
                .andExpect(jsonPath("$.content[0].purchasePrice", is(42500.5)))
                .andExpect(jsonPath("$.content[0].warrantyEnd", is("2035-01-31")))
                .andExpect(jsonPath("$.content[0].warrantyDaysLeft", is(30)));
    }

    @Test
    void ayrintisizDemirbasHalaOlusturulur() throws Exception {
        long id = newAsset("Eski kayıt", "Mobilya", null);
        call(get("/api/assets?q=Eski kayıt"), admin, null)
                .andExpect(jsonPath("$.content[0].id", is((int) id)))
                .andExpect(jsonPath("$.content[0].serialNo", nullValue()))
                .andExpect(jsonPath("$.content[0].warrantyDaysLeft", nullValue()))
                .andExpect(jsonPath("$.content[0].assignedTo", nullValue()));
    }

    @Test
    void garantiGecmisseGunSayisiNegatifOlur() throws Exception {
        newAsset("Garantisi bitmiş", "Telefon", ayrinti("SN-OLD", "2025-01-01", "100", "2034-12-22"));
        call(get("/api/assets?q=SN-OLD"), admin, null).andExpect(jsonPath("$.content[0].warrantyDaysLeft", is(-10)));
    }

    @ParameterizedTest(name = "geçersiz ayrıntı: {0}")
    @CsvSource({"fiyat negatif,-1,2025-01-01,2035-01-01", "gelecek alım,10,2099-01-01,2100-01-01"})
    void gecersizAyrintilarReddedilir(String ad, String fiyat, String alim, String garanti) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("assetTag", "GA-" + SEQ.incrementAndGet(), "name", ad, "category", "X"));
        body.putAll(ayrinti("S", alim, fiyat, garanti));
        call(post("/api/assets"), admin, body).andExpect(status().isBadRequest());
    }

    @Test
    void garantiBitisiAlimdenOnceOlamaz() throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("assetTag", "GB-" + SEQ.incrementAndGet(), "name", "x", "category", "X"));
        body.putAll(ayrinti(null, "2025-06-01", "10", "2025-05-01"));
        call(post("/api/assets"), admin, body).andExpect(status().isConflict());
    }

    @Test
    void seriNoUzunluguSinirlidir() throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("assetTag", "SU-" + SEQ.incrementAndGet(), "name", "x", "category", "X"));
        body.putAll(ayrinti("s".repeat(101), null, null, null));
        call(post("/api/assets"), admin, body).andExpect(status().isBadRequest());
    }

    @Test
    void aramaAdTagVeSeriNodaCalisir() throws Exception {
        String e = "Ara" + SEQ.incrementAndGet();
        newAsset("Monitör " + e, "Ekran", ayrinti("SERI-" + e, null, null, null));
        newAsset("Klavye " + e, "Çevre birimi", null);
        call(get("/api/assets?q=" + e.toLowerCase()), admin, null).andExpect(jsonPath("$.page.totalElements", is(2)));
        call(get("/api/assets?q=monitör " + e), admin, null).andExpect(jsonPath("$.page.totalElements", is(1)));
        call(get("/api/assets?q=SERI-" + e), admin, null).andExpect(jsonPath("$.page.totalElements", is(1)));
        call(get("/api/assets?q=" + e + "&category=Ekran"), admin, null).andExpect(jsonPath("$.page.totalElements", is(1)));
    }

    @ParameterizedTest(name = "joker [{0}]")
    @ValueSource(strings = {"%", "_", "%%", "a%b"})
    void jokerKarakterleriDuzMetindir(String q) throws Exception {
        newAsset("Joker denemesi", "X", null);
        call(get("/api/assets").param("q", q), admin, null).andExpect(jsonPath("$.page.totalElements", is(0)));
    }

    @Test
    void kategoriListesiTekrarsizVeSiralidir() throws Exception {
        String k = "Kat" + SEQ.incrementAndGet();
        newAsset("a", k, null);
        newAsset("b", k, null);
        String ham = call(get("/api/assets/categories"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem(k))).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(1, ham.split("\"" + k + "\"", -1).length - 1);
        List<String> kategoriler = com.jayway.jsonpath.JsonPath.read(ham, "$");
        List<String> sirali = kategoriler.stream().sorted().toList();
        org.junit.jupiter.api.Assertions.assertEquals(sirali, kategoriler);
    }

    @Test
    void garantisiYakindaBitenlerListelenirKayittanDusulenlerHaric() throws Exception {
        String e = "Gar" + SEQ.incrementAndGet();
        long yakin = newAsset("Yakın " + e, "G", ayrinti(null, null, null, "2035-02-15"));   // 45 gün
        newAsset("Uzak " + e, "G", ayrinti(null, null, null, "2036-01-01"));                  // 365 gün
        newAsset("Geçmiş " + e, "G", ayrinti(null, null, null, "2034-12-01"));                // -31 gün
        long dusulen = newAsset("Düşülen " + e, "G", ayrinti(null, null, null, "2035-01-10"));
        call(post("/api/assets/" + dusulen + "/retire"), admin, null).andExpect(status().isOk());

        call(get("/api/assets?q=" + e + "&warrantyWithinDays=60"), admin, null)
                .andExpect(jsonPath("$.page.totalElements", is(2)))
                .andExpect(jsonPath("$.content[*].name", hasItem("Yakın " + e)))
                .andExpect(jsonPath("$.content[*].name", hasItem("Geçmiş " + e)));
        call(get("/api/assets?q=" + e + "&warrantyWithinDays=10"), admin, null)
                .andExpect(jsonPath("$.page.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].name", is("Geçmiş " + e)));
        call(get("/api/assets?q=" + e + "&warrantyWithinDays=0"), admin, null)
                .andExpect(jsonPath("$.page.totalElements", is(1)));
        org.junit.jupiter.api.Assertions.assertTrue(yakin > 0);
    }

    @ParameterizedTest(name = "warrantyWithinDays={0} -> {1}")
    @CsvSource({"-1,400", "3651,400", "abc,400", "3650,200"})
    void garantiGunSiniri(String gun, int beklenen) throws Exception {
        call(get("/api/assets?warrantyWithinDays=" + gun), admin, null).andExpect(status().is(beklenen));
    }

    @Test
    void siralamaGarantiBitisineGore() throws Exception {
        String e = "Sira" + SEQ.incrementAndGet();
        newAsset("C " + e, "S", ayrinti(null, null, null, "2035-03-01"));
        newAsset("A " + e, "S", ayrinti(null, null, null, "2035-01-15"));
        newAsset("B " + e, "S", ayrinti(null, null, null, "2035-02-01"));
        List<String> artan = com.jayway.jsonpath.JsonPath.read(call(get("/api/assets?q=" + e + "&sort=warrantyEnd"), admin, null)
                .andReturn().getResponse().getContentAsString(), "$.content[*].name");
        org.junit.jupiter.api.Assertions.assertEquals(List.of("A " + e, "B " + e, "C " + e), artan);
        List<String> azalan = com.jayway.jsonpath.JsonPath.read(call(get("/api/assets?q=" + e + "&sort=name&desc=true"), admin, null)
                .andReturn().getResponse().getContentAsString(), "$.content[*].name");
        org.junit.jupiter.api.Assertions.assertEquals(List.of("C " + e, "B " + e, "A " + e), azalan);
        call(get("/api/assets?sort=serialNo"), admin, null).andExpect(status().isBadRequest());
    }

    @Test
    void guncellemeAyrintilariDegistirirDurumuDegistirmez() throws Exception {
        long id = newAsset("Eski ad", "Eski", null);
        Map<String, Object> govde = new HashMap<>(Map.of("name", "Yeni ad", "category", "Yeni"));
        govde.putAll(ayrinti("YENI-SN", "2025-01-01", "99.90", "2036-01-01"));
        call(patch("/api/assets/" + id), admin, govde)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Yeni ad")))
                .andExpect(jsonPath("$.category", is("Yeni")))
                .andExpect(jsonPath("$.serialNo", is("YENI-SN")))
                .andExpect(jsonPath("$.status", is("AVAILABLE")));
        // alanları boşaltmak mümkündür
        Map<String, Object> bos = new HashMap<>(Map.of("name", "Yeni ad", "category", "Yeni"));
        call(patch("/api/assets/" + id), admin, bos)
                .andExpect(jsonPath("$.serialNo", nullValue()))
                .andExpect(jsonPath("$.warrantyEnd", nullValue()));
    }

    @Test
    void guncellemeZorunluAlanlariDogrular() throws Exception {
        long id = newAsset("Ad", "Kat", null);
        call(patch("/api/assets/" + id), admin, Map.of("name", " ", "category", "Kat")).andExpect(status().isBadRequest());
        call(patch("/api/assets/" + id), admin, Map.of("name", "Ad", "category", "")).andExpect(status().isBadRequest());
        call(patch("/api/assets/999999"), admin, Map.of("name", "Ad", "category", "Kat")).andExpect(status().isNotFound());
        Map<String, Object> ters = new HashMap<>(Map.of("name", "Ad", "category", "Kat"));
        ters.putAll(ayrinti(null, "2025-06-01", null, "2025-01-01"));
        call(patch("/api/assets/" + id), admin, ters).andExpect(status().isConflict());
    }

    @Test
    void guncellemeKayittanDusulmusDemirbastaDaYapilabilir() throws Exception {
        long id = newAsset("Düşülecek", "K", null);
        call(post("/api/assets/" + id + "/retire"), admin, null).andExpect(status().isOk());
        call(patch("/api/assets/" + id), admin, Map.of("name", "Düşülmüş", "category", "K"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("RETIRED")));
    }

    @Test
    void yalnizYoneticiGunceller() throws Exception {
        long dept = newDepartment("Dmb");
        Emp mudur = newEmp("MANAGER", dept);
        Emp personel = newEmp("EMPLOYEE", dept);
        long id = newAsset("Ad", "Kat", null);
        for (Emp e : List.of(mudur, personel)) {
            call(patch("/api/assets/" + id), e.token(), Map.of("name", "X", "category", "Y")).andExpect(status().isForbidden());
        }
        call(get("/api/assets"), mudur.token(), null).andExpect(status().isOk());
        call(get("/api/assets/categories"), mudur.token(), null).andExpect(status().isOk());
        call(get("/api/assets"), personel.token(), null).andExpect(status().isForbidden());
        call(get("/api/assets/categories"), personel.token(), null).andExpect(status().isForbidden());
    }

    @Test
    void zimmetliKisiListedeGorunurIadedeKaybolur() throws Exception {
        long dept = newDepartment("Zml");
        Emp kisi = newEmp("EMPLOYEE", dept, "2020-01-15", "Zimmet Sahibi");
        String e = "Zimmet" + SEQ.incrementAndGet();
        long id = newAsset(e, "Telefon", null);
        call(post("/api/assets/" + id + "/assign"), admin, Map.of("employeeId", kisi.id())).andExpect(status().isOk());

        call(get("/api/assets?q=" + e), admin, null)
                .andExpect(jsonPath("$.content[0].status", is("ASSIGNED")))
                .andExpect(jsonPath("$.content[0].assignedTo", is("Zimmet Sahibi")))
                .andExpect(jsonPath("$.content[0].assignedToId", is((int) kisi.id())));
        call(post("/api/assets/" + id + "/return"), admin, null).andExpect(status().isOk());
        call(get("/api/assets?q=" + e), admin, null)
                .andExpect(jsonPath("$.content[0].status", is("AVAILABLE")))
                .andExpect(jsonPath("$.content[0].assignedTo", nullValue()));
    }

    @Test
    void durumSuzgeciAramayla_BirlestirilebilirVeDenetimeYazilir() throws Exception {
        String e = "Drm" + SEQ.incrementAndGet();
        long a = newAsset("Boş " + e, "Z", null);
        long b = newAsset("Düşen " + e, "Z", null);
        call(post("/api/assets/" + b + "/retire"), admin, null).andExpect(status().isOk());
        call(get("/api/assets?q=" + e + "&status=RETIRED"), admin, null)
                .andExpect(jsonPath("$.page.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is((int) b)));
        call(get("/api/assets?q=" + e + "&status=AVAILABLE"), admin, null)
                .andExpect(jsonPath("$.content[0].id", is((int) a)));
        call(patch("/api/assets/" + a), admin, Map.of("name", "Güncel " + e, "category", "Z")).andExpect(status().isOk());
        call(get("/api/audit?action=ASSET_UPDATED&size=100"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].entityId", hasItem(String.valueOf(a))));
        call(get("/api/assets?q=" + e), admin, null).andExpect(jsonPath("$.content[0].id", notNullValue()));
    }
}
