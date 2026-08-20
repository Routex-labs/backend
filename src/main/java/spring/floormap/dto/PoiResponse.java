package spring.floormap.dto;

import spring.common.geometry.LatLng;
import spring.common.geometry.LocalPoint;

/** 지도 위 마커 하나. 그래프 노드에서 승격된 시설점이다. */
public record PoiResponse(
        String id, String type, String name, LocalPoint positionLocalM, LatLng positionWgs84, String linkedNodeId) {}
