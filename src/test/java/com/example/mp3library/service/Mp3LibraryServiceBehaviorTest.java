package com.example.mp3library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.example.mp3library.model.Mp3FileRecord;
import com.example.mp3library.repository.Mp3FileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class Mp3LibraryServiceBehaviorTest
{

  @Mock
  private Mp3FileRepository repository;

  @Mock
  private MusicBrainzLookupService lookupService;

  @TempDir
  Path tempDir;

  @Test
  void scanDirectoryIndexesOnlyMp3FilesAndCapturesFallbackMetadata() throws Exception
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    final var mp3 = tempDir.resolve("Track 01.mp3");
    final var txt = tempDir.resolve("notes.txt");
    Files.write(mp3, new byte[]
    { 1, 2, 3 });
    Files.writeString(txt, "skip");

    when(repository.findByCurrentPath(anyString())).thenReturn(Optional.empty());
    when(repository.save(any(Mp3FileRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

    final int scanned = service.scanDirectory(tempDir.toString());

    assertEquals(1, scanned);
    final var recordCaptor = ArgumentCaptor.forClass(Mp3FileRecord.class);
    verify(repository).save(recordCaptor.capture());
    final var saved = recordCaptor.getValue();
    assertEquals("Track 01", saved.getTitle());
    assertEquals(saved.getTitle(), saved.getOriginalTitle());
    assertEquals("{}", saved.getExtraMetadataJson());
    assertEquals(saved.getExtraMetadataJson(), saved.getOriginalExtraMetadataJson());
    assertNotNull(saved.getCurrentPath());
    assertNotNull(saved.getOriginalPath());
  }

  @Test
  void scanDirectoryRejectsMissingDirectory()
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());

    assertThrows(IllegalArgumentException.class,
        () -> service.scanDirectory(tempDir.resolve("does-not-exist").toString()));
  }

  @Test
  void lookupAndApplyReturnsEmptyWhenLookupHasNoMatch() throws Exception
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    final var record = new Mp3FileRecord();
    record.setId("1");
    record.setTitle("Title");
    record.setArtist("Artist");
    record.setAlbum("Album");
    record.setCurrentPath(tempDir.resolve("missing.mp3").toString());
    when(repository.findById("1")).thenReturn(Optional.of(record));
    when(lookupService.lookup("Title", "Artist", "Album")).thenReturn(Optional.empty());

    final var result = service.lookupAndApply("1");

    assertTrue(result.isEmpty());
    verify(repository, never()).save(any(Mp3FileRecord.class));
  }

  @Test
  void lookupAndApplyUpdatesRecordWithResolvedValues() throws Exception
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    final var record = new Mp3FileRecord();
    record.setId("2");
    record.setTitle("Old Title");
    record.setArtist("Keep Artist");
    record.setAlbum("Old Album");
    record.setCurrentPath(tempDir.resolve("missing2.mp3").toString());
    when(repository.findById("2")).thenReturn(Optional.of(record));
    when(lookupService.lookup("Old Title", "Keep Artist", "Old Album"))
        .thenReturn(Optional.of(new MetadataSnapshot("New Title", "", "New Album", 7, "2024", "Rock", "cmt",
            "Album Artist", "Composer", 1, 2, 12, "Lyrics", "{\"BPM\":\"120\"}")));
    when(repository.save(any(Mp3FileRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

    final var result = service.lookupAndApply("2");

    assertTrue(result.isPresent());
    assertEquals("New Title", result.get().getTitle());
    assertEquals("Keep Artist", result.get().getArtist());
    assertEquals("New Album", result.get().getAlbum());
    assertEquals(7, result.get().getTrack());
    assertEquals("2024", result.get().getYear());
    assertEquals("Rock", result.get().getGenre());
    assertEquals("cmt", result.get().getCommentText());
    assertEquals("Album Artist", result.get().getAlbumArtist());
    assertEquals("Composer", result.get().getComposer());
    assertEquals(1, result.get().getDiscNumber());
    assertEquals(2, result.get().getDiscTotal());
    assertEquals(12, result.get().getTrackTotal());
    assertEquals("Lyrics", result.get().getLyrics());
    assertEquals("{\"BPM\":\"120\"}", result.get().getExtraMetadataJson());
  }

  @Test
  void lookupAndApplyThrowsForUnknownRecord()
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    when(repository.findById("missing")).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.lookupAndApply("missing"));
  }

  @Test
  void lookupPreviewDelegatesUsingCurrentRecordValues()
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    final var record = new Mp3FileRecord();
    record.setId("4");
    record.setTitle("T");
    record.setArtist("A");
    record.setAlbum("AL");
    when(repository.findById("4")).thenReturn(Optional.of(record));
    when(lookupService.lookup("T", "A", "AL")).thenReturn(Optional.empty());

    final var result = service.lookupPreview("4");

    assertTrue(result.isEmpty());
  }

  @Test
  void lookupPreviewThrowsForUnknownRecord()
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    when(repository.findById("missing")).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.lookupPreview("missing"));
  }

  @Test
  void updateRecordTrimsValuesAndUpdatesPathWhenChanged() throws Exception
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    final var record = new Mp3FileRecord();
    record.setId("5");
    record.setCurrentPath(tempDir.resolve("missing-source.mp3").toString());
    when(repository.findById("5")).thenReturn(Optional.of(record));
    when(repository.save(any(Mp3FileRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

    final var update = new Mp3FileRecord();
    update.setTitle("  Title  ");
    update.setArtist("  Artist  ");
    update.setAlbum(" Album ");
    update.setTrack(3);
    update.setYear(" 2025 ");
    update.setGenre(" Rock ");
    update.setCommentText(" note ");
    update.setAlbumArtist(" Album Artist ");
    update.setComposer(" Composer ");
    update.setLyrics(" Lyrics ");
    update.setDiscNumber(1);
    update.setDiscTotal(2);
    update.setTrackTotal(10);
    update.setExtraMetadataJson(" {\"x\":\"y\"} ");

    final var targetPath = tempDir.resolve("new-location").resolve("song.mp3").toString();
    final var updated = service.updateRecord("5", update, targetPath);

    assertEquals("Title", updated.getTitle());
    assertEquals("Artist", updated.getArtist());
    assertEquals("Album", updated.getAlbum());
    assertEquals("2025", updated.getYear());
    assertEquals("Rock", updated.getGenre());
    assertEquals("note", updated.getCommentText());
    assertEquals("Album Artist", updated.getAlbumArtist());
    assertEquals("Composer", updated.getComposer());
    assertEquals("Lyrics", updated.getLyrics());
    assertEquals("{\"x\":\"y\"}", updated.getExtraMetadataJson());
    assertEquals(Path.of(targetPath).toAbsolutePath().normalize().toString(), updated.getCurrentPath());
  }

  @Test
  void updateRecordRejectsUnknownRecord()
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    when(repository.findById("x")).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.updateRecord("x", new Mp3FileRecord(), ""));
  }

  @Test
  void updateRecordLeavesPathUntouchedWhenRequestedPathBlank() throws Exception
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    final var record = new Mp3FileRecord();
    record.setId("6");
    final var currentPath = tempDir.resolve("current.mp3").toString();
    record.setCurrentPath(currentPath);
    when(repository.findById("6")).thenReturn(Optional.of(record));
    when(repository.save(any(Mp3FileRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

    final var update = new Mp3FileRecord();
    update.setTitle("T");
    update.setArtist("A");
    update.setAlbum("AL");
    update.setTrack(1);
    update.setYear("2020");
    update.setGenre("G");
    update.setCommentText("C");
    update.setAlbumArtist("AA");
    update.setComposer("COMP");
    update.setLyrics("LYR");
    update.setExtraMetadataJson("{}");

    final var updated = service.updateRecord("6", update, "  ");

    assertEquals(Path.of(currentPath).toAbsolutePath().normalize().toString(), updated.getCurrentPath());
  }

  @Test
  void updateRecordLeavesPathUntouchedWhenRequestedPathMatchesCurrent() throws Exception
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    final var record = new Mp3FileRecord();
    record.setId("7");
    final var currentPath = tempDir.resolve("same.mp3").toAbsolutePath().normalize().toString();
    record.setCurrentPath(currentPath);
    when(repository.findById("7")).thenReturn(Optional.of(record));
    when(repository.save(any(Mp3FileRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

    final var update = new Mp3FileRecord();
    update.setTitle("T");
    update.setArtist("A");
    update.setAlbum("AL");
    update.setTrack(1);
    update.setYear("2020");
    update.setGenre("G");
    update.setCommentText("C");
    update.setAlbumArtist("AA");
    update.setComposer("COMP");
    update.setLyrics("LYR");
    update.setExtraMetadataJson("{}");

    final var updated = service.updateRecord("7", update, currentPath);

    assertEquals(currentPath, updated.getCurrentPath());
  }

  @Test
  void searchPagedNormalizesPagingAndSortingInputs()
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(java.util.List.of()));

    service.searchPaged("", "", "", "", -5, 999, "not-a-field", "desc");

    final var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findAll(any(org.springframework.data.jpa.domain.Specification.class), pageableCaptor.capture());
    final var pageable = pageableCaptor.getValue();
    assertEquals(0, pageable.getPageNumber());
    assertEquals(200, pageable.getPageSize());
    assertEquals("artist: DESC", pageable.getSort().toString());
  }

  @Test
  void searchPagedUsesAscendingWhenDirectionIsNotDesc()
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(java.util.List.of()));

    service.searchPaged("", "", "", "", 1, 10, "title", "ASC");

    final var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findAll(any(org.springframework.data.jpa.domain.Specification.class), pageableCaptor.capture());
    final var pageable = pageableCaptor.getValue();
    assertEquals(1, pageable.getPageNumber());
    assertEquals(10, pageable.getPageSize());
    assertEquals("title: ASC", pageable.getSort().toString());
  }

  @Test
  void scanDirectoryUpdatesExistingRecordWithoutResettingOriginalSnapshot() throws Exception
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    final var mp3 = tempDir.resolve("Existing.mp3");
    Files.write(mp3, new byte[]
    { 1, 2, 3, 4 });

    final var existing = new Mp3FileRecord();
    existing.setId("existing-id");
    existing.setOriginalPath("ORIGINAL");
    existing.setOriginalTitle("Original Title");
    when(repository.findByCurrentPath(anyString())).thenReturn(Optional.of(existing));
    when(repository.save(any(Mp3FileRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

    final var scanned = service.scanDirectory(tempDir.toString());

    assertEquals(1, scanned);
    final var recordCaptor = ArgumentCaptor.forClass(Mp3FileRecord.class);
    verify(repository).save(recordCaptor.capture());
    final var updated = recordCaptor.getValue();
    assertEquals("ORIGINAL", updated.getOriginalPath());
    assertEquals("Original Title", updated.getOriginalTitle());
    assertEquals("Existing", updated.getTitle());
  }

  @Test
  void searchDelegatesToRepositorySpecification()
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(java.util.List.of());

    final var result = service.search("A", "B", "C", "path");

    assertTrue(result.isEmpty());
    verify(repository).findAll(any(org.springframework.data.jpa.domain.Specification.class));
  }

  @Test
  void updateRecordReturnsIoErrorWhenTagWriteFailsForExistingFile() throws Exception
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    final var existingFile = tempDir.resolve("bad.mp3");
    Files.write(existingFile, new byte[]
    { 9, 8, 7 });

    final var record = new Mp3FileRecord();
    record.setId("9");
    record.setCurrentPath(existingFile.toString());
    when(repository.findById("9")).thenReturn(Optional.of(record));

    final var update = new Mp3FileRecord();
    update.setTitle("T");
    update.setArtist("A");
    update.setAlbum("AL");
    update.setTrack(1);
    update.setYear("2020");
    update.setGenre("G");
    update.setCommentText("C");
    update.setAlbumArtist("AA");
    update.setComposer("COMP");
    update.setLyrics("LYR");
    update.setExtraMetadataJson("{}");

    final var ex = assertThrows(java.io.IOException.class, () -> service.updateRecord("9", update, ""));
    assertTrue(ex.getMessage().contains("Unable to write tags for file:"));
    verify(repository, never()).save(any(Mp3FileRecord.class));
  }

  @Test
  void updateRecordMovesExistingFileBeforeWritingTags() throws Exception
  {
    final var service = new Mp3LibraryService(repository, lookupService, new ObjectMapper());
    final var source = tempDir.resolve("source.mp3");
    final var target = tempDir.resolve("moved").resolve("target.mp3");
    Files.write(source, new byte[]
    { 9, 8, 7 });

    final var record = new Mp3FileRecord();
    record.setId("10");
    record.setCurrentPath(source.toString());
    when(repository.findById("10")).thenReturn(Optional.of(record));

    final var update = new Mp3FileRecord();
    update.setTitle("T");
    update.setArtist("A");
    update.setAlbum("AL");
    update.setTrack(1);
    update.setYear("2020");
    update.setGenre("G");
    update.setCommentText("C");
    update.setAlbumArtist("AA");
    update.setComposer("COMP");
    update.setLyrics("LYR");
    update.setExtraMetadataJson("{}");

    assertThrows(java.io.IOException.class, () -> service.updateRecord("10", update, target.toString()));
    assertFalse(Files.exists(source));
    assertTrue(Files.exists(target));
    verify(repository, never()).save(any(Mp3FileRecord.class));
  }
}
