package spring.category.dto;

/**
 * 온디바이스 자동완성 인덱스 한 줄. 앱이 시작할 때 건물당 1회 받는다.
 *
 * <p><b>좌표를 넣지 않는 것이 이 모델의 존재 이유다.</b> 후보 목록은 이름과 층 라벨만 그리므로
 * 중심점이 필요 없고, 탭한 뒤 필요해지는 좌표는 층 지도 응답이나 상세로 이미 얻는다.
 * 실측(매장 1,640건): 이 응답 341KB에 중심점을 얹으면 최대 1.6배가 된다. 같은 데이터의
 * {@code /stores}는 1,229KB다.
 *
 * <p>{@code floorId}와 {@code floorName}을 둘 다 주는 이유: 라벨이 없으면 후보 한 줄을 그리려고
 * 건물 응답과 다시 조인해야 하고, id가 없으면 층 지도 응답·현재 층 필터와 대조할 키가 사라진다.
 */
public record StoreIndexResponse(
        String id,
        String name,
        String floorId,
        String floorName,
        String category,
        String subcategory,
        String kind,
        String entranceNodeId) {}
