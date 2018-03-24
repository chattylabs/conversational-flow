package com.chattylabs.module.voice;

import android.support.annotation.NonNull;

public class Flow {
    private Node from;
    private Edge edge;

    Flow(Edge edge) {
        this.edge = edge;
    }

    public Flow from(Node node) {
        from = node;
        return this;
    }

    public void to(Node node) {
        edge.addEdge(node, from);
    }

    interface Edge {
        void addEdge(@NonNull Node node, @NonNull Node incomingEdge);
    }
}
