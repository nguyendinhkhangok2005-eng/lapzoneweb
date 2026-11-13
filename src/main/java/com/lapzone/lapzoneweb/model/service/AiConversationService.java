package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.dto.ChatMessageDTO;
import com.lapzone.lapzoneweb.model.dto.ConversationDTO;
import com.lapzone.lapzoneweb.model.dto.AiReplyDTO;
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
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
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
        List<AiMessage> history = messages.findByConversationIdOrderByIdAsc(id);
        return new ConversationDTO.Detail(dto(conversation), messageDtos(id, history));
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
        return chatWithProducts(id, access, question).reply();
    }

    public AiReplyDTO chatWithProducts(String id, Access access, String question) {
        // Lock the conversation through the AI call and atomic pair insert. Concurrent sends,
        // rename, reset and delete for this ID serialize across application instances.
        AiConversation conversation = require(id, access, true);
        List<AiMessage> recent = new ArrayList<>(messages.findTop12ByConversationIdOrderByIdDesc(id));
        Collections.reverse(recent);
        List<ChatMessageDTO> context = messageDtos(id, recent);
        AiReplyDTO reply = consultant.consultWithProducts(question, context);
        messages.save(new AiMessage(conversation, "user", question));
        AiMessage answer = new AiMessage(conversation, "model", reply.reply());
        answer.setSupportOffered(reply.supportOffered());
        answer.setProductIds(reply.products().stream().map(p -> p.id().toString()).collect(java.util.stream.Collectors.joining(",")));
        messages.save(answer);
        conversation.touch();
        return new AiReplyDTO(reply.reply(), reply.products(), answer.getId(), reply.supportOffered());
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

    private List<Long> productIds(AiMessage message) {
        return message.getProductIds() == null || message.getProductIds().isBlank() ? List.of()
            : java.util.Arrays.stream(message.getProductIds().split(",")).map(Long::valueOf).toList();
    }

    private List<ChatMessageDTO> messageDtos(String id, List<AiMessage> history) {
        List<Long> ids = history.stream().flatMap(m -> productIds(m).stream()).distinct().toList();
        boolean highlight = !ids.isEmpty() && consultant.currentPromotionHighlightEnabled();
        java.util.Map<Long, com.lapzone.lapzoneweb.model.dto.AiProductDTO> products = new java.util.HashMap<>();
        for (int start = 0; start < ids.size(); start += 100) {
            for (var product : consultant.currentProducts(ids.subList(start, Math.min(start + 100, ids.size())), highlight))
                products.put(product.id(), product);
        }
        return history.stream().map(m -> new ChatMessageDTO(m.getRole(), m.getText(), id, m.getCreatedAt(),
            productIds(m).stream().map(products::get).filter(java.util.Objects::nonNull).toList(),
            m.getId(), m.isSupportOffered())).toList();
    }
}
