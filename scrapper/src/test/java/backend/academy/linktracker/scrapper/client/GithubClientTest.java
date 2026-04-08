package backend.academy.linktracker.scrapper.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import backend.academy.linktracker.scrapper.properties.GithubProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GithubClientTest {

    @Test
    void fetchUpdateBuildsDescriptionAndTruncatesPreviewTo200() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GithubProperties properties = new GithubProperties();
        properties.setToken("test-token");

        server.expect(
                        requestTo(
                                "https://api.github.com/repos/owner/repo/issues?state=all&sort=created&direction=desc&per_page=20"))
                .andExpect(method(GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                [
                                  {
                                    "title":"Issue title",
                                    "body":"%s",
                                    "created_at":"2026-04-08T10:00:00Z",
                                    "user":{"login":"octocat"}
                                  }
                                ]
                                """.formatted("x".repeat(250))));

        GithubClient client = new GithubClient(builder, properties);
        var update = client.fetchUpdate(java.net.URI.create("https://github.com/owner/repo"));

        assertTrue(update.isPresent());
        assertTrue(update.orElseThrow().description().contains("Issue: Issue title"));
        String preview = update.orElseThrow()
                .description()
                .substring(update.orElseThrow().description().indexOf("Превью: ") + 8);
        assertTrue(preview.length() <= 200);
        server.verify();
    }

    @Test
    void fetchUpdateReturnsEmptyOnUnauthorized() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GithubProperties properties = new GithubProperties();
        properties.setToken("bad-token");

        server.expect(
                        requestTo(
                                "https://api.github.com/repos/owner/repo/issues?state=all&sort=created&direction=desc&per_page=20"))
                .andExpect(method(GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Bad credentials\"}"));

        GithubClient client = new GithubClient(builder, properties);
        var update = client.fetchUpdate(java.net.URI.create("https://github.com/owner/repo"));

        assertFalse(update.isPresent());
        server.verify();
    }
}
