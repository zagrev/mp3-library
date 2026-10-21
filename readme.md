# mp3-library

Spring Boot web app for scanning server directories with MP3 files, storing metadata, editing tags, moving files, and filtering/searching the catalog.

## Features

- Scan a server directory recursively for `.mp3` files
- Keep both `originalPath` (first discovered location) and `currentPath`
- Store metadata in a relational database (H2)
- View and filter catalog by artist, album, title, and file path
- Update metadata and write changes back to file tags
- Move files to corrected locations while preserving database tracking
- Lookup metadata from MusicBrainz (CDDB equivalent) and apply it

## Tech stack

- Java 17
- Spring Boot (Web, Thymeleaf, Spring Data JPA)
- H2 database
- `jaudiotagger` for reading/writing MP3 tags

## Run locally

```bash
mvn spring-boot:run
```

Open `http://localhost:8080`.

## Test

```bash
mvn test
```

## Notes

- MusicBrainz requests include a user-agent but may still be rate-limited.
- Empty/corrupt MP3 files are still indexed by file path; metadata fields may remain blank.
