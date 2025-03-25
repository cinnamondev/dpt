package com.github.cinnamondev.dpt.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PTClient {
    private final Logger logger;
    private final HttpClient client = HttpClient.newHttpClient();
    private final HttpRequest.Builder requestBuilder;

    private final URI baseUri;
    private final String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    public PTClient(Logger logger, URI baseUri, String apiKey) {
        this.logger = logger;
        this.baseUri = baseUri;
        this.apiKey = apiKey;
        this.requestBuilder = HttpRequest.newBuilder()
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey);
    }



    private URI powerEndpoint(String identifier) {
        return baseUri.resolve("/api/client/servers/" + identifier.substring(0,8) + "/power");
    }
    public CompletableFuture<HttpResponse<Void>> postPower(String identifier, PowerAction action) {
        return client.sendAsync(
                requestBuilder.uri(powerEndpoint(identifier))
                        .POST(HttpRequest.BodyPublishers.ofString("{\"signal\":\"" + action + "\"}"))
                        .build(), HttpResponse.BodyHandlers.discarding()
        );
    }

    public boolean power(String identifier, PowerAction action) {
        var future = postPower(identifier, action)
                .thenApply(r -> r.statusCode() < 200 || r.statusCode() >= 300);

        try {
            return future.join();
        } catch (Exception e) {
            logger.error("ptclient set power state failed for " + identifier, e);
            return false;
        }
    }
    private URI consoleEndpoint(String identifier) {
        return baseUri.resolve("/api/client/servers/" + identifier.substring(0,8) + "/websocket");
    }
    private URI resourceEndpoint(String identifier) {
        return baseUri.resolve("/api/client/servers/" + identifier.substring(0,8) + "/resources");
    }
    public CompletableFuture<Resources> resources(String identifier) {
        return client.sendAsync(
                requestBuilder.uri(resourceEndpoint(identifier))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(r -> {
            if (r.statusCode() < 200 || r.statusCode() >= 300) {
                throw new RuntimeException("Unexpected response code: " + r.statusCode());
            }
            try {
                return ((PTObject) objectMapper.reader()
                        .forType(PTObject.class)
                        .readValue(r.body())).attributes;
            } catch (Exception e) {
                logger.info(r.body());
                throw new RuntimeException(e);
            }
        });
    }
    private URI commandEndpoint(String identifier) {
        return baseUri.resolve("/api/client/servers/" + identifier.substring(0,8) + "/command");
    }
    private URI detailEndpoint(String identifier) {
        return baseUri.resolve("/api/client/servers/" + identifier.substring(0,8));
    }
    public PowerState power(String identifier) {
        var future = client.sendAsync(
                requestBuilder.uri(resourceEndpoint(identifier))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(r -> {
            String body = r.body();
            if (!body.contains("\"current_state\"")) { throw new RuntimeException("unexpected power state: " + body); }
            Pattern regexp = Pattern.compile("\"current_state\": *\"([a-zA-Z]+)\"");
            Matcher m = regexp.matcher(body);
            if (m.find()) {
                String stateString = m.group(1);
                return PowerState.fromString(stateString);
            } else {
                return PowerState.OFFLINE;
            }
        });

        try {
            return future.join();
        } catch (Exception e) {
            logger.error("ptclient get power state failed for " + identifier, e);
            return PowerState.OFFLINE;
        }
    }
}
