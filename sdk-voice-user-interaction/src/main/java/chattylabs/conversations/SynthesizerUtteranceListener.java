package chattylabs.conversations;

/**
 * @see android.speech.tts.UtteranceProgressListener
 */
interface SynthesizerUtteranceListener {

    void onStart(String utteranceId);

    void onDone(String utteranceId);

    void onError(String utteranceId, int errorCode);

}
