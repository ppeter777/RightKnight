package dev.rightknight.analysis;

import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.analysis.DirectRelationEvents.SourcesChange;
import dev.rightknight.analysis.DirectRelationEvents.Status;

import java.util.List;

/** Direct control changes on squares whose occupant ID changed in this move. */
public record SquareControlEvents(Status status, List<Change> changes) {
    public SquareControlEvents {
        changes = List.copyOf(changes);
    }

    public enum OccupancyChange { OCCUPIED, VACATED, REPLACED }

    /** Null occupant means an empty square. Both colors are reported, even when one is unchanged. */
    public record Change(String square, OccupancyChange occupancy, Participant before, Participant after,
                         SourcesChange white, SourcesChange black) {}
}
