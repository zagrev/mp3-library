package com.example.mp3library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MusicBrainzLookupServiceTest
{

  @Mock
  private RestTemplate restTemplate;

  @Test
  void lookupWithNoInputsReturnsEmptyWithoutHttpCall()
  {
    final var service = new MusicBrainzLookupService(restTemplate);

    final var result = service.lookup("", " ", null);

    assertTrue(result.isEmpty());
    verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(com.fasterxml.jackson.databind.JsonNode.class));
  }

  @Test
  void lookupBuildsQueryAndReturnsResolvedMetadata() throws Exception
  {
    final var service = new MusicBrainzLookupService(restTemplate);
    final var mapper = new ObjectMapper();
    final var payload = mapper.readTree("""
        {
          "recordings": [
            {
              "title": "Resolved Title",
              "artist-credit": [{"name": "Resolved Artist"}],
              "releases": [{"title": "Resolved Album"}]
            }
          ]
        }
        """);
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
        .thenReturn(ResponseEntity.ok(payload));

    final var result = service.lookup("Title \"Quoted\"", "Artist", "Album");

    assertTrue(result.isPresent());
    assertEquals("Resolved Title", result.get().title());
    assertEquals("Resolved Artist", result.get().artist());
    assertEquals("Resolved Album", result.get().album());

    final var urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class),
        eq(com.fasterxml.jackson.databind.JsonNode.class));
    final var decodedUrl = URLDecoder.decode(urlCaptor.getValue(), StandardCharsets.UTF_8);
    assertTrue(decodedUrl.contains("query=recording:\"Title Quoted\" AND artist:\"Artist\" AND release:\"Album\""));
    assertTrue(decodedUrl.contains("fmt=json"));
    assertTrue(decodedUrl.contains("limit=1"));
  }

  @Test
  void lookupFallsBackToInputsWhenResponseIsMissingFields() throws Exception
  {
    final var service = new MusicBrainzLookupService(restTemplate);
    final var mapper = new ObjectMapper();
    final var payload = mapper.readTree("""
        {
          "recordings": [
            {
              "title": "   "
            }
          ]
        }
        """);
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
        .thenReturn(ResponseEntity.ok(payload));

    final var result = service.lookup("Given Title", "Given Artist", "Given Album");

    assertTrue(result.isPresent());
    assertEquals("Given Title", result.get().title());
    assertEquals("Given Artist", result.get().artist());
    assertEquals("Given Album", result.get().album());
  }

  @Test
  void lookupReturnsEmptyWhenResponseHasNoRecordings() throws Exception
  {
    final var service = new MusicBrainzLookupService(restTemplate);
    final var mapper = new ObjectMapper();
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
        .thenReturn(ResponseEntity.ok(mapper.readTree("{" + "\"recordings\":[]}")));

    final var result = service.lookup("Title", "Artist", "Album");

    assertTrue(result.isEmpty());
  }

  @Test
  void lookupReturnsEmptyWhenClientThrows()
  {
    final var service = new MusicBrainzLookupService(restTemplate);
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
        .thenThrow(new RestClientException("boom"));

    final var result = service.lookup("Title", "Artist", "Album");

    assertTrue(result.isEmpty());
  }
}
