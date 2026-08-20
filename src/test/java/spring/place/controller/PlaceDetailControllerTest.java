package spring.place.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
 * 시드 전제: {@code PO-HU40njvml1512}는 오버레이가 붙은 매장(2F 스타벅스), {@code f1}은 화장실,
 * {@code s6}은 주차장, {@code s2}는 오버레이가 없는 매장이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaceDetailControllerTest {

    private static final String OVERLAY_STORE = "/buildings/thehyundai-seoul/places/PO-HU40njvml1512";

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("코어는 오버레이가 없어도 성립한다 — 정보 없음 카드를 만들지 않기 위한 조건")
    void coreWorksWithoutOverlay() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/places/s2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("store"))
                .andExpect(jsonPath("$.name").value("올리브영"))
                // 소분류가 없으면 대분류가 부제로 온다
                .andExpect(jsonPath("$.subtitle").value("1F · 뷰티"))
                .andExpect(jsonPath("$.location.floor_label").value("1F"))
                .andExpect(jsonPath("$.provenance.source").value("studio"))
                .andExpect(jsonPath("$.provenance.updated_at").doesNotExist())
                // 폴리곤은 있으므로 지도 섹션 하나는 만들어진다
                .andExpect(jsonPath("$.sections[*].type").value(contains("map")));
    }

    @Test
    @DisplayName("입구 노드가 없으면 길찾기 버튼을 내리지 않는다")
    void directionsActionRequiresEntranceNode() throws Exception {
        // s2는 입구 노드가 없다 — 버튼을 띄우고 눌렀을 때 실패시키는 것보다 안 내리는 편이 낫다
        mockMvc.perform(get("/buildings/thehyundai-seoul/places/s2"))
                .andExpect(jsonPath("$.actions[*].type").value(not(hasItem("directions"))))
                .andExpect(jsonPath("$.actions[*].type").value(hasItem("favorite")));

        // s1은 있다
        mockMvc.perform(get("/buildings/thehyundai-seoul/places/s1"))
                .andExpect(jsonPath("$.actions[*].type").value(hasItem("directions")));
    }

    @Test
    @DisplayName("상세를 열지 않는 kind는 섹션도 저장 버튼도 없다")
    void excludedKindHasNoSections() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/places/s6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("excluded"))
                .andExpect(jsonPath("$.sections").isEmpty())
                .andExpect(jsonPath("$.actions").isEmpty());

        mockMvc.perform(get("/buildings/thehyundai-seoul/places/f1")).andExpect(jsonPath("$.kind").value("facility"));
    }

    @Test
    @DisplayName("섹션 순서는 서버가 고정한다 — 오버레이가 준 순서를 따르지 않는다")
    void sectionOrderIsFixedByServer() throws Exception {
        mockMvc.perform(get(OVERLAY_STORE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provenance.source").value("manual"))
                // 사진 → 영업시간 → 소개 → 메뉴 → 운영정보 → 링크 → 사업자정보 → 지도
                .andExpect(jsonPath("$.sections[*].type")
                        .value(contains("hero", "hours", "summary", "menu", "demoInfo", "links", "businessInfo", "map")));
    }

    @Test
    @DisplayName("영업시간은 판정 문자열 없이 규칙만 내려보낸다")
    void hoursCarryRulesNotVerdicts() throws Exception {
        mockMvc.perform(get(OVERLAY_STORE))
                .andExpect(jsonPath("$.sections[?(@.type=='hours')].weekly.mon").exists())
                .andExpect(jsonPath("$.sections[?(@.type=='hours')].weekly.sun").exists())
                // 클라이언트와 서버가 각자 기본값을 들면 갈라진다 — 항상 실어 보낸다
                .andExpect(jsonPath("$.sections[?(@.type=='hours')].utc_offset_minutes").exists())
                .andExpect(jsonPath("$.sections[?(@.type=='hours')].source").exists())
                .andExpect(jsonPath("$.sections[?(@.type=='hours')].confirmed_at").exists())
                // "영업 중" 같은 판정은 응답을 만든 순간부터 틀리기 시작한다
                .andExpect(jsonPath("$.sections[?(@.type=='hours')].open_now").doesNotExist());
    }

    @Test
    @DisplayName("메뉴의 선택 키는 값이 있을 때만 실린다 — 빈 값을 내보내면 분기가 생긴다")
    void menuOmitsEmptyOptionalKeys() throws Exception {
        mockMvc.perform(get(OVERLAY_STORE))
                .andExpect(jsonPath("$.sections[?(@.type=='menu')].items[0].name").exists())
                .andExpect(jsonPath("$.sections[?(@.type=='menu')].items[0].image_asset").exists())
                // 빈 배열도 싣지 않는다. 값이 있는 항목에서만 키가 보여야 한다.
                .andExpect(jsonPath("$.sections[?(@.type=='menu')].items[?(@.badges.length()==0)]").isEmpty());
    }

    @Test
    @DisplayName("다른 건물 id로는 열리지 않는다 — 층 라벨과 길찾기가 어긋난다")
    void wrongBuildingIsNotFound() throws Exception {
        mockMvc.perform(get("/buildings/nope/places/s1")).andExpect(status().isNotFound());
        mockMvc.perform(get("/buildings/thehyundai-seoul/places/no-such-store")).andExpect(status().isNotFound());
    }
}
