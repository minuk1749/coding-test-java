package dev.minuk.algosync;

import java.util.List;

public record Submission(String problemKey, String title, String url, String category,
                         String difficulty, String sha, String recordedAt, String result,
                         int attempt, List<SourceFile> files) {
    public record SourceFile(String path, String language, String content) {}
    public String key(String repository) { return repository + ":" + problemKey + ":" + sha; }
    public String commitUrl(String repository) { return "https://github.com/" + repository + "/commit/" + sha; }
}
