package dev.rightknight.model.attack;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Geometric battery control through one or two compatible friendly sliders. IDs are root IDs. */
public record XrayControl(String sourceId, String square, List<String> throughIds) {
    public XrayControl {
        Objects.requireNonNull(sourceId, "sourceId");
        if (square == null || !square.matches("[a-h][1-8]")) {
            throw new IllegalArgumentException("Invalid controlled square");
        }
        throughIds = List.copyOf(throughIds);
        if (throughIds.isEmpty() || throughIds.size() > 2 || throughIds.contains(sourceId)
                || new HashSet<>(throughIds).size() != throughIds.size()) {
            throw new IllegalArgumentException("Expected one or two distinct intermediate IDs, excluding source");
        }
    }
}
