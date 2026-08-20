package spring.search.service;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.ko.POS;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.springframework.stereotype.Component;

/**
 * 질의 형태소 정규화. 조사·어미·용언을 떼어 경량 매칭이 쓰는 질의를 안정화한다.
 *
 * <p><b>파이썬은 kiwipiepy, 여기는 Lucene Nori다.</b> 둘 다 mecab-ko-dic 계열 품사 체계를 써서
 * 제거 대상 태그(J·E·V·MM·MA·NP·XSV·XSA)가 그대로 옮겨진다. Nori는 Elasticsearch 기능으로
 * 알려져 있지만 실제로는 Lucene 본체의 분석기라 서버가 필요 없다.
 *
 * <p><b>분석에 실패하면 null을 돌려주고 호출부가 원문으로 폴백한다.</b> 형태소는 정확도 보정이지
 * 필수 경로가 아니다 — 파이썬도 같은 폴백을 갖고 있다.
 */
@Component
@Slf4j
public class QueryMorph {

    /**
     * 제거할 품사 태그(접두 검사).
     *
     * <ul>
     *   <li>J* 조사 — "화장실이", "스타벅스는"
     *   <li>E* 어미 — "-야", "-어", "-고"
     *   <li>V* 용언 — "가고 싶어", "급해"
     *   <li>MM 관형사("몇") · MA* 부사("빨리") · NP 대명사("어디")
     *   <li>XSV/XSA 용언파생접미사
     * </ul>
     *
     * 남기는 것: NNG·NNP(명사), NNB(의존명사 — "주차"), SL(외국어), SN(숫자), SH(한자), XSN.
     */
    private static final List<String> DROP_TAG_PREFIXES = List.of("J", "E", "V", "MM", "MA", "NP", "XSV", "XSA");

    /** 잘린 이름 복원을 시도할 최소 길이. 한 글자 이름까지 되돌리면 오탐이 늘어난다. */
    private static final int MIN_RESTORE_LEN = 2;

    /**
     * 사용자 사전에 등록한 매장명(소문자). Nori의 사전은 인스턴스 생성 시점에 고정이라, 파이썬처럼
     * 요청 중에 단어를 더할 수 없다 — 대신 첫 질의에서 건물 전체 매장명으로 한 번 만든다.
     */
    private final Set<String> registeredLower = ConcurrentHashMap.newKeySet();

    private volatile UserDictionary userDictionary;
    private volatile boolean dictionaryBuilt = false;

    /**
     * 매장명을 사용자 사전으로 굽는다. 이미 구웠으면 아무것도 하지 않는다.
     *
     * <p>등록하지 않으면 미등록 브랜드명이 조사로 오해돼 잘려 나간다 — "리모와" → "리모"("와"를
     * 접속조사로), "발렌시아가" → "발렌시아". 파이썬에서 실데이터 1,531건 중 35건이 그랬다.
     *
     * <p><b>공백이 든 이름은 사전에 넣지 않는다.</b> Nori 사용자 사전은 공백을 분절 구분자로
     * 읽어서 "물품 보관함"을 한 단어로 등록할 수 없다. 그런 이름은 {@link #restoreTruncatedName}이
     * 결과 쪽에서 되돌린다 — 파이썬도 같은 이유로 같은 보정을 갖고 있다.
     */
    public synchronized void registerWords(List<String> words) {
        if (dictionaryBuilt) {
            return;
        }
        dictionaryBuilt = true;

        List<String> entries = words.stream()
                .filter(word -> word != null && !word.isBlank())
                .peek(word -> registeredLower.add(word.toLowerCase()))
                .map(String::trim)
                .filter(word -> !word.contains(" "))
                .distinct()
                .sorted()
                .toList();

        if (entries.isEmpty()) {
            return;
        }
        try {
            userDictionary = UserDictionary.open(new StringReader(String.join("\n", entries)));
            log.info("형태소 사전 등록: {}개 단어(공백 없는 이름만)", entries.size());
        } catch (IOException | RuntimeException error) {
            // 사전이 없어도 분석은 된다 — 브랜드명이 잘릴 뿐이라 질의 하나가 약해질 뿐이다.
            log.warn("형태소 사전 생성 실패(사전 없이 분석한다): {}", error.toString());
        }
    }

