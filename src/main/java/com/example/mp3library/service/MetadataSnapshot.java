package com.example.mp3library.service;

public record MetadataSnapshot(
        String title,
        String artist,
        String album,
        String track,
        String year,
        String genre,
        String commentText
) {
}
