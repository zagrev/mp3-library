package com.example.mp3library.web;

import com.example.mp3library.model.Mp3FileRecord;
import com.example.mp3library.service.Mp3LibraryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
public class Mp3WebController {

    private final Mp3LibraryService service;

    public Mp3WebController(Mp3LibraryService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String index(
            @RequestParam(defaultValue = "") String artist,
            @RequestParam(defaultValue = "") String album,
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "") String path,
            Model model
    ) {
        List<Mp3FileRecord> records = service.search(artist, album, title, path);
        model.addAttribute("records", records);
        model.addAttribute("artist", artist);
        model.addAttribute("album", album);
        model.addAttribute("title", title);
        model.addAttribute("path", path);
        return "index";
    }

    @PostMapping("/scan")
    public String scan(@RequestParam String directoryPath, RedirectAttributes redirectAttributes) {
        try {
            int scanned = service.scanDirectory(directoryPath);
            redirectAttributes.addFlashAttribute("message", "Scanned " + scanned + " MP3 files from " + directoryPath);
        } catch (IllegalArgumentException | IOException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/mp3/{id}")
    public String edit(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return service.findById(id)
                .map(record -> {
                    model.addAttribute("record", record);
                    return "edit";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Record not found");
                    return "redirect:/";
                });
    }

    @PostMapping("/mp3/{id}/update")
    public String update(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam String artist,
            @RequestParam String album,
            @RequestParam String track,
            @RequestParam String year,
            @RequestParam String genre,
            @RequestParam(name = "commentText") String commentText,
            @RequestParam(name = "currentPath") String currentPath,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Mp3FileRecord update = new Mp3FileRecord();
            update.setTitle(title);
            update.setArtist(artist);
            update.setAlbum(album);
            update.setTrack(track);
            update.setYear(year);
            update.setGenre(genre);
            update.setCommentText(commentText);
            service.updateRecord(id, update, currentPath);
            redirectAttributes.addFlashAttribute("message", "Record updated");
        } catch (IllegalArgumentException | IOException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/mp3/" + id;
    }

    @PostMapping("/mp3/{id}/lookup")
    public String lookup(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            boolean updated = service.lookupAndApply(id).isPresent();
            if (updated) {
                redirectAttributes.addFlashAttribute("message", "Lookup succeeded and metadata was applied.");
            } else {
                redirectAttributes.addFlashAttribute("error", "No metadata found from lookup.");
            }
        } catch (IllegalArgumentException | IOException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/mp3/" + id;
    }
}
