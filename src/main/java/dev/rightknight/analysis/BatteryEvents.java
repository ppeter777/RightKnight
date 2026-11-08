package dev.rightknight.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.bhlangonijr.chesslib.Side;
import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.analysis.DirectRelationEvents.Status;

import java.util.List;

/** Per-square changes of geometric batteries in the stored x-ray layer. */
public record BatteryEvents(Status status, List<Change> changes) {
    public BatteryEvents {
        changes = List.copyOf(changes);
    }

    public enum Transition { FORMED, DISSOLVED, COMPOSITION_CHANGED, TARGET_CHANGED }
    public enum SquareReason { OCCUPIED_TARGET, MOVE_DESTINATION }
    public enum ControlKind { ATTACK, DEFENCE, SQUARE_CONTROL }

    /** Direction points from square towards the pieces (e.g. NE from d4 towards f6). */
    public record Change(String square, SquareReason reason, Side side, String direction,
                         Transition transition, State before, State after) {}

    /** directControllers covers the whole square for this side; battery covers only this ray, front first. */
    public record State(Participant target, ControlKind controlKind, List<Participant> directControllers,
                        List<Participant> battery) {
        public State {
            directControllers = List.copyOf(directControllers);
            battery = List.copyOf(battery);
        }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public int supportCount() {
            return Math.max(0, battery.size() - 1);
        }
    }
}
