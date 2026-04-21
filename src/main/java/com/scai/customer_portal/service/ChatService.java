package com.scai.customer_portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ClaudeService claudeService;
    private final IssueService issueService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public ChatService(ClaudeService claudeService,
                       IssueService issueService,
                       com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.claudeService = claudeService;
        this.issueService = issueService;
        this.objectMapper = objectMapper;
    }

    public String handleMessage(String message) throws Exception {

        String intentJson = getIntentFromAI(message);

        JsonNode node = objectMapper.readTree(intentJson);

        String intent = node.path("intent").asText();
        String ticketId = node.path("ticketId").asText(null);

        switch (intent) {

            case "ticket_status":
                return issueService.getTicketStatus(ticketId);

            case "completed_this_week":
                return issueService.getCompletedThisWeek();

            case "blocked_tickets":
                return issueService.getBlockedTickets();

            case "high_priority":
                return issueService.getHighPriorityIssues();

            default:
                return claudeService.askClaude(message);
        }
    }

    private String getIntentFromAI(String message) throws Exception {

        String prompt = """
        You are an AI assistant for Jira.

        Convert the user query into JSON.

        Possible intents:
        - ticket_status
        - completed_this_week
        - blocked_tickets
        - high_priority
        - general

        Rules:
        - Extract Jira ticket if present (format: DPAI-123)
        - If no ticket, keep ticketId null
        - Output ONLY JSON (no explanation)

        Example:
        User: What is status of DPAI-123?
        Output:
        {"intent":"ticket_status","ticketId":"DPAI-123"}

        Now process:
        """ + message;

        return claudeService.askClaude(prompt);
    }
}