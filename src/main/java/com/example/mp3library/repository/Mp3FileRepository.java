package com.example.mp3library.repository;

import com.example.mp3library.model.Mp3FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface Mp3FileRepository extends JpaRepository<Mp3FileRecord, Long>, JpaSpecificationExecutor<Mp3FileRecord> {

    Optional<Mp3FileRecord> findByCurrentPath(String currentPath);
}
