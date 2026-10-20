package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.PromiseResponse;
import com.thinklab.domain.exception.VendorAccessDeniedException;
import com.thinklab.domain.model.Agreement;
import com.thinklab.domain.model.Agreement.AgreementStatus;
import com.thinklab.domain.model.Agreement.Kind;
import com.thinklab.domain.repository.AgreementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromisesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private final UUID org = UUID.randomUUID();
    private final UUID vendor = UUID.randomUUID();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private Agreement agreement(String reference, String startsOn, String endsOn, Instant updatedAt) {
        return Agreement.reconstitute(UUID.randomUUID(), org, vendor, reference, Kind.CONTRACT, null, LocalDate.parse(startsOn), LocalDate.parse(endsOn), 30, false, AgreementStatus.ACTIVE,
                updatedAt, updatedAt, List.of());
    }

    private RetrievePromisesUseCase useCase(Agreement... found) {
        AgreementRepository repository = mock(AgreementRepository.class);
        when(repository.findAll(org)).thenReturn(Flux.just(found));
        return new RetrievePromisesUseCase(repository, clock);
    }

    @Test
    @DisplayName("an agreement ended or about to end is broken with a fixed code, one renewed lately is listed as kept, a quiet one is left out; the name is the contract's reference")
    void selects() {
        Agreement expired = agreement("CT-EXPIRED", "2025-01-01", "2026-09-30", NOW.minus(Duration.ofDays(60)));
        Agreement expiring = agreement("CT-EXPIRING", "2026-01-01", "2026-11-05", NOW.minus(Duration.ofDays(60)));
        Agreement renewed = agreement("CT-RENEWED", "2026-01-01", "2027-12-31", NOW.minus(Duration.ofHours(4)));
        Agreement quiet = agreement("CT-QUIET", "2026-01-01", "2027-12-31", NOW.minus(Duration.ofDays(60)));

        StepVerifier.create(useCase(quiet, renewed, expiring, expired).execute(org, null, null, "ENGINEER")).assertNext(promises -> {
            assertEquals(List.of("CT-EXPIRING", "CT-EXPIRED", "CT-RENEWED"), promises.stream().map(PromiseResponse::name).toList());
            assertEquals(List.of("AGREEMENT_EXPIRING", "AGREEMENT_EXPIRED"), promises.subList(0, 2).stream().map(PromiseResponse::code).toList());
            assertEquals("KEPT", promises.get(2).verdict());
            assertNull(promises.get(2).code());
            assertNull(promises.get(2).assetId());
            assertEquals("ACTIVE", promises.get(2).status());
            assertEquals(NOW.minus(Duration.ofHours(4)), promises.get(2).changedAt());
        }).verifyComplete();
    }

    @Test
    @DisplayName("the window and the limit apply to each kind")
    void window() {
        Agreement keptOld = agreement("OLD", "2026-01-01", "2027-12-31", NOW.minus(Duration.ofHours(30)));
        Agreement kept = agreement("NEW", "2026-01-01", "2027-12-31", NOW.minus(Duration.ofHours(5)));
        Agreement one = agreement("B1", "2025-01-01", "2026-09-30", NOW.minus(Duration.ofDays(60)));
        Agreement two = agreement("B2", "2025-01-01", "2026-09-30", NOW.minus(Duration.ofDays(60)));

        StepVerifier.create(useCase(keptOld, kept, one, two).execute(org, 1, 1, "ENGINEER"))
                .assertNext(promises -> assertEquals(List.of("B1", "NEW"), promises.stream().map(PromiseResponse::name).toList())).verifyComplete();
    }

    @Test
    @DisplayName("the days must be 1 to 365 and the limit 1 to 1000; a requester is refused")
    void validation() {
        RetrievePromisesUseCase useCase = useCase();

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, 0, null, "ENGINEER").block());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, 366, null, "ENGINEER").block());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, null, 0, "ENGINEER").block());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, null, 1001, "ENGINEER").block());
        assertThrows(VendorAccessDeniedException.class, () -> useCase.execute(org, null, null, "REQUESTER").block());
    }

    @Test
    @DisplayName("the selection is a utility class")
    void utility() throws ReflectiveOperationException {
        Constructor<PromiseSelection> constructor = PromiseSelection.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }
}
