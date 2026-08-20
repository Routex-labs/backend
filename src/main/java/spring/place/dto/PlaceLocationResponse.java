package spring.place.dto;

import spring.common.geometry.LocalPoint;

/**
 * 상세의 위치. 길찾기 가능 여부까지 여기서 판별된다.
 *
 * @param entranceNodeId 온디바이스 다익스트라의 도착 노드. null이면 길찾기 액션을 내리지
 *     않는다. 원본 데이터에는 이 값이 전부 비어 있고 시드가 최근접 노드로 스냅해 채우므로,
 *     길찾기 가능 여부는 시드 결과를 봐야만 알 수 있다.
 */
public record PlaceLocationResponse(
        String buildingId, String floorLabel, LocalPoint positionLocalM, String entranceNodeId) {}
