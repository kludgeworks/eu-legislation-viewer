package eu.legislation.formex.ingester.processor;

import eu.legislation.formex.ingester.model.SparqlUri;
import jakarta.annotation.PreDestroy;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class UriFilterProcessor implements ItemProcessor<SparqlUri, SparqlUri> {

    private final JdbcTemplate jdbcTemplate;
    private final Set<String> existingUris = new HashSet<>();
    private final List<String> batchBuffer = new ArrayList<>();
    private static final int BATCH_SIZE = 1000;

    public UriFilterProcessor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SparqlUri process(SparqlUri item) {
        batchBuffer.add(item.uri());

        // When buffer reaches batch size, check database
        if (batchBuffer.size() >= BATCH_SIZE) {
            checkBatchInDatabase();
        }

        // Return null if URI already exists (filters it out)
        return existingUris.contains(item.uri()) ? null : item;
    }

    private void checkBatchInDatabase() {
        if (batchBuffer.isEmpty()) return;

        // Create parameterized query with IN clause
        String placeholders = String.join(",", Collections.nCopies(batchBuffer.size(), "?"));
        String sql = "SELECT uri FROM formex_documents WHERE uri IN (" + placeholders + ")";

        List<String> found = jdbcTemplate.queryForList(sql, String.class, batchBuffer.toArray());
        existingUris.addAll(found);

        batchBuffer.clear();
    }

    // Call at end of processing to check remaining items
    @PreDestroy
    public void cleanup() {
        checkBatchInDatabase();
    }
}
