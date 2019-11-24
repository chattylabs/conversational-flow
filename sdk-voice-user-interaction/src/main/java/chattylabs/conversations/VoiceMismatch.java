package chattylabs.conversations;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.List;

public class VoiceMismatch implements VoiceAction, HasId {
    public final String id;
    public final @StringRes int resId;
    public int retries;
    public final String lowSoundErrorMessage;
    public final String listeningErrorMessage;
    public final String unexpectedErrorMessage;
    public final ComponentConsumer<VoiceMismatch, List<String>> onNotMatched;

    public static final class Builder {
        private String id;
        private @StringRes int resId;
        private int retries;
        private String lowSoundErrorMessage;
        private String listeningErrorMessage;
        private String unexpectedErrorMessage;
        private ComponentConsumer<VoiceMismatch, List<String>> onNotMatched;

        public Builder(@NonNull String id) {
            this.id = id;
        }

        public Builder(@NonNull @StringRes int resId) {
            this.resId = resId;
        }

        public Builder setRetries(int retries) {
            this.retries = retries;
            return this;
        }

        public Builder setLowSoundErrorMessage(String lowSoundErrorMessage) {
            this.lowSoundErrorMessage = lowSoundErrorMessage;
            return this;
        }

        public Builder setListeningErrorMessage(String listeningErrorMessage) {
            this.listeningErrorMessage = listeningErrorMessage;
            return this;
        }

        public Builder setUnexpectedErrorMessage(String unexpectedErrorMessage) {
            this.unexpectedErrorMessage = unexpectedErrorMessage;
            return this;
        }

        public Builder setOnNotMatched(ComponentConsumer<VoiceMismatch, List<String>> onNotMatched) {
            this.onNotMatched = onNotMatched;
            return this;
        }

        public VoiceMismatch build() {
            if (resId != 0 && id == null) {
                throw new IllegalArgumentException("ResourceId provided, use build(Context)");
            }
            if (id.trim().length() == 0) {
                throw new NullPointerException("Property \"id\" cannot be empty");
            }
            if (onNotMatched == null) {
                throw new NullPointerException("Property \"onNotMatched\" is required");
            }
            return new VoiceMismatch(this);
        }

        public VoiceMismatch build(Context context) {
            if (resId == 0) {
                throw new IllegalArgumentException("Property \"resId\" is required");
            }
            this.id = context.getString(this.resId);
            return build();
        }
    }

    private VoiceMismatch(Builder builder) {
        id = builder.id;
        resId = builder.resId;
        retries = builder.retries;
        lowSoundErrorMessage = builder.lowSoundErrorMessage;
        listeningErrorMessage = builder.listeningErrorMessage;
        unexpectedErrorMessage = builder.unexpectedErrorMessage;
        onNotMatched = builder.onNotMatched;
    }

    public static Builder newBuilder(@NonNull String id) {
        return new Builder(id);
    }

    public static Builder newBuilder(@NonNull @StringRes int resId) {
        return new Builder(resId);
    }

    @NonNull @Override
    public String getId() {
        return id;
    }
}
