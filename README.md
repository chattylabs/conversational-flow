# Conversational Flow Component - Android

|                   	     | TYPE  	| VERSION 	            | STATUS 	          | COVERAGE  |
|--------------------------- |:-------:	|---------------------- |-------------------- |:--------: |
| `conversational-flow-core` | _core_  	| ![Latest version][i1] | ![Build Status][i4] | 0%        |
| `addon-android-speech`     | _addon_ 	| ![Latest version][i2] | ![Build Status][i5] | 0%        |
| `addon-google-speech`      | _addon_	| ![Latest version][i3] | ![Build Status][i6] | 0%        |


Part of the [Voice User Interaction SDK]().

The library wraps and combines single platform resources and builds 
a _Software Component_ capable of create a communication flow between a devices and a user with ease.

Besides, it also lets you choose between the following providers:
like Android, Google Cloud, 
(Amazon, Wit.ai, Temi, Bing Speech, IBM, ...)

- [Built-in Android](https://developers.google.com/voice-actions/interaction/voice-interactions) (default)
    - [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
    - [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Google Cloud](https://cloud.google.com/)
    - [Speech-To-Text](https://cloud.google.com/speech-to-text/)
    - [Text-To-Speech](https://cloud.google.com/text-to-speech/)

<p align="center"><img src="assets/demo-sample.jpg" alt="demo-sample"/></p>

## Why choosing this library?

The **Conversational Flow Component** is based on [Directed Graph](https://en.wikipedia.org/wiki/Directed_graph) 
which also allows [Directed Cycles](https://en.wikipedia.org/wiki/Cycle_(graph_theory)) 
to create connected nodes that build a consistent flow.

Some devices don't have configured the resources you need to run a conversation in your app, 
a developer needs to learn and test quite a lot before even to start coding for voice capabilities, noise is impacting 
considerably the communication, android components force you to create a lot of boilerplate, some countries don't 
allow Google Play Services, etc.

This library helps you on all these aspects and more.
    
    
## Prerequisites
The SDK works on Android version 5.0 (Lollipop) and above. _(for lower versions [contact us](mailto:hello@chattylabs.com))_

## Setup
Add the following code to your gradle file.

```groovy
repositories {
    maven { url "https://dl.bintray.com/chattylabs/maven" }
}
 
dependencies {
    // Required
    implementation 'com.chattylabs.sdk.android:conversational-flow-core:x.y.z'
    // You can either use only one or combine addons
    implementation 'com.chattylabs.sdk.android:addon-android-speech:x.y.z'
    implementation 'com.chattylabs.sdk.android:addon-google-speech:x.y.z'
}
```

## Usage

If you use [Dagger 2](https://google.github.io/dagger/) in your project, 
you can provide the current `ConversationalFlowComponent` instance if you add the `ConversationalFlowModule.class` 
as a module item into your dagger component graph.

```java
@dagger.Component( modules = { ConversationalFlowModule.class } )
 
//...
 
@Inject ConversationalFlowComponent conversationalFlowComponent;
```

If you don't user `Dagger 2`, then you can retrieve an instance using:

```java
conversationalFlowComponent = ConversationalFlowModule.provideComponent(new ILoggerImpl());
```

By default, the library sets up the built-in Android addon, 
so you must at least import the `addon-android-speech` dependency. 
Afterwards, you won't need to configure anything else, although you could still setup a new configuration 
or update some changes on the current one at anytime.

```java
// Optional
conversationalFlowComponent.updateConfiguration(
        builder -> builder.setBluetoothScoRequired(() -> preferences.connectToBluetoothSco()).build());
```

The config builder is based on a `LazyProvider` interface. 
This is helpful for example with [SharedPreferences]() where the values can change anytime according 
to user preferences.

[Learn more]() about the configurations you can set up.

### Create a Conversation

You can use the `ConversationalFlowComponent` at any context level, both in an Activity and a Service. 

To create a conversation between the user and your app, you will create a set of `VoiceNode` objects and build a flow with them.

Retrieve a new instance of `Conversation`.

```java
Conversation conversation = conversationalFlowComponent.create(context);
```

Create the message and action nodes you expect to use during the conversation.

```java
// We create the initial message node.
VoiceMessage question = VoiceMessage.newBuilder().setText("Do you need help?").build();
 
// We define the expected replies from the user.
String[] expected = new String[]{ "Yes", "I think so", "Sure" };
VoiceMatch answers = VoiceMatch.newBuilder().setExpectedResults(expected)
                                 .setOnMatch(results -> conversation::next)
                                 .build();
```

Now add the nodes into the current instance.

```java
conversation.addNode(question);
conversation.addNode(answers);
```

Connect the nodes and start the conversation.

```java
Flow flow = conversation.prepare();
flow.from(question).to(replies);
//...
 
// Start the conversation out loud!
conversation.start(question);
```

This is a simple example of the capabilities of the **Conversational Flow Component**. 
<br/>There are several configurations and listeners you can apply to each node, and different node types to use.

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
This is a list of Apps using the library:

<a href="https://play.google.com/store/apps/details?id=com.Chatty"><img src="https://lh3.googleusercontent.com/BwP_HPbu2G523jUQitRcfgADe5qKxZclxAbESmM4xaTNFS3ckz5uqkh12OimzqPC=s50-rw" alt="Chatty" title="Chatty"/> &nbsp;&nbsp; 
&nbsp;

[i1]: https://api.bintray.com/packages/chattylabs/maven/voice-interaction/images/download.svg?label=Latest%20version
[i2]: https://api.bintray.com/packages/chattylabs/maven/voice-interaction/images/download.svg?label=Latest%20version
[i3]: https://api.bintray.com/packages/chattylabs/maven/voice-interaction/images/download.svg?label=Latest%20version

[i4]: https://app.bitrise.io/app/ad178a030b96de53/status.svg?token=Om0YDuYQ4vGPjsP0c_EbYQ&branch=master
[i5]: https://app.bitrise.io/app/ad178a030b96de53/status.svg?token=Om0YDuYQ4vGPjsP0c_EbYQ&branch=master
[i6]: https://app.bitrise.io/app/ad178a030b96de53/status.svg?token=Om0YDuYQ4vGPjsP0c_EbYQ&branch=master