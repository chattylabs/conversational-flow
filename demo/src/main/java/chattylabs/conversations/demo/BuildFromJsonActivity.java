package chattylabs.conversations.demo;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.Spanned;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import chattylabs.android.commons.HtmlUtils;
import chattylabs.android.commons.Tag;
import chattylabs.conversations.Conversation;
import chattylabs.conversations.Flow;
import chattylabs.conversations.VoiceMatch;
import chattylabs.conversations.VoiceMessage;
import chattylabs.conversations.VoiceMismatch;
import chattylabs.conversations.VoiceNode;

public class BuildFromJsonActivity extends BaseActivity {
    private static final String TAG = Tag.make(BuildFromJsonActivity.class);

    private ArrayAdapter<Spanned> listViewAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_build_from_json);
        initCommonViews();
        initViews();
    }

    private void initViews() {
        proceedEnabled = true;
        proceed.setOnClickListener(v -> loadConversation());

        ListView conversationListView = findViewById(R.id.conversation);
        listViewAdapter = new ArrayAdapter<>(this, R.layout.item_block,
                R.id.conversation_item_text, new ArrayList<>());
        listViewAdapter.setNotifyOnChange(true);
        conversationListView.setEmptyView(findViewById(R.id.empty_text));
        conversationListView.setAdapter(listViewAdapter);
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
                VoiceMessage message = VoiceMessage.newBuilder("message" + a)
                        .setText(text)
                        .setOnReady(node -> addIntoAdapter(node.text)).build();

                VoiceMatch matches = null;
                VoiceMismatch noMatches = null;
                if (object.has("results")) {
                    JSONArray jsonArray = object.getJSONArray("results");
                    String[] stringArray = new String[jsonArray.length()];
                    for (int i = 0; i < jsonArray.length(); i++) {
                        stringArray[i] = jsonArray.getString(i);
                    }
                    matches = VoiceMatch.newBuilder("matches" + a)
                            .setOnReady(node -> addIntoAdapter(dots))
                            .setExpectedResults(stringArray)
                            .setOnMatched((node, strings) -> {
                                removeLastFromAdapter();
                                if (strings != null) {
                                    addIntoAdapter("<b>You said:</b> " + strings.get(0));
                                    conversation.next();
                                }
                            })
                            .build();
                    noMatches = VoiceMismatch.newBuilder("noMatches" + a)
                            .setOnNotMatched((node, strings) -> {
                                removeLastFromAdapter();
                                addIntoAdapter("<b>You said:</b> " + strings);
                                addIntoAdapter("I did not expect that. Please try again!");
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

            flow.start(firstNode);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void removeLastFromAdapter() {
        runOnUiThread(() -> listViewAdapter.remove(listViewAdapter.getItem(
                listViewAdapter.getCount() - 1
        )));
    }

    private void addIntoAdapter(String text) {
        runOnUiThread(() -> listViewAdapter.add(HtmlUtils.from(text)));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public String loadJSONFromAsset() {
        String json;
        try {
            InputStream is = getResources().openRawResource(R.raw.demo_conversation);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }
}
