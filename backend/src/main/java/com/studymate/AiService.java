package com.studymate;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AiService {
    private static final String SYSTEM_INSTRUCTION = "You are StudyMate, a supportive academic study assistant. "
            + "Explain clearly at the student's level, use concise headings and examples where useful, "
            + "and never claim certainty when the prompt is ambiguous. Return readable plain text or Markdown.";

    private final RestClient ollamaClient;
    private final RestClient geminiClient;
    private final String provider;
    private final String apiKey;
    private final String model;
    private final String geminiModel;

    public AiService(RestClient.Builder builder,
                     @Value("${app.ai.provider}") String provider,
                     @Value("${app.ai.base-url}") String baseUrl,
                     @Value("${app.ai.api-key}") String apiKey,
                     @Value("${app.ai.model}") String model,
                     @Value("${app.ai.gemini-model}") String geminiModel) {
        this.ollamaClient = builder.baseUrl(baseUrl).build();
        this.geminiClient = builder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = model;
        this.geminiModel = geminiModel;
    }

    public String generate(String prompt, int maxTokens) {
        int boundedMaxTokens = Math.max(64, Math.min(maxTokens, 1800));
        if ("ollama".equalsIgnoreCase(provider)) return generateWithOllama(prompt, boundedMaxTokens);
        if ("gemini".equalsIgnoreCase(provider)) return generateWithGemini(prompt, boundedMaxTokens);
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_PROVIDER must be ollama or gemini");
    }

    private String generateWithOllama(String prompt, int maxTokens) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_INSTRUCTION),
                        Map.of("role", "user", "content", prompt)),
                "stream", false,
                "temperature", 0.55,
                "max_tokens", maxTokens);
        try {
            JsonNode response = ollamaClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode text = response == null ? null : response.path("choices").path(0).path("message").path("content");
            if (text == null || text.isMissingNode() || text.asText().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The local AI model returned an empty response");
            }
            return text.asText();
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The local AI server is unavailable. Run setup-local-ai.ps1 and start-local.ps1 from the project folder.");
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The configured local AI model was not found. Run setup-local-ai.ps1 and start-local.ps1 from the project folder.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The local AI model could not complete this request. Check the Ollama container and model.");
        }
    }

    private String generateWithGemini(String prompt, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini is not configured. Add GEMINI_API_KEY to backend/.env or switch AI_PROVIDER to ollama.");
        }
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.55, "maxOutputTokens", maxTokens));
        try {
                JsonNode response = geminiClient.post()
                    .uri(builder -> builder.path("/v1beta/models/{model}:generateContent").build(geminiModel))
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode text = response == null ? null : response.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (text == null || text.isMissingNode() || text.asText().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI service returned an empty response");
            }
            return text.asText();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Gemini rejected the API key. Check that it is active and has access to the Generative Language API.");
            }
            if (status == 404) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "The configured Gemini model was not found. Check GEMINI_MODEL in backend/.env.");
            }
            if (status == 429) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Gemini rate limit or quota reached. Check the API project's quota and try again later.");
            }
            if (status == 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Gemini rejected the request. Check the configured model and try a shorter prompt.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gemini is temporarily unavailable. Try again shortly.");
        }
    }
}