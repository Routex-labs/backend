package spring.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import spring.TestcontainersConfiguration;

/**
 * Flutter 웹은 브라우저에서 도는 별도 출처라, CORS가 없으면 <b>단 하나의 요청도 못 붙는다.</b>
 * 실행마다 포트가 바뀌므로 포트를 열어 두는 것이 개발 기본값의 요점이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CorsConfigTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("개발 기본값은 localhost의 임의 포트를 허용한다")
    void allowsLocalhostAnyPort() throws Exception {
        mockMvc.perform(get("/buildings").header("Origin", "http://localhost:53411"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:53411"));

        mockMvc.perform(get("/buildings").header("Origin", "http://127.0.0.1:8123"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:8123"));
    }

    @Test
    @DisplayName("프리플라이트가 통과해야 실제 요청이 나간다")
    void preflightSucceeds() throws Exception {
        mockMvc.perform(options("/buildings/thehyundai-seoul/floors/1F")
                        .header("Origin", "http://localhost:53411")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:53411"));
    }

    /** 개발 편의가 아무 사이트나 여는 구멍이 되면 안 된다 — 호스트는 localhost로 한정한다. */
    @Test
    @DisplayName("localhost가 아닌 출처는 허용 헤더를 받지 못한다")
    void rejectsForeignOrigin() throws Exception {
        mockMvc.perform(get("/buildings").header("Origin", "http://evil.example.com"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
