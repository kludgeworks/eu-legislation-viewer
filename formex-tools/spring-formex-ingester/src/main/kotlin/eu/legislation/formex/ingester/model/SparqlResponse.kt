package eu.legislation.formex.ingester.model

/**
 * W3C SPARQL JSON Results format response structure.
 * 
 * Represents the standard SPARQL 1.1 Query Results JSON Format as defined by W3C.
 * Used for automatic Jackson deserialization of SPARQL endpoint responses.
 * 
 * @see <a href="https://www.w3.org/TR/sparql11-results-json/">SPARQL 1.1 Query Results JSON Format</a>
 */
data class SparqlResponse(val results: SparqlResults) {
    
    /**
     * Container for SPARQL query result bindings.
     */
    data class SparqlResults(val bindings: List<Binding>)
    
    /**
     * Single SPARQL query result binding containing all variable bindings.
     * Each property corresponds to a variable name in the SPARQL SELECT clause.
     */
    data class Binding(
        val item: Term,
        val workId: Term,
        val expressionId: Term,
        val manifestationId: Term,
        val contentStreamId: Term,
        val language: Term,
        val mimeType: Term
    ) {
        /**
         * SPARQL term containing type and value.
         * Type can be "uri", "literal", or "bnode".
         */
        data class Term(val type: String, val value: String)
        
        /**
         * Converts this SPARQL binding to a CellarData domain object.
         * Extracts the value from each SPARQL term.
         */
        fun toCellarData() = CellarData(
            item.value,
            workId.value,
            expressionId.value,
            manifestationId.value,
            contentStreamId.value,
            language.value,
            mimeType.value
        )
    }
}
