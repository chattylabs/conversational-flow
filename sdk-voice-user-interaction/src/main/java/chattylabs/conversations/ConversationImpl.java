package chattylabs.conversations;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.SimpleArrayMap;
import androidx.core.util.Pools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import chattylabs.android.commons.Tag;
import chattylabs.android.commons.internal.ILogger;

class ConversationImpl extends Flow.Edge implements Conversation {
    private final String TAG = Tag.make("Conversation");

    // Log stuff
    private ILogger logger;

    private Context context;

    // Data
    private final Pools.Pool<ArrayList<VoiceNode>> mListPool = new Pools.SimplePool<>(10);
    private final SimpleArrayMap<VoiceNode, ArrayList<VoiceNode>> graph = new SimpleArrayMap<>();

    // Resources
    private SpeechSynthesizer speechSynthesizer;
    private SpeechRecognizer speechRecognizer;
    private Flow flow;
    private VoiceNode currentNode;

    private int flags;

    ConversationImpl(Context context,
                     SpeechSynthesizer speechSynthesizer,
                     SpeechRecognizer speechRecognizer,
                     ILogger logger) {
        this.context           = context;
        this.speechSynthesizer = speechSynthesizer;
        this.speechRecognizer  = speechRecognizer;
        this.logger            = logger;
    }

    @Override
    public void addNode(@NonNull VoiceNode node) {
        if (!graph.containsKey(node)) graph.put(node, null);
    }

    @Override
    public Flow prepare() {
        if (flow == null) flow = new Flow(this);
        return flow;
    }

    @Override
    public synchronized void next() {
        next(getNext());
    }

    @Override
    public synchronized void next(VoiceNode node) {
        if (currentNode == null)
            throw new IllegalStateException("You must run start(Node)");
        if (node != null) {
            if (node instanceof VoiceAction) {
                ArrayList<VoiceNode> nodes = new ArrayList<>(1);
                nodes.add(node);
                node = getActionSet(nodes);
            }
            if (node instanceof VoiceMessage) {
                VoiceMessage message = (VoiceMessage) node;
                logger.v(TAG, "- running Message: %s", message.text.get());
                currentNode = message;
                speechSynthesizer.playTextNow(
                    message.text.get(),
                    (SynthesizerListener.OnStart) utteranceId -> {
                        if (message.onReady != null) {
                            message.onReady.run(message);
                        }
                    },
                    (SynthesizerListener.OnDone) utteranceId -> {
                        if (message.onSuccess != null) {
                            message.onSuccess.run();
                        }
                        next();
                    }); // TODO: implement onError
            } else if (node instanceof VoiceActionList) {
                VoiceActionList actions = (VoiceActionList) node;
                VoiceCapture[] captureAction = new VoiceCapture[1];
                VoiceMismatch[] mismatchAction = new VoiceMismatch[1];
                for (VoiceNode n : actions) {
                    if (n instanceof VoiceCapture) {
                        captureAction[0] = (VoiceCapture) n;
                    } else if (n instanceof VoiceMismatch) {
                        mismatchAction[0] = (VoiceMismatch) n;
                    }
                }
                if (captureAction[0] != null) {
                    logger.v(TAG, "- running Capture");
                    // Listen Only
                    speechRecognizer.listen(
                        (RecognizerListener.OnMostConfidentResult) result -> {
                            currentNode = captureAction[0];
                            ComponentConsumer<VoiceCapture, String> consumer =
                                captureAction[0].onCaptured;
                            if (consumer != null) consumer.accept(captureAction[0], result);
                            next();
                        },
                        (RecognizerListener.OnError) (error, originalError) -> {
                            logger.e(TAG, "- listening Capture error");
                            if (mismatchAction[0] != null)
                                noMatch(mismatchAction[0], error, null);
                        });
                } else {
                    logger.v(TAG, "- running Actions");
                    speechRecognizer.listen(
                        (RecognizerListener.OnReady) params -> {
                            for (VoiceNode n : actions) {
                                if (n instanceof VoiceMatch) {
                                    VoiceMatch action = (VoiceMatch) n;
                                    if (action.onReady != null) {
                                        action.onReady.run(action);
                                    }
                                }
                            }
                        },
                        (RecognizerListener.OnResults) (results, confidences) -> {
                            String result = ConversationalFlow.selectMostConfidentResult(results, confidences);
                            processResults(Collections.singletonList(result), actions, false);
                        },
                        (RecognizerListener.OnPartialResults) (results, confidences) -> {
                            String result = ConversationalFlow.selectMostConfidentResult(results, confidences);
                            processResults(Collections.singletonList(result), actions, true);
                        },
                        (RecognizerListener.OnError) (error, originalError) -> {
                            logger.e(TAG, "- listening Action error");
                            if (mismatchAction[0] != null)
                                noMatch(mismatchAction[0], error, null);
                        });
                }
            }
        } else {
            // Otherwise there is no more nodes
            speechSynthesizer.shutdown();
            logger.w(TAG, "- no more nodes, finished.");
        }
    }

