package spring.tile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 파이썬 tiling.tile_bounds와 같은 경계를 내는지 대조한다. */
class TileBoundsTest {

    @Test
    @DisplayName("z18 타일 경계가 파이썬 구현과 일치한다")
    void matchesPython() {
        TileBounds bounds = TileBounds.of(18, 223468, 101718);

        assertThat(bounds.west()).isCloseTo(126.8865966796875, within(1e-12));
        assertThat(bounds.east()).isCloseTo(126.88796997070312, within(1e-12));
        assertThat(bounds.south()).isCloseTo(37.345050859282736, within(1e-12));
        assertThat(bounds.north()).isCloseTo(37.346142613246798, within(1e-12));
    }

    @Test
    @DisplayName("y가 작을수록 북쪽이다 — 슬리피맵은 위에서 아래로 센다")
    void yIncreasesSouthward() {
        assertThat(TileBounds.of(2, 0, 0).north()).isGreaterThan(TileBounds.of(2, 0, 1).north());
    }

    @Test
    @DisplayName("격자를 벗어난 좌표는 거절한다")
    void rejectsOutOfRange() {
        assertThatThrownBy(() -> TileBounds.of(2, 4, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TileBounds.of(2, 0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TileBounds.of(-1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
