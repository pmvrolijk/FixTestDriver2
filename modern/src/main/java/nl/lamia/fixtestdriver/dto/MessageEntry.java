package nl.lamia.fixtestdriver.dto;

import java.util.List;

public record MessageEntry(
    String kind,
    String name,
    int tag,
    String type,
    boolean required,
    List<MessageEntry> children
) {}
