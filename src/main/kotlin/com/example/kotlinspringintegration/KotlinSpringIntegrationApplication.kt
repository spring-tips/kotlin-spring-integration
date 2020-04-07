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


@Configuration
class ChannelsConfiguration {

	@Bean
	fun txt() = MessageChannels.direct().get()

	@Bean
	fun csv() = MessageChannels.direct().get()

	@Bean
	fun errors() = MessageChannels.direct().get()
}

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
