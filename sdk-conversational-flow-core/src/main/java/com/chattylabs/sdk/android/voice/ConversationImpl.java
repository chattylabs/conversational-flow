package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pools;
import android.support.v4.util.SimpleArrayMap;

import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;
import com.chattylabs.sdk.android.voice.ConversationalFlowComponent.RecognizerListener;

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
            } else if (node instanceof VoiceActionList) {
                VoiceActionList actions = (VoiceActionList) node;
                VoiceCapture captureAction[] = new VoiceCapture[1];
                VoiceMismatch mismatchAction[] = new VoiceMismatch[1];
                for (VoiceNode n : actions) {
                    if (VoiceCapture.class.isInstance(n)) {
                        captureAction[0] = (VoiceCapture) n;
                    } else if (VoiceMismatch.class.isInstance(n)) {
                        mismatchAction[0] = (VoiceMismatch) n;
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
                                boolean unexpected = error == RecognizerListener.RECOGNIZER_STOPPED_TOO_EARLY_ERROR;
                                boolean isLowSound = error == RecognizerListener.RECOGNIZER_LOW_SOUND_ERROR;
                                boolean isNoSound = error == RecognizerListener.RECOGNIZER_NO_SOUND_ERROR;
                                if (mismatchAction[0] != null)
                                    noMatch(mismatchAction[0], unexpected, isLowSound, isNoSound, null);
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
                                boolean unexpected = error == RecognizerListener.RECOGNIZER_STOPPED_TOO_EARLY_ERROR;
                                boolean isLowSound = error == RecognizerListener.RECOGNIZER_LOW_SOUND_ERROR;
                                boolean isNoSound = error == RecognizerListener.RECOGNIZER_NO_SOUND_ERROR;
                                if (mismatchAction[0] != null)
                                    noMatch(mismatchAction[0], unexpected, isLowSound, isNoSound, null);
                            });
                }
            }
        } else logger.w(TAG, "Conversation - no more nodes, finished.");
        // Otherwise there is no more nodes
    }

    private void processResults(List<String> results, VoiceActionList actions, boolean isPartial) {
        final VoiceMismatch[] mismatchAction = new VoiceMismatch[1];
        for (VoiceNode n : actions) {
            if (VoiceMismatch.class.isInstance(n)) {
                mismatchAction[0] = (VoiceMismatch) n;
            } else if (VoiceMatch.class.isInstance(n)) {
                VoiceMatch action = (VoiceMatch) n;
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
        if (!isPartial && mismatchAction[0] != null) {
            if (mismatchAction[0].retries == 0) current = mismatchAction[0];
            noMatch(mismatchAction[0], false, false, false, results);
        }
    }

    private void noMatch(VoiceMismatch mismatchAction, boolean isUnexpected, boolean isLowSound, boolean isNoSound,
                         @Nullable List<String> results) {
        if (mismatchAction.retries > 0) {
            mismatchAction.retries--;
            logger.v(TAG, "Conversation - pending retry: " + mismatchAction.retries);
            if (isUnexpected && mismatchAction.unexpectedErrorMessage != null) {
                logger.v(TAG, "Conversation - unexpected error");
                play(mismatchAction.unexpectedErrorMessage, this::next);
            } else if (hasFlag(FLAG_ENABLE_ERROR_MESSAGE_ON_LOW_SOUND) &&
                    isLowSound && mismatchAction.lowSoundErrorMessage != null) {
                logger.v(TAG, "Conversation - low sound");
                play(mismatchAction.lowSoundErrorMessage, this::next);
            } else if (!isNoSound && !isLowSound && mismatchAction.listeningErrorMessage != null) {
                play(mismatchAction.listeningErrorMessage, this::next);
            }
            //else if (!isNoSound) {
            //    next();
            //}
            else {
                logger.v(TAG, "Conversation - no sound at all!!");
                // No repeat
                mismatchAction.retries = 0;
                if (mismatchAction.onNotMatched != null) mismatchAction.onNotMatched.accept(results);
                else next(); // TODO: throw new Missing not matched?
            }
        } else {
            if (mismatchAction.onNotMatched != null) mismatchAction.onNotMatched.accept(results);
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
            if (VoiceAction.class.isInstance(node)) {
                ArrayList<VoiceNode> nodes = new ArrayList<>(1);
                nodes.add(node);
                return getActionSet(nodes);
            }
            return outgoingEdges.get(0);
        } else {
            return getActionSet(outgoingEdges);
        }
    }

    private VoiceActionList getActionSet(ArrayList<VoiceNode> edges) {
        try {
            VoiceActionList actionSet = new VoiceActionList();
            for (int i = 0, size = edges.size(); i < size; i++) {
                actionSet.add((VoiceAction) edges.get(i));
            }
            //Collections.sort(actionSet);
            return actionSet;
        } catch (ClassCastException ignored) {
            throw new IllegalStateException("Only Actions can represent several edges in the graph");
        }
    }
}
