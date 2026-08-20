package spring.route.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import spring.TestcontainersConfiguration;

/** data.sql 시드(1F 노드 3·간선 2, 2F 노드 2·간선 1, 전이 2)를 전제로 한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GraphControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("층 그래프는 그 층 간선만 담는다 — 전이 간선은 floor_id가 null이라 빠진다")
    void floorGraphExcludesTransfers() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/floors/1F/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floor.id").value("ths-1f"))
                .andExpect(jsonPath("$.floor.name").value("1F"))
                .andExpect(jsonPath("$.nodes.length()").value(3))
                .andExpect(jsonPath("$.edges.length()").value(2))
                // 층별 그래프의 노드는 floor_id를 싣지 않는다(단일 층이라 자명하다).
                .andExpect(jsonPath("$.nodes[0].floor_id").doesNotExist());
    }

    @Test
    @DisplayName("간선 키는 from_node_id가 아니라 짧은 from/to다")
    void edgeUsesShortFromToKeys() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/floors/1F/graph"))
                .andExpect(jsonPath("$.edges[?(@.id=='ths-1f:e1')].from").value("ths-1f:n1"))
                .andExpect(jsonPath("$.edges[?(@.id=='ths-1f:e1')].to").value("ths-1f:n2"))
                .andExpect(jsonPath("$.edges[?(@.id=='ths-1f:e1')].geometry_local_m.length()").value(3))
                // geometry가 없는 간선은 null이 아니라 빈 배열이다 — 클라이언트가 직선으로 그린다.
                .andExpect(jsonPath("$.edges[?(@.id=='ths-1f:e2')].geometry_local_m.length()").value(0));
    }

    @Test
    @DisplayName("건물 그래프는 전 층 노드와 수직 전이 간선을 합친다")
    void buildingGraphIncludesTransfers() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.building.id").value("thehyundai-seoul"))
                .andExpect(jsonPath("$.vertical").value("auto"))
                .andExpect(jsonPath("$.nodes.length()").value(5))
                // 층 내부 3 + 전이 2
                .andExpect(jsonPath("$.edges.length()").value(5))
                // 건물 그래프의 노드는 floor_id를 싣는다 — 전 층이 섞이므로 나누는 근거가 필요하다.
                .andExpect(jsonPath("$.nodes[?(@.id=='ths-2f:n4')].floor_id").value("ths-2f"))
                // 전이 간선의 양 끝 층은 노드에서 되찾는다.
                .andExpect(jsonPath("$.edges[?(@.id=='ths:t-elev')].from_floor_id").value("ths-1f"))
                .andExpect(jsonPath("$.edges[?(@.id=='ths:t-elev')].to_floor_id").value("ths-2f"))
                // 실제 이동 거리와 라우팅 비용은 전이 간선에서 갈라진다.
                .andExpect(jsonPath("$.edges[?(@.id=='ths:t-elev')].length_m").value(4.0))
                .andExpect(jsonPath("$.edges[?(@.id=='ths:t-elev')].cost_m").value(20.0));
    }

    @Test
    @DisplayName("vertical 정책이 전이 간선을 거른다")
    void verticalPolicyFiltersTransfers() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/graph").param("vertical", "elevator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges.length()").value(4))
                .andExpect(jsonPath("$.edges[?(@.transfer_mode=='escalator')]").isEmpty());

        mockMvc.perform(get("/buildings/thehyundai-seoul/graph").param("vertical", "escalator"))
                .andExpect(jsonPath("$.edges.length()").value(4))
                .andExpect(jsonPath("$.edges[?(@.transfer_mode=='elevator')]").isEmpty());
    }

    @Test
    @DisplayName("리비전은 같은 그래프에 같고, 정책이 다르면 달라진다")
    void revisionIsContentBased() throws Exception {
        String auto = revisionOf("auto");
        String autoAgain = revisionOf("auto");
        String elevator = revisionOf("elevator");

        assertThat(auto).hasSize(32).isEqualTo(autoAgain);
        // 간선 집합이 실제로 다르므로 리비전도 달라야 한다.
        assertThat(elevator).isNotEqualTo(auto);
    }

    @Test
    @DisplayName("모르는 정책은 조용히 auto로 떨어지지 않고 422다")
    void unknownVerticalPolicyIs422() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/graph").param("vertical", "stairs"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("없는 층·건물은 404다")
    void missingIs404() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/floors/99F/graph")).andExpect(status().isNotFound());
        mockMvc.perform(get("/buildings/nope/graph")).andExpect(status().isNotFound());
    }

    private String revisionOf(String vertical) throws Exception {
        String body = mockMvc.perform(get("/buildings/thehyundai-seoul/graph").param("vertical", vertical))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.revision");
    }
}
