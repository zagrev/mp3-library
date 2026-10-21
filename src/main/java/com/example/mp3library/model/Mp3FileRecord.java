package com.example.mp3library.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Data;

@Entity
@Data
public class Mp3FileRecord
  {

    private String album;
    private String artist;
    private String commentText;
    private Instant createdAt;
    @Column(name = "file_path_current")
    private String currentPath;
    private String genre;
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Column(name = "file_path_original")
    private String originalPath;
    private String title;
    private String track;
    private Instant updatedAt;
    @Column(name = "release_year")
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
