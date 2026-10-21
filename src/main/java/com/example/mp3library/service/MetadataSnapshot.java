package com.example.mp3library.service;

public record MetadataSnapshot(String title, String artist, String album, Integer track, String year, String genre,
    String commentText, String albumArtist, String composer, Integer discNumber, Integer discTotal,
    Integer trackTotal, String lyrics, String extraMetadataJson)
{
}
