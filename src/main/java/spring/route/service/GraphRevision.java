package spring.route.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import spring.common.geometry.LocalPoint;
import spring.route.dto.GraphEdgeResponse;
import spring.route.dto.GraphNodeResponse;

/**
 * 그래프 내용 기반 체크섬. 같은 그래프면 같은 값, 데이터가 바뀌면 다른 값이 나온다.
 *
 * <p>성질 셋을 지킨다.
 *
 * <ul>
 *   <li><b>순서 무관</b> — id로 정렬한 뒤 해싱한다. 조회 순서가 달라져도 같은 값이 나와야
 *       재시드마다 캐시가 무의미하게 깨지지 않는다.
 *   <li><b>내용 기반</b> — 응답에 실리는 필드를 그대로 넣는다. 좌표 한 점만 바뀌어도 값이 바뀐다.
 *   <li><b>정책 반영</b> — 넘긴 간선 목록 그대로를 해싱한다. vertical 정책으로 간선이 필터되면
 *       리비전도 달라진다(payload가 실제로 다르므로 옳다).
 * </ul>
 *
 * <p><b>파이썬 서버와 값이 다르다.</b> 그쪽은 blake2b-128 + 정규화 JSON인데 JDK에 blake2b가
 * 없다. 리비전은 클라이언트에게 불투명한 캐시 키라 알고리즘이 달라도 계약은 유지된다 —
 * 두 서버를 오갈 때 한 번 더 받을 뿐이다.
 */
final class GraphRevision {

    private GraphRevision() {}

    static String of(List<GraphNodeResponse> nodes, List<GraphEdgeResponse> edges) {
        StringBuilder canonical = new StringBuilder();
        nodes.stream().sorted(Comparator.comparing(GraphNodeResponse::id)).forEach(node -> canonical.append("n|")
                .append(node.id())
                .append('|')
                .append(node.type())
                .append('|')
                .append(node.name())
                .append('|')
                .append(node.xM())
                .append('|')
                .append(node.yM())
                .append('|')
                .append(node.lat())
                .append('|')
                .append(node.lng())
                .append('|')
                .append(node.floorId())
                .append('\n'));
        edges.stream().sorted(Comparator.comparing(GraphEdgeResponse::id)).forEach(edge -> {
            canonical
                    .append("e|")
                    .append(edge.id())
                    .append('|')
                    .append(edge.fromNodeId())
                    .append('|')
                    .append(edge.toNodeId())
                    .append('|')
                    .append(edge.lengthM())
                    .append('|')
                    .append(edge.costM())
                    .append('|')
                    .append(edge.bidirectional())
                    .append('|')
                    .append(edge.transferMode())
                    .append('|')
                    .append(edge.fromFloorId())
                    .append('|')
                    .append(edge.toFloorId())
                    .append('|');
            for (LocalPoint point : edge.geometryLocalM()) {
                canonical.append(point.x()).append(',').append(point.y()).append(';');
            }
            canonical.append('\n');
        });

        return sha256Prefix(canonical.toString());
    }

    /** 파이썬 리비전과 같은 길이(16바이트 hex)로 자른다 — 클라이언트가 길이를 가정할 수 있다. */
    private static String sha256Prefix(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] truncated = new byte[16];
            System.arraycopy(digest, 0, truncated, 0, 16);
            return HexFormat.of().formatHex(truncated);
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256은 모든 JDK가 반드시 제공한다(JLS 보장).
            throw new IllegalStateException(impossible);
        }
    }
}
