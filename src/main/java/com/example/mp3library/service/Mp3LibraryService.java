package com.example.mp3library.service;

import com.example.mp3library.model.Mp3FileRecord;
import com.example.mp3library.repository.Mp3FileRepository;
import jakarta.persistence.criteria.Predicate;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class Mp3LibraryService {

    private final Mp3FileRepository repository;
    private final MusicBrainzLookupService lookupService;

    public Mp3LibraryService(Mp3FileRepository repository, MusicBrainzLookupService lookupService) {
        this.repository = repository;
        this.lookupService = lookupService;
    }

    @Transactional
    public int scanDirectory(String directoryPath) throws IOException {
        Path root = Paths.get(directoryPath).toAbsolutePath().normalize();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("Directory does not exist: " + root);
        }

        int[] processed = new int[]{0};
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".mp3"))
                    .forEach(path -> {
                        saveOrUpdateFromFile(path);
                        processed[0]++;
                    });
        }
        return processed[0];
    }

    @Transactional(readOnly = true)
    public List<Mp3FileRecord> search(String artist, String album, String title, String pathContains) {
        Specification<Mp3FileRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(artist)) {
                predicates.add(cb.like(cb.lower(root.get("artist")), "%" + artist.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(album)) {
                predicates.add(cb.like(cb.lower(root.get("album")), "%" + album.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(title)) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(pathContains)) {
                String lowered = "%" + pathContains.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("originalPath")), lowered),
                        cb.like(cb.lower(root.get("currentPath")), lowered)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return repository.findAll(spec);
    }

    @Transactional(readOnly = true)
    public Optional<Mp3FileRecord> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public Mp3FileRecord updateRecord(Long id, Mp3FileRecord update, String requestedPath) throws IOException {
        Mp3FileRecord record = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MP3 record not found: " + id));

        record.setTitle(clean(update.getTitle()));
        record.setArtist(clean(update.getArtist()));
        record.setAlbum(clean(update.getAlbum()));
        record.setTrack(clean(update.getTrack()));
        record.setYear(clean(update.getYear()));
        record.setGenre(clean(update.getGenre()));
        record.setCommentText(clean(update.getCommentText()));

        if (StringUtils.hasText(requestedPath)) {
            Path current = Paths.get(record.getCurrentPath());
            Path target = Paths.get(requestedPath).toAbsolutePath().normalize();
            if (!current.equals(target)) {
                moveIfPossible(current, target);
                record.setCurrentPath(target.toString());
            }
        }

        writeTagsIfPossible(record);
        return repository.save(record);
    }

    @Transactional
    public Optional<Mp3FileRecord> lookupAndApply(Long id) throws IOException {
        Mp3FileRecord record = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MP3 record not found: " + id));

        Optional<MetadataSnapshot> snapshot = lookupService.lookup(record.getTitle(), record.getArtist(), record.getAlbum());
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }

        MetadataSnapshot metadata = snapshot.get();
        if (StringUtils.hasText(metadata.title())) {
            record.setTitle(metadata.title());
        }
        if (StringUtils.hasText(metadata.artist())) {
            record.setArtist(metadata.artist());
        }
        if (StringUtils.hasText(metadata.album())) {
            record.setAlbum(metadata.album());
        }
        if (StringUtils.hasText(metadata.track())) {
            record.setTrack(metadata.track());
        }
        if (StringUtils.hasText(metadata.year())) {
            record.setYear(metadata.year());
        }
        if (StringUtils.hasText(metadata.genre())) {
            record.setGenre(metadata.genre());
        }
        if (StringUtils.hasText(metadata.commentText())) {
            record.setCommentText(metadata.commentText());
        }

        writeTagsIfPossible(record);
        return Optional.of(repository.save(record));
    }

    private void saveOrUpdateFromFile(Path filePath) {
        String normalizedPath = filePath.toAbsolutePath().normalize().toString();
        Mp3FileRecord record = repository.findByCurrentPath(normalizedPath)
                .orElseGet(Mp3FileRecord::new);

        if (!StringUtils.hasText(record.getOriginalPath())) {
            record.setOriginalPath(normalizedPath);
        }
        record.setCurrentPath(normalizedPath);

        MetadataSnapshot metadata = readMetadata(filePath);
        record.setTitle(metadata.title());
        record.setArtist(metadata.artist());
        record.setAlbum(metadata.album());
        record.setTrack(metadata.track());
        record.setYear(metadata.year());
        record.setGenre(metadata.genre());
        record.setCommentText(metadata.commentText());

        repository.save(record);
    }

    private MetadataSnapshot readMetadata(Path filePath) {
        try {
            AudioFile audioFile = AudioFileIO.read(filePath.toFile());
            Tag tag = audioFile.getTag();
            if (tag == null) {
                return fallbackMetadata(filePath);
            }
            return new MetadataSnapshot(
                    readTag(tag, FieldKey.TITLE),
                    readTag(tag, FieldKey.ARTIST),
                    readTag(tag, FieldKey.ALBUM),
                    readTag(tag, FieldKey.TRACK),
                    readTag(tag, FieldKey.YEAR),
                    readTag(tag, FieldKey.GENRE),
                    readTag(tag, FieldKey.COMMENT)
            );
        } catch (Exception ex) {
            return fallbackMetadata(filePath);
        }
    }

    private MetadataSnapshot fallbackMetadata(Path filePath) {
        String fileName = filePath.getFileName().toString();
        String fallbackTitle = fileName.toLowerCase().endsWith(".mp3")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        return new MetadataSnapshot(fallbackTitle, "", "", "", "", "", "");
    }

    private String readTag(Tag tag, FieldKey key) {
        try {
            return clean(tag.getFirst(key));
        } catch (Exception ex) {
            return "";
        }
    }

    private void writeTagsIfPossible(Mp3FileRecord record) throws IOException {
        Path path = Paths.get(record.getCurrentPath());
        if (!Files.exists(path)) {
            return;
        }
        try {
            AudioFile audioFile = AudioFileIO.read(path.toFile());
            Tag tag = audioFile.getTagOrCreateAndSetDefault();
            setTag(tag, FieldKey.TITLE, record.getTitle());
            setTag(tag, FieldKey.ARTIST, record.getArtist());
            setTag(tag, FieldKey.ALBUM, record.getAlbum());
            setTag(tag, FieldKey.TRACK, record.getTrack());
            setTag(tag, FieldKey.YEAR, record.getYear());
            setTag(tag, FieldKey.GENRE, record.getGenre());
            setTag(tag, FieldKey.COMMENT, record.getCommentText());
            audioFile.commit();
        } catch (Exception ex) {
            throw new IOException("Unable to write tags for file: " + record.getCurrentPath(), ex);
        }
    }

    private void setTag(Tag tag, FieldKey key, String value) {
        try {
            tag.setField(key, clean(value));
        } catch (Exception ignored) {
            // Keep updates resilient across different tag versions.
        }
    }

    private void moveIfPossible(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
