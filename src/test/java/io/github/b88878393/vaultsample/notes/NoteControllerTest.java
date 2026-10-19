package io.github.b88878393.vaultsample.notes;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.b88878393.vaultsample.notes.NoteService.NoteView;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web slice test. Vault is switched off and the (then unresolvable) {@code vault://} import is ignored, so no
 * Vault server is needed.
 */
@WebMvcTest(controllers = NoteController.class, properties = {
        "spring.cloud.vault.enabled=false",
        "spring.config.on-not-found=ignore"
})
class NoteControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NoteService noteService;

    @Test
    void createsNote() throws Exception {
        when(noteService.create("title", "content"))
                .thenReturn(new NoteView(1L, "title", "content", 1, Instant.parse("2026-01-01T00:00:00Z")));

        mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"title\",\"content\":\"content\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/notes/1"))
                .andExpect(jsonPath("$.keyVersion").value(1));
    }

    @Test
    void validatesInput() throws Exception {
        mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsTooLongContentToBadRequest() throws Exception {
        when(noteService.create(anyString(), anyString())).thenThrow(new IllegalArgumentException("too long"));

        mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("too long"));
    }

    @Test
    void mapsMissingNoteToNotFound() throws Exception {
        when(noteService.get(42L)).thenThrow(new NoteNotFoundException(42L));

        mvc.perform(get("/api/notes/42")).andExpect(status().isNotFound());
    }
}
