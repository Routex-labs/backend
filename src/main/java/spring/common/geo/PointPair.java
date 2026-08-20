package spring.common.geo;

/** 변환 피팅에 쓰는 2D 대응점 하나. (x, y)는 입력 평면, (u, v)는 출력 평면 좌표다. */
public record PointPair(double x, double y, double u, double v) {}
