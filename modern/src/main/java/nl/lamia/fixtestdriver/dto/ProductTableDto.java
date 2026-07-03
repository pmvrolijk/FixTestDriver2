package nl.lamia.fixtestdriver.dto;

import java.util.List;

public record ProductTableDto(List<String> headers, List<List<String>> rows) {}
