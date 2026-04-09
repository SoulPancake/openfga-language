package dev.openfga.language.graph;

/** Exception thrown when a graph query fails. */
public class GraphException extends Exception {
    public GraphException(String message) {
        super(message);
    }
}
