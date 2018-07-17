# Voice Interaction Component

Voice Interaction: [![Latest version](https://api.bintray.com/packages/chattylabs/maven/voice-interaction/images/download.svg?label=Latest%20version)](https://bintray.com/chattylabs/maven/voice-interaction/_latestVersion) 
![Build Status](https://app.bitrise.io/app/ad178a030b96de53/status.svg?token=Om0YDuYQ4vGPjsP0c_EbYQ&branch=master)

Addon Android Speech: [![Latest version](https://api.bintray.com/packages/chattylabs/maven/voice-interaction/images/download.svg?label=Latest%20version)](https://bintray.com/chattylabs/maven/voice-interaction/_latestVersion) 
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
    // Required
    implementation 'com.chattylabs.sdk.android:voice-interaction:x.y.z'
    // You can now use either one or another, or combine both addons
    // It has been split to reduce the size of the library
    implementation 'com.chattylabs.sdk.android:addon-android-speech:x.y.z'
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

Next, create the message and action nodes you expect to use during the conversation.

```java
// We create a first message node. This will be the root node of the conversation.
VoiceMessage question = VoiceMessage.newBuilder().setText("Do you need help?").build();
 
// We define the expected replies from the user.
String[] expected = new String[]{ "Yes", "I think so", "Sure" };
VoiceAction replies = VoiceAction.newBuilder().setExpectedResults(expected)
                                 .setOnMatch(matchedResult -> conversation::next)
                                 .build();
```

Now add the nodes into the conversation.

```java
conversation.addNode(question);
conversation.addNode(replies);
```

Prepare and connect the nodes.

```java
Flow flow = conversation.prepare();
flow.from(question).to(replies);
 
// Start the conversation out loud!
conversation.start(question);
```

This is the resulting node graph:

<p align="center"><img src="assets/demo-sample.jpg" alt="demo-sample"/></p>

Of course this is a very simple example of the capabilities of the Voice Interaction Component, 
which is only limited by the way you can combine its functionality.
<br/>There are several configurations you can apply to the nodes, and different node types to use;
for instance, you could make a question and then collect a speech from the user, or you could
create multiple action nodes that directed to other responses or even other actions.

Take a look at the wiki page to [know more]().

## Demo
After you have cloned this demo project, run the following command on a terminal console. 
This will get and update the project's build system.

```bash
git submodule update --init
```