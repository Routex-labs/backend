package spring.floormap.controller;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
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

/** data.sql 시드 전제: 1F 매장 6·POI 1, 2F 매장 1·POI 1. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FloorMapControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("층 지도는 매장·POI·그래프를 한 응답에 담는다")
    void floorMapBundlesEverything() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/floors/1F"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floor.level").value(1))
                .andExpect(jsonPath("$.navigation_coordinate_system").value("local_m"))
                .andExpect(jsonPath("$.map_calibration_version").value("v1"))
                .andExpect(jsonPath("$.stores.length()").value(6))
                .andExpect(jsonPath("$.pois.length()").value(1))
                .andExpect(jsonPath("$.navigation_graph.nodes.length()").value(3))
                // 못 걷는 면은 이 응답에 싣지 않는다 — 화면은 MVT 타일로 그린다.
                .andExpect(jsonPath("$.non_walkable_polygons_local_m").doesNotExist());
    }

    @Test
    @DisplayName("층에 외곽선이 있으면 그것을 쓰고, 없으면 건물 것으로 폴백한다")
    void footprintFallsBackToBuilding() throws Exception {
        // 1F는 자기 외곽선(120x90)을 갖는다.
        mockMvc.perform(get("/buildings/thehyundai-seoul/floors/1F"))
                .andExpect(jsonPath("$.footprint_local_m[1].x").value(120.0))
                .andExpect(jsonPath("$.footprint_wgs84.length()").value(4));

        // 2F는 없으므로 건물 대표 외곽(100x80)이 온다.
        mockMvc.perform(get("/buildings/thehyundai-seoul/floors/2F"))
                .andExpect(jsonPath("$.footprint_local_m[1].x").value(100.0));
    }

    @Test
    @DisplayName("입구 좌표가 없는 매장은 local·wgs84 둘 다 null이다")
    void storeWithoutEntrance() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/floors/1F"))
                .andExpect(jsonPath("$.stores[?(@.id=='s2')].entrance_local_m").value(contains(nullValue())))
                .andExpect(jsonPath("$.stores[?(@.id=='s2')].entrance_wgs84").value(contains(nullValue())))
                .andExpect(jsonPath("$.stores[?(@.id=='s2')].entrance_node_id").value(contains(nullValue())))
                // 폴리곤이 없는 매장은 wgs84 폴리곤도 null이다.
                .andExpect(jsonPath("$.stores[?(@.id=='s6')].polygon_wgs84").value(contains(nullValue())));
    }

    @Test
    @DisplayName("centroid는 실측 앵커로 피팅한 아핀을 통과해 위경도로 나간다")
    void centroidIsGeoreferenced() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/floors/1F"))
                .andExpect(jsonPath("$.stores[?(@.id=='s1')].centroid_wgs84.lat").value(contains(closeTo(37.5665, 0.01))))
                .andExpect(jsonPath("$.stores[?(@.id=='s1')].centroid_wgs84.lng").value(contains(closeTo(126.978, 0.01))));
    }

    /**
     * 같은 폴리곤을 쓰는 s3·s4는 자기 칸으로 잘려 나가야 한다. s5는 두 이름을 이어 붙인 묶음
     * 매장이라 그룹에서 빠지고(빠지지 않으면 셋이 되어 분할 자체가 없다) 원본을 그대로 받는다.
     */
    @Test
    @DisplayName("한 폴리곤을 둘이 나눠 쓰면 긴 축으로 잘라 각자의 칸을 준다")
    void sharedPolygonIsSplit() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/floors/1F"))
                // 입구 핀이 왼쪽(x=65)인 나이키가 앞 칸 [60,80]
                .andExpect(jsonPath("$.stores[?(@.id=='s4')].polygon_local_m[?(@.x==80.0)]").exists())
                .andExpect(jsonPath("$.stores[?(@.id=='s4')].polygon_local_m[?(@.x==100.0)]").doesNotExist())
                // 오른쪽(x=95)인 아디다스가 뒤 칸 [80,100]
                .andExpect(jsonPath("$.stores[?(@.id=='s3')].polygon_local_m[?(@.x==100.0)]").exists())
                .andExpect(jsonPath("$.stores[?(@.id=='s3')].polygon_local_m[?(@.x==60.0)]").doesNotExist())
                // 묶음 매장은 원본 폴리곤 그대로(양 끝이 모두 남는다)
                .andExpect(jsonPath("$.stores[?(@.id=='s5')].polygon_local_m[?(@.x==60.0)]").exists())
                .andExpect(jsonPath("$.stores[?(@.id=='s5')].polygon_local_m[?(@.x==100.0)]").exists());
    }

    @Test
    @DisplayName("없는 층은 404다")
    void missingFloorIs404() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/floors/99F")).andExpect(status().isNotFound());
    }
}
