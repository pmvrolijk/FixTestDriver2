package nl.lamia.fixtestdriver.controller;

import lombok.RequiredArgsConstructor;
import nl.lamia.fixtestdriver.dto.*;
import nl.lamia.fixtestdriver.service.FixDictionaryBrowserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/fix-dictionary")
@RequiredArgsConstructor
public class FixDictionaryBrowserController {

    private final FixDictionaryBrowserService service;

    @GetMapping("/versions")
    public List<VersionInfo> getVersions() {
        return service.getVersions();
    }

    @GetMapping("/{versionId}/messages")
    public ResponseEntity<List<MessageSummary>> getMessages(@PathVariable String versionId) {
        try {
            return ResponseEntity.ok(service.getMessages(versionId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{versionId}/fields")
    public ResponseEntity<List<FieldSummary>> getFields(@PathVariable String versionId) {
        try {
            return ResponseEntity.ok(service.getFields(versionId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{versionId}/messages/{name}")
    public ResponseEntity<MessageDetail> getMessageDetail(
            @PathVariable String versionId, @PathVariable String name) {
        try {
            return ResponseEntity.ok(service.getMessageDetail(versionId, name));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{versionId}/fields/{number}")
    public ResponseEntity<FieldDetail> getFieldDetail(
            @PathVariable String versionId, @PathVariable int number) {
        try {
            return ResponseEntity.ok(service.getFieldDetail(versionId, number));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/reload")
    public List<VersionInfo> reload() {
        return service.reload();
    }
}
