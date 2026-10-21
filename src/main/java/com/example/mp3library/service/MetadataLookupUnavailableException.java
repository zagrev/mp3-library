package com.example.mp3library.service;

public class MetadataLookupUnavailableException extends RuntimeException
{
  public MetadataLookupUnavailableException(String message, Throwable cause)
  {
    super(message, cause);
  }
}
