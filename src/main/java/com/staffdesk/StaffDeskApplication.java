package com.staffdesk;

import com.staffdesk.config.StaffDeskProperties;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@EnableConfigurationProperties(StaffDeskProperties.class)
public class StaffDeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(StaffDeskApplication.class, args);
    }

    /** Zaman bağımlılığı enjekte edilir; testlerde sabitlenebilir. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
