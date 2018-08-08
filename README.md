# Conversational Flow Component - Android

|                   	     | TYPE  	| VERSION 	            | STATUS 	          | COVERAGE               |
|--------------------------- |:-------:	|---------------------- |-------------------- |:----------------------:|
| `conversational-flow-core` | _core_  	| ![Latest version][i1] | ![Build Status][i4] | ![Coverage Status][i7] |
| `addon-android-speech`     | _addon_ 	| ![Latest version][i2] | ![Build Status][i5] | ![Coverage Status][i8] |
| `addon-google-speech`      | _addon_	| ![Latest version][i3] | ![Build Status][i6] | ![Coverage Status][i9] |


Part of the [Voice & User Interaction SDK]().

The library wraps and combines single platform resources and builds 
a _Software Component_ capable of create a communication flow between a device and a user with ease.

Besides, it comes the following providers:

- [Built-in Android](https://developers.google.com/voice-actions/interaction/voice-interactions) (default)
    - [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
    - [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Google Cloud](https://cloud.google.com/)
    - [Speech-To-Text](https://cloud.google.com/speech-to-text/)
    - [Text-To-Speech](https://cloud.google.com/text-to-speech/)
    
    like Android, Google Cloud, 
    (Amazon, Wit.ai, Temi, Bing Speech, IBM, ...)

<p align="center"><img src="assets/demo-sample.jpg" alt="demo-sample"/></p>

## Why choosing this library?

The **Conversational Flow Component** is based on a [Directed Graph](https://en.wikipedia.org/wiki/Directed_graph) 
which also allows [Directed Cycles](https://en.wikipedia.org/wiki/Cycle_(graph_theory)) 
to create connected nodes that build a consistent conversation flow. **Consistent** here means that you won't need
to code the flow using conditional statements or add any extra complexity if you don't want.

This library helps you when:
- some devices don't have configured the resources you need to run a conversation in your app
- a developer needs to learn and test quite a lot before even to start coding for voice capabilities
- noise is impacting considerably the communication
- android components force you to create a lot of boilerplate
- some countries don't allow Google Play Services
- etc.

    
## Prerequisites
The SDK works on Android version 5.0 (Lollipop) and above. _(for lower versions [contact us](mailto:hello@chattylabs.com))_

## Setup
Add the following code to your gradle file.


    repositories {
        maven { url "https://dl.bintray.com/chattylabs/maven" }
    }
     
    dependencies {
        // Required
        implementation 'com.chattylabs.sdk.android:conversational-flow-core:x.y.z'
         
        // You can either use only one or combine addon features
        // i.e. use the Synthesizer of Google but the Recognizer of Android
        implementation 'com.chattylabs.sdk.android:addon-android-speech:x.y.z'
        implementation 'com.chattylabs.sdk.android:addon-google-speech:x.y.z'
    }


## Usage

If you use [Dagger 2](https://google.github.io/dagger/) in your project, 
you can provide the current `ConversationalFlowComponent` instance by applying the `ConversationalFlowModule.class` 
to your dagger component graph.

```java
@dagger.Component( modules = { ConversationalFlowModule.class } )
 
//...
 
@Inject ConversationalFlowComponent component;
```

If you don't user [Dagger 2](https://google.github.io/dagger/), then you can retrieve an instance using:

```java
component = ConversationalFlowModule.provideComponent(new ILoggerImpl());
```

Remember that you have to import at least one of the `addon` dependencies and configure 
which component you will be using.

```java
component.updateConfiguration(builder ->
    builder.setRecognizerServiceType(() -> AndroidSpeechRecognizer.class)
           .setSynthesizerServiceType(() -> AndroidSpeechSynthesizer.class).build());

component.setup(context, status -> {
    if (status.isAvailable()) {
        // start using the functionality
    }
});
```

The configuration builder is based on a `LazyProvider` interface. 
<br/>This is helpful for instance with [SharedPreferences](), where the values can change anytime according 
to user preferences. By providing with the `LazyProvider` once, you don't need to run `updateConfiguration()`
and `setup()` again.

[Learn more]() about the configurations you can set up.

### Create a Conversation

You can use the `ConversationalFlowComponent` at any context level, both in an Activity and a Service. 

To create a conversation between the user and your app, you will create a set of `VoiceNode` objects 
and build a flow.

Retrieve a new instance of `Conversation`.

```java
Conversation conversation = component.create(context);
```

Create the various message and action nodes you expect to use during the conversation.

```java
// We create an initial message node.
VoiceMessage question = VoiceMessage.newBuilder().setText("Do you need help?").build();
 
// We define what we expect from the user.
String[] expected = new String[]{ "Yes", "I think so", "Sure" };
 
// We create a node that handles what the user said
VoiceMatch answers = VoiceMatch.newBuilder().setExpectedResults(expected)
                                 .setOnMatch(results -> conversation::next).build();

// We can create more nodes to check for not matched results and so on...
// We also can automate the creation on a for loop from a Json File. 
// Check the sample demos!
```

Now add these nodes into the current `Conversation` instance.

```java
conversation.addNode(question);
conversation.addNode(answers);
```

Connect the nodes and start the conversation.

```java
Flow flow = conversation.prepare();
flow.from(question).to(answers);
 
// Start the conversation out loud!
conversation.start(question);
```

This is a simple example of the capabilities of the **Conversational Flow Component**. 
<br/>There are several configurations and listeners you can apply to each node, and different node types to use.

For instance, you could play a `VoiceMessage` and then collect a `VoiceCapture` from the user, 
or perhaps create multiple expected `VoiceAction`s and connect them to different `VoiceMessage`s.

<p align="center"><img src="assets/demo-sample.jpg" alt="demo-sample"/></p>

Take a look at the wiki page to [learn more]().

## Who uses this library?
This is a list of Apps using the library:

<a href="https://play.google.com/store/apps/details?id=com.Chatty"><img src="https://lh3.googleusercontent.com/BwP_HPbu2G523jUQitRcfgADe5qKxZclxAbESmM4xaTNFS3ckz5uqkh12OimzqPC=s50-rw" alt="Chatty" title="Chatty"/> &nbsp;&nbsp; 
&nbsp;

[i1]: https://api.bintray.com/packages/chattylabs/maven/voice-interaction/images/download.svg?label=Latest%20version
[i2]: https://api.bintray.com/packages/chattylabs/maven/voice-interaction/images/download.svg?label=Latest%20version
[i3]: https://api.bintray.com/packages/chattylabs/maven/voice-interaction/images/download.svg?label=Latest%20version

[i4]: https://app.bitrise.io/app/ad178a030b96de53/status.svg?token=Om0YDuYQ4vGPjsP0c_EbYQ&branch=master
[i5]: https://app.bitrise.io/app/ad178a030b96de53/status.svg?token=Om0YDuYQ4vGPjsP0c_EbYQ&branch=master
[i6]: https://app.bitrise.io/app/ad178a030b96de53/status.svg?token=Om0YDuYQ4vGPjsP0c_EbYQ&branch=master

[i7]: https://coveralls.io/repos/chattylabs/conversational-flow-core/badge.svg?branch=master&service=github
[i8]: https://coveralls.io/repos/chattylabs/addon-android-speech/badge.svg?branch=master&service=github
[i9]: https://coveralls.io/repos/chattylabs/addon-google-speech/badge.svg?branch=master&service=github