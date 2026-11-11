package com.studymate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void registersAuthenticatesAndPersistsStudyData() throws Exception {
        mvc.perform(get("/api/subjects"))
                .andExpect(status().isUnauthorized());

        String email = "student-" + UUID.randomUUID() + "@example.test";
        MvcResult registration = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test Student","email":"%s","password":"SecurePass123!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        JsonNode auth = objectMapper.readTree(registration.getResponse().getContentAsString());
        String token = auth.path("token").asText();
        String bearer = "Bearer " + token;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> profile = restTemplate.exchange(
                "http://127.0.0.1:" + port + "/api/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        assertEquals(200, profile.getStatusCode().value());
        assertEquals(email, objectMapper.readTree(profile.getBody()).path("email").asText());

        String subjectBody = """
                {"name":"Biology","description":"Cell structures","color":"#88a891"}
                """;
        MvcResult subjectResult = mvc.perform(post("/api/subjects")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subjectBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Biology"))
                .andReturn();
        long subjectId = objectMapper.readTree(subjectResult.getResponse().getContentAsString())
                .path("id").asLong();

        mvc.perform(post("/api/tasks")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Review cells","details":"Chapter 2","subjectId":%d,"priority":"HIGH","status":"TODO"}
                                """.formatted(subjectId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjectName").value("Biology"));

        mvc.perform(post("/api/notes")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Cell notes","content":"Mitochondria produce energy.","subjectId":%d}
                                """.formatted(subjectId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjectName").value("Biology"));

        mvc.perform(get("/api/dashboard").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(1))
                .andExpect(jsonPath("$.subjectCount").value(1));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"SecurePass123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
