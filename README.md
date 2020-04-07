

Hi, Spring fans! In this installment, we're going to take a look at the new Kotlin DSL for Spring Integration. I've covered both Spring Integration and Kotlin in other videos before.   I am _pretty_ sure I've also used Spring Integration from within a Kotlin-based Spring application, but this is the first time I've been able to cover a Kotlin DSL specifically for Spring Integration. 

Spring Integration has been around for a long time  - 13 years at least - and it serves a timeless use case: the integration of disparate systems and services. It's patterned after the seminal tome by Gregor Hohpe and Bobby Woolf, [_Enterprise Integration Patterns_](https://www.amazon.com/Enterprise-Integration-Patterns-Designing-Deploying/dp/0321200683). It's a fantastic tome, and I couldn't more enthusiastically recommend it as it serves, after a fashion, as the documentation required for understanding Spring Integration. Spring Integration codifies the patterns from the book; API elements are named for the relevant patterns in the book. 

Integration is necessarily high-level work. It's about different plumbing systems in terms of their inputs and outputs. You don't want to spend too long at the lowest level working at the level of object graphs to make this work. It's much easier to decouple these systems and services in terms of the inputs and outputs they support. Spring Integration gives you a way to do this.

Over the years, the DSL for Spring Integration has changed. We started the project with XML based DSL, later introduced a Java configuration component model, and later still introduced the Java DSL. There was even a brief flirtation with a Scala DSL. And now we have the Kotlin DSL. The Kotlin DSL builds upon the foundations laid down years ago in the Spring Integration Java DSL. It extends the DSL to make it more Kotlin-native. 

In this application, we're going to build an app to monitor a file system. I show thee examples because they don't require you the audience to install anything on or local machines except a file system which, presumably, you already have. Spring Integration provides a rich toolbox of integrations. You can talk to file systems (remote and local), databases, message queues, and a myriad of other protocols and integrations. 

Let's build a new application. Go to the [Spring Initializr](http://start.spring.io) and make sure to choose `Kotlin` for the choice of language. Also, make sure to use Spring Boot 2.3.x or later. 

Then in the dependency combo box, choose `Spring Integration`. We'll also need to add a Spring Integration-specific dependency. While this dependency isn't discoverable on the Spring Initializr, it _is_ managed by Spring Boot so we can easily add it to our build manually. 

Click `Generate` and then open the project up in your favorite IDE. 

Go to the `pom.xml` file and add the following dependency:


```xml
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-file</artifactId>
    <version>${spring-integration.version}</version>
</dependency>
```

Now, let's turn to the application itself. The implementation will be fairly straightforward. Let's look at the pseudocode.

* When a file arrives in the `input` directory (`$HOME/Desktop/in`), a Spring Integration inbound adapter will notice its arrival and then forward it to a...
* ...filter which will determine if the entry is a file (as opposed to a directory) and then send it to ....
* ...router which will determine, given the extension of the file, whether to send it to a handler for `.csv` files, `.xml` files, or everything else. 
* The `.csv` and `.txt` handlers will end up moving the files to appropriate directories. 

We're not going to specify a handler for the eventuality that the file is of some other type, but the possibilities are endless here. You could forward the errant file to a handler that sends an email, or writes something toa database, or publishes a message to an Apache Kafka broker, etc. The sky's the limit here! 

We'll have _three_ flows. now I know that you just read those pseudocode bullets and probably emerged thinking that there would only be one flow, but I've decoupled the handler for `.csv` files from the handler for `.txt` files. The decoupling is useful because it means that other flows may originate files that then are routed to the `csv` or `txt` flows as downstream handlers. Decoupling supports a good clean architecture. It allows me to repurpose my flows by keeping them small and singly focused. It's the same advice as applies to write functions. 

We decouple flows through he judicious use of `MessageChannels`. Channels are like name conduits - pipes - through which messages flow. 

Here's our basic Spring Boot application, with just the imports, our `main()` function, and not much else.


```kotlin
package com.example.kotlinspringintegration

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.file.dsl.Files
import java.io.File

@SpringBootApplication
class KotlinSpringIntegrationApplication

fun main(args: Array<String>) {
    runApplication<KotlinSpringIntegrationApplication>(*args)
}
```

