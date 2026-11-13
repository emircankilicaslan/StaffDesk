package com.staffdesk;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Statik web arayüzünün sunumu ve güvenlik başlıkları; veri uçları arayüz eklendikten sonra da korunmalı. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebArayuzTest {

    @Autowired MockMvc mvc;

    @Test
    void arayuzDosyalariKimlikDogrulamasizSunulur() throws Exception {
        for (String yol : new String[] {"/", "/index.html", "/app.js", "/app.css", "/favicon.svg"}) {
            mvc.perform(get(yol)).andExpect(status().isOk());
        }
    }

    @Test
    void arayuzIcerikGuvenlikPolitikasiIleSunulurVeSatirIciBetikIcermez() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", not(containsString("unsafe-inline"))))
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(not(containsString(" style="))))
                .andExpect(content().string(not(containsString(" onclick="))));
    }

    @Test
    void swaggerSayfalariIcinCspUygulanmaz() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(header().doesNotExist("Content-Security-Policy"));
    }

    @Test
    void veriUclariArayuzdenSonraDaKorunur() throws Exception {
        mvc.perform(get("/api/employees/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/leaves/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reports/leave-usage")).andExpect(status().isUnauthorized());
    }
}
