# Voice Interaction Component

Voice Interaction Core: [![Latest version](https://api.bintray.com/packages/chattylabs/maven/voice-interaction/images/download.svg?label=Latest%20version)](https://bintray.com/chattylabs/maven/voice-interaction/_latestVersion) 
![Build Status](https://app.bitrise.io/app/ad178a030b96de53/status.svg?token=Om0YDuYQ4vGPjsP0c_EbYQ&branch=master)

Addon Android Defaults: [![Latest version](https://api.bintray.com/packages/chattylabs/maven/voice-interaction/images/download.svg?label=Latest%20version)](https://bintray.com/chattylabs/maven/voice-interaction/_latestVersion) 
![Build Status](https://app.bitrise.io/app/ad178a030b96de53/status.svg?token=Om0YDuYQ4vGPjsP0c_EbYQ&branch=master)

Addon Google Speech: [![Latest version](https://api.bintray.com/packages/chattylabs/maven/voice-interaction/images/download.svg?label=Latest%20version)](https://bintray.com/chattylabs/maven/voice-interaction/_latestVersion) 
![Build Status](https://app.bitrise.io/app/ad178a030b96de53/status.svg?token=Om0YDuYQ4vGPjsP0c_EbYQ&branch=master)

This library is a component part of the [ChattyLabs SDK]().

The library wraps and combines single Android resources like _TextToSpeech_ or _SpeechRecognizer_ and builds 
a **Conversational Component** capable of create a communication flow with ease.

Besides, it also lets you choose between different providers like Google Cloud or Amazon.

<p align="center"><img src="assets/demo-sample.jpg" alt="demo-sample"/></p>

## Why choosing this SDK?

Some devices don't have configured the resources you need to run a conversation in your app, 
a developer needs to learn and test quite a lot before to start coding for voice capabilities, noise is impacting 
considerably the communication, android components force you to create a lot of boilerplate, some countries don't 
allow Google Play Services, etc.

This library helps you on all these aspects and more.

You can choose from the following providers:

- [Built-in Android Components](https://developers.google.com/voice-actions/interaction/voice-interactions) (default)
    - [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
    - [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Google Cloud Components](https://cloud.google.com/)
    - [Speech-To-Text](https://cloud.google.com/speech-to-text/)
    
    
## Prerequisites
- The SDK works on Android version 5.0 (Lollipop) and above. _(for lower versions [contact us](mailto:hello@chattylabs.com))_

## Setup
Add the following code to your gradle file.

```groovy
repositories {
    maven { url "https://dl.bintray.com/chattylabs/maven" }
}
 
dependencies {
    implementation 'com.chattylabs.sdk.android:voice-interaction-core:x.y.z'
    implementation 'com.chattylabs.sdk.android:addon-android-defaults:x.y.z'
    
    // Optional
    implementation 'com.chattylabs.sdk.android:addon-google-speech:x.y.z'
}
```

## Usage

If you make use of [Dagger 2](https://google.github.io/dagger/) in your project, 
you can provide straight away a `VoiceInteractionComponent` instance with the `@Inject` annotation.
Just add the `VoiceInteractionModule.class` as a module item into your Dagger Component graph.

```java
@Inject VoiceInteractionComponent voiceInteractionComponent;
```

In any case, you can still retrieve the current instance using:

```java
voiceInteractionComponent = VoiceInteractionModule.provideVoiceInteractionComponent(new ILoggerImpl());
```

By default, the library uses the built-in Android Components, so you don't need to configure anything else, 
but you can still setup a new configuration or update some changes on the current one at anytime. 
<br/>We recommend you to do this in the `Application#onCreate()` method.

```java
// Optional
voiceInteractionComponent.updateVoiceConfig(
        builder -> builder.setBluetoothScoRequired(() -> preferences.routeToBluetoothSco()).build());
```

The config builder is based on a `LazyProvider` interface, which means that the values provided can change
later on. This is helpful for example with `SharedPreferences` when the values can change according to user preferences.
<br/>[Learn more]() about the configurations you can set up.

#### Create a Conversation

The `Conversation` Component is based on a [Directed Graph](https://en.wikipedia.org/wiki/Directed_graph) 
which also allows [Directed Cycles](https://en.wikipedia.org/wiki/Cycle_(graph_theory)) 
to create connected nodes that makes a flow much consistent.
<br/>You can use the `VoiceInteractionComponent` at any context, either in an Activity, a Service or a BroadcastReceiver. 

To create a conversation between the user and your app, you will create a set of `VoiceNode` objects and build a flow with them.

First, retrieve a new instance of a Conversation.

```java
Conversation conversation = voiceInteractionComponent.createConversation(context);
```
Create the different message and action nodes you will use throughout the conversation.
```java
// We create a first message node. This will be the root node of the conversation.
VoiceMessage message = VoiceMessage.newBuilder().setText("Do you need help?").build();
 
// We define the expected positives replies from the user.
String[] expectedPositives = new String[]{ "Yes", "I think so" };
VoiceAction actionOnPositives = VoiceAction.newBuilder().setExpectedResults(expectedPositives)
                                .setOnMatch(matchedResult -> {
                                    conversation.next();
                                }).build();
 
// We define the expected negatives replies from the user.
String[] expectedNegatives = new String[]{ "No", "I don't think so" };
VoiceAction actionOnNegatives = VoiceAction.newBuilder().setExpectedResults(expectedNegatives)
                                .setOnMatch(matchedResult -> {
                                    conversation.next();
                                }).build();
 
// We define the responses according to the reply received from the user.
VoiceMessage messageOnPositives = VoiceMessage.newBuilder()
                    .setText("Great!, I'll show you some more information...").build();
VoiceMessage messageOnNegatives = VoiceMessage.newBuilder()
                    .setText("Ok, you can ask me anytime!").build();
```
Now add all the nodes as part of the conversation instance.
```java
conversation.addNode(message);
conversation.addNode(actionOnPositives);
conversation.addNode(actionOnNegatives);
conversation.addNode(messageOnPositives);
conversation.addNode(messageOnNegatives);
```
Prepare and connect the nodes into the conversation flow.
```java
Flow flow = conversation.prepare();
flow.from(message).to(actionOnPositives, actionOnPositives);
flow.from(actionOnPositives).to(messageOnPositives);
flow.from(actionOnNegatives).to(messageOnNegatives);
 
// Start the conversation out loud!
conversation.start(message);
```

This is the resulting node graph:

<p align="center"><img src="assets/demo-sample.jpg" alt="demo-sample"/></p>



## Demo
After you have cloned this demo project, run the following command on a terminal console. 
This will get and update the project's build system.

```bash
git submodule update --init
```