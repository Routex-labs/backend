package spring.common.text;

/**
 * 도면에서 온 장소 이름을 글로 읽을 수 있게 다듬는다.
 *
 * <p><b>왜 필요한가.</b> 원본(Dabeeo 도면)의 이름 몇 개는 지도에 넓게 퍼뜨려 그리려고
 * 글자마다 공백을 넣어 두었다 — {@code 물 품 보 관 함}, {@code 휴 대 전 화 충 전 기 대 여 유 료}.
 * 지도 위에서는 사물함 여러 칸을 가로지르는 라벨이라 그럴 만한데, 그 값이 상세 시트
 * 제목과 검색 결과까지 따라와 글이 아니라 표처럼 읽힌다.
 *
 * <p><b>지도 라벨은 건드리지 않는다.</b> 자간은 거기서는 의도한 것이고, 타일이 주는
 * 이름을 여기서 바꾸면 도면이 달라진다. 고치는 것은 <b>글로 읽는 자리</b>뿐이다.
 *
 * <p><b>원본을 고치는 것이 진짜 답이다.</b> 이 값은 시드가 만드는 것이고 재시드하면
 * 그대로 돌아온다. 여기 있는 것은 그때까지의 덮개다.
 */
public final class PlaceNames {

    private PlaceNames() {}

    /**
     * 글자마다 공백이 들어간 이름을 붙여 준다. 그 모양이 아니면 원문 그대로다.
     *
     * <p>판정은 <b>토막이 셋 이상이고 전부 한 글자</b>일 때로 좁힌다. 실제 매장 이름은
     * 이 모양이 될 수 없고(`A.P.C.`는 점으로 끊는다), 좁게 잡아야 멀쩡한 이름의 공백을
     * 지우는 사고가 안 난다.
     */
    public static String display(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 3) {
            return raw;
        }
        for (String part : parts) {
            if (part.codePointCount(0, part.length()) != 1) {
                return raw;
            }
        }
        return String.join("", parts);
    }
}
