package com.kludgeworks.eu.legislation.cli;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.jline.PromptProvider;

@Configuration
class ShellConfig {

    /**
     * Customize the shell prompt
     *
     * @return A custom prompt provider
     */
    @Bean
    public PromptProvider customPromptProvider() {
        return () -> new AttributedString("eu-legislation:> ",
            AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE));
    }


}
