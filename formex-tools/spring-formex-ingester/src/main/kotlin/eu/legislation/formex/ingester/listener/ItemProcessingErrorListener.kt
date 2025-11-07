package eu.legislation.formex.ingester.listener

import eu.legislation.formex.ingester.model.SparqlUri
import eu.legislation.formex.ingester.model.XmlMetadata
import org.slf4j.LoggerFactory
import org.springframework.batch.core.listener.ItemProcessListener
import org.springframework.stereotype.Component

@Component
class ItemProcessingErrorListener : ItemProcessListener<SparqlUri, XmlMetadata> {

    companion object {
        private val log = LoggerFactory.getLogger(ItemProcessingErrorListener::class.java)
    }

    override fun onProcessError(item: SparqlUri, e: Exception) {
        log.warn("Error processing item with URI ${item.uri}: ${e.message}", e)
    }
}