    /**
     * 조사·어미·용언을 뗀 질의. 분석 불가·남는 게 없으면 null(→ 호출부가 원문으로 폴백).
     *
     * <p>원문의 문자 위치를 유지한다 — "가게A 어디야" → "가게A", "TAX REFUND" → "TAX REFUND".
     * 제거를 삭제가 아니라 <b>공백 치환</b>으로 하는 이유는 "화장실이 어디야"에서 "이"만 지웠을 때
     * 앞뒤 토큰이 잘못 붙는 것을 막기 위해서다.
     */
    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        char[] chars = text.toCharArray();
        boolean removedAny = false;

        try (KoreanTokenizer tokenizer =
                new KoreanTokenizer(KoreanTokenizer.DEFAULT_TOKEN_ATTRIBUTE_FACTORY, userDictionary, KoreanTokenizer.DecompoundMode.NONE, false)) {
            PartOfSpeechAttribute posAttribute = tokenizer.addAttribute(PartOfSpeechAttribute.class);
            OffsetAttribute offsetAttribute = tokenizer.addAttribute(OffsetAttribute.class);

            tokenizer.setReader(new StringReader(text));
            tokenizer.reset();
            while (tokenizer.incrementToken()) {
                if (!isDropped(posAttribute.getLeftPOS())) {
                    continue;
                }
                int start = offsetAttribute.startOffset();
                int end = Math.min(offsetAttribute.endOffset(), chars.length);
                Arrays.fill(chars, start, end, ' ');
                removedAny = true;
            }
            tokenizer.end();
        } catch (IOException | RuntimeException error) {
            // 이 질의만 폴백한다. 서버는 계속 돈다.
            log.warn("형태소 분석 실패({}): {}", text, error.toString());
            return null;
        }

        if (!removedAny) {
            return squashWhitespace(text);
        }
        String result = squashWhitespace(new String(chars));
        if (result == null) {
            return null; // 전부 떨어져 나갔다 — 분석기가 브랜드명을 날린 경우 방어
        }
        return restoreTruncatedName(text, result);
    }

    private static boolean isDropped(POS.Tag tag) {
        if (tag == null) {
            return false;
        }
        String name = tag.name();
        return DROP_TAG_PREFIXES.stream().anyMatch(name::startsWith);
    }

    private static String squashWhitespace(String text) {
        String squashed = text.replaceAll("\\s+", " ").trim();
        return squashed.isEmpty() ? null : squashed;
    }

    /**
     * 분석기가 등록된 매장명을 잘라먹었으면 원래 이름으로 되돌린다.
     *
     * <p>공백이 든 이름은 사전에 넣을 수 없어(위 {@link #registerWords} 참고) "물품 보관함은 몇
     * 층이야"가 "물품 보관"으로 잘린다. 보정은 <b>실제로 잘렸을 때만</b> 한다 — 질의가 등록된
     * 이름으로 시작하고, 그 이름이 분석 결과보다 길고, 결과가 그 이름의 접두일 때.
     *
     * <p>가장 긴 이름을 먼저 본다 — "타임"과 "타임옴므"가 모두 매장일 때 "타임옴므" 질의가
     * "타임"으로 축소되면 안 된다.
     */
    private String restoreTruncatedName(String text, String result) {
        String lowered = text.toLowerCase();
        for (int end = lowered.length(); end >= MIN_RESTORE_LEN; end--) {
            if (!registeredLower.contains(lowered.substring(0, end))) {
                continue;
            }
            String name = text.substring(0, end);
            if (name.length() > result.length() && name.toLowerCase().startsWith(result.toLowerCase())) {
                return name;
            }
            return result; // 이름은 온전하다 — 분석기 결과를 그대로 쓴다
        }
        return result;
    }

    /** 사전에 등록된 이름 수. 테스트·진단용. */
    public int registeredCount() {
        return Collections.unmodifiableSet(registeredLower).size();
    }
}
