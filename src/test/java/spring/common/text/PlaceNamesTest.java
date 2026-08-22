package spring.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaceNamesTest {

    @Test
    @DisplayName("글자마다 벌어진 도면 라벨은 붙여서 읽힌다")
    void spacedLabelsAreSquashed() {
        assertThat(PlaceNames.display("물 품 보 관 함")).isEqualTo("물품보관함");
        assertThat(PlaceNames.display("휴 대 전 화 충 전 기 대 여 유 료"))
                .isEqualTo("휴대전화충전기대여유료");
    }

    @Test
    @DisplayName("멀쩡한 이름의 공백은 건드리지 않는다 — 좁게 잡는 것이 이 판정의 요점이다")
    void ordinaryNamesKeepTheirSpaces() {
        assertThat(PlaceNames.display("스타벅스 리저브")).isEqualTo("스타벅스 리저브");
        assertThat(PlaceNames.display("아미 (남/여)")).isEqualTo("아미 (남/여)");
        assertThat(PlaceNames.display("투어리스트 데스크 서비스 - 유모차 대여"))
                .isEqualTo("투어리스트 데스크 서비스 - 유모차 대여");
        // 한 글자 토막이 둘뿐이면 자간이 아니라 그냥 짧은 이름이다.
        assertThat(PlaceNames.display("송 관")).isEqualTo("송 관");
        assertThat(PlaceNames.display("A.P.C.")).isEqualTo("A.P.C.");
    }

    @Test
    @DisplayName("빈 값과 null을 그대로 돌려준다")
    void emptyValuesPassThrough() {
        assertThat(PlaceNames.display(null)).isNull();
        assertThat(PlaceNames.display("")).isEqualTo("");
        assertThat(PlaceNames.display("송")).isEqualTo("송");
    }
}
