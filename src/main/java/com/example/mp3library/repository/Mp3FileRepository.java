package com.example.mp3library.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.mp3library.model.Mp3FileRecord;

public interface Mp3FileRepository extends JpaRepository<Mp3FileRecord, String>, JpaSpecificationExecutor<Mp3FileRecord>
  {
    Optional<Mp3FileRecord> findByCurrentPath(String currentPath);
  }
