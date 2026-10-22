package com.example.mp3library.service.musicbrainz;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MusicBrainzLookupResponse(List<Recording> recordings)
{
  public record Recording(String title, @JsonProperty("artist-credit") List<ArtistCredit> artistCredit,
      List<Release> releases)
  {
  }

  public record ArtistCredit(String name)
  {
  }

  public record Release(String title)
  {
  }
}
