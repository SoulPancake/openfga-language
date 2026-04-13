package dev.openfga.language.graph;

/**
 * Encapsulates whether the graph has cycles.
 */
public final class CycleInformation {
    private final boolean hasCyclesAtCompileTime;
    private final boolean canHaveCyclesAtRuntime;

    CycleInformation(boolean hasCyclesAtCompileTime, boolean canHaveCyclesAtRuntime) {
        this.hasCyclesAtCompileTime = hasCyclesAtCompileTime;
        this.canHaveCyclesAtRuntime = canHaveCyclesAtRuntime;
    }

    /**
     * If {@code true}, we should block this model from ever being written.
     * Performing a Check on it will cause a stack overflow no matter what the tuples are.
     */
    public boolean hasCyclesAtCompileTime() {
        return hasCyclesAtCompileTime;
    }

    /**
     * If {@code true}, there could exist tuples that introduce a cycle at runtime.
     */
    public boolean canHaveCyclesAtRuntime() {
        return canHaveCyclesAtRuntime;
    }
}
