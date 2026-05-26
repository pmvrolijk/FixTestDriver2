package nl.lamia.fixtestdriver.controller;

import lombok.RequiredArgsConstructor;
import nl.lamia.fixtestdriver.domain.TestResultEntity;
import nl.lamia.fixtestdriver.repository.TestResultRepository;
import nl.lamia.fixtestdriver.testengine.TestRunnerService;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/tests")
@RequiredArgsConstructor
public class TestController {

    private final TestRunnerService testRunnerService;
    private final TestResultRepository testResultRepository;

    @GetMapping
    public List<String> listTests() {
        return testRunnerService.listTests();
    }

    @GetMapping("/content")
    public String getTestContent(@RequestParam String path) throws IOException {
        return testRunnerService.getTestContent(path);
    }

    @PostMapping("/content")
    public void saveTestContent(@RequestParam String path, @RequestBody String content) throws IOException {
        testRunnerService.saveTestContent(path, content);
    }

    @PostMapping("/run")
    public TestResultEntity runTest(@RequestParam String path) {
        File testFile = testRunnerService.getTestFile(path);
        if (!testFile.exists()) {
            throw new IllegalArgumentException("Test file not found: " + path);
        }
        return testRunnerService.runTest(testFile);
    }

    @GetMapping("/results")
    public List<TestResultEntity> getResults() {
        return testResultRepository.findAll();
    }
}
