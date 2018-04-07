package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;

class Flow implements EdgeSource, EdgeTarget {
    private Node from;
    private Edge edge;

    Flow(Edge edge) {
        this.edge = edge;
    }

    @Override
    public EdgeTarget from(@NonNull Node node) {
        from = node;
        return this;
    }

    @Override
    public void to(@NonNull Node node, Node... optNodes) {
        edge.addEdge(node, from);
        for (Node n : optNodes) edge.addEdge(n, from);
    }

    interface Edge {
        void addEdge(@NonNull Node node, @NonNull Node incomingEdge);
    }
}