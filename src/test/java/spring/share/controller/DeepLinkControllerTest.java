package spring.share.controller;

import static org.assertj.core.api.Assertions.assertThat;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DeepLinkControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("assetlinks.json은 OS가 읽는 모양 그대로 나간다")
    void androidAssetLinks() throws Exception {
        mockMvc.perform(get("/.well-known/assetlinks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].target.namespace").value("android_app"))
                .andExpect(jsonPath("$[0].target.package_name").value("com.navigation.navigation_client"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints.length()").value(1));
    }

    @Test
    @DisplayName("apple-app-site-association은 application/json이어야 iOS가 읽는다")
    void appleAppSiteAssociation() throws Exception {
        mockMvc.perform(get("/.well-known/apple-app-site-association"))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertThat(result.getResponse().getContentType()).startsWith("application/json"))
                .andExpect(jsonPath("$.applinks.details[0].components[0]['/']").value("/place/*"));
    }

    /**
     * 이 페이지는 사람들이 메신저에 붙여 넣는 공개 주소이고, 같은 출처가 assetlinks.json을 낸다.
     * 경로 파라미터는 서버가 percent-decode한 뒤 넘겨주므로 이스케이프가 빠지면 살아 있는
     * 마크업이 그대로 렌더된다.
     */
    @Test
    @DisplayName("fallback 페이지는 경로 파라미터를 이스케이프한다")
    void escapesPathParameters() throws Exception {
        String body = mockMvc.perform(get("/place/{buildingId}/{placeId}", "<img src=x onerror=alert(1)>", "p'\"&"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("<img src=x");
        assertThat(body).contains("&lt;img src=x onerror=alert(1)&gt;");
        assertThat(body).contains("&#39;").contains("&quot;").contains("&amp;");
    }
}
