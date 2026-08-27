package dev.rightknight.engine;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EngineCandidate {

    private int rank;
    private int depth;
    private int selDepth;
    private Integer evalCp;
    private Integer mateIn;
    private long nodes;
    private long nps;
    private int hashfull;
    private long timeMs;
    private String pv;

}
