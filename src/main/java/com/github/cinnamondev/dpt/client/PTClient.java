package com.github.cinnamondev.dpt.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PTClient {
    private final HttpClient client = HttpClient.newHttpClient();
    private final HttpRequest.Builder requestBuilder;

    private final URI baseUri;
    private final String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    public PTClient(URI baseUri, String apiKey) {
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
    public HttpResponse<Void> postPower(String identifier, PowerAction action) throws IOException, InterruptedException {
        return client.send(
                requestBuilder.uri(powerEndpoint(identifier))
                        .POST(HttpRequest.BodyPublishers.ofString("{\"signal\":\"" + action + "\"}"))
                        .build(), HttpResponse.BodyHandlers.discarding()
        );
    }

    public boolean power(String identifier, PowerAction action) {
        try {
            return postPower(identifier, action).statusCode() == 204;
        } catch (Exception e) {
            return false;
        }
    }
    private URI consoleEndpoint(String identifier) {
        return baseUri.resolve("/api/client/servers/" + identifier.substring(0,8) + "/websocket");
    }
    private URI resourceEndpoint(String identifier) {
        return baseUri.resolve("/api/client/servers/" + identifier.substring(0,8) + "/resources");
    }
    public Optional<Resources> resources(String identifier) {
        try {
            var resp = client.send(
                    requestBuilder.uri(resourceEndpoint(identifier))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (resp.statusCode() != 200) { return Optional.empty(); }
            return Optional.of(objectMapper.reader()
                    .withRootName("attributes")
                    .forType(Resources.class)
                    .readValue(resp.body())
            );
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    private URI commandEndpoint(String identifier) {
        return baseUri.resolve("/api/client/servers/" + identifier.substring(0,8) + "/command");
    }
    private URI detailEndpoint(String identifier) {
        return baseUri.resolve("/api/client/servers/" + identifier.substring(0,8));
    }
    public PowerState power(String identifier) {
        try {
            var resp = client.send(
                    requestBuilder.uri(resourceEndpoint(identifier))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (resp.statusCode() != 200) { return PowerState.OFFLINE; }
            String body = resp.body();
            if (!body.contains("\"current_state\"")) { return PowerState.OFFLINE; }
            Pattern r = Pattern.compile("\"current_state\": *\"([a-zA-Z]+)\"");
            Matcher m = r.matcher(body);
            if (m.find()) {
                String stateString = m.group(1);
                return PowerState.fromString(stateString);
            } else {
                return PowerState.OFFLINE;

            }
        } catch (Exception e) {
            return PowerState.OFFLINE;
        }
    }
}
