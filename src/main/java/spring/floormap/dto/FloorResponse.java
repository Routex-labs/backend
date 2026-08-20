package spring.floormap.dto;

/** 층 식별 정보. {@code id}는 원천 데이터 내부 식별자라 화면에 노출하지 않는다. */
public record FloorResponse(String id, String name, int level) {}
