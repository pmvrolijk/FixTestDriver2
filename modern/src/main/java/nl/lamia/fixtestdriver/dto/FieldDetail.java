package nl.lamia.fixtestdriver.dto;

import java.util.List;

public record FieldDetail(int number, String name, String type, List<EnumValue> values, List<String> usedInMessages) {}
