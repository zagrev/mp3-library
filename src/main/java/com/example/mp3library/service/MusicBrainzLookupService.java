package com.example.mp3library.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

@Service
public class MusicBrainzLookupService
  {

    private static final Logger LOGGER = LoggerFactory.getLogger(MusicBrainzLookupService.class);

    private static final String MUSIC_BRAINZ_URL = "https://musicbrainz.org/ws/2/recording/";

    private final RestTemplate restTemplate;

    public MusicBrainzLookupService(RestTemplate restTemplate)
    {
      this.restTemplate = restTemplate;
    }

    private String buildQuery(String title, String artist, String album)
    {
      final var sb = new StringBuilder();
      if (StringUtils.hasText(title))
      {
        sb.append("recording:").append(quoteTerm(title));
      }
      if (StringUtils.hasText(artist))
      {
        if (sb.length() > 0)
        {
          sb.append(" AND ");
        }
        sb.append("artist:").append(quoteTerm(artist));
      }
      if (StringUtils.hasText(album))
      {
        if (sb.length() > 0)
        {
          sb.append(" AND ");
        }
        sb.append("release:").append(quoteTerm(album));
      }
      return sb.toString();
    }

    public Optional<MetadataSnapshot> lookup(String title, String artist, String album)
    {
      if (!StringUtils.hasText(title) && !StringUtils.hasText(artist) && !StringUtils.hasText(album))
      {
        return Optional.empty();
      }

      final var query = buildQuery(title, artist, album);

      try
      {
        final ResponseEntity<JsonNode> response = executeLookupRequest(query);
        final var root = response.getBody();
        if (root == null || !root.has("recordings") || root.path("recordings").isEmpty())
        {
          return Optional.empty();
        }

        final var firstRecording = root.path("recordings").get(0);
        final var resolvedTitle = textOrBlank(firstRecording.path("title"));
        var resolvedArtist = "";
        final var artistCredit = firstRecording.path("artist-credit");
        if (artistCredit.isArray() && !artistCredit.isEmpty())
        {
          resolvedArtist = textOrBlank(artistCredit.get(0).path("name"));
        }

        var resolvedAlbum = "";
        final var releases = firstRecording.path("releases");
        if (releases.isArray() && !releases.isEmpty())
        {
          resolvedAlbum = textOrBlank(releases.get(0).path("title"));
        }

        return Optional.of(new MetadataSnapshot(prefer(resolvedTitle, title), prefer(resolvedArtist, artist),
            prefer(resolvedAlbum, album), 0, "", "", "", "", "", 0, 0, 0, "", "{}"));
      }
      catch (final RestClientException ex)
      {
        LOGGER.warn("MusicBrainz lookup request failed for query: {}", query, ex);
        throw new MetadataLookupUnavailableException("MusicBrainz lookup request failed.", ex);
      }
    }

    public void checkHealth()
    {
      final var query = "recording:\"test\"";
      try
      {
        executeLookupRequest(query);
      }
      catch (final RestClientException ex)
      {
        LOGGER.warn("MusicBrainz health check failed.", ex);
        throw new MetadataLookupUnavailableException("MusicBrainz lookup request failed.", ex);
      }
    }

    private ResponseEntity<JsonNode> executeLookupRequest(String query)
    {
      final var url = UriComponentsBuilder.fromUriString(MUSIC_BRAINZ_URL).queryParam("query", query)
          .queryParam("fmt", "json").queryParam("limit", 1).encode(StandardCharsets.UTF_8).toUriString();

      final var headers = new HttpHeaders();
      headers.set("User-Agent", "mp3-library/0.0.1 (local-dev)");
      return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    private String prefer(String candidate, String fallback)
    {
      return StringUtils.hasText(candidate) ? candidate : fallback == null ? "" : fallback;
    }

    private String quoteTerm(String input)
    {
      return "\"" + input.replace("\"", "") + "\"";
    }

    private String textOrBlank(JsonNode node)
    {
      return node == null ? "" : node.asText("").trim();
    }
  }
