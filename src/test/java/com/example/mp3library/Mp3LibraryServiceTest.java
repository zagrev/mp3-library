package com.example.mp3library;

import com.example.mp3library.model.Mp3FileRecord;
import com.example.mp3library.repository.Mp3FileRepository;
import com.example.mp3library.service.Mp3LibraryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class Mp3LibraryServiceTest {

    @Autowired
    private Mp3LibraryService service;

    @Autowired
    private Mp3FileRepository repository;

    @TempDir
    Path tempDir;

    @Test
    void scanDirectoryIndexesMp3FilesAndSupportsFiltering() throws Exception {
        Path artistDir = Files.createDirectories(tempDir.resolve("Artist One"));
        Path songFile = artistDir.resolve("Track 01.mp3");
        Files.write(songFile, new byte[]{0, 1, 2});

        int scanned = service.scanDirectory(tempDir.toString());

        assertEquals(1, scanned);
        List<Mp3FileRecord> all = repository.findAll();
        assertEquals(1, all.size());

        Mp3FileRecord record = all.get(0);
        assertNotNull(record.getId());
        assertEquals(songFile.toAbsolutePath().normalize().toString(), record.getCurrentPath());
        assertEquals(songFile.toAbsolutePath().normalize().toString(), record.getOriginalPath());
        assertEquals(record.getTitle(), record.getOriginalTitle());
        assertEquals(record.getArtist(), record.getOriginalArtist());
        assertEquals(record.getAlbum(), record.getOriginalAlbum());
        assertEquals(record.getTrack(), record.getOriginalTrack());
        assertEquals(record.getYear(), record.getOriginalYear());
        assertEquals(record.getGenre(), record.getOriginalGenre());
        assertEquals(record.getCommentText(), record.getOriginalCommentText());

        List<Mp3FileRecord> byPath = service.search("", "", "", "Artist One");
        assertFalse(byPath.isEmpty());

        List<Mp3FileRecord> byPathLower = service.search("", "", "", "artist one");
        assertFalse(byPathLower.isEmpty());

        List<Mp3FileRecord> byTitleLower = service.search("", "", "track 01", "");
        assertFalse(byTitleLower.isEmpty());
    }
}
