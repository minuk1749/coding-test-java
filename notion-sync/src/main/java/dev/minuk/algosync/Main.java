package dev.minuk.algosync;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

public final class Main {
    public static void main(String[] args) throws Exception {
        boolean dryRun = false;
        Path root = Path.of(".");
        Path output = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dry-run" -> dryRun = true;
                case "--repo-dir" -> root = Path.of(args[++i]);
                case "--output" -> output = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        List<Submission> submissions = new GitHistory(root).read();
        long problemCount = submissions.stream().map(Submission::problemKey).distinct().count();
        System.out.printf("Read %d problems, %d GitHub-recorded submissions.%n", problemCount, submissions.size());
        if (output != null) {
            if (output.toAbsolutePath().getParent() != null) Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(submissions));
        }
        if (dryRun) {
            for (Submission s : submissions) System.out.printf("%s / %d차 / %s / %d code files%n", s.title(), s.attempt(), s.result(), s.files().size());
            return;
        }
        String repository = required("GITHUB_REPOSITORY");
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("Invalid GITHUB_REPOSITORY.");
        String problems = uuid("NOTION_PROBLEMS_DATA_SOURCE_ID"), attempts = uuid("NOTION_ATTEMPTS_DATA_SOURCE_ID");
        var result = new SyncService(new NotionClient(required("NOTION_TOKEN")), problems, attempts, repository).sync(submissions);
        System.out.printf("Created %d problems / %d attempts; preserved %d existing attempts.%n",
            result.problemsCreated(), result.attemptsCreated(), result.attemptsSkipped());
    }
    static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing environment variable: " + key);
        return value;
    }
    static String uuid(String key) { return UUID.fromString(required(key)).toString(); }
}
