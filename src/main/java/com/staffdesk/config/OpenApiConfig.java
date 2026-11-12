package com.staffdesk.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger arayüzünde "Authorize" düğmesi: /api/auth/login ile alınan JWT buraya girilir. */
@Configuration
class OpenApiConfig {

    private static final String SCHEME = "bearerAuth";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("StaffDesk API").version("1.0.0")
                        .description("Personel izin ve zimmet yönetimi. Önce /api/auth/login ile token alın, "
                                + "sağ üstteki Authorize düğmesine yapıştırın."))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}