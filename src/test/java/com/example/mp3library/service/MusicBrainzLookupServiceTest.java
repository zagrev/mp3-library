package com.example.mp3library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.example.mp3library.service.musicbrainz.MusicBrainzLookupResponse;
import com.example.mp3library.service.musicbrainz.MusicBrainzLookupResponse.ArtistCredit;
import com.example.mp3library.service.musicbrainz.MusicBrainzLookupResponse.Recording;
import com.example.mp3library.service.musicbrainz.MusicBrainzLookupResponse.Release;

@ExtendWith(MockitoExtension.class)
class MusicBrainzLookupServiceTest
{

  @Mock
  private RestTemplate restTemplate;

  @Test
  void lookupWithNoInputsThrowsValidationError()
  {
    final var service = new MusicBrainzLookupService(restTemplate);

    assertThrows(IllegalArgumentException.class, () -> service.lookup("", " ", null));
    verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(MusicBrainzLookupResponse.class));
  }

  @Test
  void lookupBuildsQueryAndReturnsResolvedMetadata() throws Exception
  {
    final var service = new MusicBrainzLookupService(restTemplate);
    final var payload = new MusicBrainzLookupResponse(
        java.util.List.of(new Recording("Resolved Title", java.util.List.of(new ArtistCredit("Resolved Artist")),
            java.util.List.of(new Release("Resolved Album")))));
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(MusicBrainzLookupResponse.class)))
        .thenReturn(ResponseEntity.ok(payload));

    final var result = service.lookup("Title \"Quoted\"", "Artist", "Album");

    assertTrue(result.isPresent());
    assertEquals("Resolved Title", result.get().title());
    assertEquals("Resolved Artist", result.get().artist());
    assertEquals("Resolved Album", result.get().album());

    final var urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class),
        eq(MusicBrainzLookupResponse.class));
    final var decodedUrl = URLDecoder.decode(urlCaptor.getValue(), StandardCharsets.UTF_8);
    assertTrue(decodedUrl.contains("query=recording:\"Title Quoted\" AND artist:\"Artist\" AND release:\"Album\""));
    assertTrue(decodedUrl.contains("fmt=json"));
    assertTrue(decodedUrl.contains("limit=1"));
  }

  @Test
  void lookupFallsBackToInputsWhenResponseIsMissingFields() throws Exception
  {
    final var service = new MusicBrainzLookupService(restTemplate);
    final var payload = new MusicBrainzLookupResponse(
        java.util.List.of(new Recording("   ", java.util.List.of(), java.util.List.of())));
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(MusicBrainzLookupResponse.class)))
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
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(MusicBrainzLookupResponse.class)))
        .thenReturn(ResponseEntity.ok(new MusicBrainzLookupResponse(java.util.List.of())));

    final var result = service.lookup("Title", "Artist", "Album");

    assertTrue(result.isEmpty());
  }

  @Test
  void lookupThrowsUnavailableWhenClientThrows()
  {
    final var service = new MusicBrainzLookupService(restTemplate);
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(MusicBrainzLookupResponse.class)))
        .thenThrow(new RestClientException("boom"));

    assertThrows(MetadataLookupUnavailableException.class, () -> service.lookup("Title", "Artist", "Album"));
  }

  @Test
  void healthCheckPassesWhenRequestSucceeds() throws Exception
  {
    final var service = new MusicBrainzLookupService(restTemplate);
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(MusicBrainzLookupResponse.class)))
        .thenReturn(ResponseEntity.ok(new MusicBrainzLookupResponse(java.util.List.of())));

    service.checkHealth();

    verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
        eq(MusicBrainzLookupResponse.class));
  }

  @Test
  void healthCheckThrowsUnavailableWhenClientThrows()
  {
    final var service = new MusicBrainzLookupService(restTemplate);
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(MusicBrainzLookupResponse.class)))
        .thenThrow(new RestClientException("down"));

    assertThrows(MetadataLookupUnavailableException.class, service::checkHealth);
  }
}
