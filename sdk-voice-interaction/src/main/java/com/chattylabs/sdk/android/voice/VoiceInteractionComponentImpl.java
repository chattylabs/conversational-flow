package com.chattylabs.sdk.android.voice;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pools;
import android.support.v4.util.SimpleArrayMap;
import android.util.Log;

import com.chattylabs.sdk.android.common.Tag;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

@dagger.Reusable
final class VoiceInteractionComponentImpl implements VoiceInteractionComponent {

    private TextToSpeechManager textToSpeechManager;
    private VoiceRecognitionManager voiceRecognitionManager;
    private SpeechRecognizer speechRecognizer;
    private SpeechSynthesizer speechSynthesizer;

    @Inject
    VoiceInteractionComponentImpl() {
        Instance.instanceOf = new SoftReference<>(this);
    }

    @Override
    public String[] requiredPermissions() {
        return new String[]{Manifest.permission.RECORD_AUDIO};
    }

    private void init(Application application) {
        if (textToSpeechManager == null) textToSpeechManager = new TextToSpeechManager(application);
        if (voiceRecognitionManager == null) voiceRecognitionManager = new VoiceRecognitionManager(
                application, () -> android.speech.SpeechRecognizer.createSpeechRecognizer(application));
    }

    @Override
    public void setup(Context context, OnSetupListener onSetupListener) {
        Application application = (Application) context.getApplicationContext();
        init(application);
        textToSpeechManager.setup(application, textToSpeechStatus -> {
            int speechRecognizerStatus = android.speech.SpeechRecognizer.isRecognitionAvailable(application) ?
                                         VOICE_RECOGNITION_AVAILABLE : VOICE_RECOGNITION_NOT_AVAILABLE;
            onSetupListener.execute(new VoiceInteractionStatus() {
                @Override
                public boolean isAvailable() {
                    return textToSpeechStatus == TEXT_TO_SPEECH_AVAILABLE &&
                           speechRecognizerStatus == VOICE_RECOGNITION_AVAILABLE;
                }

                @Override
                public int getTextToSpeechStatus() {
                    return textToSpeechStatus;
                }

                @Override
                public int getSpeechRecognizerStatus() {
                    return speechRecognizerStatus;
                }
            });
        });
    }

    @Override
    public void setBluetoothScoRequired(Context context, boolean required) {
        getSpeechSynthesizer(context).setBluetoothScoRequired(required);
        getSpeechRecognizer(context).setBluetoothScoRequired(required);
    }

    @Override
    public VoiceInteractionComponent.SpeechSynthesizer getSpeechSynthesizer(Context context) {
        if (speechSynthesizer == null || textToSpeechManager == null) {
            Log.w(TAG, "Synthesizer - create new object");
            init((Application) context.getApplicationContext());
            speechSynthesizer = new VoiceInteractionComponent.SpeechSynthesizer() {
                @Override
                public void addFilter(MessageFilter filter) {
                    textToSpeechManager.addFilter(filter);
                }

                @Override
                public void setBluetoothScoRequired(boolean required) {
                    textToSpeechManager.setBluetoothScoRequired(required);
                }

                @SafeVarargs
                @Override
                public final <T extends TextToSpeechListeners> void play(String text, String groupId, T... listeners) {
                    textToSpeechManager.speak(text, groupId, listeners);
                }

                @SafeVarargs
                @Override
                public final <T extends TextToSpeechListeners> void playNow(String text, T... listeners) {
                    textToSpeechManager.speakNow(text, listeners);
                }

                @SafeVarargs
                @Override
                public final <T extends TextToSpeechListeners> void playSilence(long durationInMillis, String groupId, T... listeners) {
                    textToSpeechManager.playSilence(durationInMillis, groupId, listeners);
                }

                @SafeVarargs
                @Override
                public final <T extends TextToSpeechListeners> void playSilenceNow(long durationInMillis, T... listeners) {
                    textToSpeechManager.playSilenceNow(durationInMillis, listeners);
                }

                @Override
                public void resume() {
                    textToSpeechManager.resume();
                }

                @Override
                public void shutdown() {
                    if (textToSpeechManager != null) {
                        textToSpeechManager.shutdown();
                        textToSpeechManager = null;
                    }
                }

                @Override
                public boolean isEmpty() {
                    return textToSpeechManager.isEmpty();
                }

                @Override
                public boolean isCurrentGroupEmpty() {
                    return textToSpeechManager.isCurrentGroupEmpty();
                }

                @Override
                public String lastGroup() {
                    return textToSpeechManager.getLastGroup();
                }

                @Nullable
                @Override
                public String nextGroup() {
                    return textToSpeechManager.getNextGroup();
                }

                @Override
                public String group() {
                    return textToSpeechManager.getGroupId();
                }

                @Override
                public Set<String> groupQueue() {
                    return textToSpeechManager.getGroupQueue();
                }
            };
        }
        return speechSynthesizer;
    }

