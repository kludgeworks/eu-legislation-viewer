package com.kludgeworks.eu.legislation.config

import com.kludgeworks.eu.legislation.util.DateUtils
import kotlinx.datetime.LocalDate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.thymeleaf.context.IExpressionContext
import org.thymeleaf.dialect.AbstractDialect
import org.thymeleaf.dialect.IExpressionObjectDialect
import org.thymeleaf.expression.IExpressionObjectFactory

/**
 * Extension class for Thymeleaf to provide custom formatters and utilities
 */
@Configuration
class ThymeleafExtensions {
    
    @Bean
    fun customFormattersDialect(): CustomFormattersDialect {
        return CustomFormattersDialect()
    }
    
    /**
     * Dialect for custom formatters
     */
    class CustomFormattersDialect : AbstractDialect("CustomFormatters"), IExpressionObjectDialect {
        private val expressionObjectFactory = CustomFormatterFactory()
        
        override fun getExpressionObjectFactory(): IExpressionObjectFactory {
            return expressionObjectFactory
        }
    }
    
    /**
     * Factory for custom formatters
     */
    class CustomFormatterFactory : IExpressionObjectFactory {
        private val objects = setOf("dateUtils")
        
        override fun getAllExpressionObjectNames(): Set<String> {
            return objects
        }
        
        override fun buildObject(context: IExpressionContext, expressionObjectName: String): Any? {
            return when (expressionObjectName) {
                "dateUtils" -> DateFormatUtils()
                else -> null
            }
        }
        
        override fun isCacheable(expressionObjectName: String): Boolean {
            return true
        }
    }
    
    /**
     * Date formatting utilities for templates
     */
    class DateFormatUtils {
        /**
         * Format a date with a pattern
         */
        fun format(date: LocalDate, pattern: String): String {
            return DateUtils.format(date, pattern)
        }
    }
}
