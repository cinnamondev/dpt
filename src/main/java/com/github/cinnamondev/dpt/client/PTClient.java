package com.github.cinnamondev.dpt.client;

import org.slf4j.Logger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PTClient {
    private final String apiKey;
    private final HttpClient client = HttpClient.newHttpClient();
    private final String baseURI;

    private final HttpRequest.Builder baseRequest;
    private final Logger logger;

    public PTClient(String apiKey, String baseURI, Logger logger) {
        this.logger = logger;
        this.apiKey = apiKey;
        this.baseURI = baseURI;
        this.baseRequest = HttpRequest.newBuilder()
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey);
    }

    public Logger getLogger() {
        return logger;
    }
    private static String uuidToIdentifier(String uuid) {
        return uuid.substring(0, 8);
    }
    private Optional<HttpResponse<String>> post(String endpoint, String payload) {
        String resource = baseURI + endpoint;

        HttpRequest req = baseRequest
                .uri(URI.create(resource))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        try {
            return Optional.of(client.send(req, HttpResponse.BodyHandlers.ofString()));
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return Optional.empty();
    }
    private Optional<HttpResponse<Void>> postDiscarding(String endpoint, String payload) {
        String resource = baseURI + endpoint;

        HttpRequest req = baseRequest
                .uri(URI.create(resource))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        try {
            return Optional.of(client.send(req, HttpResponse.BodyHandlers.discarding()));
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<HttpResponse<String>> get(String endpoint) {
        String resource = baseURI + endpoint;

        HttpRequest req = baseRequest
                .uri(URI.create(resource))
                .GET()
                .build();

        try {
            return Optional.of(client.send(req, HttpResponse.BodyHandlers.ofString()));
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return Optional.empty();
    }
    public Optional<String> resources(String identifier) {
        Optional<HttpResponse<String>> response = get("/api/client/servers/" + identifier + "/resources");

        return response.map(HttpResponse::body);
    }
    public PowerState state(String identifier) {
        Optional<String> resources = resources(identifier);
        PowerState state = resources.filter(json -> {return json.contains("\"current_state\"");})
                .map(json -> {
                   Pattern r = Pattern.compile("\"current_state\": *\"([a-zA-Z]+)\"");
                   Matcher m = r.matcher(json);
                   if (m.find()) {
                       String stateString = m.group(1);
                       return PowerState.fromString(stateString);
                   } else {
                       logger.error("couldnt get power state for " + identifier);
                       logger.info(json);
                   }
                   return PowerState.OFFLINE;
                })
                .orElse(PowerState.OFFLINE);
        logger.info("PowerState {} is {}", identifier, state);
        return state;
    }
    public int power(String identifier, PowerAction power) {
        String payload = "{\"signal\": \"" + power.toString() + "\"}";
        Optional<HttpResponse<Void>> response = postDiscarding("/api/client/servers/" + identifier + "/power", payload);

        return response.map(HttpResponse::statusCode).orElse(0);
    }

}
