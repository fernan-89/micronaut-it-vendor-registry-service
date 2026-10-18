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
import com.thinklab.domain.exception.DuplicateVendorException;
import com.thinklab.domain.exception.InvalidVendorStatusException;
import com.thinklab.domain.model.Vendor;
import com.thinklab.domain.model.Vendor.VendorStatus;
import com.thinklab.domain.model.Vendor.Category;
import com.thinklab.infrastructure.adapter.out.persistence.entity.VendorDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.VendorDocument.VendorPersistenceMapper;
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
class VendorMongoAdapterTest {

    private static final String URI = "mongodb://localhost:27017/vnd_test";

    private final UUID org = UUID.randomUUID();
    private MongoClient client;
    private MongoCollection<VendorDocument> collection;

    @BeforeEach
    void setUp() {
        client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        collection = mock(MongoCollection.class);
        when(client.getDatabase("vnd_test")).thenReturn(database);
        when(client.getDatabase("thinklab_it_vendor_registry_db")).thenReturn(database);
        when(database.getCollection("vendors", VendorDocument.class)).thenReturn(collection);
        when(collection.withCodecRegistry(any())).thenReturn(collection);
    }

    private Vendor vendor() {
        Vendor vendor = Vendor.createNew(UUID.randomUUID(), org, "Dell", Category.HARDWARE, "op");
        vendor.deactivate("op");
        return vendor;
    }

    private static MongoWriteException writeError(int code) {
        return new MongoWriteException(new WriteError(code, "write error", new BsonDocument()), new ServerAddress());
    }

    private void finds(Vendor found) {
        FindPublisher<VendorDocument> publisher = mock(FindPublisher.class);
        when(collection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        when(publisher.sort(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<VendorDocument> subscriber = invocation.getArgument(0);
            Flux.just(VendorPersistenceMapper.toDocument(found)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
    }

    @Test
    @DisplayName("vendor survives the round trip, with its status and its audit trail")
    void roundTrip() {
        Vendor vendor = vendor();

        VendorDocument document = VendorPersistenceMapper.toDocument(vendor);
        Vendor back = VendorPersistenceMapper.toDomain(document);

        assertEquals(VendorStatus.INACTIVE, back.getStatus());
        assertEquals(2, back.getAuditTrail().size());
        assertEquals(null, back.getAuditTrail().get(0).fromStatus());
        assertEquals(VendorStatus.ACTIVE, back.getAuditTrail().get(1).fromStatus());
        assertEquals("INACTIVE", document.getStatus());
        assertEquals(org, document.getOrganisationId());
        assertEquals(vendor.getId(), document.getId());
        assertEquals(vendor.getCreatedAt(), document.getCreatedAt());
        assertEquals(vendor.getUpdatedAt(), document.getUpdatedAt());
        assertEquals(2, document.getAuditTrail().size());
        assertEquals("Dell", document.getName());
        assertEquals("HARDWARE", document.getCategory());
        assertEquals(Category.HARDWARE, back.getCategory());
        assertEquals("Dell", back.getName());
    }

    @Test
    @DisplayName("the document setters are plain, and the persistence mapper is a utility class")
    void plainDocument() throws ReflectiveOperationException {
        VendorDocument document = new VendorDocument();
        document.setAuditTrail(new ArrayList<>());
        assertTrue(document.getAuditTrail().isEmpty());

        Constructor<VendorPersistenceMapper> constructor = VendorPersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    @DisplayName("create and the lookups are scoped to the organisation, the list oldest first")
    void lookups() {
        Vendor vendor = vendor();
        when(collection.insertOne(any(VendorDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));
        finds(vendor);
        VendorMongoRepositoryAdapter adapter = new VendorMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.create(vendor)).expectNext(vendor).verifyComplete();
        StepVerifier.create(adapter.findById(vendor.getId(), org)).assertNext(found -> assertEquals(vendor.getId(), found.getId())).verifyComplete();
        StepVerifier.create(adapter.findAll(org)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).find(filter.capture());
        assertTrue(filter.getAllValues().get(0).toString().contains("organisationId"));
    }

    @Test
    @DisplayName("a duplicate is a 409 of the domain, on create or on a change; any other write error passes through; the URI may omit the database")
    void duplicates() {
        Vendor vendor = vendor();
        when(collection.insertOne(any(VendorDocument.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(1)));
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(2)));
        VendorMongoRepositoryAdapter adapter = new VendorMongoRepositoryAdapter(client, "mongodb://localhost:27017");

        StepVerifier.create(adapter.create(vendor)).expectError(DuplicateVendorException.class).verify();
        StepVerifier.create(adapter.create(vendor)).expectError(MongoWriteException.class).verify();
        StepVerifier.create(adapter.save(vendor, VendorStatus.ACTIVE, vendor.getAuditTrail().get(1))).expectError(DuplicateVendorException.class).verify();
        StepVerifier.create(adapter.save(vendor, VendorStatus.ACTIVE, vendor.getAuditTrail().get(1))).expectError(MongoWriteException.class).verify();
        assertThrows(NullPointerException.class, () -> new VendorMongoRepositoryAdapter(client, null));
    }

    @Test
    @DisplayName("save is guarded by the status loaded and pushes the audit entry; a lost race is a conflict")
    void save() {
        Vendor vendor = vendor();
        when(collection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));
        VendorMongoRepositoryAdapter adapter = new VendorMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.save(vendor, VendorStatus.ACTIVE, vendor.getAuditTrail().get(1))).verifyComplete();
        StepVerifier.create(adapter.save(vendor, VendorStatus.ACTIVE, vendor.getAuditTrail().get(1))).expectError(InvalidVendorStatusException.class).verify();

        ArgumentCaptor<Bson> guard = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).updateOne(guard.capture(), update.capture());
        assertTrue(guard.getAllValues().get(0).toString().contains("ACTIVE") && guard.getAllValues().get(0).toString().contains("organisationId"));
        String text = update.getAllValues().get(0).toString();
        assertTrue(text.contains("auditTrail"));
        for (String field : new String[]{"name", "category"}) {
            assertTrue(text.contains(field), field);
        }
    }
}
