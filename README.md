# Conversational Flow Component - Android

|                   	     | TYPE  	| VERSION 	            | STATUS 	          | COVERAGE                |
|--------------------------- |:-------:	|---------------------- |-------------------- |:-----------------------:|
| `demo`                     | _demo_  	| ![Latest demo][v0]    | ![Build Status][s0] | ![Coverage Status][c0]  |
| `conversational-flow-core` | _core_  	| ![Latest version][v1] | ![Build Status][s1] | ![Coverage Status][c1]  |
| `addon-android-speech`     | _addon_ 	| ![Latest version][v2] | ![Build Status][s2] | ![Coverage Status][c2] |
| `addon-google-speech`      | _addon_	| ![Latest version][v3] | ![Build Status][s3] | ![Coverage Status][c3] |


Part of the [Voice & User Interaction SDK]().

This library combines both native built-in resources and cloud services from various providers into 
a Component capable to run reliably a Speech Synthesizer and a Voice Recognizer.

Besides, it provides an [Interface](#create-a-conversation) based on a 
[Directed Graph](https://en.wikipedia.org/wiki/Directed_graph) 
implementation with [Directed Cycles](https://en.wikipedia.org/wiki/Cycle_(graph_theory)) 
that allows a developer to create connected nodes and build a consistent conversation flow between 
a device and a user with ease. 
<br/>**Consistency** here stands for the needless to code the flow using conditional statements or 
any extra state complexity while ensuring the conversation will behave as expected.

It enables currently the following providers:

- [Built-in Android](https://developers.google.com/voice-actions/interaction/voice-interactions)
    - [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
    - [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Google Cloud](https://cloud.google.com/)
    - [Speech-To-Text](https://cloud.google.com/speech-to-text/)
    - [Text-To-Speech](https://cloud.google.com/text-to-speech/)
    
    like Android, Google Cloud, 
    (Amazon, Wit.ai, Temi, Bing Speech, IBM, ...)

<p align="center"><img src="assets/demo-sample.jpg" alt="demo-sample"/></p>

## Why choosing this library?

Apart from the above mentioned, it also helps you when:
- some devices don't have configured the resources you need to run a conversation in your app
- a developer needs to learn and test quite a lot before even to start coding for voice capabilities
- noise is impacting considerably the communication
- android components force you to create a lot of boilerplate
- some countries don't allow Google Play Services
- etc.

    
## Prerequisites
The SDK works on Android version 5.0 (Lollipop) and above. _(for lower versions [contact us](mailto:hello@chattylabs.com))_

## Dependencies

    repositories {
        maven { url "https://dl.bintray.com/chattylabs/maven" }
    }
     
    dependencies {
        // Required
        implementation 'com.chattylabs.sdk.android:conversational-flow-core:x.y.z'
         
        // You can either use only one or combine addons
        // i.e. the Voice Recognizer of Google with the Synthesizer of Android
        implementation 'com.chattylabs.sdk.android:addon-android-speech:x.y.z'
        implementation 'com.chattylabs.sdk.android:addon-google-speech:x.y.z'
    }

### How to create a Conversation?

You can use the component at any [Context]() level, both in an [Activity]() and a [Service](). 
<br/>You will create a set of `VoiceNode` objects and build a flow.

```java
// Retrieve the Component
component = ConversationalFlowModule.provideComponent(...);
 
// Create a new instance of a Conversation
Conversation conversation = component.create(context);
 
// Create the various nodes you need
VoiceMessage question = ...;
VoiceMatch answers = ...;
 
// Add the nodes into the current instance
conversation.addNode(question);
conversation.addNode(answers);
 
// Connect the nodes each other
ConversationFlow flow = conversation.prepare();
flow.from(question).to(answers);
 
// Start the conversation
conversation.start(question);
```

There are different [Voice Nodes](), check the [wiki page]()

<p align="center"><img src="assets/flow-sample.jpg" alt="flow-sample"/></p>

&nbsp;

[v0]: https://img.shields.io/badge/demo-v0.6.3-blue.svg
[v1]: https://api.bintray.com/packages/chattylabs/maven/conversational-flow-core/images/download.svg?label=Latest%20version
[v2]: https://api.bintray.com/packages/chattylabs/maven/addon-android-speech/images/download.svg?label=Latest%20version
[v3]: https://api.bintray.com/packages/chattylabs/maven/addon-google-speech/images/download.svg?label=Latest%20version

[s0]: https://app.bitrise.io/app/140e33e4fa4ab888/status.svg?token=QxUVT4wZRj6JGkZb4zSVAA&branch=master
[s1]: https://app.bitrise.io/app/0967af538a0efcc5/status.svg?token=95j60AolkTmhbMvDK5zhFw&branch=master
[s2]: https://app.bitrise.io/app/b555517d495ac587/status.svg?token=Fa2M4c_F5YHkhPddufLCNA&branch=master
[s3]: https://app.bitrise.io/app/6a8c16b3b5c964a8/status.svg?token=Q6_u9joriJEzfzcWaLuVjg&branch=master

[c0]: https://coveralls.io/repos/chattylabs/unknown/badge.svg?branch=master&service=github
[c1]: https://coveralls.io/repos/chattylabs/conversational-flow-core/badge.svg?branch=master&service=github
[c2]: https://coveralls.io/repos/chattylabs/addon-android-speech/badge.svg?branch=master&service=github
[c3]: https://coveralls.io/repos/chattylabs/addon-google-speech/badge.svg?branch=master&service=github