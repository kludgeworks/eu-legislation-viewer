package com.kludgeworks.transformation.autoconfigure

import com.kludgeworks.transformation.FormexTransformationService
import com.kludgeworks.transformation.FormexTransformer
import com.kludgeworks.transformation.FormexValidator
import com.kludgeworks.transformation.FormexProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.io.ResourceLoader

/**
 * Auto-configuration for the Formex Transformation library.
 *
 * This configuration is activated when the FormexTransformationService is on the classpath
 * and the property 'formex.enabled' is set to 'true' (which it is by default).
 */
@AutoConfiguration
@ConditionalOnClass(FormexTransformationService::class)
@EnableConfigurationProperties(FormexProperties::class)
@ConditionalOnProperty(prefix = "formex", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class FormexAutoConfiguration {

    @Bean
    fun formexTransformer(properties: FormexProperties, resourceLoader: ResourceLoader): FormexTransformer {
        return FormexTransformer(properties, resourceLoader)
    }

    @Bean
    fun formexValidator(properties: FormexProperties, resourceLoader: ResourceLoader): FormexValidator {
        return FormexValidator(properties, resourceLoader)
    }

    @Bean
    fun formexTransformationService(
        transformer: FormexTransformer,
        validator: FormexValidator
    ): FormexTransformationService {
        return FormexTransformationService(transformer, validator)
    }
}
