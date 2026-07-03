package nl.lamia.fixtestdriver.controller;

import lombok.RequiredArgsConstructor;
import nl.lamia.fixtestdriver.dto.DecodeMessageRequest;
import nl.lamia.fixtestdriver.dto.DecodedFieldDto;
import nl.lamia.fixtestdriver.dto.ProductTableDto;
import nl.lamia.fixtestdriver.dto.TagMetadataDto;
import nl.lamia.fixtestdriver.service.DictionaryService;
import nl.lamia.fixtestdriver.service.MessageTransformationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/dictionary")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService dictionaryService;
    private final MessageTransformationService messageTransformationService;

    @GetMapping("/table")
    public ProductTableDto getProductTable() {
        return dictionaryService.getProductTable();
    }

    @PutMapping("/table")
    public ResponseEntity<Void> saveProductTable(@RequestBody ProductTableDto dto) {
        List<String> headers = dto.headers();
        List<List<String>> rows = dto.rows();

        if (headers == null || headers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Headers must not be empty");
        }
        if (headers.get(0).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "First header (product ID column) must not be blank");
        }
        for (String h : headers) {
            if (h.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Column headers must not be blank");
            }
        }

        Map<String, Map<String, String>> newDictionary = new LinkedHashMap<>();
        if (rows != null) {
            Set<String> seenIds = new LinkedHashSet<>();
            for (int i = 0; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (row == null || row.isEmpty() || row.get(0).isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Product ID in row " + (i + 1) + " must not be blank");
                }
                String productId = row.get(0);
                if (!seenIds.add(productId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Duplicate product ID: " + productId);
                }
                Map<String, String> props = new LinkedHashMap<>();
                for (int j = 1; j < headers.size(); j++) {
                    props.put(headers.get(j), j < row.size() ? row.get(j) : "");
                }
                newDictionary.put(productId, props);
            }
        }

        boolean saved = dictionaryService.saveDictionary(headers, newDictionary);
        if (!saved) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save dictionary file");
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/headers")
    public List<String> getHeaders() {
        return dictionaryService.getHeaders();
    }

    @GetMapping("/products")
    public Map<String, Map<String, String>> getAllProducts() {
        return dictionaryService.getAllProducts();
    }

    @GetMapping("/products/{productId}")
    public Map<String, String> getProduct(@PathVariable String productId) {
        return dictionaryService.getProduct(productId);
    }

    @PostMapping("/decode-message")
    public List<DecodedFieldDto> decodeMessage(@RequestBody DecodeMessageRequest request) {
        String msg = request.message();
        if (msg == null || msg.isBlank()) {
            return List.of();
        }

        // Parse into tags & values preserving original order
        String normalized = msg.replace('\u0001', '|');
        String[] parts = normalized.split("\\|");
        
        // Find BeginString to get correct dictionary context
        String beginString = "FIX.4.2";
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("8=")) {
                beginString = part.substring(2);
                break;
            }
        }

        List<DecodedFieldDto> decodedFields = new ArrayList<>();
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty() || !part.contains("=")) {
                continue;
            }
            int eqIdx = part.indexOf('=');
            String tagStr = part.substring(0, eqIdx);
            String val = part.substring(eqIdx + 1);
            try {
                int tag = Integer.parseInt(tagStr);
                String name = messageTransformationService.getFieldName(beginString, tag);
                String type = messageTransformationService.getFieldType(beginString, tag);
                decodedFields.add(new DecodedFieldDto(tag, name, val, type));
            } catch (NumberFormatException e) {
                // Ignore malformed tag numbers
            }
        }

        return decodedFields;
    }

    @GetMapping("/lookup-tag")
    public TagMetadataDto lookupTag(@RequestParam(defaultValue = "FIX.4.2") String beginString, @RequestParam int tag) {
        String name = messageTransformationService.getFieldName(beginString, tag);
        String type = messageTransformationService.getFieldType(beginString, tag);
        return new TagMetadataDto(name, type);
    }
}
