package spring.search.dto;

import spring.common.geometry.LatLng;
import spring.common.geometry.LocalPoint;

/**
 * 목적지·정보 질의가 공유하는 매칭 대상 매장 정보.
 *
 * @param entranceNodeId 온디바이스 경로의 도착 노드. 없으면 status가 ok_no_route다
 * @param centroidWgs84 지도 표시용 실좌표. 건물에 wgs84 앵커가 없으면 null
 */
public record QueryMatchResponse(
        String storeId,
        String name,
        String category,
        String subcategory,
        String floorId,
        String floorName,
        String entranceNodeId,
        LocalPoint centroidLocalM,
        LatLng centroidWgs84) {}