From here, let's define the message channels.


```kotlin
@Configuration
class ChannelsConfiguration {

    @Bean
    fun txt() = MessageChannels.direct().get()

    @Bean
    fun csv() = MessageChannels.direct().get()

    @Bean
    fun errors() = MessageChannels.direct().get()
}
```


We can inject the configuration class and then invoke the methods to dereference the individual channel. 

Let's look at the main integration flow. The Spring Integration Java DSL has always been convenient, but it's even more accessible from the Kotli language. In this configuration class, I define an `IntegrationFlow` using the Kotlin `integrationFlow` factory function. It in turn takes a lambda that acts as the context off of which I hang the various steps int the integration flow. I construct the `IntegrationFlow` by pointing it to where new messages that initiate the flow will arrive. In this case, the messages arrive after consuming the messages from an inbound file adapter that monitors a directory (`$HOME/Desktop/in`) every 500 milliseconds. The configured poller determines the schedule on which new messages are polled. Here too, the Spring Integration Kotlin DSL makes things easier. This is much cleaner (to my eyes at least) than the original Java configuration DSL. 

As soon as the files arrive, they're wrapped in a `Message<File>` and forwarded to the `filter<File>` extension function. note that here I don't need to specify `File.class` - Kotlin has pseudo reified generics - the type of the argument is captured in the generic invocation of the function itself. No need fr a type token. The `filter` function expects a lambda that inspects the current message (available through the implicit parameter `it`) and confirms its a file (and not a directory or something else). If it is, the flow continues to a router.

The router then inspects the message and determines which outbound `MessageChannel` the resulting message should be forwarded. This router uses Kotlin's nifty `when` expression - kind of like a supercharged `switch` statement in Java. (NB: there is a switch expression in Java that is very promising, but then how many of you are using that right now?). The `when` expression produces a value. In Kotlin, the last expression of the function is the return value (you rarely need to specify `return`).  In this case, the last expression of the function is the result of the `when` expression: a `MessageChannel`. 


```kotlin 
@Configuration
class FileConfiguration(private val channels: ChannelsConfiguration) {

    private val input = File("${System.getenv("HOME")}/Desktop/in")
    private val output = File("${System.getenv("HOME")}/Desktop/out")
    private val csv = File(output, "csv")
    private val txt = File(output, "txt")

    @Bean
    fun filesFlow() = integrationFlow(
            Files.inboundAdapter(this.input).autoCreateDirectory(true),
            { poller { it.fixedDelay(500).maxMessagesPerPoll(1) } }
    ) {

        filter<File> { it.isFile }
        route<File> {
            when (it.extension.toLowerCase()) {
                "csv" -> channels.csv()
                "txt" -> channels.txt()
                else -> channels.errors()
            }
        }
    }
}

```


At this point, this flow finishes. There's nowhere to go for a message. We've run out of track for our train! We need to lay down two more flows. One is supporting files that end in `csv` and another supporting file ending in `txt`. Let's look at that.

In the same configuration class, add two more integration flow bean definitions. 

```kotlin

    @Bean
    fun csvFlow() = integrationFlow(channels.csv()) {
        handle(Files.outboundAdapter(csv).autoCreateDirectory(true))
    }

    @Bean
    fun txtFlow() = integrationFlow(channels.txt()) {
        handle(Files.outboundAdapter(txt).autoCreateDirectory(true))
    }
````

This last example should look reasonably similar to what you've seen thus far except that the thing that starts the flow is not an inbound adapter, its any message that comes off of a message channel. The first flow, `csvFlow`, kicks off when messages arrive from the `csv` message channel. The same is true fo the second flow, `txtFlow`. Both flows terminate rather abruptly, only doing one thing. They forward the message to an outbound adapter that, in turn, writes the file to some other directory. The outbound adapter is the mirror image of the inbound adapter; it takes a message from the Spring Integration flow and sends it to some sink in the real world (a file system). The inbound adapter takes values from the real world and turns them into Spring Integration messages. 

At this point, we've got a working processing flow. I have sort of hand-waived away the question of what happens tot he message if it isn't a `txt` or a `csv` file and ends up in the `errors` channel? As I alluded earlier, the sky's the limit here.

