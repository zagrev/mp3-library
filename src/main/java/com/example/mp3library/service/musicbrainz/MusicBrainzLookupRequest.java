package com.example.mp3library.service.musicbrainz;

import org.springframework.util.StringUtils;

public record MusicBrainzLookupRequest(String title, String artist, String album)
{
  public boolean hasAnyCriteria()
  {
    return StringUtils.hasText(title) || StringUtils.hasText(artist) || StringUtils.hasText(album);
  }

  public String toQuery()
  {
    final var sb = new StringBuilder();
    if (StringUtils.hasText(title))
    {
      sb.append("recording:").append(quoteTerm(title));
    }
    if (StringUtils.hasText(artist))
    {
      if (sb.length() > 0)
      {
        sb.append(" AND ");
      }
      sb.append("artist:").append(quoteTerm(artist));
    }
    if (StringUtils.hasText(album))
    {
      if (sb.length() > 0)
      {
        sb.append(" AND ");
      }
      sb.append("release:").append(quoteTerm(album));
    }
    return sb.toString();
  }

  private String quoteTerm(String input)
  {
    return "\"" + input.replace("\"", "") + "\"";
  }
}
