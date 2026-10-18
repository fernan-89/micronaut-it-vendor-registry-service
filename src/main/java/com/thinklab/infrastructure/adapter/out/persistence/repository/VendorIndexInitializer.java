package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Creates the indexes at startup. The UNIQUE ones are the atomic backstops (ADR-033): a vendor name per organisation, and an agreement reference
 * per vendor and organisation. The list indexes serve the real queries. Fail-open: {@code createIndex} is idempotent, a failure is logged and the
 * application still starts. Turn it off with {@code thinklab.mongo.create-indexes=false}.
 */
@Singleton
@Requires(property = "thinklab.mongo.create-indexes", notEquals = "false")
public class VendorIndexInitializer implements ApplicationEventListener<StartupEvent> {

    static final String VENDOR_NAME_INDEX = "organisationId_1_name_1";
    static final String VENDOR_STATUS_INDEX = "organisationId_1_status_1";
    static final String AGREEMENT_REFERENCE_INDEX = "organisationId_1_vendorId_1_reference_1";
    static final String AGREEMENT_END_INDEX = "organisationId_1_endsOn_1";

    private static final Logger log = LoggerFactory.getLogger(VendorIndexInitializer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final MongoClient mongoClient;
    private final String database;
    private final Duration timeout;

    @Inject
    public VendorIndexInitializer(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this(mongoClient, mongoUri, TIMEOUT);
    }

    VendorIndexInitializer(MongoClient mongoClient, String mongoUri, Duration timeout) {
        this.mongoClient = Objects.requireNonNull(mongoClient, "Infrastructure constraint violated: MongoClient cannot be null.");
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : VendorMongoRepositoryAdapter.DEFAULT_DATABASE;
        this.timeout = timeout;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        Objects.requireNonNull(event, "Application constraint violated: StartupEvent cannot be null.");
        String vendors = VendorMongoRepositoryAdapter.COLLECTION_NAME;
        ensureIndex(vendors, VENDOR_NAME_INDEX, new Document("organisationId", 1).append("name", 1), new IndexOptions().unique(true));
        ensureIndex(vendors, VENDOR_STATUS_INDEX, new Document("organisationId", 1).append("status", 1), new IndexOptions());
        String agreements = AgreementMongoRepositoryAdapter.COLLECTION_NAME;
        ensureIndex(agreements, AGREEMENT_REFERENCE_INDEX, new Document("organisationId", 1).append("vendorId", 1).append("reference", 1), new IndexOptions().unique(true));
        ensureIndex(agreements, AGREEMENT_END_INDEX, new Document("organisationId", 1).append("endsOn", 1), new IndexOptions());
    }

    private void ensureIndex(String collection, String indexName, Document keys, IndexOptions options) {
        try {
            Mono.from(mongoClient.getDatabase(database).getCollection(collection).createIndex(keys, options.name(indexName))).block(timeout);
            log.info("[MONGO_INDEXES] Ensured index [{}] on [{}.{}]", indexName, database, collection);
        } catch (MongoTimeoutException e) {
            log.error("[MONGO_INDEXES] MongoDB unreachable; index [{}] was not created. Reason: {}", indexName, e.getMessage());
        } catch (RuntimeException e) {
            log.error("[MONGO_INDEXES] Could not create index [{}] on [{}.{}]: {}", indexName, database, collection, e.getMessage());
        }
    }
}
