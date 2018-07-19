package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;

public class Flow implements FlowSource {
    private VoiceNode from;
    private Edge edge;

    Flow(Edge edge) {
        this.edge = edge;
    }

    @Override
    public FlowTarget from(@NonNull VoiceNode node) {
        from = node;
        return target;
    }

    private FlowTarget target = (node, optNodes) -> {
        edge.addEdge(node, from);
        for (VoiceNode n : optNodes) edge.addEdge(n, from);
    };

    abstract static class Edge {
        abstract void addEdge(@NonNull VoiceNode node, @NonNull VoiceNode incomingEdge);
    }
}