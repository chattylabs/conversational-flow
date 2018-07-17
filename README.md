# Voice Interaction Component

|                   	| TYPE  	| VERSION 	                | STATUS 	          |
|-------------------	|-------	|----------------------	    |-------------------- |
| Voice Interaction 	| Core  	| ![Latest version][i1]	    | ![Build Status][i4] |
| Android Speech    	| Addon 	| ![Latest version][i2]     | ![Build Status][i5] |
| Google Speech     	| Addon 	| ![Latest version][i3]     | ![Build Status][i6] |


This library is a Component part of the [ChattyLabs SDK]().

The library wraps and combines single Android resources like _TextToSpeech_ or _SpeechRecognizer_ and builds 
a **Conversational Component** capable of create a communication flow with ease.

Besides, it also lets you choose between different providers like Google Cloud or Amazon.

<p align="center"><img src="assets/demo-sample.jpg" alt="demo-sample"/></p>

## Why choosing this SDK?

Some devices don't have configured the resources you need to run a conversation in your app, 
a developer needs to learn and test quite a lot before even to start coding for voice capabilities, noise is impacting 
considerably the communication, android components force you to create a lot of boilerplate, some countries don't 
allow Google Play Services, etc.

This library helps you on all these aspects and more.

Also, you can choose from the following providers:

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
you can provide the current `VoiceInteractionComponent` instance if you add the `VoiceInteractionModule.class` 
as a module item into your Component graph.

```java
@dagger.Component( modules = { VoiceInteractionModule.class } )
 
//...
 
@Inject VoiceInteractionComponent voiceInteractionComponent;
```

If you don't user `Dagger`, then you can retrieve the current instance using:

```java
voiceInteractionComponent = VoiceInteractionModule.provideVoiceInteractionComponent(new ILoggerImpl());
```

By default, the library uses the built-in Android Components if you import the `addon-android-speech` dependency, 
so you don't need to configure anything else, but you can still setup a new configuration or update some changes 
on the current one at anytime.

```java
// Optional
voiceInteractionComponent.updateVoiceConfig(
        builder -> builder.setBluetoothScoRequired(() -> preferences.connectToBluetoothSco()).build());
```

The config builder is based on a `LazyProvider` interface. 
This is helpful for example with `SharedPreferences` when the values can change anytime according to user preferences.
<br/>[Learn more]() about the configurations you can set up.

#### Create a Conversation

The **Conversation Component** is based on a [Directed Graph](https://en.wikipedia.org/wiki/Directed_graph) 
which also allows [Directed Cycles](https://en.wikipedia.org/wiki/Cycle_(graph_theory)) 
to create connected nodes that build a consistent flow.
<br/>You can use the `VoiceInteractionComponent` at any context, either in an Activity, a Service or a BroadcastReceiver. 

To create a conversation between the user and your app, you will create a set of `VoiceNode` objects and build a flow with them.

Retrieve a new instance of `Conversation`.

```java
Conversation conversation = voiceInteractionComponent.createConversation(context);
```

Create message and action nodes you expect to use during the conversation.

```java
// We create the initial message node.
VoiceMessage question = VoiceMessage.newBuilder().setText("Do you need help?").build();
 
// We define the expected replies from the user.
String[] expected = new String[]{ "Yes", "I think so", "Sure" };
VoiceAction replies = VoiceAction.newBuilder().setExpectedResults(expected)
                                 .setOnMatch(matchedResult -> conversation::next)
                                 .build();
```

Now add the nodes into the current instance.

```java
conversation.addNode(question);
conversation.addNode(replies);
```

Connect the nodes and start the conversation.

```java
Flow flow = conversation.prepare();
flow.from(question).to(replies);
//...
 
// Start the conversation out loud!
conversation.start(question);
```

This is a very simple example of the capabilities of the **Voice Interaction Component**. 
<br/>There are several configurations you can apply to the nodes, and different node types to use.

For instance, you could make a `VoiceMessage` and then collect a `VoiceCapture` from the user, 
or perhaps create multiple `VoiceAction` and connect them to different `VoiceMessage` responses 
and even to other `VoiceAction` nodes.

<p align="center"><img src="assets/demo-sample.jpg" alt="demo-sample"/></p>

Take a look at the wiki page to [learn more]().

## Running the Demo
After you have cloned this project, run the following command on a terminal console. 
<br/>This will retrieve and update the project's build system.

```bash
git submodule update --init
```

## Who use this library?
This is a list of Apps or Companies using this particular library in their project:

<a href="https://play.google.com/store/apps/details?id=com.Chatty"><img src="https://lh3.googleusercontent.com/BwP_HPbu2G523jUQitRcfgADe5qKxZclxAbESmM4xaTNFS3ckz5uqkh12OimzqPC=s50-rw" alt="Chatty" title="Chatty"/> &nbsp;&nbsp; 
&nbsp;

[i1]: https://api.bintray.com/packages/chattylabs/maven/voice-interaction/images/download.svg?label=Latest%20version
[i2]: https://api.bintray.com/packages/chattylabs/maven/voice-interaction/images/download.svg?label=Latest%20version
[i3]: https://api.bintray.com/packages/chattylabs/maven/voice-interaction/images/download.svg?label=Latest%20version

[i4]: https://app.bitrise.io/app/ad178a030b96de53/status.svg?token=Om0YDuYQ4vGPjsP0c_EbYQ&branch=master
[i5]: https://app.bitrise.io/app/ad178a030b96de53/status.svg?token=Om0YDuYQ4vGPjsP0c_EbYQ&branch=master
[i6]: https://app.bitrise.io/app/ad178a030b96de53/status.svg?token=Om0YDuYQ4vGPjsP0c_EbYQ&branch=master