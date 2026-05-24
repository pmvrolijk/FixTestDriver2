package nl.lamia.fixtestdriver.controller;

import lombok.RequiredArgsConstructor;
import nl.lamia.fixtestdriver.service.DictionaryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dictionary")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService dictionaryService;

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
}
