package com.example.mp3library.service;

public record MetadataSnapshot(String title, String artist, String album, Integer track, String year, String genre,
    String commentText)
{
}
