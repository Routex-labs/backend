package spring.event.controller;

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

/**
 * 행사 API 계약 검증. {@code resources/events/thehyundai-seoul.json} 스냅샷을 전제로 한다.
 *
 * <p>지키는 것 셋이다 — <b>키가 snake_case인가</b>(Flutter가 그렇게 파싱한다), <b>서버가 날짜로
 * 거르지 않는가</b>(지난 행사도 그대로 내려와야 화면이 자기 로컬 날짜로 판정할 수 있다),
 * <b>본문 블록이 종류를 잃지 않는가</b>(모르는 종류가 와도 응답이 서야 한다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BuildingEventControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("GET /buildings/{id}/events — 쪽과 행사가 원본 순서로 온다")
    void getEvents() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captured_on").value("2026-08-21"))
                // 쪽은 카드가 될 것들이다. 셋 다 대표 사진을 갖는다.
                .andExpect(jsonPath("$.diaries[0].key").value("popup"))
                .andExpect(jsonPath("$.diaries[0].title").value("WEEKLY POP-UP"))
                .andExpect(jsonPath("$.diaries[0].image").value("assets/events/diary_popup.png"))
                .andExpect(jsonPath("$.diaries.length()").value(3))
                // 한글이 깨지지 않는지 본다.
                .andExpect(jsonPath("$.events[0].title").value("모드나인"))
                .andExpect(jsonPath("$.events[0].place").value("3층 POP-UP (CP컴퍼니 옆)"))
                // 안내를 걸 매장은 snake_case로 나간다.
                .andExpect(jsonPath("$.events[0].store_id").value("PO-EKH6IFeex9644"))
                .andExpect(jsonPath("$.events[0].diary").value("popup"))
                .andExpect(jsonPath("$.events.length()").value(17));
    }

    @Test
    @DisplayName("기간이 지난 행사도 걸러 내지 않는다 — 오늘 판정은 화면이 한다")
    void keepsExpiredEvents() throws Exception {
        // 스냅샷의 가장 이른 시작일. 서버가 날짜로 거르기 시작하면 이 줄이 먼저 깨진다.
        mockMvc.perform(get("/buildings/thehyundai-seoul/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].start").value("2026-08-14"))
                .andExpect(jsonPath("$.events[0].end").value("2026-09-10"));
    }

    @Test
    @DisplayName("본문 블록은 종류를 그대로 들고 온다")
    void keepsDetailBlocks() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].details[0].t").value("p"))
                .andExpect(jsonPath("$.events[0].details").isNotEmpty());
    }

    @Test
    @DisplayName("모아 둔 것이 없는 건물은 빈 목록이 아니라 404다")
    void unknownBuilding() throws Exception {
        mockMvc.perform(get("/buildings/no-such-building/events")).andExpect(status().isNotFound());
    }
}
