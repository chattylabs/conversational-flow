package chattylabs.conversations.demo;

import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import chattylabs.android.commons.HtmlUtils;
import chattylabs.android.commons.Tag;
import chattylabs.conversations.Peripheral;
import chattylabs.conversations.RecognizerListener;
import chattylabs.conversations.SynthesizerListener;

import static chattylabs.conversations.ConversationalFlow.matches;


public class TestingAddonsActivity extends BaseActivity {

    private static final String TAG = Tag.make(TestingAddonsActivity.class);

    // Constants
    private static final int LISTEN = 1;
    private static final int READ = 0;
    // ...

    // Resources
    private TextView execution;
    private Spinner actionSpinner;
    private Spinner audioModeSpinner;
    private EditText text;
    private Button add;
    private Button clear;
    private SparseArray<Pair<Integer, String>> queue = new SparseArray<>();
    private ArrayAdapter<CharSequence> actionAdapter;
    private ArrayAdapter<CharSequence> audioModeAdapter;

    // Components
    private Peripheral peripheral;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_testing_addons);
        peripheral = new Peripheral((AudioManager) getSystemService(AUDIO_SERVICE));
        initCommonViews();
        initViews();
        initActions();
    }

    @Override
    public void onDestroy() {
        component.updateConfiguration(
                builder -> {
                    builder.setBluetoothScoRequired(() -> false);
                    return builder.build();
                });
        component.shutdown();
        super.onDestroy();
    }

    private void initActions() {
        add.setOnClickListener(v -> {
            String msg = text.getText().toString().trim();
            if (msg.length() > 0) {
                int itemPosition = actionSpinner.getSelectedItemPosition();
                queue.put(queue.size(), Pair.create(itemPosition, msg));
                representQueue(-1);
                proceed.setEnabled(true);
                text.setText(null);
            }
        });
        clear.setOnClickListener(v -> {
            queue.clear();
            text.setText(null);
            execution.setText(null);
            component.shutdown();
        });
        proceed.setOnClickListener((View v) -> {
            representQueue(-1);
            readAll();
        });
    }

    private void representQueue(int index) {
        representQueue(index, false);
    }
    private void representQueue(int index, boolean listen) {
        StringBuilder tx = new StringBuilder();
        int lastKey = -2;
        for (int i = 0; i < queue.size(); i++) {
            Pair<Integer, String> item = queue.get(i);
            boolean isListen = item.first == LISTEN;
            String text = item.second;
            String label = (String) actionAdapter.getItem(item.first);
            if (i == index) {
                if (text != null && !listen) {
                    text = "<font color=\"#FFFFFF\">" + text + "</font>";
                } else {
                    label = "<font color=\"#FFFFFF\">" + label + "</font>";
                }
            }
            String action = label + " \"<i>" + text + "</i>\" ";
            if (lastKey == item.first && isListen)
                tx.append("or \"<i>").append(text).append("</i>\" ");
            else if (lastKey == item.first)
                tx.append("then \"<i>").append(text).append("</i>\" ");
            else
                tx.append("<br/><br/> > ").append(action);
            lastKey = item.first;
        }

        final StringBuilder copy = tx;

        runOnUiThread(() -> {
            execution.setText(HtmlUtils.from(copy.toString()));
        });
    }

    private void playByIndex(String text, int index) {
        synthesizer.playText(text, "default",
                (SynthesizerListener.OnStart) utteranceId -> {
                    representQueue(index);
                },
                (SynthesizerListener.OnDone) utteranceId -> {
                    Log.i(TAG, "on Done index: " + index);
                    Pair<Integer, String> next = queue.get(index + 1);
                    if (next != null && next.first == LISTEN) {
                        synthesizer.holdCurrentQueue();
                        listen(index);
                    } else {
                        if (synthesizer.isEmpty()) {
                            component.shutdown();
                        } else {
                            synthesizer.freeCurrentQueue();
                            synthesizer.resume();
                        }
                    }
                },
                (SynthesizerListener.OnError) (utteranceId, errorCode) -> {
                    if (errorCode == SynthesizerListener.Status.UNKNOWN_ERROR) {
                        if (synthesizer.isEmpty()) {
                            component.shutdown();
                        } else {
                            synthesizer.freeCurrentQueue();
                            synthesizer.resume();
                        }
                    } else {
                        component.shutdown();
                    }
                });
    }

    private void listen(int index) {
        recognizer.listen((RecognizerListener.OnReady) bundle -> representQueue(index + 1, true),
                (RecognizerListener.OnMostConfidentResult) o -> {
                    representQueue(-1);
                    SparseArray<String> news = getChecks(new SparseArray<>(), index + 1);
                    if (news.size() > 0) {
                        for (int a = 0; a < news.size(); a++) {
                            if (matches(news.valueAt(a), o)) {
                                representQueue(news.keyAt(a));
                                break;
                            }
                        }
                    }
                    new Handler().postDelayed(() -> {
                        if (synthesizer.isEmpty()) {
                            component.shutdown();
                        } else {
                            synthesizer.freeCurrentQueue();
                            synthesizer.resume();
                        }
                    }, 1000);
                }, (RecognizerListener.OnError) (i, i1) -> {
                    Log.e(TAG, "Error " + i);

                    if (synthesizer.isEmpty()) {
                        component.shutdown();
                    } else {
                        synthesizer.freeCurrentQueue();
                        synthesizer.resume();
                    }

                    //runOnUiThread(() -> new AlertDialog.Builder(this)
                    //        .setTitle("Error")
                    //        .setMessage(getErrorString(i1))
                    //        .create().show());
                });
    }

    private SparseArray<String> getChecks(SparseArray<String> news, int index) {
        Pair<Integer, String> checks = queue.get(index);
        if (checks != null && checks.first == LISTEN) {
            news.append(index, checks.second);
            news = getChecks(news, index + 1);
        }
        return news;
    }

    private void readAll() {
        serialThread.addTask(() -> { System.out.println("readAll");
            Log.i(TAG, "Start processing the queue");
            for (int i = 0; i < queue.size(); i++) {
                Log.i(TAG, "queue index: " + i);
                Pair<Integer, String> item = queue.get(i);
                if (item.first == READ) {
                    playByIndex(item.second, i);
                } else if (i == 0 && item.first == LISTEN) {
                    listen(-1);
                }
            }
        });
    }

    private void initViews() {
        execution = findViewById(R.id.execution);
        actionSpinner = findViewById(R.id.action);
        audioModeSpinner = findViewById(R.id.audio_mode);
        text = findViewById(R.id.text);
        add = findViewById(R.id.add);
        clear = findViewById(R.id.clear);
        CheckBox scoCheck = findViewById(R.id.bluetooth_sco);

        execution.setMovementMethod(new ScrollingMovementMethod());

        proceed.setEnabled(false);
        proceedEnabled = false;

        // Check if there is a Bluetooth device connected and checkStatus the config for a Sco connection
        scoCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !peripheral.get(Peripheral.Type.BLUETOOTH).isConnected()) {
                buttonView.setChecked(false);
                Toast.makeText(this, "Not connected to a Bluetooth device", Toast.LENGTH_LONG).show();
                return;
            }
            component.updateConfiguration(
                    builder -> {
                        builder.setBluetoothScoRequired(() ->
                                peripheral.get(Peripheral.Type.BLUETOOTH).isConnected() && isChecked);
                        return builder.build();
                    });
        });

        // Create an ArrayAdapter for the actions
        actionAdapter = ArrayAdapter.createFromResource(this, R.array.actions, android.R.layout.simple_spinner_item);
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actionSpinner.setAdapter(actionAdapter);
        actionSpinner.setSelection(0, false);

        // Create an ArrayAdapter for the audio modes
        audioModeAdapter = ArrayAdapter.createFromResource(this, R.array.audio_mode, android.R.layout.simple_spinner_item);
        audioModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        audioModeSpinner.setAdapter(audioModeAdapter);
        audioModeSpinner.setSelection(0, false);
        audioModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                component.updateConfiguration(
                        builder -> {
                            builder.setCustomBeepEnabled(() -> position != 0)
                                    .setBluetoothScoAudioMode(() -> position == 0 ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_IN_CALL);
                            return builder.build();
                        });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }
}
