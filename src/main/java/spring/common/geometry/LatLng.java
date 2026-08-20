package spring.common.geometry;

/** WGS84 좌표. 야외 지도가 건물 폴리곤을 그릴 때 쓴다. */
public record LatLng(double lng, double lat) {}
