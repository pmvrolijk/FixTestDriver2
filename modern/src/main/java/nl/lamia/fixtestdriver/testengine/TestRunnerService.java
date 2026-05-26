package nl.lamia.fixtestdriver.testengine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.lamia.fixtestdriver.domain.TestResultEntity;
import nl.lamia.fixtestdriver.repository.TestResultRepository;
import nl.lamia.fixtestdriver.service.FixEngineService;
import nl.lamia.fixtestdriver.service.MessageTransformationService;
import nl.lamia.fixtestdriver.testengine.step.*;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestRunnerService {

    private final FixEngineService fixEngineService;
    private final MessageTransformationService transformationService;
    private final TestResultRepository testResultRepository;

    @org.springframework.beans.factory.annotation.Value("${application.testcases.root:testcases}")
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

        TestResultEntity result = TestResultEntity.builder()
                .testName(file.getName())
                .startTime(startTime)
                .endTime(LocalDateTime.now())
                .success(success)
                .logOutput(context.getOutput().toString())
                .errorMessage(errorMessage)
                .build();

        return testResultRepository.save(result);
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

    public File getTestFile(String testPath) {
        return new File(testCasesRoot, testPath);
    }

    public String getTestContent(String path) throws IOException {
        File file = getTestFile(path);
        if (!file.exists()) {
            throw new IllegalArgumentException("Test file not found: " + path);
        }
        return java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
    }

    public void saveTestContent(String path, String content) throws IOException {
        File file = getTestFile(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        java.nio.file.Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
    }

    private List<TestStep> loadSteps(File file) throws IOException {
        List<TestStep> steps = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("I")) {
                    steps.add(new SendMessageStep(line, transformationService, fixEngineService));
                } else if (line.startsWith("E")) {
                    steps.add(new ExpectMessageStep(line, 10000, fixEngineService, transformationService));
                } else if (line.startsWith("WAIT")) {
                    long time = Long.parseLong(line.split(" ")[1]);
                    steps.add(new WaitStep(time));
                } else if (line.matches("^i\\d*,?CONNECT.*")) {
                    steps.add(new ConnectStep(line.split(" ")[1]));
                } else if (line.startsWith("RESET")) {
                    // Logic for ResetOrderStep would go here
                }
            }
        }
        return steps;
    }
}
