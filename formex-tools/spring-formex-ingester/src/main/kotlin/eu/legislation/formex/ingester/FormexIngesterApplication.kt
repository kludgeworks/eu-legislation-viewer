package eu.legislation.formex.ingester

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FormexIngesterApplication

fun main(args: Array<String>) {
	runApplication<FormexIngesterApplication>(*args)
}
