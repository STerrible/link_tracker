package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.properties.GithubProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class GithubClient implements LinkSourceClient {

    private static final Logger log = LoggerFactory.getLogger(GithubClient.class);
    private static final String GITHUB_HOST = "github.com";
    private static final String GITHUB_API_BASE_URL = "https://api.github.com";
    private static final String GITHUB_API_VERSION = "2022-11-28";
    private static final String USER_AGENT = "link-tracker-scrapper";
    private static final int ISSUES_PER_PAGE = 20;
    private static final int PREVIEW_LIMIT = 200;

    private final RestClient restClient;

    public GithubClient(RestClient.Builder builder, GithubProperties properties) {
        this.restClient = builder.baseUrl(GITHUB_API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getToken())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
    }

    @Override
    public Optional<LinkSourceUpdate> fetchUpdate(URI uri) {
        if (!GITHUB_HOST.equalsIgnoreCase(uri.getHost())) {
            return Optional.empty();
        }

        String[] segments = Arrays.stream(uri.getPath().split("/"))
                .filter(part -> !part.isBlank())
                .toArray(String[]::new);
        if (segments.length < 2) {
            return Optional.empty();
        }

        try {
            IssueResponse[] response = restClient
                    .get()
                    .uri(
                            "/repos/{owner}/{repo}/issues?state=all&sort=created&direction=desc&per_page="
                                    + ISSUES_PER_PAGE,
                            segments[0],
                            segments[1])
                    .retrieve()
                    .body(IssueResponse[].class);
            List<IssueResponse> issues = response == null ? List.of() : Arrays.asList(response);

            return issues.stream()
                    .filter(issue -> issue != null && issue.createdAt() != null)
                    .max(Comparator.comparing(IssueResponse::createdAt))
                    .map(issue -> new LinkSourceUpdate(issue.createdAt(), formatDescription(issue)));
        } catch (HttpClientErrorException exception) {
            log.atWarn()
                    .addKeyValue("uri", uri)
                    .addKeyValue("status", exception.getStatusCode().value())
                    .setCause(exception)
                    .log("github_fetch_failed");
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.atWarn().addKeyValue("uri", uri).setCause(exception).log("github_fetch_failed");
            return Optional.empty();
        }
    }

    private String formatDescription(IssueResponse issue) {
        String entityType = issue.pullRequest() == null ? "Issue" : "PR";
        String author = issue.user() == null || issue.user().login() == null
                ? "unknown"
                : issue.user().login();
        String title = issue.title() == null ? "(без названия)" : issue.title();
        String createdAt =
                issue.createdAt() == null ? "unknown-time" : issue.createdAt().toString();
        String preview = sanitizePreview(issue.body(), PREVIEW_LIMIT);
        return "%s: %s%nАвтор: %s%nСоздано: %s%nПревью: %s".formatted(entityType, title, author, createdAt, preview);
    }

    private String sanitizePreview(String source, int limit) {
        if (source == null || source.isBlank()) {
            return "(пусто)";
        }
        String normalized = source.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        if (limit <= 3) {
            return "...".substring(0, limit);
        }
        return normalized.substring(0, limit - 3) + "...";
    }

    private record IssueResponse(
            String title,
            String body,
            UserResponse user,

            @JsonProperty("updated_at") java.time.Instant updatedAt,

            @JsonProperty("created_at") java.time.Instant createdAt,

            @JsonProperty("pull_request") Object pullRequest) {}

    private record UserResponse(String login) {}
}
