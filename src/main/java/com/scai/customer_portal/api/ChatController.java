package com.scai.customer_portal.api;

import com.scai.customer_portal.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/customerportal")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/chat")
    public String chat(@RequestBody Map<String, String> request) throws Exception {
        String message = request.get("message");
        return chatService.handleMessage(message);
    }
}