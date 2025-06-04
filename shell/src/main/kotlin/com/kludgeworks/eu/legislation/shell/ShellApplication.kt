package com.kludgeworks.eu.legislation.shell

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.IFactory
import kotlin.system.exitProcess
import java.util.concurrent.Callable

/**
 * Root command that serves as a parent for all other commands
 */
@Command(
    name = "eu-legislation-cli",
    mixinStandardHelpOptions = true,
    version = ["1.0"],
    description = ["EU Legislation CLI tool"],
    subcommands = []
)
class RootCommand : Callable<Int> {
    override fun call(): Int = 0
}

@SpringBootApplication
@ComponentScan(basePackages = ["com.kludgeworks.eu.legislation"])
class ShellApplication(
    private val factory: IFactory,
    private val workCommand: WorkCommand,
    private val downloadCommand: DownloadCommand,
    private val downloadAllCommand: DownloadAllCommand
) : CommandLineRunner, ExitCodeGenerator {

    private var exitCode = 0

    override fun run(vararg args: String) {
        // Create a parent command to hold all subcommands
        val parentCommand = CommandLine(RootCommand(), factory)

        // Add all commands as peers
        parentCommand.addSubcommand("work", workCommand)
        parentCommand.addSubcommand("download", downloadCommand)
        parentCommand.addSubcommand("download-all", downloadAllCommand)

        exitCode = parentCommand.execute(*args)
    }

    override fun getExitCode(): Int {
        return exitCode
    }
}

fun main(args: Array<String>) {
    exitProcess(SpringApplication.exit(runApplication<ShellApplication>(*args)))
}
