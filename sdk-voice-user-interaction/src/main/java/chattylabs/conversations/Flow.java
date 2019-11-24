package chattylabs.conversations;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

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

    @Override
    public TargetId from(@StringRes int id) {
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

    private TargetId targetId = new TargetId() {
        @Override
        public void to(@NonNull String id, String... ids) {
            edge.addEdge(edge.getNode(id), from);
            for (String s : ids) edge.addEdge(edge.getNode(s), from);
        }

        @Override
        public void to(@StringRes int id, @StringRes Integer... ids) {
            edge.addEdge(edge.getNode(id), from);
            for (int i : ids) edge.addEdge(edge.getNode(i), from);
        }
    };

    abstract static class Edge {
        abstract VoiceNode getNode(@NonNull String id);
        abstract VoiceNode getNode(@StringRes int id);
        abstract void addEdge(@NonNull VoiceNode node, @NonNull VoiceNode incomingEdge);
        abstract void start(@NonNull VoiceNode root);
    }
}