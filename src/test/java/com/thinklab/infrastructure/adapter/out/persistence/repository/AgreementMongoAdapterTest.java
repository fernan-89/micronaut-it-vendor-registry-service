package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.DuplicateAgreementException;
import com.thinklab.domain.exception.InvalidAgreementStatusException;
import com.thinklab.domain.model.Agreement;
import com.thinklab.domain.model.Agreement.AgreementStatus;
import com.thinklab.domain.model.Agreement.Kind;
import com.thinklab.infrastructure.adapter.out.persistence.entity.AgreementDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.AgreementDocument.AgreementPersistenceMapper;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AgreementMongoAdapterTest {

    private static final String URI = "mongodb://localhost:27017/vnd_test";

    private final UUID org = UUID.randomUUID();
    private MongoClient client;
    private MongoCollection<AgreementDocument> collection;

    @BeforeEach
    void setUp() {
        client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        collection = mock(MongoCollection.class);
        when(client.getDatabase("vnd_test")).thenReturn(database);
        when(client.getDatabase("thinklab_it_vendor_registry_db")).thenReturn(database);
        when(database.getCollection("agreements", AgreementDocument.class)).thenReturn(collection);
        when(collection.withCodecRegistry(any())).thenReturn(collection);
    }

    private Agreement agreement() {
        Agreement agreement = Agreement.createNew(UUID.randomUUID(), org, UUID.randomUUID(), "CT-1", Kind.WARRANTY, UUID.randomUUID(), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), 30, true, "op");
        agreement.pause("op");
        return agreement;
    }

    private static MongoWriteException writeError(int code) {
        return new MongoWriteException(new WriteError(code, "write error", new BsonDocument()), new ServerAddress());
    }

    private void finds(Agreement found) {
        FindPublisher<AgreementDocument> publisher = mock(FindPublisher.class);
        when(collection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        when(publisher.sort(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<AgreementDocument> subscriber = invocation.getArgument(0);
            Flux.just(AgreementPersistenceMapper.toDocument(found)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
    }

    @Test
    @DisplayName("agreement survives the round trip, with its status and its audit trail")
    void roundTrip() {
        Agreement agreement = agreement();

        AgreementDocument document = AgreementPersistenceMapper.toDocument(agreement);
        Agreement back = AgreementPersistenceMapper.toDomain(document);

        assertEquals(AgreementStatus.PAUSED, back.getStatus());
        assertEquals(2, back.getAuditTrail().size());
        assertEquals(null, back.getAuditTrail().get(0).fromStatus());
        assertEquals(AgreementStatus.ACTIVE, back.getAuditTrail().get(1).fromStatus());
        assertEquals("PAUSED", document.getStatus());
        assertEquals(org, document.getOrganisationId());
        assertEquals(agreement.getId(), document.getId());
        assertEquals(agreement.getCreatedAt(), document.getCreatedAt());
        assertEquals(agreement.getUpdatedAt(), document.getUpdatedAt());
        assertEquals(2, document.getAuditTrail().size());
        assertEquals("2026-01-01", document.getStartsOn());
        assertEquals("2026-12-31", document.getEndsOn());
        assertEquals("WARRANTY", document.getKind());
        assertEquals("CT-1", document.getReference());
        assertEquals(agreement.getVendorId(), document.getVendorId());
        assertEquals(agreement.getAssetId(), document.getAssetId());
        assertEquals(30, document.getNoticeDays());
        assertTrue(document.isAutoRenew());
        assertEquals(LocalDate.parse("2026-12-31"), back.getEndsOn());
        assertEquals(Kind.WARRANTY, back.getKind());
        assertEquals(30, back.getNoticeDays());
        assertTrue(back.isAutoRenew());
        assertEquals(agreement.getAssetId(), back.getAssetId());
    }

    @Test
    @DisplayName("the document setters are plain, and the persistence mapper is a utility class")
    void plainDocument() throws ReflectiveOperationException {
        AgreementDocument document = new AgreementDocument();
        document.setAuditTrail(new ArrayList<>());
        assertTrue(document.getAuditTrail().isEmpty());

        Constructor<AgreementPersistenceMapper> constructor = AgreementPersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    @DisplayName("create and the lookups are scoped to the organisation, the list oldest first")
    void lookups() {
        Agreement agreement = agreement();
        when(collection.insertOne(any(AgreementDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));
        finds(agreement);
        AgreementMongoRepositoryAdapter adapter = new AgreementMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.create(agreement)).expectNext(agreement).verifyComplete();
        StepVerifier.create(adapter.findById(agreement.getId(), org)).assertNext(found -> assertEquals(agreement.getId(), found.getId())).verifyComplete();
        StepVerifier.create(adapter.findAll(org)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).find(filter.capture());
        assertTrue(filter.getAllValues().get(0).toString().contains("organisationId"));
    }

    @Test
    @DisplayName("a duplicate is a 409 of the domain, on create or on a change; any other write error passes through; the URI may omit the database")
    void duplicates() {
        Agreement agreement = agreement();
        when(collection.insertOne(any(AgreementDocument.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(1)));
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(2)));
        AgreementMongoRepositoryAdapter adapter = new AgreementMongoRepositoryAdapter(client, "mongodb://localhost:27017");

        StepVerifier.create(adapter.create(agreement)).expectError(DuplicateAgreementException.class).verify();
        StepVerifier.create(adapter.create(agreement)).expectError(MongoWriteException.class).verify();
        StepVerifier.create(adapter.save(agreement, AgreementStatus.ACTIVE, agreement.getAuditTrail().get(1))).expectError(DuplicateAgreementException.class).verify();
        StepVerifier.create(adapter.save(agreement, AgreementStatus.ACTIVE, agreement.getAuditTrail().get(1))).expectError(MongoWriteException.class).verify();
        assertThrows(NullPointerException.class, () -> new AgreementMongoRepositoryAdapter(client, null));
    }

    @Test
    @DisplayName("save is guarded by the status loaded and pushes the audit entry; a lost race is a conflict")
    void save() {
        Agreement agreement = agreement();
        when(collection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));
        AgreementMongoRepositoryAdapter adapter = new AgreementMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.save(agreement, AgreementStatus.ACTIVE, agreement.getAuditTrail().get(1))).verifyComplete();
        StepVerifier.create(adapter.save(agreement, AgreementStatus.ACTIVE, agreement.getAuditTrail().get(1))).expectError(InvalidAgreementStatusException.class).verify();

        ArgumentCaptor<Bson> guard = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).updateOne(guard.capture(), update.capture());
        assertTrue(guard.getAllValues().get(0).toString().contains("ACTIVE") && guard.getAllValues().get(0).toString().contains("organisationId"));
        String text = update.getAllValues().get(0).toString();
        assertTrue(text.contains("auditTrail"));
        for (String field : new String[]{"kind", "assetId", "startsOn", "endsOn", "noticeDays", "autoRenew"}) {
            assertTrue(text.contains(field), field);
        }
    }
}
