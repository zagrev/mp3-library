package com.example.mp3library.web;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.mp3library.model.Mp3FileRecord;
import com.example.mp3library.service.Mp3LibraryService;

@Controller
public class Mp3WebController
  {

    public record DirectoryOption(String name, String fullPath)
    {
    }

    private final Mp3LibraryService service;

    public Mp3WebController(Mp3LibraryService service)
    {
      this.service = service;
    }

    @GetMapping("/mp3/{id}")
    public String edit(@PathVariable String id, @RequestParam(defaultValue = "") String artist,
        @RequestParam(defaultValue = "") String album, @RequestParam(defaultValue = "") String title,
        @RequestParam(defaultValue = "") String path, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size, @RequestParam(defaultValue = "artist") String sort,
        @RequestParam(defaultValue = "asc") String dir, Model model, RedirectAttributes redirectAttributes)
    {
      model.addAttribute("artist", artist);
      model.addAttribute("album", album);
      model.addAttribute("title", title);
      model.addAttribute("path", path);
      model.addAttribute("page", page);
      model.addAttribute("size", size);
      model.addAttribute("sort", sort);
      model.addAttribute("dir", dir);
      model.addAttribute("backToCatalogUrl", buildCatalogUrl(artist, album, title, path, page, size, sort, dir));

      return service.findById(id).map(record ->
      {
        addLookupPreviewModel(model, record);
        model.addAttribute("record", record);
        return "edit";
      }).orElseGet(() ->
      {
        redirectAttributes.addFlashAttribute("error", "Record not found");
        return "redirect:/";
      });
    }

    @GetMapping("/")
    public String index(@RequestParam(defaultValue = "") String artist, @RequestParam(defaultValue = "") String album,
        @RequestParam(defaultValue = "") String title, @RequestParam(defaultValue = "") String path,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
        @RequestParam(defaultValue = "artist") String sort, @RequestParam(defaultValue = "asc") String dir,
        Model model)
    {
      final var pagedRecords = service.searchPaged(artist, album, title, path, page, size, sort, dir);

      model.addAttribute("records", pagedRecords.getContent());
      model.addAttribute("page", pagedRecords.getNumber());
      model.addAttribute("size", pagedRecords.getSize());
      model.addAttribute("totalPages", pagedRecords.getTotalPages());
      model.addAttribute("hasPrevious", pagedRecords.hasPrevious());
      model.addAttribute("hasNext", pagedRecords.hasNext());
      model.addAttribute("sort", sort);
      model.addAttribute("dir", dir);
      model.addAttribute("artist", artist);
      model.addAttribute("album", album);
      model.addAttribute("title", title);
      model.addAttribute("path", path);
      return "index";
    }

    @GetMapping("/browse")
    public String browse(@RequestParam(defaultValue = "") String browsePath, Model model)
    {
      final var browseRoot = resolveBrowsePath(browsePath);
      model.addAttribute("browsePath", browseRoot.toString());
      model.addAttribute("browseParentPath", browseRoot.getParent() == null ? "" : browseRoot.getParent().toString());
      model.addAttribute("browseDirectories", listDirectories(browseRoot));
      return "browse";
    }

    private List<DirectoryOption> listDirectories(Path root)
    {
      try (var stream = Files.list(root))
      {
        return stream.filter(Files::isDirectory)
            .sorted(Comparator.comparing(
                path -> path.getFileName() == null ? path.toString() : path.getFileName().toString(),
                String.CASE_INSENSITIVE_ORDER))
            .map(path -> new DirectoryOption(
                path.getFileName() == null ? path.toString() : path.getFileName().toString(), path.toString()))
            .toList();
      }
      catch (final IOException ex)
      {
        return List.of();
      }
    }

    @PostMapping("/mp3/{id}/lookup")
    public String lookup(@PathVariable String id, @RequestParam(defaultValue = "") String artist,
        @RequestParam(defaultValue = "") String album, @RequestParam(defaultValue = "") String title,
        @RequestParam(defaultValue = "") String path, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size, @RequestParam(defaultValue = "artist") String sort,
        @RequestParam(defaultValue = "asc") String dir, RedirectAttributes redirectAttributes)
    {
      try
      {
        final var preview = service.lookupPreview(id);
        if (preview.isPresent())
        {
          final var metadata = preview.get();
          redirectAttributes.addFlashAttribute("lookupTitle", metadata.title());
          redirectAttributes.addFlashAttribute("lookupArtist", metadata.artist());
          redirectAttributes.addFlashAttribute("lookupAlbum", metadata.album());
          redirectAttributes.addFlashAttribute("lookupTrack", metadata.track() == null || metadata.track() <= 0 ? ""
              : metadata.track().toString());
          redirectAttributes.addFlashAttribute("lookupYear", metadata.year());
          redirectAttributes.addFlashAttribute("lookupGenre", metadata.genre());
          redirectAttributes.addFlashAttribute("lookupCommentText", metadata.commentText());
          redirectAttributes.addFlashAttribute("message", "Lookup completed. Review highlighted changes, then save.");
        }
        else
        {
          redirectAttributes.addFlashAttribute("error", "No metadata found from lookup.");
        }
      }
      catch (IllegalArgumentException ex)
      {
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
      }
      return "redirect:/mp3/" + id + buildEditQuery(artist, album, title, path, page, size, sort, dir);
    }

    private void addLookupPreviewModel(Model model, Mp3FileRecord record)
    {
      final var lookupTitle = stringValue(model.asMap().get("lookupTitle"));
      final var lookupArtist = stringValue(model.asMap().get("lookupArtist"));
      final var lookupAlbum = stringValue(model.asMap().get("lookupAlbum"));
      final var lookupTrack = stringValue(model.asMap().get("lookupTrack"));
      final var lookupYear = stringValue(model.asMap().get("lookupYear"));
      final var lookupGenre = stringValue(model.asMap().get("lookupGenre"));
      final var lookupCommentText = stringValue(model.asMap().get("lookupCommentText"));

      model.addAttribute("lookupTitle", lookupTitle);
      model.addAttribute("lookupArtist", lookupArtist);
      model.addAttribute("lookupAlbum", lookupAlbum);
      model.addAttribute("lookupTrack", lookupTrack);
      model.addAttribute("lookupYear", lookupYear);
      model.addAttribute("lookupGenre", lookupGenre);
      model.addAttribute("lookupCommentText", lookupCommentText);

      model.addAttribute("titleChanged", isChanged(record.getTitle(), lookupTitle));
      model.addAttribute("artistChanged", isChanged(record.getArtist(), lookupArtist));
      model.addAttribute("albumChanged", isChanged(record.getAlbum(), lookupAlbum));
      model.addAttribute("trackChanged", isChanged(stringValue(record.getTrack()), lookupTrack));
      model.addAttribute("yearChanged", isChanged(record.getYear(), lookupYear));
      model.addAttribute("genreChanged", isChanged(record.getGenre(), lookupGenre));
      model.addAttribute("commentChanged", isChanged(record.getCommentText(), lookupCommentText));
    }

    private boolean isChanged(String original, String preview)
    {
      return !preview.isBlank() && !preview.equals(original == null ? "" : original);
    }

    private String stringValue(Object value)
    {
      return value == null ? "" : value.toString();
    }

    private Path resolveBrowsePath(String browsePath)
    {
      try
      {
        if (browsePath != null && !browsePath.isBlank())
        {
          final var requested = Paths.get(browsePath).toAbsolutePath().normalize();
          if (Files.exists(requested) && Files.isDirectory(requested))
          {
            return requested;
          }
        }
      }
      catch (final Exception ignored)
      {
        // Fall back to user home when the incoming path cannot be parsed.
      }

      return Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
    }

    @PostMapping("/scan")
    public String scan(@RequestParam String directoryPath,
        @RequestParam(name = "returnTo", defaultValue = "/") String returnTo,
        @RequestParam(name = "browsePath", defaultValue = "") String browsePath,
        RedirectAttributes redirectAttributes)
    {
      try
      {
        final var scanned = service.scanDirectory(directoryPath);
        redirectAttributes.addFlashAttribute("message", "Scanned " + scanned + " MP3 files from " + directoryPath);
      }
      catch (IllegalArgumentException | IOException ex)
      {
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
      }
      if ("/browse".equals(returnTo))
      {
        return "redirect:/browse" + (browsePath.isBlank() ? ""
            : "?browsePath=" + URLEncoder.encode(browsePath, StandardCharsets.UTF_8));
      }
      return "redirect:/";
    }

    @PostMapping("/mp3/{id}/update")
    public String update(@PathVariable String id, @RequestParam String title, @RequestParam String artist,
        @RequestParam String album, @RequestParam Integer track, @RequestParam String year, @RequestParam String genre,
        @RequestParam(name = "commentText") String commentText, @RequestParam(name = "currentPath") String currentPath,
        @RequestParam(name = "catalogArtist", defaultValue = "") String catalogArtist,
        @RequestParam(name = "catalogAlbum", defaultValue = "") String catalogAlbum,
        @RequestParam(name = "catalogTitle", defaultValue = "") String catalogTitle,
        @RequestParam(name = "catalogPath", defaultValue = "") String catalogPath,
        @RequestParam(name = "catalogPage", defaultValue = "0") int catalogPage,
        @RequestParam(name = "catalogSize", defaultValue = "25") int catalogSize,
        @RequestParam(name = "catalogSort", defaultValue = "artist") String catalogSort,
        @RequestParam(name = "catalogDir", defaultValue = "asc") String catalogDir,
        RedirectAttributes redirectAttributes)
    {
      try
      {
        final var update = new Mp3FileRecord();
        update.setTitle(title);
        update.setArtist(artist);
        update.setAlbum(album);
        update.setTrack(track);
        update.setYear(year);
        update.setGenre(genre);
        update.setCommentText(commentText);
        service.updateRecord(id, update, currentPath);
        redirectAttributes.addFlashAttribute("message", "Record updated");
      }
      catch (IllegalArgumentException | IOException ex)
      {
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
      }
      return "redirect:/mp3/" + id
          + buildEditQuery(catalogArtist, catalogAlbum, catalogTitle, catalogPath, catalogPage, catalogSize,
              catalogSort, catalogDir);
    }

    private String buildCatalogUrl(String artist, String album, String title, String path, int page, int size, String sort,
        String dir)
    {
      return "/" + buildEditQuery(artist, album, title, path, page, size, sort, dir);
    }

    private String buildEditQuery(String artist, String album, String title, String path, int page, int size, String sort,
        String dir)
    {
      return "?artist=" + encode(artist) + "&album=" + encode(album) + "&title=" + encode(title) + "&path="
          + encode(path) + "&page=" + page + "&size=" + size + "&sort=" + encode(sort) + "&dir=" + encode(dir);
    }

    private String encode(String value)
    {
      return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
  }
