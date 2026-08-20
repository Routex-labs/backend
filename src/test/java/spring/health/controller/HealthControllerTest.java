package spring.health.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import spring.TestcontainersConfiguration;

/** 헬스 응답 스키마는 Flutter가 파싱한다 — 키 이름까지 계약이다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HealthControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("liveness 두 경로는 의존성과 무관하게 ok다")
    void liveness() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
        mockMvc.perform(get("/health/live")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("readiness는 DB를 확인하고 embedding_model은 snake_case로 나간다")
    void readiness() throws Exception {
        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.database").value("ok"))
                // search 이식 전이라 unknown 고정. 키 이름이 camelCase로 새면 여기서 깨진다.
                .andExpect(jsonPath("$.embedding_model").value("unknown"));
    }
}
