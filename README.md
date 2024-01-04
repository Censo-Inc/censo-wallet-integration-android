# Censo Wallet Integration Android SDK

The Censo Wallet Integration Android SDK allows you to easily empower any of your users who use
the Censo Seed Phrase Manager to save their seed phrases simply and securely.

The SDK will give you a deep link to the Censo app which you convey to the user, such as by
displaying it as a link. This link is only good for a few minutes, but you should take
care to show it only to the user and not store it. When their Censo app opens that deep link,
it will have established a secure communication channel to the SDK.

At this point, the SDK will trigger a callback where you provide the seed phrase (just as the
raw binary entropy) and the SDK will encrypt it and relay it to the user's Censo app, which
will display the seed phrase and allow the user to securely save it.

## Getting Started

### Example Usage

First, load and instantiate the SDK:

```kotlin
val sdk = CensoWalletIntegration()
```

Then, when a user wishes to export their seed phrase to their Censo app, initiate a session:

```kotlin
val session = sdk.initiate(onFinished: {success -> })
```

Then, connect to get a deep link and show it to the user. The callback will be called after the
user's Censo app has established the secure channel.

```kotlin
val deepLink = session.connect(onConnected: {
  // get the binary representation of the user's seed phrase as a hex number
  val seedPhraseEntropy = "..."
  session.phrase(seedPhraseEntropy)
}
```

Once the user has received the seed phrase in their Censo app, the `onFinished` callback
will be called with `true`. If there's an error or timeout along the way, `onFinished`
will instead be called with `false`. In either case, the session will be closed at that
point.
