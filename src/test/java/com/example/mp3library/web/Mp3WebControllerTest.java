package com.example.mp3library.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.mp3library.model.Mp3FileRecord;
import com.example.mp3library.service.MetadataSnapshot;
import com.example.mp3library.service.Mp3LibraryService;

@WebMvcTest(Mp3WebController.class)
class Mp3WebControllerTest
  {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Mp3LibraryService service;

    @TempDir
    Path tempDir;

    @Test
    void browsePageShowsDirectoryData() throws Exception
    {
      final var browseRoot = Files.createDirectories(tempDir.resolve("browse"));
      Files.createDirectories(browseRoot.resolve("ArtistA"));

      mockMvc.perform(get("/browse").param("browsePath", browseRoot.toString())).andExpect(status().isOk())
          .andExpect(view().name("browse"))
          .andExpect(model().attributeExists("browsePath", "browseParentPath", "browseDirectories"))
          .andExpect(content().string(containsString("/css/app.css")));
    }

    @Test
    void editCarriesBackToCatalogUrl() throws Exception
    {
      final var record = new Mp3FileRecord();
      when(service.findById("4")).thenReturn(Optional.of(record));

      mockMvc
          .perform(get("/mp3/4").param("artist", "AA").param("album", "BB").param("title", "CC").param("path", "DD")
              .param("page", "2").param("size", "10").param("sort", "title").param("dir", "desc"))
          .andExpect(status().isOk()).andExpect(view().name("edit")).andExpect(model().attribute("backToCatalogUrl",
              "/?artist=AA&album=BB&title=CC&path=DD&page=2&size=10&sort=title&dir=desc"));
    }

    @Test
    void editFoundReturnsEditView() throws Exception
    {
      final var record = new Mp3FileRecord();
      record.setTitle("Old Title");
      when(service.findById("4")).thenReturn(Optional.of(record));

      mockMvc.perform(get("/mp3/4")).andExpect(status().isOk()).andExpect(view().name("edit"))
          .andExpect(model().attributeExists("record")).andExpect(content().string(containsString("/css/app.css")));
    }

    @Test
    void editMissingRedirectsToIndexWithError() throws Exception
    {
      when(service.findById("404")).thenReturn(Optional.empty());

      mockMvc.perform(get("/mp3/404")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"))
          .andExpect(flash().attribute("error", "Record not found"));
    }

    @Test
    void editShowsLookupPreviewChanges() throws Exception
    {
      final var record = new Mp3FileRecord();
      record.setTitle("Old Title");
      when(service.findById("4")).thenReturn(Optional.of(record));

      mockMvc.perform(get("/mp3/4").flashAttr("lookupTitle", "New Title")).andExpect(status().isOk())
          .andExpect(view().name("edit")).andExpect(model().attribute("titleChanged", true));
    }

    @Test
    void indexHonorsPagingAndSortingParams() throws Exception
    {
      when(service.searchPaged(eq("AR"), eq("AL"), eq("TI"), eq("P"), eq(2), eq(10), eq("title"), eq("desc")))
          .thenReturn(new PageImpl<>(List.of(new Mp3FileRecord()), PageRequest.of(2, 10), 35));

      mockMvc
          .perform(get("/").param("artist", "AR").param("album", "AL").param("title", "TI").param("path", "P")
              .param("page", "2").param("size", "10").param("sort", "title").param("dir", "desc"))
          .andExpect(status().isOk()).andExpect(view().name("index")).andExpect(model().attribute("page", 2))
          .andExpect(model().attribute("size", 10)).andExpect(model().attribute("totalPages", 4))
          .andExpect(model().attribute("hasPrevious", true)).andExpect(model().attribute("hasNext", true))
          .andExpect(model().attribute("sort", "title")).andExpect(model().attribute("dir", "desc"));
    }

    @Test
    void indexShowsRecords() throws Exception
    {
      when(service.searchPaged(eq("A"), eq("B"), eq("C"), eq("D"), anyInt(), anyInt(), anyString(), anyString()))
          .thenReturn(new PageImpl<>(List.of(new Mp3FileRecord()), PageRequest.of(0, 25), 1));

      mockMvc.perform(get("/").param("artist", "A").param("album", "B").param("title", "C").param("path", "D"))
          .andExpect(status().isOk()).andExpect(view().name("index"))
          .andExpect(model().attributeExists("records", "page", "size", "totalPages", "hasPrevious", "hasNext"))
          .andExpect(model().attribute("page", 0)).andExpect(model().attribute("size", 25))
          .andExpect(model().attribute("sort", "artist")).andExpect(model().attribute("dir", "asc"))
          .andExpect(content().string(containsString("/css/app.css")));

      verify(service, times(1)).searchPaged("A", "B", "C", "D", 0, 25, "artist", "asc");
    }

    @Test
    void lookupFailureAddsError() throws Exception
    {
      when(service.lookupPreview("11")).thenThrow(new IllegalArgumentException("MP3 record not found: 11"));

      mockMvc.perform(post("/mp3/11/lookup")).andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrlPattern("/mp3/11*"))
          .andExpect(flash().attribute("error", "MP3 record not found: 11"));
    }

    @Test
    void lookupNotFoundAddsError() throws Exception
    {
      when(service.lookupPreview("10")).thenReturn(Optional.empty());

      mockMvc.perform(post("/mp3/10/lookup")).andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrlPattern("/mp3/10*"))
          .andExpect(flash().attribute("error", "No metadata found from lookup."));
    }

    @Test
    void lookupSuccessAddsMessage() throws Exception
    {
      when(service.lookupPreview("9")).thenReturn(Optional.of(
          new MetadataSnapshot("Title", "Artist", "Album", 1, "2024", "Rock", "Comment", "", "", 0, 0, 0, "", "{}")));

      mockMvc.perform(post("/mp3/9/lookup")).andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrlPattern("/mp3/9*"))
          .andExpect(flash().attribute("message", "Lookup completed. Review highlighted changes, then save."))
          .andExpect(flash().attribute("lookupTitle", "Title")).andExpect(flash().attribute("lookupArtist", "Artist"))
          .andExpect(flash().attribute("lookupAlbum", "Album")).andExpect(flash().attribute("lookupTrack", "1"));
    }

    @Test
    void scanFailureRedirectsWithError() throws Exception
    {
      when(service.scanDirectory("C:\\bad")).thenThrow(new IllegalArgumentException("Directory does not exist"));

      mockMvc.perform(post("/scan").param("directoryPath", "C:\\bad")).andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/")).andExpect(flash().attribute("error", "Directory does not exist"));
    }

    @Test
    void scanFromBrowseRedirectsBackToBrowse() throws Exception
    {
      when(service.scanDirectory("C:\\music")).thenReturn(1);

      mockMvc
          .perform(post("/scan").param("directoryPath", "C:\\music").param("returnTo", "/browse").param("browsePath",
              "C:\\music"))
          .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/browse?browsePath=C%3A%5Cmusic"))
          .andExpect(flash().attribute("message", "Scanned 1 MP3 files from C:\\music"));
    }

    @Test
    void scanSelectedDirectoriesUsesRequestSelection() throws Exception
    {
      when(service.scanDirectory("C:\\music\\A")).thenReturn(2);
      when(service.scanDirectory("C:\\music\\B")).thenReturn(3);

      mockMvc
          .perform(post("/browse/scan-selected").param("browsePath", "C:\\music").param("selectedPaths", "C:\\music\\A",
              "C:\\music\\B"))
          .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/browse?browsePath=C%3A%5Cmusic"))
          .andExpect(flash().attribute("message", "Scanned 5 MP3 files from 2 selected directories."));

      verify(service, times(1)).scanDirectory("C:\\music\\A");
      verify(service, times(1)).scanDirectory("C:\\music\\B");
    }

    @Test
    void scanSelectedDirectoriesWithNoSelectionShowsError() throws Exception
    {
      mockMvc.perform(post("/browse/scan-selected").param("browsePath", "C:\\music"))
          .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/browse?browsePath=C%3A%5Cmusic"))
          .andExpect(flash().attribute("error", "No directories selected."));
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
      when(service.updateRecord(eq("5"), any(Mp3FileRecord.class), eq("C:\\locked.mp3")))
          .thenThrow(new IOException("Unable to write tags"));

      mockMvc
          .perform(post("/mp3/5/update").param("title", "T").param("artist", "AR").param("album", "AL")
              .param("track", "1").param("year", "2026").param("genre", "Rock").param("commentText", "note")
              .param("currentPath", "C:\\locked.mp3"))
          .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/mp3/5*"))
          .andExpect(flash().attribute("error", "Unable to write tags"));
    }

    @Test
    void updateSuccessPersistsAndRedirects() throws Exception
    {
      final var persisted = new Mp3FileRecord();
      when(service.updateRecord(eq("2"), any(Mp3FileRecord.class), eq("C:\\new\\song.mp3"))).thenReturn(persisted);

      mockMvc
          .perform(post("/mp3/2/update").param("title", "T").param("artist", "AR").param("album", "AL")
              .param("track", "1").param("year", "2026").param("genre", "Rock").param("commentText", "note")
              .param("currentPath", "C:\\new\\song.mp3"))
          .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/mp3/2*"))
          .andExpect(flash().attribute("message", "Record updated"));

      final ArgumentCaptor<Mp3FileRecord> captor = ArgumentCaptor.forClass(Mp3FileRecord.class);
      verify(service, times(1)).updateRecord(eq("2"), captor.capture(), eq("C:\\new\\song.mp3"));
      assertEquals("T", captor.getValue().getTitle());
      assertEquals("AR", captor.getValue().getArtist());
      assertEquals("AL", captor.getValue().getAlbum());
    }
  }
