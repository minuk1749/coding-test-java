package dev.minuk.algosync;

import com.google.gson.*;
import java.util.*;

/** Payload construction is separate from IO so the exact content can be previewed and tested. */
public final class NotionPages {
    private static final Gson JSON = new Gson();
    private NotionPages() {}
    public static JsonObject problem(String source, Submission s) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("문제", title(s.title()));
        properties.put("플랫폼", Map.of("select", Map.of("name", "코드트리")));
        properties.put("분류", text(s.category()));
        properties.put("난이도", text(s.difficulty()));
        properties.put("문제 링크", Map.of("url", s.url()));
        properties.put("복습 상태", Map.of("select", Map.of("name", "미정")));
        properties.put("문제 키", text(s.problemKey()));
        return page(source, properties, List.of(
            block("paragraph", "위의 ‘시도 이력’에서 1차·2차 기록을 열 수 있습니다."),
            block("heading_2", "문제 전체 정리"),
            block("paragraph", "이곳에 배운 점과 다시 풀 때 기억할 내용을 직접 적어주세요.")));
    }
    public static JsonObject attempt(String source, String problemId, String repository, Submission s) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("시도", title(s.attempt() + "차 시도 · " + s.title()));
        properties.put("문제", Map.of("relation", List.of(Map.of("id", problemId))));
        properties.put("차수", Map.of("number", s.attempt()));
        properties.put("결과", Map.of("select", Map.of("name", s.result())));
        properties.put("기록 시각", Map.of("date", Map.of("start", s.recordedAt())));
        properties.put("커밋", Map.of("url", s.commitUrl(repository)));
        properties.put("시도 키", text(s.key(repository)));
        List<Object> blocks = new ArrayList<>();
        blocks.add(block("heading_2", "내 코멘트"));
        blocks.add(block("paragraph", "접근 방법, 막힌 부분, 수정한 이유를 직접 적어주세요."));
        blocks.add(block("heading_2", "제출 코드"));
        if (s.files().isEmpty()) blocks.add(block("paragraph", "이 커밋에는 코드 파일이 없습니다. 이론 문제의 답안이나 오답 이유는 GitHub 기록만으로 확인할 수 없습니다."));
        for (var file : s.files()) {
            blocks.add(block("paragraph", file.path().substring(file.path().lastIndexOf('/') + 1)));
            for (String part : chunks(file.content(), 6000)) blocks.add(Map.of("object", "block", "type", "code",
                "code", Map.of("language", file.language(), "rich_text", rich(part))));
        }
        blocks.add(block("paragraph", "차수는 GitHub에 기록된 순서이며, 기록 시각은 커밋 시각입니다."));
        return page(source, properties, blocks);
    }
    static JsonObject page(String source, Map<String, Object> properties, List<?> blocks) {
        if (blocks.size() > 95) throw new IllegalArgumentException("Page too large; refusing to truncate code.");
        JsonObject result = JSON.toJsonTree(Map.of("parent", Map.of("type", "data_source_id", "data_source_id", source),
            "properties", properties, "children", blocks)).getAsJsonObject();
        if (result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 450_000)
            throw new IllegalArgumentException("Page payload exceeds safe Notion size; refusing to truncate.");
        return result;
    }
    static Object block(String type, String content) { return Map.of("object", "block", "type", type, type, Map.of("rich_text", rich(content))); }
    static Object title(String value) { return Map.of("title", rich(value)); }
    static Object text(String value) { return Map.of("rich_text", rich(value)); }
    static List<Object> rich(String value) { return chunks(value, 1900).stream().map(s -> (Object) Map.of("type", "text", "text", Map.of("content", s))).toList(); }
    static List<String> chunks(String value, int size) {
        List<String> out = new ArrayList<>();
        for (int start = 0; start < value.length();) {
            int end = Math.min(start + size, value.length());
            if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) end--;
            out.add(value.substring(start, end)); start = end;
        }
        return out.isEmpty() ? List.of("") : out;
    }
}