    private synchronized VoiceNode getNext() {
        ArrayList<VoiceNode> outgoingEdges = getOutgoingEdges(currentNode);
        if (outgoingEdges == null || outgoingEdges.isEmpty()) {
            return null;
        }

        if (outgoingEdges.size() == 1) {
            VoiceNode node = outgoingEdges.get(0);
            if (node instanceof VoiceAction) {
                ArrayList<VoiceNode> nodes = new ArrayList<>(1);
                nodes.add(node);
                return getActionSet(nodes);
            }
            return outgoingEdges.get(0);
        } else {
            return getActionSet(outgoingEdges);
        }
    }

    @Override
    void addEdge(@NonNull VoiceNode node, @NonNull VoiceNode incomingEdge) {
        if (!graph.containsKey(node) || !graph.containsKey(incomingEdge)) {
            throw new IllegalArgumentException("All nodes must be present in the graph " +
                                               "before being added as an edge");
        }

        ArrayList<VoiceNode> edges = graph.get(node);
        if (edges == null) {
            // If edges is null, we should try and get one from the pool and add it to the graph
            edges = getEmptyList();
            graph.put(node, edges);
        }
        // Finally add the edge to the list
        edges.add(incomingEdge);
    }

    @Override
    public VoiceNode getNode(@NonNull String id) {
        for (int i = 0, size = graph.size(); i < size; i++) {
            VoiceNode node = graph.keyAt(i);
            if (node instanceof HasId && ((HasId) node).getId().equals(id)) {
                return node;
            }
        }
        throw new IllegalArgumentException("Node [" + id + "] does not exists in the graph yet! " +
                                           "Have you forgotten to add it with addNode(" + id + ")? " +
                                           "Have you called from(Node).to(Node) before addNode(" + id + ")?");
    }

    @Override
    public VoiceNode getNode(@StringRes int id) {
        return getNode(context.getString(id));
    }

    @Override
    void start(@NonNull VoiceNode root) {
        currentNode = root;
        next(currentNode);
    }

    private VoiceActionList getActionSet(ArrayList<VoiceNode> edges) {
        String id = "UNKNOWN";
        try {
            VoiceActionList actionSet = new VoiceActionList();
            for (int i = 0, size = edges.size(); i < size; i++) {
                if (edges.get(i) instanceof HasId)
                    id = ((HasId)edges.get(i)).getId();
                actionSet.add((VoiceAction) edges.get(i));
            }
            //Collections.sort(actionSet);
            return actionSet;
        } catch (ClassCastException ignored) {
            throw new IllegalStateException("Only Actions can represent several edges in the graph. Error in [" + id + "] node.");
        }
    }

    @NonNull
    private ArrayList<VoiceNode> getEmptyList() {
        ArrayList<VoiceNode> list = mListPool.acquire();
        if (list == null) {
            list = new ArrayList<>();
        }
        return list;
    }

    @Nullable
    private ArrayList<VoiceNode> getIncomingEdges(@NonNull VoiceNode node) {
        return graph.get(node);
    }

    @Nullable
    private ArrayList<VoiceNode> getOutgoingEdges(@NonNull VoiceNode node) {
        ArrayList<VoiceNode> result = null;
        for (int i = 0, size = graph.size(); i < size; i++) {
            ArrayList<VoiceNode> edges = graph.valueAt(i);
            if (edges != null && edges.contains(node)) {
                if (result == null) {
                    result = new ArrayList<>();
                }
                result.add(graph.keyAt(i));
            }
        }
        return result;
    }


