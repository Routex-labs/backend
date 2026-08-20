package spring.search.dto;

/**
 * clarify 질문의 선택지 하나. 실제 후보가 있는 값만 만든다 — 눌러도 결과가 없는 chip은 만들지 않는다.
 *
 * @param facet 축 이름(intents·styles·...). 클라이언트가 selected_facets 키로 되돌려 보낸다
 * @param count 이 값을 가진 현재 후보 수. 화면에 숫자로 찍히므로 그만큼 도달할 수 있어야 한다
 */
public record DiscoveryOption(String facet, String value, String label, int count) {}
