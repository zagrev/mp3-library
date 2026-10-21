package com.example.mp3library.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.mp3library.model.Mp3FileRecord;
import com.example.mp3library.service.Mp3LibraryService;

@WebMvcTest(Mp3WebController.class)
class Mp3WebControllerTest
  {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Mp3LibraryService service;

    @TempDir
    Path tempDir;

    @Test
    void editFoundReturnsEditView() throws Exception
    {
      final var record = new Mp3FileRecord();
      when(service.findById("4L")).thenReturn(Optional.of(record));

      mockMvc.perform(get("/mp3/4L")).andExpect(status().isOk()).andExpect(view().name("edit"))
          .andExpect(model().attributeExists("record"));
    }

    @Test
    void editMissingRedirectsToIndexWithError() throws Exception
    {
      when(service.findById("404L")).thenReturn(Optional.empty());

      mockMvc.perform(get("/mp3/404L")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"))
          .andExpect(flash().attribute("error", "Record not found"));
    }

    @Test
    void indexShowsRecordsAndBrowseData() throws Exception
    {
      final var browseRoot = Files.createDirectories(tempDir.resolve("browse"));
      Files.createDirectories(browseRoot.resolve("ArtistA"));

      when(service.search("A", "B", "C", "D")).thenReturn(List.of(new Mp3FileRecord()));

      mockMvc
          .perform(get("/").param("artist", "A").param("album", "B").param("title", "C").param("path", "D")
              .param("browsePath", browseRoot.toString()))
          .andExpect(status().isOk()).andExpect(view().name("index"))
          .andExpect(model().attributeExists("records", "browsePath", "browseParentPath", "browseDirectories"));
    }

    @Test
    void lookupFailureAddsError() throws Exception
    {
      when(service.lookupAndApply("11L")).thenThrow(new IllegalArgumentException("MP3 record not found: 11"));

      mockMvc.perform(post("/mp3/11L/lookup")).andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/mp3/11")).andExpect(flash().attribute("error", "MP3 record not found: 11"));
    }

    @Test
    void lookupNotFoundAddsError() throws Exception
    {
      when(service.lookupAndApply("10L")).thenReturn(Optional.empty());

      mockMvc.perform(post("/mp3/10L/lookup")).andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/mp3/10")).andExpect(flash().attribute("error", "No metadata found from lookup."));
    }

    @Test
    void lookupSuccessAddsMessage() throws Exception
    {
      when(service.lookupAndApply("9L")).thenReturn(Optional.of(new Mp3FileRecord()));

      mockMvc.perform(post("/mp3/9L/lookup")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/mp3/9"))
          .andExpect(flash().attribute("message", "Lookup succeeded and metadata was applied."));
    }

    @Test
    void scanFailureRedirectsWithError() throws Exception
    {
      when(service.scanDirectory("C:\\bad")).thenThrow(new IllegalArgumentException("Directory does not exist"));

      mockMvc.perform(post("/scan").param("directoryPath", "C:\\bad")).andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/")).andExpect(flash().attribute("error", "Directory does not exist"));
    }

    @Test
    void scanSuccessRedirectsWithMessage() throws Exception
    {
      when(service.scanDirectory("C:\\music")).thenReturn(7);

      mockMvc.perform(post("/scan").param("directoryPath", "C:\\music")).andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/")).andExpect(flash().attribute("message", "Scanned 7 MP3 files from C:\\music"));
    }

    @Test
    void updateFailureAddsErrorFlash() throws Exception
    {
      when(service.updateRecord(eq("5L"), any(Mp3FileRecord.class), eq("C:\\locked.mp3")))
          .thenThrow(new IOException("Unable to write tags"));

      mockMvc
          .perform(post("/mp3/5L/update").param("title", "T").param("artist", "AR").param("album", "AL")
              .param("track", "1").param("year", "2026").param("genre", "Rock").param("commentText", "note")
              .param("currentPath", "C:\\locked.mp3"))
          .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/mp3/5"))
          .andExpect(flash().attribute("error", "Unable to write tags"));
    }

    @Test
    void updateSuccessPersistsAndRedirects() throws Exception
    {
      final var persisted = new Mp3FileRecord();
      when(service.updateRecord(eq("2L"), any(Mp3FileRecord.class), eq("C:\\new\\song.mp3"))).thenReturn(persisted);

      mockMvc
          .perform(post("/mp3/2/update").param("title", "T").param("artist", "AR").param("album", "AL")
              .param("track", "1").param("year", "2026").param("genre", "Rock").param("commentText", "note")
              .param("currentPath", "C:\\new\\song.mp3"))
          .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/mp3/2"))
          .andExpect(flash().attribute("message", "Record updated"));

      final ArgumentCaptor<Mp3FileRecord> captor = ArgumentCaptor.forClass(Mp3FileRecord.class);
      verify(service, times(1)).updateRecord(eq("2L"), captor.capture(), eq("C:\\new\\song.mp3"));
      assertEquals("T", captor.getValue().getTitle());
      assertEquals("AR", captor.getValue().getArtist());
      assertEquals("AL", captor.getValue().getAlbum());
    }
  }
