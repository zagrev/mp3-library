package com.example.mp3library.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Mp3FileRecordTest
{

  @Test
  void lifecycleMethodsPopulateAndRefreshTimestamps() throws Exception
  {
    final var record = new Mp3FileRecord();

    record.onCreate();
    final var createdAt = record.getCreatedAt();
    final var firstUpdatedAt = record.getUpdatedAt();

    Thread.sleep(2);
    record.onUpdate();

    assertNotNull(createdAt);
    assertNotNull(firstUpdatedAt);
    assertNotNull(record.getUpdatedAt());
    assertTrue(!record.getUpdatedAt().isBefore(firstUpdatedAt));
  }
}
