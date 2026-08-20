package spring.share.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * 장소 공유 링크 — 앱을 여는 증명 파일 둘과, 앱이 없는 사람이 볼 페이지.
 *
 * <pre>
 *   GET /.well-known/assetlinks.json             → Android App Links 검증
 *   GET /.well-known/apple-app-site-association  → iOS Universal Links 검증
 *   GET /place/{buildingId}/{placeId}            → 미설치 fallback 페이지
 * </pre>
 *
 * <p>증명 파일은 <b>OS가 직접 받아 간다.</b> 앱이 "이 주소는 내 것"이라고 주장하는 것만으로는
 * 링크를 가로챌 수 없고, 그 주소가 앱을 인정해야 성립한다. 그래서 앱이 아니라 도메인을
 * 가진 쪽(여기)이 낸다.
 */
@RestController
public class DeepLinkController {

    private static final String SERVICE_NAME = "Routex";
    private static final String ANDROID_PACKAGE = "com.navigation.navigation_client";

    /**
     * 지문이 하나뿐인 이유는 release가 아직 debug 키로 서명하기 때문이다. 별도 release 키를
     * 만드는 날 그 지문을 이 목록에 <b>더한다</b> — 갈아 끼우면 이미 깔린 앱이 링크를 잃는다.
     */
    private static final List<String> ANDROID_SHA256_FINGERPRINTS =
            List.of("C3:0C:5F:05:50:BB:95:9E:80:AB:83:AD:01:F7:25:8E:F8:DB:66:79:19:9E:96:01:EA:15:10:31:CB:D7:3F:9F");

    /** iOS는 아직 서명 팀이 없다. 목록이 비어도 파일 자체는 유효해야 OS가 재시도를 포기하지 않는다. */
    private static final List<String> IOS_APP_IDS = List.of();

    @GetMapping("/.well-known/assetlinks.json")
    public List<Map<String, Object>> androidAssetLinks() {
        return List.of(
                Map.of(
                        "relation",
                        List.of("delegate_permission/common.handle_all_urls"),
                        "target",
                        Map.of(
                                "namespace", "android_app",
                                "package_name", ANDROID_PACKAGE,
                                "sha256_cert_fingerprints", ANDROID_SHA256_FINGERPRINTS)));
    }

    /** 확장자가 없고 Content-Type이 application/json이어야 iOS가 읽는다. */
    @GetMapping(value = "/.well-known/apple-app-site-association", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> appleAppSiteAssociation() {
        return Map.of(
                "applinks",
                Map.of("details", List.of(Map.of("appIDs", IOS_APP_IDS, "components", List.of(Map.of("/", "/place/*"))))));
    }

    /**
     * 앱이 없는 사람에게 "무엇을 받았는지"만 알리는 자리다.
     *
     * <p><b>여기서 매장을 조회하지 않는다.</b> 조회를 붙이면 삭제된 매장에서 이 페이지가 500을
     * 내며 공유 링크가 통째로 죽는다. 이름·층은 앱이 서버에서 다시 구한다.
     *
     * <p><b>두 값은 반드시 이스케이프한다.</b> 경로 파라미터는 서버가 percent-decode한 뒤
     * 넘겨주므로 {@code %3Cimg src=x onerror=...%3E}가 살아 있는 마크업으로 들어온다. 이 주소는
     * 사람들이 메신저에 붙여 넣는 공개 페이지이고, 같은 출처가 assetlinks.json을 낸다.
     */
    @GetMapping("/place/{buildingId}/{placeId}")
    public ResponseEntity<String> placeFallback(@PathVariable String buildingId, @PathVariable String placeId) {
        String safeBuildingId = HtmlUtils.htmlEscape(buildingId);
        String safePlaceId = HtmlUtils.htmlEscape(placeId);
        String page =
                """
                <!doctype html>
                <html lang="ko">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>
                  body { font-family: system-ui, -apple-system, sans-serif; margin: 0;
                         display: grid; place-items: center; min-height: 100vh; color: #17171B; }
                  main { padding: 24px; max-width: 24rem; text-align: center; }
                  code { background: #F3F4F6; padding: 2px 6px; border-radius: 6px;
                          font-size: 0.85em; word-break: break-all; }
                </style>
                </head>
                <body>
                <main>
                  <h1>%s</h1>
                  <p>앱에서 이 장소를 열 수 있습니다.</p>
                  <p><code>%s</code> / <code>%s</code></p>
                </main>
                </body>
                </html>"""
                        .formatted(SERVICE_NAME, SERVICE_NAME, safeBuildingId, safePlaceId);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(page);
    }
}
