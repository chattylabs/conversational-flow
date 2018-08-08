package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;

public class ConversationFlow implements ConversationFlowSource, ConversationFlowSourceId {
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

    @Override
    public ConversationFlowTargetId from(@NonNull String id) {
        from = edge.getNode(id);
        return targetId;
    }

    private ConversationFlowTarget target = (node, optNodes) -> {
        edge.addEdge(node, from);
        for (VoiceNode n : optNodes) edge.addEdge(n, from);
    };

    private ConversationFlowTargetId targetId = (id, optIds) -> {
        edge.addEdge(edge.getNode(id), from);
        for (String s : optIds) edge.addEdge(edge.getNode(s), from);
    };

    abstract static class Edge {
        abstract VoiceNode getNode(@NonNull String id);
        abstract void addEdge(@NonNull VoiceNode node, @NonNull VoiceNode incomingEdge);
    }
}