package nl.lamia.fixtestdriver.dto;

import java.util.List;

public record MessageDetail(String name, String msgtype, String msgcat, List<MessageEntry> entries) {}
