package com.kludgeworks.eu.legislation.cellarclient

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlFactory
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.kludgeworks.eu.legislation.cellarclient.model.* // Import all model classes
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CellarApiClient {

    private val logger = LoggerFactory.getLogger(CellarApiClient::class.java)

    private val httpClient = HttpClient(CIO) {
        // Configure HttpClient if needed
    }

    private val xmlMapper: XmlMapper = run {
        val xmlFactory = XmlFactory()
        xmlFactory.xmlInputFactory.setProperty(javax.xml.stream.XMLInputFactory.IS_NAMESPACE_AWARE, true)
        XmlMapper(xmlFactory).apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
        }
    }

    private val baseUrl = "http://publications.europa.eu/resource/cellar"

    suspend fun fetchNoticeXml(uuid: String, language: String? = "eng"): String {
        val url = "$baseUrl/$uuid"
        logger.info("Fetching notice from URL: {} with language: {}", url, language)
        return try {
            val response: HttpResponse = httpClient.get(url) {
                if (language != null) { parameter("language", language) }
            }
            if (response.status.isSuccess()) {
                response.bodyAsText().also { logger.debug("Successfully fetched XML for UUID: {}, Language: {}. Length: {}", uuid, language, it.length) }
            } else {
                val errorBody = try { response.bodyAsText() } catch (e: Exception) { "Could not read error body." }
                logger.error("Failed to fetch notice for UUID: {}. Status: {}. Body: {}", uuid, response.status, errorBody)
                throw CellarApiException("Failed to fetch notice. Status: ${response.status}. Details: $errorBody")
            }
        } catch (e: Exception) {
            logger.error("Exception occurred while fetching notice for UUID: {}: {}", uuid, e.message, e)
            throw CellarApiException("Exception during API call to Cellar: ${e.message}", e)
        }
    }

    suspend fun getNotice(uuid: String, language: String? = "eng"): Notice {
        val xmlString = fetchNoticeXml(uuid, language)
        logger.debug("Attempting to parse XML for UUID: {} into RdfRoot", uuid)
        val rdfRoot = try {
            xmlMapper.readValue(xmlString, RdfRoot::class.java)
        } catch (e: Exception) {
            logger.error("XML parsing into RdfRoot failed for UUID: {}. Error: {}", uuid, e.message, e)
            logger.error("Problematic XML string for UUID {} was:\n{}", uuid, xmlString)
            throw CellarApiException("Failed to parse XML response from Cellar into RdfRoot: ${e.message}", e)
        }
        logger.info("Successfully parsed XML into RdfRoot for UUID: {}. Found {} descriptions.", uuid, rdfRoot.descriptions.size)
        val mainResourceCellarUri = "$baseUrl/$uuid"
        return mapRdfToNotice(rdfRoot, mainResourceCellarUri)
    }

    private fun mapRdfToNotice(rdfRoot: RdfRoot, mainResourceCellarUri: String): Notice {
        logger.info("Starting mapping from RdfRoot to domain Notice for main resource: {}", mainResourceCellarUri)
        val descriptionsByAboutUri = rdfRoot.descriptions.filter { it.about != null }.associateBy { it.about!! }
        
        val mainWorkDescription = findMainWorkDescription(descriptionsByAboutUri, mainResourceCellarUri)
        if (mainWorkDescription == null) {
            logger.error("Could not find the main Work RdfDescription for URI: {} or its equivalents.", mainResourceCellarUri)
            throw CellarApiException("Could not identify the main Work resource from the RDF response for $mainResourceCellarUri.")
        }
        logger.info("Identified main Work description with URI: {}", mainWorkDescription.about)

        val work = extractWork(mainWorkDescription, descriptionsByAboutUri)
        val expressions = work.expressionUris.mapNotNull { exprUri ->
            descriptionsByAboutUri[exprUri]?.let { extractExpression(it, descriptionsByAboutUri) }
        }
        val manifestations = expressions.flatMap { expr ->
            expr.manifestationUris.mapNotNull { manifUri ->
                descriptionsByAboutUri[manifUri]?.let { extractManifestation(it, descriptionsByAboutUri) }
            }
        }
        return Notice(work = work, expressions = expressions, manifestations = manifestations)
    }

    private fun findMainWorkDescription(
        descriptionsByAboutUri: Map<String, RdfDescription>,
        initialQueryUri: String
    ): RdfDescription? {
        logger.debug("findMainWorkDescription: Searching for main work, initialQueryUri: {}", initialQueryUri)

        fun checkDescTypes(desc: RdfDescription, uriForLog: String): Boolean {
            val typesList = desc.properties["rdf:type"] ?: desc.properties["type"] // Check with and without prefix
            logger.debug("findMainWorkDescription: Checking types for URI '{}'. Property 'rdf:type' (or 'type') is: {} (type: {})", uriForLog, typesList, typesList?.javaClass?.simpleName)

            fun checkItemIsWorkType(item: Any?): Boolean {
                if (item is Map<*, *>) {
                    val resource = item["rdf:resource"] as? String ?: item["resource"] as? String // Check with and without prefix for attribute
                    return resource == "http://publications.europa.eu/ontology/cdm#work" || resource == "http://publications.europa.eu/ontology/cdm#resource_legal"
                }
                return false
            }
            return typesList?.any { checkItemIsWorkType(it) } ?: false
        }

        descriptionsByAboutUri[initialQueryUri]?.let { desc ->
            logger.debug("findMainWorkDescription: Attempt 1 - Checking direct URI: {}", desc.about)
            if (checkDescTypes(desc, desc.about ?: initialQueryUri)) {
                logger.info("findMainWorkDescription: Found main work by direct URI: {}", desc.about)
                return desc
            }
        }

        val initialDescription = descriptionsByAboutUri[initialQueryUri]
        logger.debug("findMainWorkDescription: Attempt 2 - initialDescription for {} is {}", initialQueryUri, if (initialDescription == null) "null" else "found")
        
        val sameAsLinksList = initialDescription?.properties?.get("owl:sameAs") ?: initialDescription?.properties?.get("sameAs")
        logger.debug("findMainWorkDescription: Attempt 2 - Raw 'owl:sameAs' (or 'sameAs') for {} is: {} (type: {})", initialQueryUri, sameAsLinksList, sameAsLinksList?.javaClass?.simpleName)

        val equivalentUrisFromSameAs = sameAsLinksList?.mapNotNull { item ->
            if (item is Map<*, *>) { item["rdf:resource"] as? String ?: item["resource"] as? String } else null
        } ?: emptyList()
        logger.debug("findMainWorkDescription: Attempt 2 - Extracted equivalent URIs: {}", equivalentUrisFromSameAs)
        
        equivalentUrisFromSameAs.forEach { equivalentUri ->
            logger.debug("findMainWorkDescription: Attempt 2 - Checking equivalent URI from owl:sameAs: {}", equivalentUri)
            if (descriptionsByAboutUri.containsKey(equivalentUri) && (equivalentUri.contains("celex", ignoreCase = true) || equivalentUri.contains("eli", ignoreCase = true))) {
                descriptionsByAboutUri[equivalentUri]?.let { equivalentDesc ->
                    logger.debug("findMainWorkDescription: Attempt 2 - Found description for equivalent URI: {}", equivalentDesc.about)
                    if (checkDescTypes(equivalentDesc, equivalentDesc.about ?: equivalentUri)) {
                        logger.info("findMainWorkDescription: Found main work by equivalent URI (celex/eli): {}", equivalentDesc.about)
                        return equivalentDesc
                    }
                }
            }
        }

        if (initialQueryUri.contains("celex", ignoreCase = true) || initialQueryUri.contains("eli", ignoreCase = true)) {
             descriptionsByAboutUri[initialQueryUri]?.let { desc ->
                logger.debug("findMainWorkDescription: Attempt 3 - Checking initialQueryUri itself as celex/eli: {}", desc.about)
                if (checkDescTypes(desc, desc.about ?: initialQueryUri)) {
                    logger.info("findMainWorkDescription: Found main work by initialQueryUri (was celex/eli): {}", desc.about)
                    return desc
                }
            }
        }

        logger.debug("findMainWorkDescription: Attempt 4 - Fallback heuristic search")
        return descriptionsByAboutUri.values.find { d ->
            val isWorkType = checkDescTypes(d, d.about ?: "unknown_fallback_uri")
            val hasIdentifiers = d.properties.keys.any { 
                val localName = it.substringAfterLast(':')
                localName == "identifier" || localName == "id_celex" || localName == "eli" ||
                localName == "resource_legal_id_celex" || localName == "resource_legal_eli" ||
                localName == "work_id_document"
            }
            if (isWorkType && hasIdentifiers) {
                 logger.info("findMainWorkDescription: Found main work by fallback heuristic: {}", d.about)
            }
            isWorkType && hasIdentifiers
        }
    }

    private fun getPropertyList(props: Map<String, MutableList<Any>>, key: String, altKey: String? = null): List<Any>? {
        return props[key] ?: altKey?.let { props[it] }
    }
    
    private fun getFirstTextFromList(propertyList: List<Any>?): String? {
        return propertyList?.firstNotNullOfOrNull { item ->
            when (item) {
                is Map<*, *> -> item[""] as? String // For <tag attr="val">text</tag> or <tag>text</tag>
                is String -> item
                else -> null
            }
        }
    }
    
    private fun getFirstResourceFromList(propertyList: List<Any>?): String? {
         return propertyList?.firstNotNullOfOrNull { item ->
            (item as? Map<*,*>)?.let { it["rdf:resource"] as? String ?: it["resource"] as? String }
        }
    }

    private fun getAllResourcesFromList(propertyList: List<Any>?): List<String> {
        return propertyList?.mapNotNull { item ->
            (item as? Map<*,*>)?.let { it["rdf:resource"] as? String ?: it["resource"] as? String }
        } ?: emptyList()
    }
    
    private fun getAllTextsFromList(propertyList: List<Any>?): List<String> {
         return propertyList?.mapNotNull { item ->
            when (item) {
                is Map<*, *> -> item[""] as? String
                is String -> item
                else -> null
            }
        } ?: emptyList()
    }

    private fun extractWork(desc: RdfDescription, allDescs: Map<String, RdfDescription>): Work {
        val props = desc.properties

        val lastModDateStr = getFirstTextFromList(getPropertyList(props, "j.3:lastModificationDate", "lastModificationDate"))
        val lastModDateTime = lastModDateStr?.let { parseXmlDateTime(it) }

        val dateCreationLegacyPropList = getPropertyList(props, "j.0:DATE_CREATION_LEGACY", "DATE_CREATION_LEGACY") 
            ?: getPropertyList(props, "j.0:work_date_creation_legacy", "work_date_creation_legacy") 
            ?: getPropertyList(props, "j.0:date_creation_legacy", "date_creation_legacy")
        val legacyDate = extractLegacyDate(dateCreationLegacyPropList?.firstOrNull())

        val extractedIdentifiers = mutableListOf<String>()
        extractedIdentifiers.addAll(getAllTextsFromList(getPropertyList(props, "j.0:identifier", "identifier")))
        extractedIdentifiers.addAll(getAllTextsFromList(getPropertyList(props, "j.0:id_celex", "id_celex")))
        extractedIdentifiers.addAll(getAllTextsFromList(getPropertyList(props, "j.0:resource_legal_id_celex", "resource_legal_id_celex")))
        
        desc.about?.let { aboutUri ->
            if (aboutUri.startsWith("http://publications.europa.eu/resource/celex/")) {
                val celexFromAbout = aboutUri.substringAfter("http://publications.europa.eu/resource/celex/")
                val cleanedCelex = celexFromAbout.substringBefore("R(").substringBefore("%28").trimEnd('/')
                if (cleanedCelex.isNotBlank()) {
                    extractedIdentifiers.add(cleanedCelex)
                }
            }
        }

        return Work(
            selfUri = desc.about,
            identifiers = extractedIdentifiers.distinct().filter { it.isNotBlank() },
            eli = getFirstTextFromList(getPropertyList(props, "j.0:eli", "eli")) ?: getFirstTextFromList(getPropertyList(props, "j.0:resource_legal_eli", "resource_legal_eli")),
            lastModificationDate = lastModDateTime,
            dateCreationLegacy = legacyDate,
            idSector = getFirstTextFromList(getPropertyList(props, "j.0:id_sector", "id_sector")) ?: getFirstTextFromList(getPropertyList(props, "j.0:resource_legal_id_sector", "resource_legal_id_sector")),
            resourceLegalNumberNaturalCelex = getFirstTextFromList(getPropertyList(props, "j.0:resource_legal_number_natural_celex", "resource_legal_number_natural_celex")) ?: getFirstTextFromList(getPropertyList(props, "j.0:number_natural_celex", "number_natural_celex")),
            resourceLegalEli = getFirstTextFromList(getPropertyList(props, "j.0:resource_legal_eli", "resource_legal_eli")), // Often same as eli
            resourceLegalRepertoire = getFirstTextFromList(getPropertyList(props, "j.0:resource_legal_repertoire", "resource_legal_repertoire")) ?: getFirstTextFromList(getPropertyList(props, "j.0:repertoire", "repertoire")),
            version = getFirstTextFromList(getPropertyList(props, "j.0:version", "version")) ?: getFirstTextFromList(getPropertyList(props, "j.0:work_version", "work_version")),
            resourceLegalCommentInternal = getFirstTextFromList(getPropertyList(props, "j.0:resource_legal_comment_internal", "resource_legal_comment_internal")) ?: getFirstTextFromList(getPropertyList(props, "j.0:comment_internal", "comment_internal")),
            expressionUris = getAllResourcesFromList(getPropertyList(props, "j.0:work_has_expression", "work_has_expression")),
            typeUris = getAllResourcesFromList(getPropertyList(props, "rdf:type", "type"))
        )
    }

    private fun extractExpression(desc: RdfDescription, allDescs: Map<String, RdfDescription>): Expression {
        val props = desc.properties
        fun getFirstText(key: String, altKey: String? = null): String? = getFirstTextFromList(getPropertyList(props, key, altKey))
        fun getAllResources(key: String, altKey: String? = null): List<String> = getAllResourcesFromList(getPropertyList(props, key, altKey))
        
        val lastModDateStr = getFirstText("j.0:LASTMODIFICATIONDATE", "LASTMODIFICATIONDATE")
        val lastModDateTime = lastModDateStr?.let { parseXmlDateTime(it) }

        return Expression(
            selfUri = desc.about,
            title = getFirstText("j.0:EXPRESSION_TITLE", "EXPRESSION_TITLE") ?: getFirstText("j.0:TITLE", "TITLE"),
            lang = getFirstText("j.0:LANG", "LANG"),
            lastModificationDate = lastModDateTime,
            manifestationUris = getAllResources("j.0:expression_manifested_by_manifestation", "expression_manifested_by_manifestation")
        )
    }

    private fun extractManifestation(desc: RdfDescription, allDescs: Map<String, RdfDescription>): Manifestation {
        val props = desc.properties
        fun getFirstText(key: String, altKey: String? = null): String? = getFirstTextFromList(getPropertyList(props, key, altKey))
        fun getAllResources(key: String, altKey: String? = null): List<String> = getAllResourcesFromList(getPropertyList(props, key, altKey))

        val lastModDateStr = getFirstText("j.0:LASTMODIFICATIONDATE", "LASTMODIFICATIONDATE")
        val lastModDateTime = lastModDateStr?.let { parseXmlDateTime(it) }

        return Manifestation(
            selfUri = desc.about,
            type = getFirstText("j.0:MANIFESTATION_TYPE", "MANIFESTATION_TYPE"),
            lastModificationDate = lastModDateTime,
            itemUris = getAllResources("j.0:manifestation_has_item", "manifestation_has_item")
        )
    }
    
    private fun parseXmlDateTime(dateTimeStr: String): LocalDateTime? {
        return try {
            if (dateTimeStr.endsWith("Z")) {
                LocalDateTime.parse(dateTimeStr.substring(0, dateTimeStr.length - 1))
            } else if (dateTimeStr.contains("+") || (dateTimeStr.count { it == '-' } > 2 && dateTimeStr.contains("T") && dateTimeStr.substringAfterLast("-", "").contains(":"))) {
                java.time.OffsetDateTime.parse(dateTimeStr).toLocalDateTime()
            }
            else {
                LocalDateTime.parse(dateTimeStr)
            }
        } catch (e: java.time.format.DateTimeParseException) {
            logger.warn("Could not parse XML dateTime: {}. Error: {}", dateTimeStr, e.message)
            null
        }
    }

    private fun parseXmlDate(dateStr: String): LocalDate? {
        return try {
            LocalDate.parse(dateStr)
        } catch (e: java.time.format.DateTimeParseException) {
            logger.warn("Could not parse XML date: {}. Error: {}", dateStr, e.message)
            null
        }
    }

    private fun extractLegacyDate(value: Any?): LegacyDate? {
        if (value is Map<*, *>) {
            val dateStr = getFirstTextFromList(value["VALUE"] as? List<Any>) ?: (value[""] as? String) // VALUE might be a list or direct text
            val yearStr = getFirstTextFromList(value["YEAR"] as? List<Any>)
            val monthStr = getFirstTextFromList(value["MONTH"] as? List<Any>)
            val dayStr = getFirstTextFromList(value["DAY"] as? List<Any>)

            dateStr?.let { ds ->
                parseXmlDate(ds)?.let { parsedDate ->
                    return LegacyDate(
                        value = parsedDate,
                        year = yearStr?.toIntOrNull() ?: parsedDate.year,
                        month = monthStr?.toIntOrNull() ?: parsedDate.monthValue,
                        day = dayStr?.toIntOrNull() ?: parsedDate.dayOfMonth
                    )
                }
            }
        } else if (value is String) { 
            parseXmlDate(value)?.let { parsedDate ->
                return LegacyDate(parsedDate, parsedDate.year, parsedDate.monthValue, parsedDate.dayOfMonth)
            }
        }
        logger.warn("Could not extract LegacyDate from value: {}", value)
        return null
    }
}

class CellarApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)