    @Override
    public VoiceInteractionComponent.SpeechRecognizer getSpeechRecognizer(Context context) {
        if (speechRecognizer == null || voiceRecognitionManager == null) {
            Log.w(TAG, "Recognizer - create new object");
            init((Application) context.getApplicationContext());
            speechRecognizer = new VoiceInteractionComponent.SpeechRecognizer() {
                @SafeVarargs
                @Override
                public final <T extends VoiceRecognitionListeners> void listen(T... listeners) {
                    voiceRecognitionManager.start(listeners);
                }

                @Override
                public void setBluetoothScoRequired(boolean required) {
                    voiceRecognitionManager.setBluetoothScoRequired(required);
                }

                @Override
                public void shutdown() {
                    if (voiceRecognitionManager != null) {
                        voiceRecognitionManager.shutdown();
                        voiceRecognitionManager = null;
                    }
                }

                @Override
                public void cancel() {
                    voiceRecognitionManager.cancel();
                }

                @Override
                public void setRmsDebug(boolean debug) {
                    voiceRecognitionManager.setRmsDebug(debug);
                }

                @Override
                public void setNoSoundThreshold(float maxValue) {
                    voiceRecognitionManager.setNoSoundThreshold(maxValue);
                }

                @Override
                public void setLowSoundThreshold(float maxValue) {
                    voiceRecognitionManager.setLowSoundThreshold(maxValue);
                }
            };
        }
        return speechRecognizer;
    }

