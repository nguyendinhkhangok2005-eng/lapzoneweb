package com.lapzone.lapzoneweb.controller;

import com.lapzone.lapzoneweb.model.service.AiSupportHandoffService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Objects;

@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/ai/conversations/{conversationId}/messages/{messageId}/support")
public class AiSupportHandoffController {
    private final AiSupportHandoffService handoff;
    public AiSupportHandoffController(AiSupportHandoffService handoff) { this.handoff = handoff; }
    public record PreviewResponse(AiSupportHandoffService.Preview preview, String headerName, String token) {}

    @GetMapping
    public ResponseEntity<PreviewResponse> preview(@PathVariable String conversationId, @PathVariable Long messageId,
                                                   CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new PreviewResponse(
            handoff.preview(conversationId, messageId), token.getHeaderName(), token.getToken()));
    }
    @PostMapping
    public ResponseEntity<AiSupportHandoffService.RequestStatus> submit(@PathVariable String conversationId,
            @PathVariable Long messageId, @RequestBody AiSupportHandoffService.Submission request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handoff.submit(conversationId, messageId, request));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore()).body(Map.of("message",
            Objects.requireNonNullElse(error.getReason(), "Không thể xử lý yêu cầu hỗ trợ.")));
    }
}
