package chattylabs.conversations;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.SimpleArrayMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import chattylabs.android.commons.Tag;
import chattylabs.android.commons.internal.ILogger;
import kotlin.collections.CollectionsKt;

class ConversationImpl extends Flow.Edge implements Conversation {
    private final String TAG = Tag.make("Conversation");

    // Log stuff
    private ILogger logger;

    private Context context;

    // Data
    private final SimpleArrayMap<VoiceNode, ArrayList<VoiceNode>> graph = new SimpleArrayMap<>();

    // Resources
    private SpeechSynthesizer speechSynthesizer;
    private SpeechRecognizer speechRecognizer;
    private Flow flow;
    private VoiceNode currentNode;

    private Runnable onCompleteListener;
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
    public Flow prepare(@NonNull Runnable onCompleteListener) {
        this.onCompleteListener = onCompleteListener;
        if (flow == null) flow = new Flow(this);
        return flow;
    }

    @Override
    public Flow prepare() {
        return prepare(() -> {});
    }

    @Override
    public synchronized void next() {
        next(getNext());
    }

    @Override
    public synchronized void next(VoiceNode node) {
        if (currentNode == null)
            throw new IllegalStateException("You must run start(Node)");
        //speechSynthesizer.lock();
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
                VoiceCapture captureAction = null;
                for (VoiceNode n : actions) {
                    if (n instanceof VoiceCapture) {
                        captureAction = (VoiceCapture) n;
                        break;
                    }
                }
                final VoiceCapture finalCaptureAction = captureAction;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (finalCaptureAction != null) {
                        logger.v(TAG, "- running Capture");
                        // Listen Only
                        speechRecognizer.listen(
                            (RecognizerListener.OnMostConfidentResult) result -> {
                                currentNode = finalCaptureAction;
                                ComponentConsumer<VoiceCapture, String> consumer =
                                    finalCaptureAction.onCaptured;
                                if (consumer != null) consumer.accept(finalCaptureAction, result);
                                next();
                            },
                            (RecognizerListener.OnError) (error, originalError) -> {
                                logger.e(TAG, "- listening Capture error");
                                noMatch(finalCaptureAction, null, error, originalError);
                            });
                    } else {
                        logger.v(TAG, "- running Match");
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
                            //(RecognizerListener.OnMostConfidentResult) result -> {
                            //    processMatchResults(Collections.singletonList(result), actions, false);
                            //},
                            (RecognizerListener.OnResults) (results, confidences) -> {
                                //String result = ConversationalFlow.selectMostConfidentResult(results, confidences);
                                processMatchResults(results, actions);
                            },
                            (RecognizerListener.OnPartialResults) (results, confidences) -> {
                                String result = speechRecognizer.selectMostConfidentResult(results, confidences);
                                processPartialMatchResults(Collections.singletonList(result), actions);
                            },
                            (RecognizerListener.OnError) (error, originalError) -> {
                                logger.e(TAG, "- listening Match error");
                                for (VoiceNode n : actions) {
                                    if (n instanceof VoiceMatch) {
                                        noMatch(n, null, error, originalError);
                                    }
                                }
                            });
                    }
                });
            }
        } else {
            // Otherwise there is no more nodes
            logger.w(TAG, "- no more nodes");
            //speechSynthesizer.unlock();
            onCompleteListener.run();
            release();
        }
    }

    /**
     * This process tries to skip listening for a command if the user is on an active speech,
     * like speaking with somebody else or on a call.
     * We understand in these cases, the user isn't intending to say a command.
     * <p/>
     * We observe whether the amount of words recognized are over {@link #MINIMUM_WORDS_TO_DISCARD_RECOGNITION},
     * if so and none of the words match with {@link VoiceMatch#expected}, we stop listening.
     * <p/>
     * This will fire the {@link RecognizerListener.OnResults} callback
     * and will call {@link #processMatchResults(List, VoiceActionList)} which <i>potentially</i> will not match
     * either with any of the {@link VoiceMatch#expected} and will fallback to {@link VoiceMatch#onNotMatched}.
     */
    private void processPartialMatchResults(@NonNull List<String> results, VoiceActionList actions) {
        if (!results.isEmpty() && !TextUtils.isEmpty(results.get(0)) // we receive here a unique most confident result
            && results.get(0).split("\\s+").length > MINIMUM_WORDS_TO_DISCARD_RECOGNITION)
            if (CollectionsKt.none(CollectionsKt.filterIsInstance(actions, VoiceMatch.class),
                                   voiceMatch -> speechRecognizer.anyMatch(results, Arrays.asList(voiceMatch.expected)))) {
                logger.i(TAG, "- partial didn't match: " + results);
                speechRecognizer.stopListening();
            }
    }

    private void processMatchResults(@NonNull List<String> results, VoiceActionList actions) {
        for (VoiceNode node : actions) {
            if (node instanceof VoiceMatch) {
                VoiceMatch action = (VoiceMatch) node;
                List<String> expected = Arrays.asList(action.expected);
                boolean matches = speechRecognizer.anyMatch(results, expected);
                if (matches) {
                    logger.i(TAG, "- matched with: " + expected);
                    currentNode = action;
                    if (action.onMatched != null)
                        action.onMatched.accept(action, results);
                    next();
                    return;
                }
            }
        }

        for (VoiceNode node : actions) {
            if (node instanceof VoiceMatch) {
                // We got results, but none matches
                logger.w(TAG, "- not matched");
                noMatch(node, results, 0, 0);
            }
        }
    }

    private void noMatch(VoiceNode node, List<String> results, int error, int originalError) {
        boolean isUnexpected = error == RecognizerListener.Status.STOPPED_TOO_EARLY_ERROR;
        boolean isLowSound = error == RecognizerListener.Status.LOW_SOUND_ERROR;
        boolean isNoSound = error == RecognizerListener.Status.NO_SOUND_ERROR;
        if (node instanceof VoiceMatch) {
            ComponentConsumer2<VoiceMatch, List<String>, Integer> voiceNotMatched = ((VoiceMatch) node).onNotMatched;
            if (voiceNotMatched != null) {
                currentNode = node;
                voiceNotMatched.accept((VoiceMatch) node, results, error);
            }
        } else if (node instanceof VoiceCapture) {
            ComponentConsumer<VoiceCapture, Integer> noCapture = ((VoiceCapture) node).onNoCapture;
            if (noCapture != null) {
                currentNode = node;
                noCapture.accept((VoiceCapture) node, error);
            }
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
                                               "before generating the Flow. " +
                                               "\nNode [" + (!graph.containsKey(incomingEdge) ?
                                                           ((HasId) incomingEdge).getId() :
                                                           ((HasId) node).getId()) +
                                               "] has not been added yet.");
        }

        ArrayList<VoiceNode> edges = graph.get(node);
        if (edges == null) {
            // If edges is null, we should try and get one and add it to the graph
            edges = new ArrayList<>();
            graph.put(node, edges);
        }

        // Finally add the edge to the list
        edges.add(incomingEdge);

        // Check there is only 1 onNotMatched() listener for the incomingEdge
        if (node instanceof HasNotMatched && ((HasNotMatched) node).getOnNotMatched() != null) {
            ArrayList<VoiceNode> outGoingEdges = getOutgoingEdges(incomingEdge);
            if (outGoingEdges != null) {
                List<String> exceededOnNotMatched = new ArrayList<>();
                for (VoiceNode outGoingEdge : outGoingEdges) {
                    if (outGoingEdge instanceof HasNotMatched
                        && ((HasNotMatched) outGoingEdge).getOnNotMatched() != null)
                        exceededOnNotMatched.add(((HasId) outGoingEdge).getId());
                }
                if (exceededOnNotMatched.size() > 1)
                    throw new IllegalArgumentException("Only 1 Action can have onNotMatched() listener for the corresponding Node " +
                                                       "[" + ((HasId) incomingEdge).getId() + "]. " +
                                                       "\nRemove onNotMatched() from one of the following nodes " + exceededOnNotMatched + " " +
                                                       "or add an extra Action to [" + ((HasId) incomingEdge).getId() + "] " +
                                                       "with only onNotMatched() listener.");
            }
        }
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

    void resetSpeechSynthesizer(SpeechSynthesizer speechSynthesizer) {
        this.speechSynthesizer = speechSynthesizer;
    }

    void resetSpeechRecognizer(SpeechRecognizer speechRecognizer) {
        this.speechRecognizer = speechRecognizer;
    }

    private void release() {
        onCompleteListener = null;
        logger             = null;
        context            = null;
        speechSynthesizer  = null;
        speechRecognizer   = null;
        flow               = null;
        currentNode        = null;
        flags              = 0;
        graph.clear();
    }
}
