package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.dto.ChatMessageDTO;
import com.lapzone.lapzoneweb.model.dto.ConversationDTO;
import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Transactional
public class AiConversationService {
    public record Access(Long ownerId, String guestKey) {
        public Access {
            if ((ownerId == null) == (guestKey == null) || (guestKey != null && guestKey.isBlank())) {
                throw new IllegalArgumentException("Exactly one conversation owner is required");
            }
        }
    }

    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final UserRepository users;
    private final AiConsultantService consultant;

    public AiConversationService(AiConversationRepository conversations, AiMessageRepository messages,
                                 UserRepository users, AiConsultantService consultant) {
        this.conversations = conversations;
        this.messages = messages;
        this.users = users;
        this.consultant = consultant;
    }

    public ConversationDTO create(Access access, String title) {
        User owner = access.ownerId() == null ? null : users.findById(access.ownerId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return dto(conversations.save(new AiConversation(owner, access.guestKey(),
            title == null ? "Cuộc trò chuyện mới" : validTitle(title))));
    }

    @Transactional(readOnly = true)
    public List<ConversationDTO> list(Access access, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination (size 1-100)");
        }
        return conversations.findAccessible(access.ownerId(), access.guestKey(), PageRequest.of(page, size))
            .stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public ConversationDTO.Detail retrieve(String id, Access access) {
        AiConversation conversation = require(id, access, false);
        return new ConversationDTO.Detail(dto(conversation),
            messages.findByConversationIdOrderByIdAsc(id).stream().map(m -> messageDto(id, m)).toList());
    }

    public ConversationDTO rename(String id, Access access, String title) {
        AiConversation conversation = require(id, access, true);
        conversation.rename(validTitle(title));
        return dto(conversation);
    }

    public void delete(String id, Access access) {
        AiConversation conversation = require(id, access, true);
        messages.deleteConversationMessages(id);
        conversations.delete(conversation);
    }

    public void reset(String id, Access access) {
        AiConversation conversation = require(id, access, true);
        conversation.touch();
        messages.deleteConversationMessages(id);
    }

    public String chat(String id, Access access, String question) {
        // Lock the conversation through the AI call and atomic pair insert. Concurrent sends,
        // rename, reset and delete for this ID serialize across application instances.
        AiConversation conversation = require(id, access, true);
        List<AiMessage> recent = new ArrayList<>(messages.findTop12ByConversationIdOrderByIdDesc(id));
        Collections.reverse(recent);
        List<ChatMessageDTO> context = recent.stream()
            .map(m -> new ChatMessageDTO(m.getRole(), m.getText())).toList();
        String reply = consultant.consult(question, context);
        messages.save(new AiMessage(conversation, "user", question));
        messages.save(new AiMessage(conversation, "model", reply));
        conversation.touch();
        return reply;
    }

    private AiConversation require(String id, Access access, boolean lock) {
        return (lock
            ? conversations.findAccessibleByIdForUpdate(id, access.ownerId(), access.guestKey())
            : conversations.findAccessibleById(id, access.ownerId(), access.guestKey()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private String validTitle(String title) {
        if (title == null || title.strip().isEmpty() || title.strip().length() > 120
                || title.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title must contain 1-120 characters without controls");
        }
        return title.strip();
    }

    private ConversationDTO dto(AiConversation c) {
        return new ConversationDTO(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private ChatMessageDTO messageDto(String id, AiMessage m) {
        return new ChatMessageDTO(m.getRole(), m.getText(), id, m.getCreatedAt());
    }
}
