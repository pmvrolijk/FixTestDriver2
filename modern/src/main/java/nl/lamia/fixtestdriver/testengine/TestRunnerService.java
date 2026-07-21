package nl.lamia.fixtestdriver.testengine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.lamia.fixtestdriver.domain.TestResultEntity;
import nl.lamia.fixtestdriver.dto.FileTreeNode;
import nl.lamia.fixtestdriver.repository.TestResultRepository;
import nl.lamia.fixtestdriver.service.FixEngineService;
import nl.lamia.fixtestdriver.service.LatencyTraceService;
import nl.lamia.fixtestdriver.service.MessageTransformationService;
import nl.lamia.fixtestdriver.testengine.step.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestRunnerService {

    private final FixEngineService fixEngineService;
    private final MessageTransformationService transformationService;
    private final TestResultRepository testResultRepository;
    private final LatencyTraceService latencyTraceService;

    @Value("${application.testcases.root:testcases}")
    private String testCasesRoot;

    public TestResultEntity runTest(File file) {
        log.info("Starting test case: {}", file.getName());
        LocalDateTime startTime = LocalDateTime.now();
        TestContext context = TestContext.builder().build();
        boolean success = false;
        String errorMessage = null;

        try {
            List<TestStep> steps = loadSteps(file);
            for (int i = 0; i < steps.size(); i++) {
                TestStep step = steps.get(i);
                log.info("Executing step {}/{}", i + 1, steps.size());
                step.execute(context);
            }
            success = true;
            log.info("Test case {} completed successfully.", file.getName());
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            log.error("Test case {} failed: {}", file.getName(), errorMessage);
            context.log("ERROR: " + errorMessage);
        }

        List<LatencyTraceService.LiveTrace> traces = latencyTraceService.drainAndReset();

        TestResultEntity result = TestResultEntity.builder()
                .testName(file.getName())
                .startTime(startTime)
                .endTime(LocalDateTime.now())
                .success(success)
                .logOutput(truncateLog(context.getOutput().toString()))
                .errorMessage(errorMessage)
                .hasTrace(!traces.isEmpty())
                .traceCount(traces.size())
                .build();

        TestResultEntity saved = testResultRepository.save(result);
        latencyTraceService.persistTraces(saved.getId(), traces);
        return saved;
    }

    /** Guards against a pathological run overflowing the TEXT column on stricter engines. */
    private static String truncateLog(String output) {
        if (output == null || output.length() <= TestResultEntity.MAX_LOG_OUTPUT_LENGTH) {
            return output;
        }
        return output.substring(0, TestResultEntity.MAX_LOG_OUTPUT_LENGTH);
    }

    public List<String> listTests() {
        File root = new File(testCasesRoot);
        List<String> tests = new ArrayList<>();
        findDefFiles(root, "", tests);
        return tests;
    }

    private void findDefFiles(File dir, String path, List<String> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String currentPath = path.isEmpty() ? f.getName() : path + "/" + f.getName();
            if (f.isDirectory()) {
                findDefFiles(f, currentPath, result);
            } else if (f.getName().endsWith(".def")) {
                result.add(currentPath);
            }
        }
    }

    public List<FileTreeNode> buildFileTree() {
        return buildTreeChildren(new File(testCasesRoot), "");
    }

    private List<FileTreeNode> buildTreeChildren(File dir, String basePath) {
        File[] entries = dir.listFiles();
        if (entries == null) return List.of();
        return Arrays.stream(entries)
            .filter(f -> f.isDirectory() || f.getName().endsWith(".def"))
            .sorted(Comparator.<File, Boolean>comparing(f -> !f.isDirectory())
                             .thenComparing(f -> f.getName().toLowerCase()))
            .map(f -> {
                String path = basePath.isEmpty() ? f.getName() : basePath + "/" + f.getName();
                if (f.isDirectory()) {
                    return new FileTreeNode(f.getName(), path, true, buildTreeChildren(f, path));
                }
                return new FileTreeNode(f.getName(), path, false, null);
            })
            .collect(Collectors.toList());
    }

    private Path validatePath(String relativePath) {
        Path root = Path.of(testCasesRoot).toAbsolutePath().normalize();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid path: " + relativePath);
        }
        return resolved;
    }

    public void createTestFile(String path, String content) throws IOException {
        Path target = validatePath(path);
        if (Files.exists(target)) {
            throw new IllegalStateException("File already exists: " + path);
        }
        Files.createDirectories(target.getParent());
        Files.writeString(target, content != null ? content : "", StandardCharsets.UTF_8);
    }

    public void createDirectory(String dirPath) throws IOException {
        Path target = validatePath(dirPath);
        Files.createDirectories(target);
    }

    public void renameNode(String path, String newPath) throws IOException {
        Path source = validatePath(path);
        Path target = validatePath(newPath);
        if (!Files.exists(source)) {
            throw new IllegalArgumentException("Not found: " + path);
        }
        if (Files.exists(target)) {
            throw new IllegalStateException("Target already exists: " + newPath);
        }
        Files.createDirectories(target.getParent());
        Files.move(source, target);
    }

    public void deleteNode(String path) throws IOException {
        Path target = validatePath(path);
        if (!Files.exists(target)) {
            throw new IllegalArgumentException("Not found: " + path);
        }
        if (Files.isDirectory(target)) {
            try (var walk = Files.walk(target)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException e) { throw new RuntimeException(e); }
                    });
            }
        } else {
            Files.delete(target);
        }
    }

    public File getTestFile(String testPath) {
        return new File(testCasesRoot, testPath);
    }

    public String getTestContent(String path) throws IOException {
        File file = getTestFile(path);
        if (!file.exists()) {
            throw new IllegalArgumentException("Test file not found: " + path);
        }
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    public void saveTestContent(String path, String content) throws IOException {
        File file = getTestFile(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }

    private List<TestStep> loadSteps(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
        }
        int[] pos = {0};
        return parseSteps(lines, pos);
    }

    private List<TestStep> parseSteps(List<String> lines, int[] pos) {
        List<TestStep> steps = new ArrayList<>();
        while (pos[0] < lines.size()) {
            String line = lines.get(pos[0]++);
            if (line.equals("END")) {
                break;
            } else if (line.startsWith("I")) {
                steps.add(new SendMessageStep(line, transformationService, fixEngineService));
            } else if (line.startsWith("E")) {
                steps.add(new ExpectMessageStep(line, 10000, fixEngineService, transformationService));
            } else if (line.startsWith("WAIT")) {
                long time = Long.parseLong(line.split(" ")[1]);
                steps.add(new WaitStep(time));
            } else if (line.startsWith("TRACE")) {
                steps.add(new TraceStep(line.split("\\s+")[1], latencyTraceService));
            } else if (line.matches("^i\\d*,?CONNECT.*")) {
                steps.add(new ConnectStep(line.split(" ")[1]));
            } else if (line.startsWith("LOOP")) {
                int iterations = Integer.parseInt(line.split("\\s+")[1]);
                List<TestStep> body = parseSteps(lines, pos);
                steps.add(new LoopStep(iterations, body));
            }
        }
        return steps;
    }
}
