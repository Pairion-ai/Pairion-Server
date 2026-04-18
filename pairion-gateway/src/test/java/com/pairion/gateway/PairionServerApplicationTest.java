package com.pairion.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** Verifies the Spring Boot application context loads successfully. */
@SpringBootTest
class PairionServerApplicationTest {

    @Autowired private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void mainMethodRuns() {
        PairionServerApplication.main(new String[] {"--server.port=0"});
    }
}
