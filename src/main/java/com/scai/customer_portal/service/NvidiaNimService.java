package com.scai.customer_portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI-compatible chat completions against NVIDIA Build / NIM
 * ({@code https://integrate.api.nvidia.com/v1/chat/completions}).
 */
@Service
public class NvidiaNimService {

	private static final MediaType JSON = MediaType.parse("application/json");

	private final OkHttpClient client = new OkHttpClient.Builder()
			.callTimeout(120, TimeUnit.SECONDS)
			.readTimeout(120, TimeUnit.SECONDS)
			.build();
	private final ObjectMapper mapper = new ObjectMapper();
	private final String baseUrl;
	private final String apiKey;
	private final String model;

	public NvidiaNimService(
			@Value("${nvidia.api.base-url:https://integrate.api.nvidia.com/v1}") String baseUrl,
			@Value("${nvidia.api.key:}") String apiKey,
			@Value("${nvidia.api.model:meta/llama-3.1-8b-instruct}") String model) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.model = model;
	}

	public boolean isConfigured() {
		return !apiKey.isEmpty();
	}

	/**
	 * @param systemPrompt optional system message (may be null or blank)
	 * @param userMessage  user / combined prompt
	 */
	public String chatCompletion(String systemPrompt, String userMessage, int maxTokens, double temperature)
			throws Exception {
		if (!isConfigured()) {
			throw new IllegalStateException(
					"NVIDIA API key is not configured. Set environment variable NVIDIA_API_KEY or property nvidia.api.key.");
		}
		String url = baseUrl + "/chat/completions";
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("temperature", temperature);
		body.put("max_tokens", maxTokens);
		List<Map<String, String>> messages = new ArrayList<>();
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			messages.add(Map.of("role", "system", "content", systemPrompt));
		}
		messages.add(Map.of("role", "user", "content", userMessage));
		body.put("messages", messages);
		String json = mapper.writeValueAsString(body);
		Request request = new Request.Builder()
				.url(url)
				.post(RequestBody.create(json, JSON))
				.addHeader("Authorization", "Bearer " + apiKey)
				.addHeader("Content-Type", "application/json")
				.build();
		try (Response response = client.newCall(request).execute()) {
			String responseBody = response.body() != null ? response.body().string() : "";
			if (!response.isSuccessful()) {
				throw new IllegalStateException("NVIDIA chat API error HTTP " + response.code() + ": " + responseBody);
			}
			JsonNode root = mapper.readTree(responseBody);
			JsonNode choices = root.path("choices");
			if (!choices.isArray() || choices.isEmpty()) {
				throw new IllegalStateException("NVIDIA chat API returned no choices: " + responseBody);
			}
			return choices.get(0).path("message").path("content").asText("").trim();
		}
	}
}
