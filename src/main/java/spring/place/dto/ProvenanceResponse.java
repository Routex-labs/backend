package spring.place.dto;

/**
 * 표시된 값의 출처. 화면 하단에 "정보 출처·최종 확인일"로 노출한다.
 *
 * @param source 출처의 <b>종류</b>다(studio/manual/derived). 어디서 왔는지는 {@code url}이
 *     따로 들고 있다 — 둘을 한 필드에 담으면 "manual인지 URL인지"를 소비자가 매번 분기해야 한다.
 * @param updatedAt ISO 날짜. 오버레이가 적어 준 최종 확인일.
 * @param url 공식 사이트에서 옮겨 온 문구라면 그 페이지 주소, 직접 적은 내용이면 null.
 *     {@code updatedAt}은 이 주소를 다시 열어 봤다는 뜻이라 둘은 짝으로 읽는다.
 */
public record ProvenanceResponse(String source, String updatedAt, String url) {}
