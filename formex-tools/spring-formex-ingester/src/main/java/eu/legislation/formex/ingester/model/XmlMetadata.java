package eu.legislation.formex.ingester.model;

import java.util.UUID;

/**
 * Record representing XML metadata extracted from downloaded files
 */
public record XmlMetadata(
    UUID id,
    String uri,
    long fileSize,
    UUID workId,
    String exprId,
    String manId,
    String csId,
    String tempFilePath  // Path to downloaded file
) {
}
