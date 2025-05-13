package com.kludgeworks.eu.legislation

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.shell.command.annotation.CommandScan
import java.lang.management.ManagementFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


@SpringBootApplication
@CommandScan
class Application {
    companion object {
        /**
         * Main method to start the application
         *
         * @param args Command line arguments
         */
        @JvmStatic
        fun main(args: Array<String>) {
            val app = SpringApplication(Application::class.java)
            app.setBannerMode(Banner.Mode.OFF)
            app.webApplicationType = WebApplicationType.NONE
            app.run(*args)
        }
    }
}
