package com.kludgeworks.eu.legislation.shell.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import picocli.CommandLine

@Configuration
class PicocliConfiguration {

    @Bean
    fun commandLineFactory(applicationContext: org.springframework.context.ApplicationContext): CommandLine.IFactory {
        return object : CommandLine.IFactory {
            override fun <K : Any> create(cls: Class<K>): K {
                return applicationContext.getBean(cls)
            }
        }
    }
}
