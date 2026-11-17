package dev.minuk.algosync;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowTest {
    @TempDir Path dir;
    private void git(String... args) throws Exception {
        var cmd = new ArrayList<>(List.of("git", "-C", dir.toString())); cmd.addAll(List.of(args));
        var process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }
    private void init() throws Exception {
        git("init"); git("config", "user.name", "Fixture"); git("config", "user.email", "fixture@example.com");
    }
    private void write(String path, String content) throws Exception {
        Path file = dir.resolve(path); Files.createDirectories(file.getParent()); Files.writeString(file, content);
    }
    private void commit(String subject) throws Exception { git("add", "."); git("commit", "--allow-empty", "-m", subject); }
    private String readme(String name, String slug) {
        return "# [[연습]" + name + "](https://www.codetree.ai/trails/complete/curated-cards/" + slug + ")\n\n"
            + "| 항목 | 내용 |\n|---|---|\n| 커리큘럼 | [Trail 1 / 조건문](https://www.codetree.ai/trail-info/) |\n| 난이도 | 쉬움 |\n";
    }
    @Test void immutableSnapshotsEmptySubmissionsAndSlashInTitle() throws Exception {
        init(); String base = "trail1/A/B 출력/";
        write(base + "README.md", readme("A/B 출력", "a-divide-b"));
        write(base + "solution.java", "class Main { int value = 1; }\n");
        commit("[Wrong Answer] A/B 출력 (127ms, 10MB)");
        write(base + "solution.java", "class Main { int value = 2; }\n");
        commit("[Passed] A/B 출력 (125ms, 10MB)");
        commit("[Passed] A/B 출력 (126ms, 10MB)");
        write("README.md", "ordinary documentation"); commit("docs: change README");
        List<Submission> data = new GitHistory(dir).read();
        assertEquals(3, data.size());
        assertEquals(List.of(1, 2, 3), data.stream().map(Submission::attempt).toList());
        assertTrue(data.get(0).files().getFirst().content().contains("value = 1"));
        assertTrue(data.get(1).files().getFirst().content().contains("value = 2"));
        assertEquals(data.get(1).files(), data.get(2).files());
        assertNotEquals(data.get(1).sha(), data.get(2).sha());
        assertEquals("Trail 1 / 조건문", data.get(0).category());
        assertEquals("A/B 출력", data.get(0).title());
    }
    @Test void theoryProblemDoesNotInventSourceOrExplanation() throws Exception {
        init(); write("trail3/시간복잡도/README.md", readme("시간복잡도", "complexity"));
        commit("[Wrong Answer] 시간복잡도");
        var data = new GitHistory(dir).read();
        assertEquals(1, data.size()); assertTrue(data.getFirst().files().isEmpty());
        String payload = NotionPages.attempt("attempts", "problem", "test/repo", data.getFirst()).toString();
        assertTrue(payload.contains("코드 파일이 없습니다"));
        assertFalse(payload.contains("AI"));
    }
    @Test void folderRenameKeepsProblemIdentity() throws Exception {
        init(); write("trail1/old/README.md", readme("이름", "same-id"));
        commit("[Wrong Answer] 이름");
        git("mv", "trail1/old", "trail1/new"); commit("[Passed] 이름");
        var data = new GitHistory(dir).read();
        assertEquals(2, data.size()); assertEquals(data.get(0).problemKey(), data.get(1).problemKey());
        assertEquals(2, data.get(1).attempt());
    }
    @Test void sourceChunksPreserveUnicodeExactly() {
        String source = "a".repeat(1899) + "😀" + "한글\n".repeat(2500);
        assertEquals(source, String.join("", NotionPages.chunks(source, 1900)));
        for (String chunk : NotionPages.chunks(source, 1900)) {
            assertTrue(chunk.length() <= 1900);
            assertFalse(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1)));
        }
    }
    private Submission submission(int n) {
        return new Submission("codetree:problem", "문제", "https://www.codetree.ai/problems/problem", "조건문", "쉬움",
            Integer.toString(n).repeat(40), "2026-11-17T07:00:00Z", n == 1 ? "오답" : "정답", n,
            List.of(new Submission.SourceFile("trail1/문제/main.java", "java", "int n = " + n + ";")));
    }
    static class FakeNotion implements NotionClient.Transport {
        final Map<String, List<JsonObject>> rows = new HashMap<>();
        int creates, queries, failCreateAt = -1;
        FakeNotion() { rows.put("problems", new ArrayList<>()); rows.put("attempts", new ArrayList<>()); }
        public NotionClient.Response call(String method, String path, JsonObject body) {
            assertEquals("POST", method, "Sync must never PATCH or DELETE existing user notes.");
            if (path.equals("pages")) {
                JsonObject row = body.deepCopy(); row.addProperty("id", "page-" + ++creates);
                rows.get(body.getAsJsonObject("parent").get("data_source_id").getAsString()).add(row);
                if (creates == failCreateAt) return new NotionClient.Response(503, new JsonObject(), "");
                return new NotionClient.Response(200, row, "");
            }
            queries++;
            JsonArray result = new JsonArray();
            for (JsonObject row : rows.get(path.split("/")[1])) {
                if (body.has("filter")) {
                    JsonObject f = body.getAsJsonObject("filter");
                    String prop = f.get("property").getAsString();
                    String wanted = f.getAsJsonObject("rich_text").get("equals").getAsString();
                    String key = row.getAsJsonObject("properties").getAsJsonObject(prop).getAsJsonArray("rich_text")
                        .get(0).getAsJsonObject().getAsJsonObject("text").get("content").getAsString();
                    if (!wanted.equals(key)) continue;
                }
                result.add(row);
            }
            JsonObject out = new JsonObject(); out.add("results", result); out.addProperty("has_more", false);
            return new NotionClient.Response(200, out, "");
        }
    }
    @Test void rerunPreservesCommentsAndDoesNotCreateDuplicates() throws Exception {
        FakeNotion fake = new FakeNotion();
        var service = new SyncService(new NotionClient(fake, ms -> {}), "problems", "attempts", "test/repo");
        var data = List.of(submission(1), submission(2));
        assertEquals(new SyncService.Result(1, 2, 0), service.sync(data));
        JsonObject first = fake.rows.get("attempts").getFirst();
        first.addProperty("user_written_comment", "내가 직접 쓴 메모");
        String saved = first.toString();
        assertEquals(new SyncService.Result(0, 0, 2), service.sync(data));
        assertEquals(3, fake.creates); assertEquals(saved, first.toString());
    }
    @Test void lostWriteResponseReconcilesWithoutRepeatingCreation() throws Exception {
        FakeNotion fake = new FakeNotion(); fake.failCreateAt = 2;
        var service = new SyncService(new NotionClient(fake, ms -> {}), "problems", "attempts", "test/repo");
        assertEquals(1, service.sync(List.of(submission(1))).attemptsCreated());
        assertEquals(2, fake.creates); assertEquals(1, fake.rows.get("attempts").size());
    }
    @Test void unknownWriteOutcomeStopsInsteadOfBlindRetry() {
        int[] writes = {0};
        NotionClient.Transport transport = (method, path, body) -> {
            if (path.equals("pages")) { writes[0]++; throw new IOException("lost connection"); }
            return new NotionClient.Response(200, JsonParser.parseString("{\"results\":[],\"has_more\":false}").getAsJsonObject(), "");
        };
        var client = new NotionClient(transport, ms -> {});
        assertThrows(IOException.class, () -> client.createUnique("attempts", "시도 키", "key", new JsonObject()));
        assertEquals(1, writes[0]);
    }
    @Test void detectsMissingHistoryBeforeWriting() throws Exception {
        FakeNotion fake = new FakeNotion();
        var service = new SyncService(new NotionClient(fake, ms -> {}), "problems", "attempts", "test/repo");
        service.sync(List.of(submission(1), submission(2)));
        assertThrows(IOException.class, () -> service.sync(List.of(submission(1))));
        assertEquals(3, fake.creates);
    }
    @Test void paginatesAndHonorsRateLimit() throws Exception {
        int[] requests = {0}; List<Long> sleeps = new ArrayList<>();
        var client = new NotionClient((method, path, body) -> {
            int n = ++requests[0];
            if (n == 1) return new NotionClient.Response(429, new JsonObject(), "2");
            if (n == 2) return new NotionClient.Response(200, JsonParser.parseString("{\"results\":[{\"id\":\"one\"}],\"has_more\":true,\"next_cursor\":\"next\"}").getAsJsonObject(), "");
            assertEquals("next", body.get("start_cursor").getAsString());
            return new NotionClient.Response(200, JsonParser.parseString("{\"results\":[{\"id\":\"two\"}],\"has_more\":false}").getAsJsonObject(), "");
        }, sleeps::add);
        assertEquals(2, client.query("problems", null).size());
        assertEquals(List.of(2000L), sleeps);
    }
    @Test void incompleteIndexPreventsWrites() {
        var client = new NotionClient((method, path, body) -> new NotionClient.Response(200,
            JsonParser.parseString("{\"results\":[],\"has_more\":false,\"request_status\":{\"type\":\"incomplete\"}}").getAsJsonObject(), ""), ms -> {});
        assertThrows(IOException.class, () -> client.query("problems", null));
    }
}
