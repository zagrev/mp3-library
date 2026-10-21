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
    private String album;
    @Column(name = "album_artist")
    private String albumArtist;
    private String artist;
    @Column(length = 1000000)
    private String commentText;
    private Instant createdAt;
    @Column(name = "file_path_current", length = 1000000)
    private String currentPath;
    private String genre;
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Column(name = "file_path_original", length = 1000000)
    private String originalPath;
    @Column(name = "original_title")
    private String originalTitle;
    @Column(name = "original_artist")
    private String originalArtist;
    @Column(name = "original_album")
    private String originalAlbum;
    @Column(name = "original_album_artist")
    private String originalAlbumArtist;
    @Column(name = "original_track")
    private Integer originalTrack;
    @Column(name = "original_track_total")
    private Integer originalTrackTotal;
    @Column(name = "original_disc_number")
    private Integer originalDiscNumber;
    @Column(name = "original_disc_total")
    private Integer originalDiscTotal;
    @Column(name = "original_composer")
    private String originalComposer;
    @Column(name = "original_lyrics", length = 1000000)
    private String originalLyrics;
    @Column(name = "original_release_year")
    private String originalYear;
    @Column(name = "original_genre")
    private String originalGenre;
    @Column(name = "original_comment_text", length = 1000000)
    private String originalCommentText;
    @Column(name = "original_extra_metadata_json", length = 1000000)
    private String originalExtraMetadataJson;
    private String title;
    private Integer track;
    @Column(name = "track_total")
    private Integer trackTotal;
    @Column(name = "disc_number")
    private Integer discNumber;
    @Column(name = "disc_total")
    private Integer discTotal;
    private String composer;
    @Column(length = 1000000)
    private String lyrics;
    private Instant updatedAt;
    @Column(name = "release_year")
    private String year;
    @Column(name = "extra_metadata_json", length = 1000000)
    private String extraMetadataJson;

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
