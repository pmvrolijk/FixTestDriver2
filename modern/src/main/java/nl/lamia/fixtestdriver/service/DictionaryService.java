package nl.lamia.fixtestdriver.service;

import lombok.extern.slf4j.Slf4j;
import nl.lamia.fixtestdriver.dto.ProductTableDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DictionaryService {

    @Value("${application.dictionary.filename:config/products.def}")
    private String dictionaryFilename;

    private final Map<String, Map<String, String>> dictionary =
            Collections.synchronizedMap(new LinkedHashMap<>());
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
            for (String h : headerArray) headers.add(unescape(h));

            // Parse rows
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                if (fields.length > 0) {
                    String productId = unescape(fields[0]);
                    Map<String, String> productProps = new LinkedHashMap<>();
                    for (int i = 1; i < fields.length; i++) {
                        if (i < headerArray.length) {
                            productProps.put(headers.get(i), unescape(fields[i]));
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

    public synchronized ProductTableDto getProductTable() {
        List<String> hdrs = new ArrayList<>(headers);
        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : dictionary.entrySet()) {
            List<String> row = new ArrayList<>();
            row.add(entry.getKey());
            for (int i = 1; i < hdrs.size(); i++) {
                row.add(entry.getValue().getOrDefault(hdrs.get(i), ""));
            }
            rows.add(row);
        }
        return new ProductTableDto(hdrs, rows);
    }

    private String escape(String s) {
        return s.replace("~", "~~").replace(",", ";");
    }

    private String unescape(String s) {
        return s.replace("~~", "~");
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
                    .map(this::escape)
                    .collect(Collectors.joining(","));
            writer.write(headerLine);
            writer.newLine();

            // Write products
            for (Map.Entry<String, Map<String, String>> entry : newDictionary.entrySet()) {
                String productId = entry.getKey();
                Map<String, String> props = entry.getValue();

                StringBuilder row = new StringBuilder(escape(productId));
                for (int i = 1; i < newHeaders.size(); i++) {
                    String val = props.getOrDefault(newHeaders.get(i), "");
                    row.append(",").append(escape(val));
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
