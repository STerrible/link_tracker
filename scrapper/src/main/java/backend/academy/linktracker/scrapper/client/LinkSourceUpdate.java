package backend.academy.linktracker.scrapper.client;

import java.time.Instant;

public record LinkSourceUpdate(Instant updatedAt, String description) {}
