package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.micronaut.context.event.StartupEvent;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class VendorIndexInitializerTest {

    private final MongoClient client = mock(MongoClient.class);

    @Test
    @DisplayName("startup creates the unique vendor name, the status list index, the unique agreement reference per vendor and the end-date index")
    void indexes() {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> vendors = mock(MongoCollection.class);
        MongoCollection<Document> agreements = mock(MongoCollection.class);
        when(client.getDatabase("tenant_vnd")).thenReturn(database);
        when(database.getCollection("vendors")).thenReturn(vendors);
        when(database.getCollection("agreements")).thenReturn(agreements);
        when(vendors.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));
        when(agreements.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new VendorIndexInitializer(client, "mongodb://mongo:27017/tenant_vnd").onApplicationEvent(mock(StartupEvent.class));

        ArgumentCaptor<IndexOptions> vendorOptions = ArgumentCaptor.forClass(IndexOptions.class);
        verify(vendors, times(2)).createIndex(any(), vendorOptions.capture());
        assertTrue(vendorOptions.getAllValues().get(0).isUnique());
        assertEquals("organisationId_1_name_1", vendorOptions.getAllValues().get(0).getName());
        assertFalse(vendorOptions.getAllValues().get(1).isUnique());
        assertEquals("organisationId_1_status_1", vendorOptions.getAllValues().get(1).getName());
        ArgumentCaptor<IndexOptions> agreementOptions = ArgumentCaptor.forClass(IndexOptions.class);
        verify(agreements, times(2)).createIndex(any(), agreementOptions.capture());
        assertTrue(agreementOptions.getAllValues().get(0).isUnique());
        assertEquals("organisationId_1_vendorId_1_reference_1", agreementOptions.getAllValues().get(0).getName());
        assertFalse(agreementOptions.getAllValues().get(1).isUnique());
        assertEquals("organisationId_1_endsOn_1", agreementOptions.getAllValues().get(1).getName());
    }

    @Test
    @DisplayName("fail-open: an unreachable server or a rejected index is logged, never propagated; the URI may omit the database; arguments are null-checked")
    void failOpen() {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(client.getDatabase("thinklab_it_vendor_registry_db")).thenReturn(database);
        when(database.getCollection(any(String.class))).thenReturn(collection);
        when(collection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.error(new MongoTimeoutException("no server")))
                .thenReturn(Mono.error(new IllegalStateException("rejected"))).thenReturn(Mono.just("ok"));
        var initializer = new VendorIndexInitializer(client, "mongodb://mongo:27017");

        initializer.onApplicationEvent(mock(StartupEvent.class));

        verify(collection, times(4)).createIndex(any(), any(IndexOptions.class));
        assertThrows(NullPointerException.class, () -> initializer.onApplicationEvent(null));
        assertThrows(NullPointerException.class, () -> new VendorIndexInitializer(null, "mongodb://mongo:27017"));
        assertThrows(NullPointerException.class, () -> new VendorIndexInitializer(client, null));
    }
}
