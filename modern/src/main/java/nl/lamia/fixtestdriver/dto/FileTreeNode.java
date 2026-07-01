package nl.lamia.fixtestdriver.dto;

import java.util.List;

public record FileTreeNode(
    String name,
    String path,
    boolean directory,
    List<FileTreeNode> children
) {}
