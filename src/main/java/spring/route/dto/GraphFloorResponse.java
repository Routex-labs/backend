package spring.route.dto;

/** 이 그래프가 어느 층 것인지. 층 지도 응답의 FloorResponse보다 얇다(level 없음). */
public record GraphFloorResponse(String id, String name) {}
