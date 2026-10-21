package com.example.mp3library.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "mp3_file", indexes = {
    @Index(name = "idx_mp3_file_album", columnList = "album"),
    @Index(name = "idx_mp3_file_artist", columnList = "artist"),
    @Index(name = "idx_mp3_file_comment_text", columnList = "comment_text"),
    @Index(name = "idx_mp3_file_created_at", columnList = "created_at"),
    @Index(name = "idx_mp3_file_file_path_current", columnList = "file_path_current"),
    @Index(name = "idx_mp3_file_genre", columnList = "genre"),
    @Index(name = "idx_mp3_file_id", columnList = "id"),
    @Index(name = "idx_mp3_file_file_path_original", columnList = "file_path_original"),
    @Index(name = "idx_mp3_file_title", columnList = "title"),
    @Index(name = "idx_mp3_file_track", columnList = "track"),
    @Index(name = "idx_mp3_file_updated_at", columnList = "updated_at"),
    @Index(name = "idx_mp3_file_release_year", columnList = "release_year")
})
public class Mp3FileRecord
  {

    @Column(columnDefinition = "TEXT")
    private String album;
    @Column(columnDefinition = "TEXT")
    private String artist;
    @Column(columnDefinition = "TEXT")
    private String commentText;
    private Instant createdAt;
    @Column(name = "file_path_current", columnDefinition = "TEXT")
    private String currentPath;
    @Column(columnDefinition = "TEXT")
    private String genre;
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Column(name = "file_path_original", columnDefinition = "TEXT")
    private String originalPath;
    @Column(columnDefinition = "TEXT")
    private String title;
    @Column(columnDefinition = "TEXT")
    private String track;
    private Instant updatedAt;
    @Column(name = "release_year", columnDefinition = "TEXT")
    private String year;

    @PrePersist
    public void onCreate()
    {
      Instant now = Instant.now();
      this.createdAt = now;
      this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate()
    {
      this.updatedAt = Instant.now();
    }

  }
