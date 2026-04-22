package com.scai.customer_portal.api;

import com.scai.customer_portal.api.dto.ChatRequest;
import com.scai.customer_portal.api.dto.ChatResponse;
import com.scai.customer_portal.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	@PostMapping
	public ChatResponse chat(@RequestBody ChatRequest request) {
		try {
			return chatService.handle(request);
		}
		catch (IllegalStateException e) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		}
		catch (Exception e) {
			throw new ResponseStatusException(
					HttpStatus.BAD_GATEWAY,
					"Assistant request failed: " + e.getMessage());
		}
	}
}
