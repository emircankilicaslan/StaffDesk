package com.staffdesk;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Personel listesi: arama, süzgeç, sıralama, sayfalama ve müdür kapsamı. */
class PersonelAramaTest extends ApiTestSupport {

    private long dept;
    private Emp ayse;
    private Emp mehmet;
    private Emp zeynep;

    private void hazirla() throws Exception {
        dept = newDepartment("Arama");
        ayse = newEmp("EMPLOYEE", dept, "2021-03-01", "Ayşe Yıldız");
        mehmet = newEmp("EMPLOYEE", dept, "2019-05-10", "Mehmet Kaya");
        zeynep = newEmp("MANAGER", dept, "2015-09-01", "Zeynep Arslan");
    }

    private List<String> adlar(String sorgu) throws Exception {
        String yanit = call(get("/api/employees?departmentId=" + dept + "&" + sorgu), admin, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(yanit, "$.content[*].fullName");
    }

    @ParameterizedTest(name = "ara [{0}] -> {1} kayıt")
    @CsvSource({"ayşe,1", "AYŞE,1", "yıldız,1", "kaya,1", "a,3", "arslan,1", "bulunmayan,0", "zeynep arslan,1"})
    void adaGoreAramaBuyukKucukHarfDuyarsizdir(String q, int adet) throws Exception {
        hazirla();
        call(get("/api/employees").param("departmentId", String.valueOf(dept)).param("q", q), admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements", is(adet)));
    }

    @Test
    void ePostaIleAranir() throws Exception {
        hazirla();
        call(get("/api/employees?q=" + ayse.email()), admin, null)
                .andExpect(jsonPath("$.page.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is((int) ayse.id())));
        call(get("/api/employees?q=" + mehmet.email().toUpperCase()), admin, null)
                .andExpect(jsonPath("$.page.totalElements", is(1)));
    }

    @Test
    void sicilNoIleAranir() throws Exception {
        hazirla();
        String sicil = read(call(get("/api/employees/" + zeynep.id()), admin, null), "$.registrationNo");
        call(get("/api/employees?q=" + sicil), admin, null)
                .andExpect(jsonPath("$.page.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].fullName", is("Zeynep Arslan")));
    }

    @ParameterizedTest(name = "joker [{0}] düz metin sayılır")
    @ValueSource(strings = {"%", "_", "%%", "a%b"})
    void jokerKarakterleriDuzMetindir(String q) throws Exception {
        hazirla();
        call(get("/api/employees").param("departmentId", String.valueOf(dept)).param("q", q), admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements", is(0)));
    }

    @Test
    void rolVeAktiflikSuzgeci() throws Exception {
        hazirla();
        call(get("/api/employees?departmentId=" + dept + "&role=MANAGER"), admin, null)
                .andExpect(jsonPath("$.page.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].fullName", is("Zeynep Arslan")));
        call(patch("/api/employees/" + mehmet.id() + "/deactivate"), admin, null).andExpect(status().isOk());
        call(get("/api/employees?departmentId=" + dept + "&active=false"), admin, null)
                .andExpect(jsonPath("$.page.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].fullName", is("Mehmet Kaya")));
        call(get("/api/employees?departmentId=" + dept + "&active=true"), admin, null)
                .andExpect(jsonPath("$.page.totalElements", is(2)));
    }

    @Test
    void suzgeclerBirlestirilebilir() throws Exception {
        hazirla();
        call(get("/api/employees?departmentId=" + dept + "&role=EMPLOYEE&q=kaya&active=true"), admin, null)
                .andExpect(jsonPath("$.page.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].fullName", is("Mehmet Kaya")));
    }

    @Test
    void siralama() throws Exception {
        hazirla();
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("Ayşe Yıldız", "Mehmet Kaya", "Zeynep Arslan"), adlar("sort=name"));
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("Zeynep Arslan", "Mehmet Kaya", "Ayşe Yıldız"), adlar("sort=name&desc=true"));
        // işe giriş: Zeynep 2015, Mehmet 2019, Ayşe 2021
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("Zeynep Arslan", "Mehmet Kaya", "Ayşe Yıldız"), adlar("sort=hireDate"));
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("Ayşe Yıldız", "Mehmet Kaya", "Zeynep Arslan"), adlar("sort=hireDate&desc=true"));
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("Ayşe Yıldız", "Mehmet Kaya", "Zeynep Arslan"), adlar("sort=id"));
    }

    @ParameterizedTest(name = "geçersiz sıralama [{0}]")
    @ValueSource(strings = {"password", "id;drop", "email", "NAME"})
    void gecersizSiralamaReddedilir(String sort) throws Exception {
        call(get("/api/employees?sort=" + sort), admin, null).andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "sayfa {0} boyut {1} -> {2}")
    @CsvSource({"-1,10,400", "0,0,400", "0,101,400", "0,100,200", "0,1,200", "5,10,200"})
    void sayfalamaSiniri(int sayfa, int boyut, int beklenen) throws Exception {
        call(get("/api/employees?page=" + sayfa + "&size=" + boyut), admin, null).andExpect(status().is(beklenen));
    }

    @Test
    void sayfalamaSayfalariBolerToplamDogrudur() throws Exception {
        hazirla();
        call(get("/api/employees?departmentId=" + dept + "&size=2&page=0&sort=name"), admin, null)
                .andExpect(jsonPath("$.content.length()", is(2)))
                .andExpect(jsonPath("$.page.totalElements", is(3)))
                .andExpect(jsonPath("$.page.totalPages", is(2)));
        call(get("/api/employees?departmentId=" + dept + "&size=2&page=1&sort=name"), admin, null)
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.content[0].fullName", is("Zeynep Arslan")));
    }

    @Test
    void mudurYalnizcaKendiBiriminiGorurBaskaBirimIstegindeBile() throws Exception {
        hazirla();
        long baska = newDepartment("Baska");
        newEmp("EMPLOYEE", baska, "2022-01-10", "Başka Birimli");

        call(get("/api/employees"), zeynep.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].department", everyItem(is(
                        (String) read(call(get("/api/employees/" + zeynep.id()), admin, null), "$.department")))));
        // başka birimin kimliği verilse de müdürün kapsamı değişmez
        call(get("/api/employees?departmentId=" + baska), zeynep.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", is(3)));
        call(get("/api/employees?q=Başka"), zeynep.token(), null)
                .andExpect(jsonPath("$.page.totalElements", is(0)));
    }

    @Test
    void personelListeyiGoremez() throws Exception {
        hazirla();
        call(get("/api/employees"), ayse.token(), null).andExpect(status().isForbidden());
        call(get("/api/employees?q=a"), ayse.token(), null).andExpect(status().isForbidden());
    }

    @Test
    void aramaYanitiSifreAlaniIcermez() throws Exception {
        hazirla();
        String ham = call(get("/api/employees?departmentId=" + dept), admin, null).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(ham.toLowerCase().contains("password"));
        org.junit.jupiter.api.Assertions.assertFalse(ham.contains("$2a$"));
        call(get("/api/employees?departmentId=" + dept), admin, null)
                .andExpect(jsonPath("$.content[*].email", hasItem(ayse.email())))
                .andExpect(jsonPath("$.content[*].fullName", contains("Ayşe Yıldız", "Mehmet Kaya", "Zeynep Arslan")));
    }
}