    @Override
    public VoiceInteractionComponent.Conversation createConversation(Context context) {
        return new VoiceInteractionComponent.Conversation() {
            private final String TAG = Tag.make(Conversation.class);

            private final Pools.Pool<ArrayList<Node>> mListPool = new Pools.SimplePool<>(10);
            private final SimpleArrayMap<Node, ArrayList<Node>> graph = new SimpleArrayMap<>();

            private Flow flow;
            private Node current;
            private int flags;

            @Override
            public VoiceInteractionComponent.SpeechSynthesizer getSpeechSynthesizer() {
                return VoiceInteractionComponentImpl.this.getSpeechSynthesizer(context);
            }

            @Override
            public VoiceInteractionComponent.SpeechRecognizer getSpeechRecognizer() {
                return VoiceInteractionComponentImpl.this.getSpeechRecognizer(context);
            }

            @Override
            public void addFlag(@Flag int flag) {
                if (this.flags > 0)
                    this.flags |= flag;
                else this.flags = flag;
            }

            @Override
            public void removeFlag(@Flag int flag) {
                if (this.flags > 0)
                    this.flags = this.flags & (~flag);
            }

            @Override
            public boolean hasFlag(@Flag int flag) {
                return (this.flags > 0) && (this.flags & flag) == flag;
            }

            @Override
            public void addNode(@NonNull Node node) {
                if (!graph.containsKey(node)) {
                    graph.put(node, null);
                }
            }

            @Override
            public EdgeSource prepare() {
                if (flow == null) flow = new Flow(this);
                return flow;
            }

            @Override
            public void addEdge(@NonNull Node node, @NonNull Node incomingEdge) {
                if (!graph.containsKey(node) || !graph.containsKey(incomingEdge)) {
                    throw new IllegalArgumentException("All nodes must be present in the graph before being added as an edge");
                }

                ArrayList<Node> edges = graph.get(node);
                if (edges == null) {
                    // If edges is null, we should try and get one from the pool and add it to the graph
                    edges = getEmptyList();
                    graph.put(node, edges);
                }
                // Finally add the edge to the list
                edges.add(incomingEdge);
            }

            @Override
            public void start(Node root) {
                current = root; next(current);
            }

            @Override
            public void next() {
                Log.v(TAG, "Conversation - running next");
                next(getNext());
            }

            private void next(Node node) {
                if (current == null) throw new NullPointerException("You must start(Node) the conversation");
                if (node != null) {
                    if (node instanceof VoiceMessage) {
                        VoiceMessage message = (VoiceMessage) node;
                        Log.v(TAG, "Conversation - running Message: " + message.text);
                        current = message;
                        SpeechSynthesizer speechSynthesizer = getSpeechSynthesizer();
                        speechSynthesizer.playNow(
                                message.text,
                                (OnTextToSpeechDoneListener) utteranceId -> {
                                    if (message.onSuccess != null) {
                                        message.onSuccess.run();
                                    }
                                    next();
                                }); // TODO: onError
                    } else if (node instanceof VoiceActionSet) {
                        VoiceActionSet actions = (VoiceActionSet) node;
                        VoiceCaptureAction captureAction[] = new VoiceCaptureAction[1];
                        VoiceNoMatchAction noMatchAction[] = new VoiceNoMatchAction[1];
                        for (Node n : actions) {
                            if (VoiceCaptureAction.class.isInstance(n)) {
                                captureAction[0] = (VoiceCaptureAction) n;
                            } else if (VoiceNoMatchAction.class.isInstance(n)) {
                                noMatchAction[0] = (VoiceNoMatchAction) n;
                            }
                        }
                        if (captureAction[0] != null) {
                            Log.v(TAG, "Conversation - running Capture");
                            // Listen Only
                            getSpeechRecognizer().listen(
                                    (OnVoiceRecognitionMostConfidentResultListener) result -> {
                                        current = captureAction[0];
                                        Consumer<String> consumer = captureAction[0].onCaptured;
                                        if (consumer != null) consumer.accept(result);
                                        else next();
                                    },
                                    (OnVoiceRecognitionErrorListener) (error, originalError) -> {
                                        Log.e(TAG, "Conversation - listening Capture error");
                                        boolean unexpected = error == VOICE_RECOGNITION_STOPPED_TOO_EARLY_ERROR;
                                        boolean isLowSound = error == VOICE_RECOGNITION_LOW_SOUND_ERROR;
                                        boolean isNoSound = error == VOICE_RECOGNITION_NO_SOUND_ERROR;
                                        if (noMatchAction[0] != null) noMatch(noMatchAction[0], unexpected, isLowSound, isNoSound, null);
                                    });
                        } else {
                            Log.v(TAG, "Conversation - running Actions");
                            getSpeechRecognizer().listen(
                                    (OnVoiceRecognitionResultsListener) (results, confidences) -> {
                                        String result = VoiceInteractionComponent.selectMostConfidentResult(results, confidences);
                                        processResults(Collections.singletonList(result), actions, false);
                                    },
                                    (OnVoiceRecognitionPartialResultsListener) (results, confidences) -> {
                                        String result = VoiceInteractionComponent.selectMostConfidentResult(results, confidences);
                                        processResults(Collections.singletonList(result), actions, true);
                                    },
                                    (OnVoiceRecognitionErrorListener) (error, originalError) -> {
                                        Log.e(TAG, "Conversation - listening Action error");
                                        boolean unexpected = error == VOICE_RECOGNITION_STOPPED_TOO_EARLY_ERROR;
                                        boolean isLowSound = error == VOICE_RECOGNITION_LOW_SOUND_ERROR;
                                        boolean isNoSound = error == VOICE_RECOGNITION_NO_SOUND_ERROR;
                                        if (noMatchAction[0] != null) noMatch(noMatchAction[0], unexpected, isLowSound, isNoSound, null);
                                    });
                        }
                    }
                } else Log.w(TAG, "Conversation - no more nodes, finished.");
                // Otherwise there is no more nodes
            }

            private void processResults(List<String> results, VoiceActionSet actions, boolean isPartial) {
                final VoiceNoMatchAction[] noMatchAction = new VoiceNoMatchAction[1];
                for (Node n : actions) {
                    if (VoiceNoMatchAction.class.isInstance(n)) {
                        noMatchAction[0] = (VoiceNoMatchAction) n;
                    } else if (VoiceAction.class.isInstance(n)) {
                        VoiceAction action = (VoiceAction) n;
                        if (results != null && results.size() > 0 && results.get(0).length() > 0) {
                            List<String> expected = Arrays.asList(action.expectedResults);
                            boolean matches = VoiceInteractionComponent.anyMatch(results, expected);
                            if (matches) {
                                Log.i(TAG, "Conversation - matched with: " + expected);
                                getSpeechRecognizer().cancel();
                                current = action;
                                if (action.onMatched != null) action.onMatched.accept(results);
                                else next();
                                return;
                            }
                        }
                    }
                }
                Log.w(TAG, "Conversation - not matched");
                if (!isPartial && noMatchAction[0] != null) {
                    if (noMatchAction[0].retry == 0) current = noMatchAction[0];
                    noMatch(noMatchAction[0], false, false, false, results);
                }
            }

            private void noMatch(VoiceNoMatchAction noMatchAction, boolean isUnexpected, boolean isLowSound, boolean isNoSound,
                                 @Nullable List<String> results) {
                if (noMatchAction.retry > 0) {
                    noMatchAction.retry--;
                    Log.v(TAG, "Conversation - pending retry: " + noMatchAction.retry);
                    if (isUnexpected && noMatchAction.unexpectedErrorMessage != null) {
                        Log.v(TAG, "Conversation - unexpected error");
                        play(noMatchAction.unexpectedErrorMessage, this::next);
                    }
                    else if (hasFlag(FLAG_ENABLE_ON_LOW_SOUND_ERROR_MESSAGE) &&
                            isLowSound && noMatchAction.lowSoundErrorMessage != null) {
                        Log.v(TAG, "Conversation - low sound");
                        play(noMatchAction.lowSoundErrorMessage, this::next);
                    }
                    else if (!isNoSound && !isLowSound && noMatchAction.listeningErrorMessage != null) {
                        play(noMatchAction.listeningErrorMessage, this::next);
                    }
                    //else if (!isNoSound) {
                    //    next();
                    //}
                    else {
                        Log.v(TAG, "Conversation - no sound at all!!");
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
                SpeechSynthesizer speechSynthesizer = getSpeechSynthesizer();
                speechSynthesizer.playNow(text, (OnTextToSpeechDoneListener) utteranceId -> runnable.run()); // TODO: onError
            }

            @NonNull
            private ArrayList<Node> getEmptyList() {
                ArrayList<Node> list = mListPool.acquire();
                if (list == null) {
                    list = new ArrayList<>();
                }
                return list;
            }

            @Nullable
            private ArrayList<Node> getIncomingEdges(@NonNull Node node) {
                return graph.get(node);
            }

            @Nullable
            private ArrayList<Node> getOutgoingEdges(@NonNull Node node) {
                ArrayList<Node> result = null;
                for (int i = 0, size = graph.size(); i < size; i++) {
                    ArrayList<Node> edges = graph.valueAt(i);
                    if (edges != null && edges.contains(node)) {
                        if (result == null) {
                            result = new ArrayList<>();
                        }
                        result.add(graph.keyAt(i));
                    }
                }
                return result;
            }

            private Node getNext() {
                ArrayList<Node> outgoingEdges = getOutgoingEdges(current);
                if (outgoingEdges == null || outgoingEdges.isEmpty()) {
                    return null;
                }

                if (outgoingEdges.size() == 1) {
                    Node node = outgoingEdges.get(0);
                    if (IAction.class.isInstance(node)) {
                        ArrayList<Node> nodes = new ArrayList<>(1);
                        nodes.add(node);
                        return getActionSet(nodes);
                    }
                    return outgoingEdges.get(0);
                }
                else {
                    return getActionSet(outgoingEdges);
                }
            }

            private VoiceActionSet getActionSet(ArrayList<Node> edges) {
                try {
                    VoiceActionSet actionSet = new VoiceActionSet();
                    for (int i = 0, size = edges.size(); i < size; i++) {
                        actionSet.add((IAction)edges.get(i));
                    }
                    //Collections.sort(actionSet);
                    return actionSet;
                } catch (ClassCastException ignored) {
                    throw new IllegalStateException("Only Actions can represent several edges in the graph");
                }
            }
        };
    }

    @Override
    public void stop() {
        if (textToSpeechManager != null) textToSpeechManager.stop();
        if (voiceRecognitionManager != null) voiceRecognitionManager.stopAndSendCapturedSpeech();
    }

    @Override
    public void shutdown() {
        if (speechSynthesizer != null) {
            speechSynthesizer.shutdown();
            speechSynthesizer = null;
        }
        if (speechRecognizer != null) {
            speechRecognizer.shutdown();
            speechRecognizer = null;
        }
    }
}