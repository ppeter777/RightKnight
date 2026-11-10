package dev.rightknight.analysis;

import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.analysis.DirectRelationEvents.SourcesChange;
import dev.rightknight.analysis.DirectRelationEvents.Status;

import java.util.List;

/** Direct control changes on selected squares for one move. */
public record SquareControlEvents(Status status, List<Change> changes) {
    public SquareControlEvents {
        changes = List.copyOf(changes);
    }

    public enum OccupancyChange { OCCUPIED, VACATED, REPLACED, UNCHANGED }
    public enum Reason { OCCUPANCY_CHANGED, CENTER, WHITE_KING_ZONE, BLACK_KING_ZONE }

    /** Null occupant means an empty square. Both colors are reported, even when one is unchanged. */
    public record Change(String square, OccupancyChange occupancy, Participant before, Participant after,
                         SourcesChange white, SourcesChange black, List<Reason> reasons) {
        public Change {
            // Earlier reports contained only changes of occupant and had no reasons field.
            reasons = reasons == null ? List.of(Reason.OCCUPANCY_CHANGED) : List.copyOf(reasons);
        }

        public Change(String square, OccupancyChange occupancy, Participant before, Participant after,
                      SourcesChange white, SourcesChange black) {
            this(square, occupancy, before, after, white, black, List.of(Reason.OCCUPANCY_CHANGED));
        }
    }
}
