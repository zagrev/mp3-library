package com.example.mp3library.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class MusicBrainzLookupService {

    private static final String MUSIC_BRAINZ_URL = "https://musicbrainz.org/ws/2/recording/";

    private final RestTemplate restTemplate;

    public MusicBrainzLookupService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Optional<MetadataSnapshot> lookup(String title, String artist, String album) {
        if (!StringUtils.hasText(title) && !StringUtils.hasText(artist) && !StringUtils.hasText(album)) {
            return Optional.empty();
        }

        String query = buildQuery(title, artist, album);
        String url = UriComponentsBuilder.fromHttpUrl(MUSIC_BRAINZ_URL)
                .queryParam("query", query)
                .queryParam("fmt", "json")
                .queryParam("limit", 1)
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "mp3-library/0.0.1 (local-dev)");

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    JsonNode.class
            );
            JsonNode root = response.getBody();
            if (root == null || !root.has("recordings") || root.path("recordings").isEmpty()) {
                return Optional.empty();
            }

            JsonNode firstRecording = root.path("recordings").get(0);
            String resolvedTitle = textOrBlank(firstRecording.path("title"));
            String resolvedArtist = "";
            JsonNode artistCredit = firstRecording.path("artist-credit");
            if (artistCredit.isArray() && !artistCredit.isEmpty()) {
                resolvedArtist = textOrBlank(artistCredit.get(0).path("name"));
            }

            String resolvedAlbum = "";
            JsonNode releases = firstRecording.path("releases");
            if (releases.isArray() && !releases.isEmpty()) {
                resolvedAlbum = textOrBlank(releases.get(0).path("title"));
            }

            return Optional.of(new MetadataSnapshot(
                    prefer(resolvedTitle, title),
                    prefer(resolvedArtist, artist),
                    prefer(resolvedAlbum, album),
                    "",
                    "",
                    "",
                    ""
            ));
        } catch (RestClientException ex) {
            return Optional.empty();
        }
    }

    private String buildQuery(String title, String artist, String album) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(title)) {
            sb.append("recording:").append(quoteTerm(title));
        }
        if (StringUtils.hasText(artist)) {
            if (sb.length() > 0) {
                sb.append(" AND ");
            }
            sb.append("artist:").append(quoteTerm(artist));
        }
        if (StringUtils.hasText(album)) {
            if (sb.length() > 0) {
                sb.append(" AND ");
            }
            sb.append("release:").append(quoteTerm(album));
        }
        return sb.toString();
    }

    private String quoteTerm(String input) {
        return "\"" + input.replace("\"", "") + "\"";
    }

    private String textOrBlank(JsonNode node) {
        return node == null ? "" : node.asText("").trim();
    }

    private String prefer(String candidate, String fallback) {
        return StringUtils.hasText(candidate) ? candidate : (fallback == null ? "" : fallback);
    }
}
