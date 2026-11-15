package com.example.qsale.auth;

import com.example.qsale.AbstractContainerBaseTest;
import com.example.qsale.auth.infrastructure.AuthSessionRepository;
import com.example.qsale.notification.domain.EmailService;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SessionRevocationIntegrationTest extends AbstractContainerBaseTest {
    @Autowired MockMvc mvc;
    @Autowired AuthSessionRepository sessions;
    @MockitoBean EmailService email;

    @Test void logoutRevokesAccessAndRefreshWithoutAffectingAnotherLogin() throws Exception {
        String address = UUID.randomUUID() + "@qsale.test";
        Tokens first = signup(address);
        Tokens other = tokens(mvc.perform(post("/api/v1/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"SessionTest2026\"}".formatted(address)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content(body(first.refresh()))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + first.access()))
                .andExpect(status().isUnauthorized());
        refresh(first.refresh(), 401);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + other.access()))
                .andExpect(status().isOk());
        refresh(other.refresh(), 200);
    }

    @Test void rotatingARefreshIsUniqueAndReplayRevokesItsSession() throws Exception {
        Tokens first = signup(UUID.randomUUID() + "@qsale.test");
        String response = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(body(first.refresh()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Tokens rotated = tokens(response);
        assertNotEquals(first.refresh(), rotated.refresh());
        assertTrue(sessions.findAll().stream().noneMatch(session -> session.getRefreshHash().equals(first.refresh())));
        refresh(first.refresh(), 401);
        refresh(rotated.refresh(), 401);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + rotated.access()))
                .andExpect(status().isUnauthorized());
    }

    @Test void logoutIsIdempotentAndDoesNotAcceptAnAccessTokenAsRefresh() throws Exception {
        Tokens first = signup(UUID.randomUUID() + "@qsale.test");
        for (int attempt = 0; attempt < 2; attempt++)
            mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                    .content(body(first.access()))).andExpect(status().isNoContent());
        refresh(first.refresh(), 200);
    }

    private Tokens signup(String address) throws Exception {
        return tokens(mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Session Test\",\"email\":\"%s\",\"password\":\"SessionTest2026\"}".formatted(address)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void refresh(String token, int expected) throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(token)))
                .andExpect(status().is(expected));
    }

    private String body(String token) { return "{\"refreshToken\":\"" + token + "\"}"; }
    private Tokens tokens(String response) { return new Tokens(JsonPath.read(response, "$.token"), JsonPath.read(response, "$.refreshToken")); }
    private record Tokens(String access, String refresh) {}
}
