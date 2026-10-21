package com.example.mp3library.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.mp3library.model.Mp3FileRecord;
import com.example.mp3library.repository.Mp3FileRepository;

import jakarta.persistence.criteria.Predicate;

@Service
public class Mp3LibraryService
  {

    private final MusicBrainzLookupService lookupService;
    private final Mp3FileRepository repository;

    public Mp3LibraryService(Mp3FileRepository repository, MusicBrainzLookupService lookupService)
    {
      this.repository    = repository;
      this.lookupService = lookupService;
    }

    private String clean(String value)
    {
      return value == null ? "" : value.trim();
    }

    private MetadataSnapshot fallbackMetadata(Path filePath)
    {
      final var fileName = filePath.getFileName().toString();
      final var fallbackTitle = fileName.toLowerCase().endsWith(".mp3") ? fileName.substring(0, fileName.length() - 4)
          : fileName;
      return new MetadataSnapshot(fallbackTitle, "", "", 0, "", "", "");
    }

    @Transactional(readOnly = true)
    public Optional<Mp3FileRecord> findById(String id)
    {
      return repository.findById(id);
    }

    @Transactional
    public Optional<Mp3FileRecord> lookupAndApply(String id) throws IOException
    {
      final var record = repository.findById(id)
          .orElseThrow(() -> new IllegalArgumentException("MP3 record not found: " + id));

      final var snapshot = lookupService.lookup(record.getTitle(), record.getArtist(), record.getAlbum());
      if (snapshot.isEmpty())
      {
        return Optional.empty();
      }

      final var metadata = snapshot.get();
      if (StringUtils.hasText(metadata.title()))
      {
        record.setTitle(metadata.title());
      }
      if (StringUtils.hasText(metadata.artist()))
      {
        record.setArtist(metadata.artist());
      }
      if (StringUtils.hasText(metadata.album()))
      {
        record.setAlbum(metadata.album());
      }
      record.setTrack(metadata.track());
      if (StringUtils.hasText(metadata.year()))
      {
        record.setYear(metadata.year());
      }
      if (StringUtils.hasText(metadata.genre()))
      {
        record.setGenre(metadata.genre());
      }
      if (StringUtils.hasText(metadata.commentText()))
      {
        record.setCommentText(metadata.commentText());
      }

      writeTagsIfPossible(record);
      return Optional.of(repository.save(record));
    }

    private void moveIfPossible(Path source, Path target) throws IOException
    {
      if (!Files.exists(source))
      {
        return;
      }
      if (target.getParent() != null)
      {
        Files.createDirectories(target.getParent());
      }
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private MetadataSnapshot readMetadata(Path filePath)
    {
      try
      {
        final var audioFile = AudioFileIO.read(filePath.toFile());
        final var tag = audioFile.getTag();
        if (tag == null)
        {
          return fallbackMetadata(filePath);
        }
        return new MetadataSnapshot(readTag(tag, FieldKey.TITLE), readTag(tag, FieldKey.ARTIST),
            readTag(tag, FieldKey.ALBUM), Integer.decode(readTag(tag, FieldKey.TRACK)), readTag(tag, FieldKey.YEAR),
            readTag(tag, FieldKey.GENRE), readTag(tag, FieldKey.COMMENT));
      }
      catch (final Exception ex)
      {
        return fallbackMetadata(filePath);
      }
    }

    private String readTag(Tag tag, FieldKey key)
    {
      try
      {
        return clean(tag.getFirst(key));
      }
      catch (final Exception ex)
      {
        return "";
      }
    }

    private void saveOrUpdateFromFile(Path filePath)
    {
      final var normalizedPath = filePath.toAbsolutePath().normalize().toString();
      final var record = repository.findByCurrentPath(normalizedPath).orElseGet(Mp3FileRecord::new);

      if (!StringUtils.hasText(record.getOriginalPath()))
      {
        record.setOriginalPath(normalizedPath);
      }
      record.setCurrentPath(normalizedPath);

      final var metadata = readMetadata(filePath);
      record.setTitle(metadata.title());
      record.setArtist(metadata.artist());
      record.setAlbum(metadata.album());
      record.setTrack(metadata.track());
      record.setYear(metadata.year());
      record.setGenre(metadata.genre());
      record.setCommentText(metadata.commentText());

      repository.save(record);
    }

    @Transactional
    public int scanDirectory(String directoryPath) throws IOException
    {
      final var root = Paths.get(directoryPath).toAbsolutePath().normalize();
      if (!Files.exists(root) || !Files.isDirectory(root))
      {
        throw new IllegalArgumentException("Directory does not exist: " + root);
      }

      final int[] processed =
      { 0 };
      try (var stream = Files.walk(root))
      {
        stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().toLowerCase().endsWith(".mp3"))
            .forEach(path ->
            {
              saveOrUpdateFromFile(path);
              processed[0]++;
            });
      }
      return processed[0];
    }

    @Transactional(readOnly = true)
    public List<Mp3FileRecord> search(String artist, String album, String title, String pathContains)
    {
      final Specification<Mp3FileRecord> spec = (root, query, cb) ->
      {
        final List<Predicate> predicates = new ArrayList<>();
        if (StringUtils.hasText(artist))
        {
          predicates.add(cb.like(root.get("artist"), "%" + artist + "%"));
        }
        if (StringUtils.hasText(album))
        {
          predicates.add(cb.like(root.get("album"), "%" + album + "%"));
        }
        if (StringUtils.hasText(title))
        {
          predicates.add(cb.like(root.get("title"), "%" + title + "%"));
        }
        if (StringUtils.hasText(pathContains))
        {
          final var matcher = "%" + pathContains + "%";
          predicates.add(cb.or(cb.like(root.get("originalPath"), matcher), cb.like(root.get("currentPath"), matcher)));
        }
        return cb.and(predicates.toArray(Predicate[]::new));
      };

      return repository.findAll(spec);
    }

    private void setTag(Tag tag, FieldKey key, String value)
    {
      try
      {
        tag.setField(key, clean(value));
      }
      catch (final Exception ignored)
      {
        // Keep updates resilient across different tag versions.
      }
    }

    @Transactional
    public Mp3FileRecord updateRecord(String id, Mp3FileRecord update, String requestedPath) throws IOException
    {
      final var record = repository.findById(id)
          .orElseThrow(() -> new IllegalArgumentException("MP3 record not found: " + id));

      record.setTitle(clean(update.getTitle()));
      record.setArtist(clean(update.getArtist()));
      record.setAlbum(clean(update.getAlbum()));
      record.setTrack(update.getTrack());
      record.setYear(clean(update.getYear()));
      record.setGenre(clean(update.getGenre()));
      record.setCommentText(clean(update.getCommentText()));

      if (StringUtils.hasText(requestedPath))
      {
        final var current = Paths.get(record.getCurrentPath());
        final var target = Paths.get(requestedPath).toAbsolutePath().normalize();
        if (!current.equals(target))
        {
          moveIfPossible(current, target);
          record.setCurrentPath(target.toString());
        }
      }

      writeTagsIfPossible(record);
      return repository.save(record);
    }

    private void writeTagsIfPossible(Mp3FileRecord record) throws IOException
    {
      final var path = Paths.get(record.getCurrentPath());
      if (!Files.exists(path))
      {
        return;
      }
      try
      {
        final var audioFile = AudioFileIO.read(path.toFile());
        final var tag = audioFile.getTagOrCreateAndSetDefault();
        setTag(tag, FieldKey.TITLE, record.getTitle());
        setTag(tag, FieldKey.ARTIST, record.getArtist());
        setTag(tag, FieldKey.ALBUM, record.getAlbum());
        setTag(tag, FieldKey.TRACK, record.getTrack().toString());
        setTag(tag, FieldKey.YEAR, record.getYear());
        setTag(tag, FieldKey.GENRE, record.getGenre());
        setTag(tag, FieldKey.COMMENT, record.getCommentText());
        audioFile.commit();
      }
      catch (final Exception ex)
      {
        throw new IOException("Unable to write tags for file: " + record.getCurrentPath(), ex);
      }
    }
  }
