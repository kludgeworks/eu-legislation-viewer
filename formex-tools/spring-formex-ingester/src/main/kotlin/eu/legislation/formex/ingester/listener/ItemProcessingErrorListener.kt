package eu.legislation.formex.ingester.listener

import eu.legislation.formex.ingester.model.CellarData
import eu.legislation.formex.ingester.model.XmlMetadata
import eu.legislation.formex.ingester.util.logger
import org.springframework.batch.core.listener.ItemProcessListener
import org.springframework.stereotype.Component

@Component
class ItemProcessingErrorListener : ItemProcessListener<CellarData, XmlMetadata> {

    private val logger = logger()

    override fun onProcessError(item: CellarData, e: Exception) {
        logger.warn(e) { "Error processing item with URI ${item.item}: ${e.message}" }
    }
}
