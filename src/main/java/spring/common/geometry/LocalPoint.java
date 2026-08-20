package spring.common.geometry;

/**
 * 건물 로컬 미터 좌표의 한 점. 원점은 건물마다 다르고 단위는 미터다.
 *
 * <p>DB의 JSON 컬럼(footprint_local_m)과 API 응답이 {@code {"x":…, "y":…}} 로 같은 모양이라
 * 엔티티와 DTO가 이 타입 하나를 공유한다. 두 모양이 갈라지면 그때 나눈다.
 */
public record LocalPoint(double x, double y) {}
