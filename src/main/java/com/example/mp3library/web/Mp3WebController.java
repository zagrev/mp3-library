package com.example.mp3library.web;

import java.io.IOException;
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
    public String edit(@PathVariable String id, Model model, RedirectAttributes redirectAttributes)
    {
      return service.findById(id).map(record ->
      {
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
        @RequestParam(defaultValue = "") String browsePath, Model model)
    {
      final var records = service.search(artist, album, title, path);
      final var browseRoot = resolveBrowsePath(browsePath);

      model.addAttribute("browsePath", browseRoot.toString());
      model.addAttribute("browseParentPath", browseRoot.getParent() == null ? "" : browseRoot.getParent().toString());
      model.addAttribute("browseDirectories", listDirectories(browseRoot));
      model.addAttribute("records", records);
      model.addAttribute("artist", artist);
      model.addAttribute("album", album);
      model.addAttribute("title", title);
      model.addAttribute("path", path);
      return "index";
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
    public String lookup(@PathVariable String id, RedirectAttributes redirectAttributes)
    {
      try
      {
        final var updated = service.lookupAndApply(id).isPresent();
        if (updated)
        {
          redirectAttributes.addFlashAttribute("message", "Lookup succeeded and metadata was applied.");
        }
        else
        {
          redirectAttributes.addFlashAttribute("error", "No metadata found from lookup.");
        }
      }
      catch (IllegalArgumentException | IOException ex)
      {
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
      }
      return "redirect:/mp3/" + id;
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
    public String scan(@RequestParam String directoryPath, RedirectAttributes redirectAttributes)
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
      return "redirect:/";
    }

    @PostMapping("/mp3/{id}/update")
    public String update(@PathVariable String id, @RequestParam String title, @RequestParam String artist,
        @RequestParam String album, @RequestParam Integer track, @RequestParam String year, @RequestParam String genre,
        @RequestParam(name = "commentText") String commentText, @RequestParam(name = "currentPath") String currentPath,
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
      return "redirect:/mp3/" + id;
    }
  }
