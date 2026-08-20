package spring.category.controller;

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

/** data.sql 시드 전제: 1F 매장 6(그중 카테고리 없음 1)·2F 매장 1. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CategoryControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("카테고리 집계는 대분류 없는 매장을 뺀다 — pill을 만들 수 없다")
    void categoryCountsSkipUncategorized() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/categories"))
                .andExpect(status().isOk())
                // 1F: 카페 1 · 뷰티 1 · 패션 3, 2F: 리빙 1 · 카페 1 · 편의시설 1 → 조합 6개.
                // '주차장 A'는 대분류가 없어 빠진다.
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[?(@.category=='패션')].count").value(3))
                .andExpect(jsonPath("$[?(@.category=='주차')]").isEmpty())
                // 소분류가 없는 매장도 집계에는 남고 subcategory만 null이다.
                .andExpect(jsonPath("$[?(@.category=='뷰티')].subcategory").isNotEmpty());
    }

    @Test
    @DisplayName("매장 검색은 이름 부분 일치이고, q가 없으면 전체다")
    void searchStores() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/stores").param("q", "아디다스"))
                .andExpect(status().isOk())
                // '아디다스'와 '아디다스나이키' 둘 다 걸린다
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/buildings/thehyundai-seoul/stores")).andExpect(jsonPath("$.length()").value(9));
    }

    @Test
    @DisplayName("검색 결과에는 공유 폴리곤 분할을 걸지 않는다 — 부분 집합이라 짝을 못 찾는다")
    void searchDoesNotSplitSharedPolygons() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/stores").param("q", "나이키"))
                // 층 지도에서는 [60,80] 칸으로 잘리지만 여기서는 원본이라 x=100이 남아 있다
                .andExpect(jsonPath("$[?(@.id=='s4')].polygon_local_m[?(@.x==100.0)]").exists());
    }

    @Test
    @DisplayName("색인은 좌표를 싣지 않고 kind와 층 라벨을 함께 준다")
    void storeIndexCarriesKindWithoutCoordinates() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/store-index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                // 층 내림차순(2F 먼저) → id 순. 정렬이 풀리면 클라이언트 캐시가 매번 깨진다.
                // id 순서는 DB 콜레이션에 달렸으므로 층 경계만 본다 — 2F 매장 3건이 앞이다.
                .andExpect(jsonPath("$[0].floor_name").value("2F"))
                .andExpect(jsonPath("$[2].floor_name").value("2F"))
                .andExpect(jsonPath("$[3].floor_name").value("1F"))
                .andExpect(jsonPath("$[0].centroid_local_m").doesNotExist())
                .andExpect(jsonPath("$[?(@.id=='s1')].kind").value("store"))
                // 주차는 상세를 열지 않는 소분류라 excluded다
                .andExpect(jsonPath("$[?(@.id=='s6')].kind").value("excluded"));
    }

    @Test
    @DisplayName("없는 건물은 빈 배열이 아니라 404다")
    void missingBuildingIs404() throws Exception {
        mockMvc.perform(get("/buildings/nope/categories")).andExpect(status().isNotFound());
        mockMvc.perform(get("/buildings/nope/stores")).andExpect(status().isNotFound());
        mockMvc.perform(get("/buildings/nope/store-index")).andExpect(status().isNotFound());
    }
}
