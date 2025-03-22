package com.kludgeworks.eu.legislation.cli;

import java.io.PrintStream;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.shell.command.annotation.CommandScan;

@SpringBootApplication
@CommandScan
class Application {


	/**
	 * Main method to start the application
	 *
	 * @param args Command line arguments
	 */
	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(Application.class);
		app.setBannerMode( Banner.Mode.OFF);
		app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
		app.run(args);
	}

}
