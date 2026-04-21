package com.scai.customer_portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ClaudeService {

    private static final String API_KEY = "YOUR_CLAUDE_API_KEY";
    private static final String URL = "https://api.anthropic.com/v1/messages";

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public String askClaude(String userMessage) throws Exception {

        // ✅ Build JSON safely
        Map<String, Object> body = new HashMap<>();
        body.put("model", "claude-3-sonnet-20240229");
        body.put("max_tokens", 300);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userMessage));

        body.put("messages", messages);

        String json = mapper.writeValueAsString(body);

        Request request = new Request.Builder()
                .url(URL)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .addHeader("x-api-key", API_KEY)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .build();

        Response response = client.newCall(request).execute();

        String responseBody = response.body().string();

        if (!response.isSuccessful()) {
            throw new RuntimeException("Claude API error: " + responseBody);
        }

        // ✅ Extract actual text
        JsonNode root = mapper.readTree(responseBody);

        return root
                .path("content")
                .get(0)
                .path("text")
                .asText();
    }
}

//chatgpt
//package com.scai.customer_portal.service;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import okhttp3.*;
//import org.springframework.stereotype.Service;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//@Service
//public class ClaudeService {
//

//    private static final String URL = "https://api.openai.com/v1/chat/completions";
//
//    private final OkHttpClient client = new OkHttpClient();
//
//    public String askAI(String userMessage) throws Exception {
//
//        ObjectMapper mapper = new ObjectMapper();
//
//        Map<String, Object> body = new HashMap<>();
//        body.put("model", "gpt-4o-mini");
//
//        List<Map<String, String>> messages = new ArrayList<>();
//        messages.add(Map.of("role", "user", "content", userMessage));
//
//        body.put("messages", messages);
//
//        String json = mapper.writeValueAsString(body); // ✅ SAFE JSON
//
//        Request request = new Request.Builder()
//                .url(URL)
//                .post(RequestBody.create(json, MediaType.parse("application/json")))
//                .addHeader("Authorization", "Bearer " + API_KEY)
//                .addHeader("Content-Type", "application/json")
//                .build();
//
//        Response response = client.newCall(request).execute();
//
//        String responseBody = response.body().string();
//
//        if (!response.isSuccessful()) {
//            throw new RuntimeException("API error: " + responseBody);
//        }
//
//        return responseBody;
//    }
//}