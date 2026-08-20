package spring.tile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import spring.TestcontainersConfiguration;

/**
 * 시드 건물은 서울시청 근처(실측 앵커 3개)라 z16 타일 (55883, 25378)이 1F 전체를 덮는다.
 *
 * <p>MVT 바이트가 스펙에 맞는지는 자바만으로 확인할 수 없어, 이 테스트가 바이트를
 * {@code build/tile-dump.mvt}로 떨궈 파이썬 mapbox_vector_tile로 디코드해 확인했다
 * (docs/migration/대응표.md의 MVT 항목).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TileControllerTest {

    private static final String TILE = "/buildings/thehyundai-seoul/floors/1F/tiles/16/55883/25378.mvt";

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("타일은 MVT 바이트와 캐시 헤더를 준다")
    void servesTileBytes() throws Exception {
        MvcResult result = mockMvc.perform(get(TILE))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.mapbox-vector-tile"))
                // 버전 토큰이 없으면 짧게 잡는다 — 재시드하면 같은 주소의 내용이 바뀐다.
                .andExpect(header().string("Cache-Control", "public, max-age=60"))
                .andReturn();

        byte[] tile = result.getResponse().getContentAsByteArray();
        assertThat(tile).isNotEmpty();

        // 파이썬 디코더로 검증하기 위해 바이트를 남긴다.
        Files.createDirectories(Path.of("build"));
        Files.write(Path.of("build", "tile-dump.mvt"), tile);
    }

    @Test
    @DisplayName("?v=가 붙으면 URL이 곧 버전이라 immutable을 준다")
    void versionedTileIsImmutable() throws Exception {
        mockMvc.perform(get(TILE).param("v", "anything"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"));
    }

    @Test
    @DisplayName("같은 ETag로 다시 물으면 304이고 캐시 헤더가 함께 온다")
    void revalidationReturns304() throws Exception {
        String etag = mockMvc.perform(get(TILE)).andReturn().getResponse().getHeader("ETag");

        mockMvc.perform(get(TILE).header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("Cache-Control", "public, max-age=60"));
    }

    @Test
    @DisplayName("같은 입력이면 바이트가 같다 — 캐시가 안전하려면 결정적이어야 한다")
    void renderIsDeterministic() throws Exception {
        byte[] first = mockMvc.perform(get(TILE)).andReturn().getResponse().getContentAsByteArray();
        byte[] second = mockMvc.perform(get(TILE)).andReturn().getResponse().getContentAsByteArray();

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("건물 상세의 tile_revision은 타일 URL에 붙일 수 있는 값이다")
    void buildingCarriesTileRevision() throws Exception {
        String body = mockMvc.perform(get("/buildings/thehyundai-seoul"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"tile_revision\":\"");
    }

    @Test
    @DisplayName("데이터가 없는 타일도 200이다 — MapLibre가 404를 스타일 오류로 본다")
    void emptyTileIsStillOk() throws Exception {
        // 건물에서 한참 떨어진 타일
        mockMvc.perform(get("/buildings/thehyundai-seoul/floors/1F/tiles/16/1000/1000.mvt"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("격자를 벗어난 좌표는 400, 없는 층은 404다")
    void invalidCoordinatesAndMissingFloor() throws Exception {
        mockMvc.perform(get("/buildings/thehyundai-seoul/floors/1F/tiles/2/9/0.mvt"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/buildings/thehyundai-seoul/floors/99F/tiles/16/55883/25378.mvt"))
                .andExpect(status().isNotFound());
    }
}
