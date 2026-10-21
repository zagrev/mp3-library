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
        assertEquals(songFile.toAbsolutePath().normalize().toString(), record.getCurrentPath());
        assertEquals(songFile.toAbsolutePath().normalize().toString(), record.getOriginalPath());

        List<Mp3FileRecord> byPath = service.search("", "", "", "Artist One");
        assertFalse(byPath.isEmpty());
    }
}
