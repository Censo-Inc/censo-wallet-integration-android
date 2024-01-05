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
import co.censo.walletintegration.CensoWalletIntegration

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

## Publishing to maven

First, sign up on https://issues.sonatype.org/browse/OSSRH using your JIRA username.

Next, upload your PGP key to keyserver.ubuntu.com

Then, add to e.g. `~/.gradle/gradle.properties`:

```
signing.keyId=YourKeyId
signing.password=YourPublicKeyPassword
signing.secretKeyRingFile=PathToYourKeyRingFile

ossrhUsername=your-jira-id
ossrhPassword=your-jira-password
```

See https://docs.gradle.org/current/userguide/signing_plugin.html#sec:signatory_credentials for
details about how to get the `signing.` data. The ossrh credentials are those you created in the
first step.

Now, you should be able to publish using `./gradlew publish` (don't forget to bump the version
number in `censowalletintegration/build.gradle.kts`)

If this succeeds, there will be a staging build available on https://s01.oss.sonatype.org/#stagingRepositories
(log in using your ossrh credentials).

Select the build and hit the "Close" button. If the close completes successfully, then select the build again
and hit the "Release" button. The release should automatically be deployed.
