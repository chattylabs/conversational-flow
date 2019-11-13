package chattylabs.conversations;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef({Conversation.FLAG_ENABLE_ERROR_MESSAGE_ON_LOW_SOUND})
@Retention(RetentionPolicy.SOURCE)
public @interface Flag {}
