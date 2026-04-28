package com.travel.intellitravel_pioneer.service;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TripSummaryGenerationStateStore {

    public enum Status {
        IN_PROGRESS,
        SUCCEEDED,
        FAILED
    }

    public record StateSnapshot(Status status, OffsetDateTime updatedAt, String errorMessage) {
    }

    private record StateRecord(Status status, OffsetDateTime updatedAt, String errorMessage) {
        private StateSnapshot toSnapshot() {
            return new StateSnapshot(status, updatedAt, errorMessage);
        }
    }

    private final ConcurrentHashMap<String, StateRecord> stateByTripId = new ConcurrentHashMap<>();

    public void markInProgress(String tripId) {
        if (tripId == null || tripId.isBlank()) {
            return;
        }
        stateByTripId.put(tripId, new StateRecord(Status.IN_PROGRESS, OffsetDateTime.now(), null));
    }

    public void markSucceeded(String tripId) {
        if (tripId == null || tripId.isBlank()) {
            return;
        }
        stateByTripId.put(tripId, new StateRecord(Status.SUCCEEDED, OffsetDateTime.now(), null));
    }

    public void markFailed(String tripId, String errorMessage) {
        if (tripId == null || tripId.isBlank()) {
            return;
        }
        String normalizedError = errorMessage == null ? null : errorMessage.trim();
        stateByTripId.put(tripId, new StateRecord(Status.FAILED, OffsetDateTime.now(), normalizedError));
    }

    public Optional<StateSnapshot> getSnapshot(String tripId) {
        if (tripId == null || tripId.isBlank()) {
            return Optional.empty();
        }
        StateRecord record = stateByTripId.get(tripId);
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(record.toSnapshot());
    }
}