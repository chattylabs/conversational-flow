package com.chattylabs.demo.voice;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.voice.Conversation;
import com.chattylabs.sdk.android.voice.Flow;
import com.chattylabs.sdk.android.voice.VoiceMatch;
import com.chattylabs.sdk.android.voice.VoiceMessage;
import com.chattylabs.sdk.android.voice.VoiceMismatch;
import com.chattylabs.sdk.android.voice.VoiceNode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class BuildFromJsonActivity extends BaseActivity {

    private static final String TAG = Tag.make(BuildFromJsonActivity.class);

    private ArrayAdapter<String> listViewAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_build_from_json);
        initViews();
    }

    private void initViews() {
        Button proceed = findViewById(R.id.proceed);
        proceed.setOnClickListener(v -> {
            loadConversation();
        });

        ListView conversationListView = findViewById(R.id.conversation);
        listViewAdapter = new ArrayAdapter<>(this, R.layout.item_block,
                R.id.conversation_item_text,
                new ArrayList<>());
        listViewAdapter.setNotifyOnChange(true);
        TextView emptyView = new TextView(this);
        emptyView.setText("Choose an addon and press on proceed");
        emptyView.setLayoutParams(new AbsListView.LayoutParams(
                AbsListView.LayoutParams.WRAP_CONTENT,
                AbsListView.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        conversationListView.setAdapter(listViewAdapter);
        conversationListView.setEmptyView(emptyView);
    }

    @SuppressLint("MissingPermission")
    private void loadConversation() {
        listViewAdapter.clear();
        listViewAdapter.notifyDataSetChanged();
        Conversation conversation = component.create(this);
        Flow flow = conversation.prepare();
        try {
            String dots = ". . .";
            VoiceNode firstNode = null;
            VoiceNode lastNode = null;
            JSONArray array = new JSONArray(loadJSONFromAsset());
            for (int a = 0, size = array.length(); a < size; a++) {

                JSONObject object = array.getJSONObject(a);

                String text = object.getString("message");
                VoiceMessage message = VoiceMessage.newBuilder()
                        .setText(text)
                        .setOnReady(() -> {
                            runOnUiThread(() -> {
                                listViewAdapter.add(text);
                            });
                        }).build();

                VoiceMatch matches = null;
                VoiceMismatch noMatches = null;
                if (object.has("results")) {
                    JSONArray jsonArray = object.getJSONArray("results");
                    String[] stringArray = new String[jsonArray.length()];
                    for (int i = 0; i < jsonArray.length(); i++) {
                        stringArray[i] = jsonArray.getString(i);
                    }
                    matches = VoiceMatch.newBuilder()
                            .setOnReady(() -> {
                                listViewAdapter.add(dots);
                            })
                            .setExpectedResults(stringArray)
                            .setOnMatched(strings -> {
                                listViewAdapter.remove(dots);
                                if (strings != null) {
                                    listViewAdapter.add(strings.get(0));
                                    conversation.next();
                                }
                            })
                            .build();
                    noMatches = VoiceMismatch.newBuilder()
                            .setOnNotMatched(strings -> {
                                listViewAdapter.remove(dots);
                                listViewAdapter.add("You said: " + strings);
                                listViewAdapter.add("I did not expect that. Please try again!");
                            }).build();
                }

                conversation.addNode(message);
                if (matches != null) {
                    conversation.addNode(matches);
                    conversation.addNode(noMatches);
                }
                if (lastNode != null) flow.from(lastNode).to(message);
                if (firstNode == null) firstNode = message;
                if (matches != null) {
                    flow.from(message).to(matches, noMatches);
                }
                lastNode = matches != null ? matches : message;
            }

            conversation.start(firstNode);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String loadJSONFromAsset() {
        String json = null;
        try {
            InputStream is = getResources().openRawResource(R.raw.demo_conversation);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }
}
