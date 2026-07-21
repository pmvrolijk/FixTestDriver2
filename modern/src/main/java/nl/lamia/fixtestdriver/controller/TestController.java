package nl.lamia.fixtestdriver.controller;

import lombok.RequiredArgsConstructor;
import nl.lamia.fixtestdriver.domain.TestResultEntity;
import nl.lamia.fixtestdriver.dto.FileTreeNode;
import nl.lamia.fixtestdriver.dto.LatencyTraceDto;
import nl.lamia.fixtestdriver.repository.TestResultRepository;
import nl.lamia.fixtestdriver.service.LatencyTraceService;
import nl.lamia.fixtestdriver.testengine.TestRunnerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tests")
@RequiredArgsConstructor
public class TestController {

    private final TestRunnerService testRunnerService;
    private final TestResultRepository testResultRepository;
    private final LatencyTraceService latencyTraceService;

    @GetMapping
    public List<String> listTests() {
        return testRunnerService.listTests();
    }

    @GetMapping("/tree")
    public List<FileTreeNode> getFileTree() {
        return testRunnerService.buildFileTree();
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

    @GetMapping("/results/{id}/trace")
    public List<LatencyTraceDto> getTrace(@PathVariable Long id) {
        return latencyTraceService.getTracesForResult(id);
    }

    @PostMapping("/file")
    @ResponseStatus(HttpStatus.CREATED)
    public void createFile(@RequestBody Map<String, String> req) throws IOException {
        try {
            testRunnerService.createTestFile(req.get("path"), req.get("content"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/directory")
    @ResponseStatus(HttpStatus.CREATED)
    public void createDirectory(@RequestBody Map<String, String> req) throws IOException {
        try {
            testRunnerService.createDirectory(req.get("path"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping("/rename")
    public void renameNode(@RequestBody Map<String, String> req) throws IOException {
        try {
            testRunnerService.renameNode(req.get("path"), req.get("newPath"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @DeleteMapping("/node")
    public void deleteNode(@RequestParam String path) throws IOException {
        try {
            testRunnerService.deleteNode(path);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
