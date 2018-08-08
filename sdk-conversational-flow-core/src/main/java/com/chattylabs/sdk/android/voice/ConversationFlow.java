package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;

public class ConversationFlow implements ConversationFlowSource {
    private VoiceNode from;
    private Edge edge;

    ConversationFlow(Edge edge) {
        this.edge = edge;
    }

    @Override
    public ConversationFlowTarget from(@NonNull VoiceNode node) {
        from = node;
        return target;
    }

    private ConversationFlowTarget target = (node, optNodes) -> {
        edge.addEdge(node, from);
        for (VoiceNode n : optNodes) edge.addEdge(n, from);
    };

    abstract static class Edge {
        abstract void addEdge(@NonNull VoiceNode node, @NonNull VoiceNode incomingEdge);
    }
}