package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.DuplicateVendorException;
import com.thinklab.domain.exception.InvalidVendorStatusException;
import com.thinklab.domain.model.Vendor;
import com.thinklab.domain.model.Vendor.VendorAuditEntry;
import com.thinklab.domain.model.Vendor.VendorStatus;
import com.thinklab.domain.repository.VendorRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.VendorDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.VendorDocument.AuditEntryDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.VendorDocument.VendorPersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * MongoDB Reactive Repository Adapter for the Vendor aggregate, raw reactive-streams driver. Every change is one atomic update guarded by the
 * status loaded (ADR-033) that also appends the audit entry; a name unique per organisation is the atomic backstop of a duplicate.
 */
@Singleton
public class VendorMongoRepositoryAdapter implements VendorRepository {

    private static final Logger log = LoggerFactory.getLogger(VendorMongoRepositoryAdapter.class);
    static final String DEFAULT_DATABASE = "thinklab_it_vendor_registry_db";
    static final String COLLECTION_NAME = "vendors";
    static final int DUPLICATE_KEY = 11000;
    private static final String FIELD_ID = "_id";
    private static final String FIELD_ORGANISATION = "organisationId";
    private static final String FIELD_STATUS = "status";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public VendorMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : DEFAULT_DATABASE;
    }

    private MongoCollection<VendorDocument> getCollection() {
        return mongoClient.getDatabase(database).getCollection(COLLECTION_NAME, VendorDocument.class).withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<Vendor> create(Vendor vendor) {
        log.debug("[PERSISTENCE] Monolithic create for Vendor Aggregate: {}", vendor.getId());
        return Mono.from(getCollection().insertOne(VendorPersistenceMapper.toDocument(vendor)))
                .map(result -> vendor)
                .onErrorMap(MongoWriteException.class, error -> duplicate(error, vendor));
    }

    @Override
    public Mono<Vendor> findById(UUID id, UUID organisationId) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ID, id), Filters.eq(FIELD_ORGANISATION, organisationId))).first())
                .map(VendorPersistenceMapper::toDomain);
    }

    @Override
    public Flux<Vendor> findAll(UUID organisationId) {
        return Flux.from(getCollection().find(Filters.eq(FIELD_ORGANISATION, organisationId)).sort(Sorts.ascending("createdAt"))).map(VendorPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> save(Vendor vendor, VendorStatus expectedStatus, VendorAuditEntry auditEntry) {
        Bson guard = Filters.and(Filters.eq(FIELD_ID, vendor.getId()), Filters.eq(FIELD_ORGANISATION, vendor.getOrganisationId()), Filters.eq(FIELD_STATUS, expectedStatus.name()));
        Bson update = Updates.combine(
                Updates.set("name", vendor.getName()),
                Updates.set("category", vendor.getCategory().name()),
                Updates.set(FIELD_STATUS, vendor.getStatus().name()),
                Updates.set("updatedAt", Instant.now()),
                Updates.push("auditTrail", AuditEntryDocument.fromDomain(auditEntry))
        );
        return Mono.from(getCollection().updateOne(guard, update))
                .flatMap(result -> result.getMatchedCount() == 0
                        ? Mono.error(new InvalidVendorStatusException("Vendor was changed by someone else while this change was being recorded; read it again and retry."))
                        : Mono.<Void>empty())
                .onErrorMap(MongoWriteException.class, error -> duplicate(error, vendor));
    }

    private static Throwable duplicate(MongoWriteException error, Vendor vendor) {
        return error.getError().getCode() == DUPLICATE_KEY
                ? new DuplicateVendorException("A vendor named '" + vendor.getName() + "' already exists in this organisation.") : error;
    }
}
