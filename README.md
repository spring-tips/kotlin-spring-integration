# The Kotlin Spring Integration DSL

Hi, Spring fans! In this installment we're going to take a look at the new Kotlin DSL for Spring Integration. I've covered both Spring Integration and Kotlin in other videos before, and I am _pretty_ sure I've also used Spring Integration from within a Kotlin-based Spring application, but this is the first time I've been able to cover a Kotlin DSL specifically for Spring Integration. 

Spring Integration has been around for a long time  - 13 years at least - and it serves a timeless use case: the integration of disparate systems and services. It's patterned after the seminal tome by Greghor Hohpe and Boobby Woolf, [_Enterprise Integration Patterns_](https://www.amazon.com/Enterprise-Integration-Patterns-Designing-Deploying/dp/0321200683). It's an amazing tome, and I couldn't more enthusiastically recommend it as it serves, after a fashion, as the documentation required for udnerstanding Sprign Integration. Spring Intgrration codifies the patterns in the book itself; API elements are named for the relevant patterns in the book. 

integration is necessarily high level work. It's about plumbing different systems in terms of their inputs and outputs. You don't want to spend to long at the lowest level working at the level of object graphs to make this work. It's much easier to decouple these systems and services in terms of the inputs and outputs they support. Spring Integration gives you a way to do this.

Over the years, the DSL for SPring Integation has changed. We started the proejct iwth XML based DSLs.Then we inroduced a Java configuration compatible component model. Then we introduced the Java DSL. And there wa even a brief flirtation with a Scala DSL. And now we have the Kotlin DSL. the Kotlin DSL builds upon the foundations laid down years ago in the Spring Integration Java DSL. It extends the DSL to make it more Kotlin-native. 

In ths application we're going to do as we always do and build an appkcation ot monitor a file system. I show thee examples becaue they don't require you the audience to install anything on or local macines except a file system which, presumanly, you already have. Spring Integratino provides a rich toolbox of integrations. You can talk to file systems (remote and local), databses, message queues, and a myriad of other protocols and integrations. 

Let's build a new application. Go tot he [Spring Initializr](http://start.spring.io) and make sure to choose `Kotlin` for the choice of language. Also, make sure to use Spring Boot 2.3.x or later. 

Then in the depdency combo box choose `Spring Integration`. We'll also need to add a Spring Integration-sepcific dependency. While this dependnecy isnt discoverable ont he Spring iNitialir, it _is_ managed by Spring Boot so we can easily add it to our build manually. 

Click `Generate` and then open the project up in your favorite IDE. 

Go to the `pom.xml` file and add the following dependnecy:


```xml
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-file</artifactId>
    <version>${spring-integration.version}</version>
</dependency>
```

Now, let's turn to the application itself. The appliatin wil be fairly straighforward. Let's look at the pseudocode.

* when a file arrives in the `input` directory (`$HOME/Desktop/in`), a Spring Integration inbound adapter will notice its arrival and then forward it to a...
* ...filter which will determine if the entry is a file (as opposed to a directory) and then send it to ....
* ...router which will determine, given the extension of the file, whetehr to send it send it to a handler for `.csv` files, `.xml` files, or everything else. 
* The `.csv` and `.txt` handlers will end up moving the files to appropriate diretctories. 


We're not going to specify a hander for the eventuality that the file is of some other type, but the possibilities are endless here. You coudl forward the errant file to a handler that sends an email, or writes something toa database, or publishes a message to an Apache Kafka broker, etc. The sky's the limit here! 

We'll have _three_ flows. now I know that you just read those pseudocode bullets and probably emerged thinking that there would only be one flow, but I've decoupled the handler for `.csv` files from the handler for `.txt` files. The decoupling is useful becaue it means that other flows may originate files that then et routed ot the `csv` or `txt` flows as downstream handlers. Decoupling in this way is good clean archicture. It allows me to repurpose my flows by keeping them small and singly focused. It's basically the same advice as applies to writing functions. 

We decouple flows throught he judicisious use fo `MessageChannels`. Channels are like name conduits - pipes - through which messages flow. 

Here's our basic Spring Boot application, with just the imports, our `main()` function and not much else.


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


We can inject the configuration class and then invoke the methods to dereference the indivudual chanensl.


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

	@Bean
	fun csvFlow() = integrationFlow(channels.csv()) {
		handle(Files.outboundAdapter(csv).autoCreateDirectory(true))
	}

	@Bean
	fun txtFlow() = integrationFlow(channels.txt()) {
		handle(Files.outboundAdapter(txt).autoCreateDirectory(true))
	}
}

```