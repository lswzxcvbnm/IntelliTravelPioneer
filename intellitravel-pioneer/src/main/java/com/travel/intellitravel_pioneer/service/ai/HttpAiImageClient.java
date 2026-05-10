package com.travel.intellitravel_pioneer.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class HttpAiImageClient implements AiImageClient {

    private final ObjectMapper objectMapper;

    @Value("${trip.ai.image.base-url}")
    private String baseUrl;

    @Value("${trip.ai.image.path}")
    private String imagePath;

    @Value("${trip.ai.image.api-key}")
    private String apiKey;

    @Value("${trip.ai.image.model}")
    private String defaultModel;

    @Value("${trip.ai.image.size}")
    private String defaultSize;

    public HttpAiImageClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> generateImages(String prompt, int count, String size) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IntellitravelException("AI API key is not configured");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", defaultModel);
        body.put("prompt", prompt);
        body.put("n", count > 0 ? count : 1);
        body.put("size", size != null && !size.isBlank() ? size : defaultSize);

        String raw;
        try {
            raw = RestClient.create()
                    .post()
                    .uri(baseUrl + imagePath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new IntellitravelException("Failed to call Image Generation API: " + e.getMessage());
        }

        return parseImageResponse(raw);
    }

    private List<String> parseImageResponse(String rawResponse) {
        List<String> urls = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            if (root.has("data") && root.get("data").isArray()) {
                for (JsonNode item : root.get("data")) {
                    if (item.has("url")) {
                        urls.add(item.get("url").asText());
                    }
                }
            }
            if (urls.isEmpty()) {
                 throw new IntellitravelException("AI response does not contain valid image URLs");
            }
        } catch (Exception e) {
            if (e instanceof IntellitravelException) {
                throw (IntellitravelException) e;
            }
            throw new IntellitravelException("Failed to parse AI image response: " + e.getMessage());
        }
        return urls;
    }
}
