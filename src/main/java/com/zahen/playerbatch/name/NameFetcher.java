package com.zahen.playerbatch.name;

import com.zahen.playerbatch.PlayerBatch;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NameFetcher {
    private static final Pattern PROFILE_NAME_PATTERN = Pattern.compile("href=\"/profile/([A-Za-z0-9_]{1,16})\"");
    private static final List<URI> NAME_MC_URIS = List.of(
            URI.create("https://namemc.com/minecraft-names"),
            URI.create("https://namemc.com/minecraft-names?sort=trending-week"),
            URI.create("https://namemc.com/minecraft-names?sort=trending-month")
    );

    private static final ExecutorService FETCH_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "playerbatch-namemc");
        thread.setDaemon(true);
        return thread;
    });

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private NameFetcher() {
    }

    public static CompletableFuture<List<String>> fetchNamesAsync(int requestedCount) {
        return CompletableFuture.supplyAsync(() -> fetchNames(requestedCount), FETCH_EXECUTOR);
    }

    private static List<String> fetchNames(int requestedCount) {
        Set<String> names = new LinkedHashSet<>();
        for (URI uri : NAME_MC_URIS) {
            if (names.size() >= requestedCount) {
                break;
            }

            try {
                String html = fetchPage(uri);
                extractNames(html, names);
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                PlayerBatch.LOGGER.error("Failed to fetch names from {}", uri, exception);
            }
        }
        return new ArrayList<>(names);
    }

    private static String fetchPage(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (compatible; PlayerBatch/1.0)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Unexpected NameMC response code " + response.statusCode() + " for " + uri);
        }
        return response.body();
    }

    private static void extractNames(String html, Set<String> names) {
        Matcher matcher = PROFILE_NAME_PATTERN.matcher(html);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate.length() >= 3) {
                names.add(candidate);
            }
        }
    }
}

