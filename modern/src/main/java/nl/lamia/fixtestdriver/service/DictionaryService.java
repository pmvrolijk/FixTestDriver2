package nl.lamia.fixtestdriver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DictionaryService {

    @Value("${application.dictionary.filename:config/products.def}")
    private String dictionaryFilename;

    private final Map<String, Map<String, String>> dictionary = new ConcurrentHashMap<>();
    private final List<String> headers = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadDictionary();
    }

    public synchronized void loadDictionary() {
        log.info("Loading dictionary from {}", dictionaryFilename);
        File file = new File(dictionaryFilename);
        if (!file.exists()) {
            log.warn("Dictionary file {} not found. Creating empty dictionary structure.", dictionaryFilename);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null) return;

            // Reset current state
            dictionary.clear();
            headers.clear();

            // Parse headers
            String[] headerArray = line.split(",");
            headers.addAll(Arrays.asList(headerArray));

            // Parse rows
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                if (fields.length > 0) {
                    String productId = fields[0];
                    Map<String, String> productProps = new HashMap<>();
                    for (int i = 1; i < fields.length; i++) {
                        if (i < headerArray.length) {
                            productProps.put(headerArray[i], fields[i]);
                        }
                    }
                    dictionary.put(productId, productProps);
                }
            }
            log.info("Loaded {} products from dictionary.", dictionary.size());
        } catch (IOException e) {
            log.error("Error loading dictionary file: {}", dictionaryFilename, e);
        }
    }

    public String getProductProp(String productId, String propName) {
        Map<String, String> props = dictionary.get(productId);
        return props != null ? props.get(propName) : null;
    }

    public Map<String, String> getProduct(String productId) {
        return dictionary.get(productId);
    }

    public List<String> getHeaders() {
        return Collections.unmodifiableList(headers);
    }

    public Map<String, Map<String, String>> getAllProducts() {
        return Collections.unmodifiableMap(dictionary);
    }

    public synchronized boolean saveDictionary(List<String> newHeaders, Map<String, Map<String, String>> newDictionary) {
        File file = new File(dictionaryFilename);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            // Write headers
            String headerLine = newHeaders.stream()
                    .map(h -> h.replace(",", ";"))
                    .collect(Collectors.joining(","));
            writer.write(headerLine);
            writer.newLine();

            // Write products
            for (Map.Entry<String, Map<String, String>> entry : newDictionary.entrySet()) {
                String productId = entry.getKey();
                Map<String, String> props = entry.getValue();

                StringBuilder row = new StringBuilder(productId.replace(",", ";"));
                for (int i = 1; i < newHeaders.size(); i++) {
                    String val = props.getOrDefault(newHeaders.get(i), "");
                    row.append(",").append(val.replace(",", ";"));
                }
                writer.write(row.toString());
                writer.newLine();
            }

            // Update internal state after successful save
            this.headers.clear();
            this.headers.addAll(newHeaders);
            this.dictionary.clear();
            this.dictionary.putAll(newDictionary);

            log.info("Successfully saved dictionary to {}", dictionaryFilename);
            return true;
        } catch (IOException e) {
            log.error("Error saving dictionary to {}", dictionaryFilename, e);
            return false;
        }
    }
}
