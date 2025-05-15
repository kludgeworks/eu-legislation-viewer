package com.kludgeworks.eu.legislation.shell.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.util.Locale

/**
 * Service for processing templates using Thymeleaf
 */
@Service
class TemplateService @Autowired constructor(
    private val templateEngine: TemplateEngine
) {

    /**
     * Process a template with the given model
     *
     * @param templateName The name of the template (without extension)
     * @param model The model containing variables for the template
     * @return The processed template as a string
     */
    fun processTemplate(templateName: String, model: Map<String, Any>): String {
        val context = Context(Locale.getDefault())
        model.forEach { (key, value) -> context.setVariable(key, value) }
        
        return templateEngine.process(templateName, context)
    }
}
