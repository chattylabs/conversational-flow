package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;

class Flow implements EdgeSource, EdgeTarget {
    private VoiceNode from;
    private Edge edge;

    Flow(Edge edge) {
        this.edge = edge;
    }

    @Override
    public EdgeTarget from(@NonNull VoiceNode node) {
        from = node;
        return this;
    }

    @Override
    public void to(@NonNull VoiceNode node, VoiceNode... optNodes) {
        edge.addEdge(node, from);
        for (VoiceNode n : optNodes) edge.addEdge(n, from);
    }

    interface Edge {
        void addEdge(@NonNull VoiceNode node, @NonNull VoiceNode incomingEdge);
    }
}