package nl.lamia.fixtestdriver.controller;

import lombok.RequiredArgsConstructor;
import nl.lamia.fixtestdriver.dto.DecodeMessageRequest;
import nl.lamia.fixtestdriver.dto.DecodedFieldDto;
import nl.lamia.fixtestdriver.dto.TagMetadataDto;
import nl.lamia.fixtestdriver.service.DictionaryService;
import nl.lamia.fixtestdriver.service.MessageTransformationService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dictionary")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService dictionaryService;
    private final MessageTransformationService messageTransformationService;

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
