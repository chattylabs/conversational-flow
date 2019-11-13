package chattylabs.conversations;

import androidx.annotation.NonNull;

public class Flow implements Source, SourceId {
    private VoiceNode from;
    private Edge edge;

    Flow(Edge edge) {
        this.edge = edge;
    }

    @Override
    public Target from(@NonNull VoiceNode node) {
        from = node;
        return target;
    }

    @Override
    public TargetId from(@NonNull String id) {
        from = edge.getNode(id);
        return targetId;
    }

    public void start(VoiceNode root) {
        edge.start(root);
    }

    private Target target = (node, optNodes) -> {
        edge.addEdge(node, from);
        for (VoiceNode n : optNodes) edge.addEdge(n, from);
    };

    private TargetId targetId = (id, optIds) -> {
        edge.addEdge(edge.getNode(id), from);
        for (String s : optIds) edge.addEdge(edge.getNode(s), from);
    };

    public abstract static class Edge {
        public abstract VoiceNode getNode(@NonNull String id);
        abstract void addEdge(@NonNull VoiceNode node, @NonNull VoiceNode incomingEdge);
        abstract void start(@NonNull VoiceNode root);
    }
}