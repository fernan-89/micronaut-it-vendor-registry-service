package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.DuplicateAgreementException;
import com.thinklab.domain.exception.InvalidAgreementStatusException;
import com.thinklab.domain.model.Agreement;
import com.thinklab.domain.model.Agreement.AgreementAuditEntry;
import com.thinklab.domain.model.Agreement.AgreementStatus;
import com.thinklab.domain.repository.AgreementRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.AgreementDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.AgreementDocument.AuditEntryDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.AgreementDocument.AgreementPersistenceMapper;
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
 * MongoDB Reactive Repository Adapter for the Agreement aggregate, raw reactive-streams driver. Every change is one atomic update guarded by the
 * status loaded (ADR-033) that also appends the audit entry; a reference unique per vendor and organisation is the atomic backstop of a duplicate.
 */
@Singleton
public class AgreementMongoRepositoryAdapter implements AgreementRepository {

    private static final Logger log = LoggerFactory.getLogger(AgreementMongoRepositoryAdapter.class);
    static final String DEFAULT_DATABASE = "thinklab_it_vendor_registry_db";
    static final String COLLECTION_NAME = "agreements";
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

    public AgreementMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : DEFAULT_DATABASE;
    }

    private MongoCollection<AgreementDocument> getCollection() {
        return mongoClient.getDatabase(database).getCollection(COLLECTION_NAME, AgreementDocument.class).withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<Agreement> create(Agreement agreement) {
        log.debug("[PERSISTENCE] Monolithic create for Agreement Aggregate: {}", agreement.getId());
        return Mono.from(getCollection().insertOne(AgreementPersistenceMapper.toDocument(agreement)))
                .map(result -> agreement)
                .onErrorMap(MongoWriteException.class, error -> duplicate(error, agreement));
    }

    @Override
    public Mono<Agreement> findById(UUID id, UUID organisationId) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ID, id), Filters.eq(FIELD_ORGANISATION, organisationId))).first())
                .map(AgreementPersistenceMapper::toDomain);
    }

    @Override
    public Flux<Agreement> findAll(UUID organisationId) {
        return Flux.from(getCollection().find(Filters.eq(FIELD_ORGANISATION, organisationId)).sort(Sorts.ascending("createdAt"))).map(AgreementPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> save(Agreement agreement, AgreementStatus expectedStatus, AgreementAuditEntry auditEntry) {
        Bson guard = Filters.and(Filters.eq(FIELD_ID, agreement.getId()), Filters.eq(FIELD_ORGANISATION, agreement.getOrganisationId()), Filters.eq(FIELD_STATUS, expectedStatus.name()));
        Bson update = Updates.combine(
                Updates.set("kind", agreement.getKind().name()),
                Updates.set("assetId", agreement.getAssetId()),
                Updates.set("startsOn", agreement.getStartsOn().toString()),
                Updates.set("endsOn", agreement.getEndsOn().toString()),
                Updates.set("noticeDays", agreement.getNoticeDays()),
                Updates.set("autoRenew", agreement.isAutoRenew()),
                Updates.set(FIELD_STATUS, agreement.getStatus().name()),
                Updates.set("updatedAt", Instant.now()),
                Updates.push("auditTrail", AuditEntryDocument.fromDomain(auditEntry))
        );
        return Mono.from(getCollection().updateOne(guard, update))
                .flatMap(result -> result.getMatchedCount() == 0
                        ? Mono.error(new InvalidAgreementStatusException("Agreement was changed by someone else while this change was being recorded; read it again and retry."))
                        : Mono.<Void>empty())
                .onErrorMap(MongoWriteException.class, error -> duplicate(error, agreement));
    }

    private static Throwable duplicate(MongoWriteException error, Agreement agreement) {
        return error.getError().getCode() == DUPLICATE_KEY
                ? new DuplicateAgreementException("The reference '" + agreement.getReference() + "' is already used for this vendor.") : error;
    }
}
