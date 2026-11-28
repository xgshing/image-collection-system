package com.xgsheng.backend.controller;

import com.xgsheng.backend.service.MessageQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    private final MessageQueryService messageQueryService;

    public MessageController(MessageQueryService messageQueryService) {
        this.messageQueryService = messageQueryService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(value = "page", required = false) Integer page,
                                    @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return messageQueryService.list(page, pageSize);
    }

    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount() {
        return messageQueryService.unreadCount();
    }

    @PostMapping("/{messageId:\\d+}/read")
    public Map<String, Object> markRead(@PathVariable("messageId") long messageId) {
        return messageQueryService.markRead(messageId);
    }
}
