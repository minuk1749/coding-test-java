package dev.minuk.algosync;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/** Reads immutable snapshots. Solution files are data; never compile or execute them. */
public final class GitHistory {
    private static final Pattern SUBJECT = Pattern.compile("^\\[([^]]+)] (.+)$");
    private static final Pattern LINK = Pattern.compile("^# +\\[(.+)]\\((https://(?:www\\.)?codetree\\.ai/[^\\s)]+)\\)", Pattern.MULTILINE);
    private static final Map<String, String> RESULTS = Map.ofEntries(
        Map.entry("Passed", "정답"), Map.entry("Wrong Answer", "오답"),
        Map.entry("Time Limit Exceeded", "시간 초과"), Map.entry("Memory Limit Exceeded", "메모리 초과"),
        Map.entry("Runtime Error", "런타임 에러"), Map.entry("Compile Error", "컴파일 에러"),
        Map.entry("Compilation Error", "컴파일 에러"));
    private static final Map<String, String> LANGUAGES = Map.ofEntries(
        Map.entry("java", "java"), Map.entry("py", "python"), Map.entry("cpp", "c++"),
        Map.entry("cc", "c++"), Map.entry("c", "c"), Map.entry("js", "javascript"),
        Map.entry("ts", "typescript"), Map.entry("kt", "kotlin"), Map.entry("sql", "sql"),
        Map.entry("go", "go"), Map.entry("rs", "rust"), Map.entry("cs", "c#"), Map.entry("swift", "swift"));
    private final Path root;
    public GitHistory(Path root) { this.root = root; }

    public List<Submission> read() throws IOException, InterruptedException {
        if (!git("rev-parse", "--is-shallow-repository").strip().equals("false"))
            throw new IOException("Full Git history required: checkout fetch-depth: 0.");
        List<Submission> found = new ArrayList<>();
        Map<String, Integer> numbers = new HashMap<>();
        // Parent order, not author timestamps: a clock change must not renumber attempts.
        for (String line : git("log", "--reverse", "--first-parent", "--format=%H%x09%cI%x09%s", "HEAD").lines().toList()) {
            String[] fields = line.split("\t", 3);
            var subject = SUBJECT.matcher(fields[2]);
            if (!subject.matches() || !RESULTS.containsKey(subject.group(1))) continue;
            String sha = fields[0];
            String titleHint = subject.group(2).replaceFirst(" \\([^()]*ms, [^()]*[KMG]?B\\)$", "");
            List<String> snapshot = nulLines(git("ls-tree", "-rz", "--name-only", sha));
            Set<String> paths = new HashSet<>(snapshot);
            Set<String> readmes = new TreeSet<>();
            for (String changed : nulLines(git("diff-tree", "--root", "--no-commit-id", "--name-only", "--no-renames", "-r", "-z", sha))) {
                String directory = parent(changed);
                while (!directory.isEmpty()) {
                    String candidate = directory + "/README.md";
                    if (paths.contains(candidate)) { readmes.add(candidate); break; }
                    directory = parent(directory);
                }
            }
            // A real empty commit can still represent a distinct identical-code submission.
            if (readmes.isEmpty()) {
                for (String path : snapshot) {
                    if (!path.endsWith("/README.md")) continue;
                    Metadata metadata = metadata(git("show", sha + ":" + path));
                    if (metadata != null && metadata.title.equals(titleHint)) readmes.add(path);
                }
                if (readmes.size() != 1) throw new IOException("Cannot uniquely resolve submission " + sha);
            }
            Set<String> seenInCommit = new HashSet<>();
            for (String readme : readmes) {
                Metadata data = metadata(git("show", sha + ":" + readme));
                if (data == null) continue;
                if (!seenInCommit.add(data.key)) throw new IOException("Ambiguous duplicate problem in " + sha);
                String directory = parent(readme);
                List<Submission.SourceFile> files = new ArrayList<>();
                for (String path : snapshot) {
                    if (!parent(path).equals(directory)) continue;
                    String extension = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                    if (LANGUAGES.containsKey(extension))
                        files.add(new Submission.SourceFile(path, LANGUAGES.get(extension), git("show", sha + ":" + path)));
                }
                int attempt = numbers.merge(data.key, 1, Integer::sum);
                found.add(new Submission(data.key, data.title, data.url, data.category, data.difficulty,
                    sha, fields[1], RESULTS.get(subject.group(1)), attempt, List.copyOf(files)));
            }
            if (seenInCommit.isEmpty()) throw new IOException("Submission has no supported problem metadata: " + sha);
        }
        return List.copyOf(found);
    }

    record Metadata(String key, String title, String url, String category, String difficulty) {}
    static Metadata metadata(String text) {
        var link = LINK.matcher(text);
        if (!link.find()) return null;
        URI uri = URI.create(link.group(2));
        String path = uri.getPath().replaceAll("/+$", "");
        String key = "codetree:" + path.substring(path.lastIndexOf('/') + 1);
        String title = link.group(1).replaceFirst("^\\[[^]]+]\\s*", "");
        return new Metadata(key, title, "https://www.codetree.ai" + uri.getRawPath(),
            cell(text, "커리큘럼"), cell(text, "난이도"));
    }
    static String cell(String text, String name) {
        var match = Pattern.compile("(?m)^\\|\\s*" + Pattern.quote(name) + "\\s*\\|\\s*(.*?)\\s*\\|\\s*$").matcher(text);
        if (!match.find()) return "";
        return match.group(1).replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1");
    }
    static String parent(String path) { int index = path.lastIndexOf('/'); return index < 0 ? "" : path.substring(0, index); }
    static List<String> nulLines(String value) { return Arrays.stream(value.split("\u0000")).filter(s -> !s.isEmpty()).toList(); }
    private String git(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString(), "-c", "core.quotepath=false"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) throw new IOException("Git read failed: " + String.join(" ", args));
        return new String(output, StandardCharsets.UTF_8);
    }
}
