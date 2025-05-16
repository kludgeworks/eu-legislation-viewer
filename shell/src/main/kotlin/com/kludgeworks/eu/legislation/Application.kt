package com.kludgeworks.eu.legislation

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.shell.command.annotation.CommandScan


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
