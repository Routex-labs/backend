package spring.building.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import spring.TestcontainersConfiguration;

/**
 * 건물 API 계약 검증. data.sql 시드(더현대 서울 · B1/1F/2F)를 전제로 한다.
 *
 * <p>JSON 키를 직접 검사하는 것이 이 테스트의 핵심이다 — Flutter 클라이언트가 snake_case를
 * 파싱하므로, Jackson 네이밍 전략이 풀리면 여기서 깨져야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BuildingControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("GET /buildings — 층이 위층부터 내려오고 기본 층은 1F다")
    void listBuildings() throws Exception {
        mockMvc.perform(get("/buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("thehyundai-seoul"))
                // 한글이 깨지지 않는지 본다. data.sql을 CP949로 읽어 한 번 깨졌다.
                .andExpect(jsonPath("$[0].name").value("더현대 서울"))
                .andExpect(jsonPath("$[0].floors").value(contains("2F", "1F", "B1")))
                .andExpect(jsonPath("$[0].default_floor").value("1F"))
                // 목록에는 무거운 값이 실리지 않는다
                .andExpect(jsonPath("$[0].footprint_local_m").doesNotExist());
    }

    @Test
    @DisplayName("GET /buildings/{id} — 상세는 면적·둘레·외곽선을 더한다")
    void getBuilding() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area_m2").value(21000.0))
                .andExpect(jsonPath("$.perimeter_m").value(620.5))
                .andExpect(jsonPath("$.footprint_local_m.length()").value(4))
                .andExpect(jsonPath("$.footprint_local_m[1].x").value(100.0))
                // 아직 이식하지 않은 두 값은 계약대로 null로 나간다
                .andExpect(jsonPath("$.footprint_wgs84").value(nullValue()))
                .andExpect(jsonPath("$.tile_revision").value(nullValue()));
    }

    @Test
    @DisplayName("없는 건물은 404다")
    void missingBuildingIs404() throws Exception {
        mockMvc.perform(get("/buildings/no-such-building")).andExpect(status().isNotFound());
    }
}
