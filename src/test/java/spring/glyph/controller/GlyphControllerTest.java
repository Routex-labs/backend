package spring.glyph.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import spring.TestcontainersConfiguration;

/** 글리프는 지도 표시의 선택 사항이 아니다 — 실패하면 fill 레이어까지 안 그려진다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GlyphControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("커밋된 범위는 내용과 캐시 헤더를 함께 준다")
    void servesCommittedRange() throws Exception {
        MvcResult result = mockMvc.perform(get("/fonts/Pretendard Regular/0-255.pbf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=86400"))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
        assertThat(result.getResponse().getHeader("ETag")).isNotBlank();
    }

    @Test
    @DisplayName("같은 ETag로 다시 물으면 304이고, 304에도 캐시 헤더가 붙는다")
    void revalidationReturns304() throws Exception {
        String etag = mockMvc.perform(get("/fonts/Pretendard Regular/0-255.pbf"))
                .andReturn()
                .getResponse()
                .getHeader("ETag");

        mockMvc.perform(get("/fonts/Pretendard Regular/0-255.pbf").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                // 헤더가 빠지면 재검증 직후 곧바로 또 재검증하러 온다.
                .andExpect(header().string("Cache-Control", "public, max-age=86400"));

        // 프록시가 W/ 접두사를 붙여 되돌려도 304를 놓치지 않는다.
        mockMvc.perform(get("/fonts/Pretendard Regular/0-255.pbf").header("If-None-Match", "W/" + etag))
                .andExpect(status().isNotModified());
    }

    @Test
    @DisplayName("없는 범위·없는 폰트는 404가 아니라 빈 200이다")
    void unknownRangeIsEmptyOk() throws Exception {
        // 커밋하지 않은 한자 범위
        MvcResult result = mockMvc.perform(get("/fonts/Pretendard Regular/19968-20223.pbf"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();

        mockMvc.perform(get("/fonts/NoSuchFont/0-255.pbf")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("fontstack은 쉼표 목록이라 가진 폰트를 골라 쓴다")
    void picksFirstAvailableFontFromStack() throws Exception {
        MvcResult result = mockMvc.perform(get("/fonts/NoSuchFont,Pretendard Regular/0-255.pbf"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
    }

    @Test
    @DisplayName("256자 단위가 아닌 범위는 400이다")
    void invalidRangeIs400() throws Exception {
        mockMvc.perform(get("/fonts/Pretendard Regular/0-100.pbf")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/fonts/Pretendard Regular/65280-65535.pbf")).andExpect(status().isOk());
    }

    /** 이 경로는 파일 시스템을 가리킨다 — 디렉터리 한 칸을 벗어나는 이름은 받지 않는다. */
    @Test
    @DisplayName("경로 조작 시도는 빈 응답으로 떨어진다")
    void rejectsPathTraversal() throws Exception {
        MvcResult result = mockMvc.perform(get("/fonts/..%2F..%2Fapplication/0-255.pbf"))
                .andReturn();
        // 파일을 내주지 않는다(빈 200이거나 매핑 실패). 절대 내용이 실려서는 안 된다.
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
    }
}
