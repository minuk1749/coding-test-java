package dev.minuk.algosync;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;

/** Create-only sync: no PATCH/DELETE operations, so existing notes and snapshots are preserved. */
public final class SyncService {
    private final NotionClient notion;
    private final String problems, attempts, repository;
    public SyncService(NotionClient notion, String problems, String attempts, String repository) {
        this.notion = notion; this.problems = problems; this.attempts = attempts; this.repository = repository;
    }
    public record Result(int problemsCreated, int attemptsCreated, int attemptsSkipped) {}
    public Result sync(List<Submission> submissions) throws IOException, InterruptedException {
        Map<String, JsonObject> problemIndex = index(notion.query(problems, null), "문제 키");
        Map<String, JsonObject> attemptIndex = index(notion.query(attempts, null), "시도 키");
        Set<String> expected = new HashSet<>();
        for (Submission s : submissions) {
            if (!expected.add(s.key(repository))) throw new IOException("Duplicate input submission.");
            JsonObject saved = attemptIndex.get(s.key(repository));
            if (saved != null && saved.getAsJsonObject("properties").getAsJsonObject("차수").get("number").getAsInt() != s.attempt())
                throw new IOException("Attempt numbering differs from saved history; refusing to renumber existing notes.");
        }
        for (String key : attemptIndex.keySet())
            if (key.startsWith(repository + ":") && !expected.contains(key))
                throw new IOException("Saved submission missing from Git history. Check branch, shallow clone or force push.");
        int addedProblems = 0, addedAttempts = 0, skipped = 0;
        for (Submission s : submissions) {
            if (attemptIndex.containsKey(s.key(repository))) { skipped++; continue; }
            JsonObject problem = problemIndex.get(s.problemKey());
            if (problem == null) {
                problem = notion.createUnique(problems, "문제 키", s.problemKey(), NotionPages.problem(problems, s));
                problemIndex.put(s.problemKey(), problem); addedProblems++;
            }
            JsonObject created = notion.createUnique(attempts, "시도 키", s.key(repository),
                NotionPages.attempt(attempts, problem.get("id").getAsString(), repository, s));
            attemptIndex.put(s.key(repository), created); addedAttempts++;
        }
        return new Result(addedProblems, addedAttempts, skipped);
    }
    static Map<String, JsonObject> index(List<JsonObject> pages, String property) throws IOException {
        Map<String, JsonObject> result = new HashMap<>();
        for (JsonObject page : pages) {
            JsonObject props = page.getAsJsonObject("properties");
            if (props == null || !props.has(property)) throw new IOException("Missing property: " + property);
            StringBuilder key = new StringBuilder();
            for (JsonElement rich : props.getAsJsonObject(property).getAsJsonArray("rich_text")) {
                JsonObject item = rich.getAsJsonObject();
                key.append(item.has("plain_text") ? item.get("plain_text").getAsString() : item.getAsJsonObject("text").get("content").getAsString());
            }
            if (key.isEmpty()) continue; // Manually created rows do not claim a sync key.
            if (result.put(key.toString(), page) != null) throw new IOException("Duplicate " + property + "; refusing to add more records.");
        }
        return result;
    }
}
