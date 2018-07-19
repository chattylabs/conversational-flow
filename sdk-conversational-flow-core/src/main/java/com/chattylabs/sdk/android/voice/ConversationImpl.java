package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pools;
import android.support.v4.util.SimpleArrayMap;

import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class ConversationImpl extends Flow.Edge implements Conversation {
    private final String TAG = Tag.make("Conversation");

    // Data
    private final Pools.Pool<ArrayList<VoiceNode>> mListPool = new Pools.SimplePool<>(10);
    private final SimpleArrayMap<VoiceNode, ArrayList<VoiceNode>> graph = new SimpleArrayMap<>();

    // Resources
    private ConversationalFlowComponent.SpeechSynthesizer speechSynthesizer;
    private ConversationalFlowComponent.SpeechRecognizer speechRecognizer;
    private Flow flow;
    private VoiceNode current;
    private int flags;

    // Log stuff
    private ILogger logger;

    ConversationImpl(ConversationalFlowComponent.SpeechSynthesizer speechSynthesizer,
                     ConversationalFlowComponent.SpeechRecognizer speechRecognizer,
                     ILogger logger) {
        this.speechSynthesizer = speechSynthesizer;
        this.speechRecognizer = speechRecognizer;
        this.logger = logger;
    }

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
    public void start(VoiceNode root) {
        current = root;
        next(current);
    }

    @Override
    public void next() {
        logger.v(TAG, "Conversation - running next");
        next(getNext());
    }

    private void next(VoiceNode node) {
        if (current == null)
            throw new NullPointerException("You must start(Node) the conversation");
        if (node != null) {
            if (node instanceof VoiceMessage) {
                VoiceMessage message = (VoiceMessage) node;
                logger.v(TAG, "Conversation - running Message: " + message.text);
                current = message;
                speechSynthesizer.playText(
                        message.text,
                        (ConversationalFlowComponent.OnSynthesizerDone) utteranceId -> {
                            if (message.onSuccess != null) {
                                message.onSuccess.run();
                            }
                            next();
                        }); // TODO: implement onError
            } else if (node instanceof VoiceActionSet) {
                VoiceActionSet actions = (VoiceActionSet) node;
                VoiceCaptureAction captureAction[] = new VoiceCaptureAction[1];
                VoiceNoMatchAction noMatchAction[] = new VoiceNoMatchAction[1];
                for (VoiceNode n : actions) {
                    if (VoiceCaptureAction.class.isInstance(n)) {
                        captureAction[0] = (VoiceCaptureAction) n;
                    } else if (VoiceNoMatchAction.class.isInstance(n)) {
                        noMatchAction[0] = (VoiceNoMatchAction) n;
                    }
                }
                if (captureAction[0] != null) {
                    logger.v(TAG, "Conversation - running Capture");
                    // Listen Only
                    speechRecognizer.listen(
                            (ConversationalFlowComponent.OnRecognizerMostConfidentResult) result -> {
                                current = captureAction[0];
                                ConversationalFlowComponent.Consumer<String> consumer = captureAction[0].onCaptured;
                                if (consumer != null) consumer.accept(result);
                                else next();
                            },
                            (ConversationalFlowComponent.OnRecognizerError) (error, originalError) -> {
                                logger.e(TAG, "Conversation - listening Capture error");
                                boolean unexpected = error == ConversationalFlowComponent.RECOGNIZER_STOPPED_TOO_EARLY_ERROR;
                                boolean isLowSound = error == ConversationalFlowComponent.RECOGNIZER_LOW_SOUND_ERROR;
                                boolean isNoSound = error == ConversationalFlowComponent.RECOGNIZER_NO_SOUND_ERROR;
                                if (noMatchAction[0] != null)
                                    noMatch(noMatchAction[0], unexpected, isLowSound, isNoSound, null);
                            });
                } else {
                    logger.v(TAG, "Conversation - running Actions");
                    speechRecognizer.listen(
                            (ConversationalFlowComponent.OnRecognizerResults) (results, confidences) -> {
                                String result = ConversationalFlowComponent.selectMostConfidentResult(results, confidences);
                                processResults(Collections.singletonList(result), actions, false);
                            },
                            (ConversationalFlowComponent.OnRecognizerPartialResults) (results, confidences) -> {
                                String result = ConversationalFlowComponent.selectMostConfidentResult(results, confidences);
                                processResults(Collections.singletonList(result), actions, true);
                            },
                            (ConversationalFlowComponent.OnRecognizerError) (error, originalError) -> {
                                logger.e(TAG, "Conversation - listening Action error");
                                boolean unexpected = error == ConversationalFlowComponent.RECOGNIZER_STOPPED_TOO_EARLY_ERROR;
                                boolean isLowSound = error == ConversationalFlowComponent.RECOGNIZER_LOW_SOUND_ERROR;
                                boolean isNoSound = error == ConversationalFlowComponent.RECOGNIZER_NO_SOUND_ERROR;
                                if (noMatchAction[0] != null)
                                    noMatch(noMatchAction[0], unexpected, isLowSound, isNoSound, null);
                            });
                }
            }
        } else logger.w(TAG, "Conversation - no more nodes, finished.");
        // Otherwise there is no more nodes
    }

    private void processResults(List<String> results, VoiceActionSet actions, boolean isPartial) {
        final VoiceNoMatchAction[] noMatchAction = new VoiceNoMatchAction[1];
        for (VoiceNode n : actions) {
            if (VoiceNoMatchAction.class.isInstance(n)) {
                noMatchAction[0] = (VoiceNoMatchAction) n;
            } else if (VoiceAction.class.isInstance(n)) {
                VoiceAction action = (VoiceAction) n;
                if (results != null && !results.isEmpty() && results.get(0).length() > 0) {
                    List<String> expected = Arrays.asList(action.expectedResults);
                    boolean matches = ConversationalFlowComponent.anyMatch(results, expected);
                    if (matches) {
                        logger.i(TAG, "Conversation - matched with: " + expected);
                        speechRecognizer.cancel();
                        current = action;
                        if (action.onMatched != null) action.onMatched.accept(results);
                        else next();
                        return;
                    }
                }
            }
        }
        logger.w(TAG, "Conversation - not matched");
        if (!isPartial && noMatchAction[0] != null) {
            if (noMatchAction[0].retry == 0) current = noMatchAction[0];
            noMatch(noMatchAction[0], false, false, false, results);
        }
    }

    private void noMatch(VoiceNoMatchAction noMatchAction, boolean isUnexpected, boolean isLowSound, boolean isNoSound,
                         @Nullable List<String> results) {
        if (noMatchAction.retry > 0) {
            noMatchAction.retry--;
            logger.v(TAG, "Conversation - pending retry: " + noMatchAction.retry);
            if (isUnexpected && noMatchAction.unexpectedErrorMessage != null) {
                logger.v(TAG, "Conversation - unexpected error");
                play(noMatchAction.unexpectedErrorMessage, this::next);
            } else if (hasFlag(FLAG_ENABLE_ERROR_MESSAGE_ON_LOW_SOUND) &&
                    isLowSound && noMatchAction.lowSoundErrorMessage != null) {
                logger.v(TAG, "Conversation - low sound");
                play(noMatchAction.lowSoundErrorMessage, this::next);
            } else if (!isNoSound && !isLowSound && noMatchAction.listeningErrorMessage != null) {
                play(noMatchAction.listeningErrorMessage, this::next);
            }
            //else if (!isNoSound) {
            //    next();
            //}
            else {
                logger.v(TAG, "Conversation - no sound at all!!");
                // No repeat
                noMatchAction.retry = 0;
                if (noMatchAction.onNotMatched != null) noMatchAction.onNotMatched.accept(results);
                else next(); // TODO: throw new Missing not matched?
            }
        } else {
            if (noMatchAction.onNotMatched != null) noMatchAction.onNotMatched.accept(results);
            else next(); // TODO: throw new Missing not matched?
        }
    }

    private void play(String text, Runnable runnable) {
        // TODO: implement onError
        speechSynthesizer.playText(text, (ConversationalFlowComponent.OnSynthesizerDone) utteranceId -> runnable.run());
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

    private VoiceNode getNext() {
        ArrayList<VoiceNode> outgoingEdges = getOutgoingEdges(current);
        if (outgoingEdges == null || outgoingEdges.isEmpty()) {
            return null;
        }

        if (outgoingEdges.size() == 1) {
            VoiceNode node = outgoingEdges.get(0);
            if (VoiceActionContract.class.isInstance(node)) {
                ArrayList<VoiceNode> nodes = new ArrayList<>(1);
                nodes.add(node);
                return getActionSet(nodes);
            }
            return outgoingEdges.get(0);
        } else {
            return getActionSet(outgoingEdges);
        }
    }

    private VoiceActionSet getActionSet(ArrayList<VoiceNode> edges) {
        try {
            VoiceActionSet actionSet = new VoiceActionSet();
            for (int i = 0, size = edges.size(); i < size; i++) {
                actionSet.add((VoiceActionContract) edges.get(i));
            }
            //Collections.sort(actionSet);
            return actionSet;
        } catch (ClassCastException ignored) {
            throw new IllegalStateException("Only Actions can represent several edges in the graph");
        }
    }
}
