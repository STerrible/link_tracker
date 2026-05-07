package backend.academy.linktracker.scrapper.client;

import java.net.URI;
import java.util.Optional;

public interface LinkSourceClient {
    Optional<LinkSourceUpdate> fetchUpdate(URI uri);
}