    // Internal

    @Override
    public void addFlag(@Flag int flag) {
        if (this.flags > 0) this.flags |= flag;
        else this.flags = flag;
    }

    @Override
    public void removeFlag(@Flag int flag) {
        if (this.flags > 0) this.flags = this.flags & (~flag);
    }

    @Override
    public boolean hasFlag(@Flag int flag) {
        return (this.flags > 0) && (this.flags & flag) == flag;
    }

    private void processResults(List<String> results, VoiceActionList actions, boolean isPartial) {
        final VoiceMismatch[] mismatchAction = new VoiceMismatch[1];
        for (VoiceNode n : actions) {
            if (n instanceof VoiceMismatch) {
                mismatchAction[0] = (VoiceMismatch) n;
            } else if (n instanceof VoiceMatch) {
                VoiceMatch action = (VoiceMatch) n;
                if (results != null && !results.isEmpty()) {
                    List<String> expected = Arrays.asList(action.expectedResults);
                    boolean matches = ConversationalFlow.anyMatch(results, expected);
                    if (matches) {
                        logger.i(TAG, "- matched with: " + expected);
                        speechRecognizer.stop();
                        currentNode = action;
                        if (action.onMatched != null) {
                            action.onMatched.accept(action, results);
                        }
                        next();
                        return;
                    }
                }
            }
        }
        logger.w(TAG, "- not matched");
        if (!isPartial && mismatchAction[0] != null) {
            //if (mismatchAction[0].retries == 0)
                currentNode = mismatchAction[0];
            noMatch(mismatchAction[0], 0, results);
        } else if (!isPartial) next();
    }

    private void noMatch(VoiceMismatch mismatchAction, int error, @Nullable List<String> results) {
        boolean isUnexpected = error == RecognizerListener.Status.STOPPED_TOO_EARLY_ERROR;
        boolean isLowSound = error == RecognizerListener.Status.LOW_SOUND_ERROR;
        boolean isNoSound = error == RecognizerListener.Status.NO_SOUND_ERROR;

//        if (mismatchAction.retries > 0) {
//            mismatchAction.retries--;
//            logger.v(TAG, "- pending retry: " + mismatchAction.retries);
//            if (isUnexpected && mismatchAction.unexpectedErrorMessage != null) {
//                logger.v(TAG, "- unexpected error");
//                play(mismatchAction.unexpectedErrorMessage, this::next);
//            } else if (hasFlag(FLAG_ENABLE_ERROR_MESSAGE_ON_LOW_SOUND) &&
//                    isLowSound && mismatchAction.lowSoundErrorMessage != null) {
//                logger.v(TAG, "- low sound");
//                play(mismatchAction.lowSoundErrorMessage, this::next);
//            } else if (!isNoSound && !isLowSound && mismatchAction.listeningErrorMessage != null) {
//                play(mismatchAction.listeningErrorMessage, this::next);
//            }
//            //else if (!isNoSound) {
//            //    next();
//            //}
//            else {
//                logger.v(TAG, "- no sound at all!!");
//                // No repeat
//                mismatchAction.retries = 0;
//                if (mismatchAction.onNotMatched != null) {
//                    mismatchAction.onNotMatched.accept(mismatchAction, results);
//                }
//                else next(); // TODO: throw new Missing not matched?
//            }
//        } else {
        //currentNode = mismatchAction;
        if (mismatchAction.onNotMatched != null) {
            mismatchAction.onNotMatched.accept(mismatchAction, results);
        } else next(); // TODO: throw new Missing not matched?
//        }
    }

    private void play(String text, Runnable runnable) {
        // TODO: implement onError
        speechSynthesizer.playTextNow(text, (SynthesizerListener.OnDone) utteranceId -> runnable.run());
    }

    void resetSpeechSynthesizer(SpeechSynthesizer speechSynthesizer) {
        this.speechSynthesizer = speechSynthesizer;
    }

    void resetSpeechRecognizer(SpeechRecognizer speechRecognizer) {
        this.speechRecognizer = speechRecognizer;
    }
}
