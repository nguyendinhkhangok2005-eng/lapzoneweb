package com.lapzone.lapzoneweb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lapzone.lapzoneweb.model.dto.ChatMessageDTO;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import com.lapzone.lapzoneweb.model.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiSupportPolicyTest {
    final ProductRepository products = mock(ProductRepository.class);
    final RestTemplate transport = mock(RestTemplate.class);
    final AiKnowledgeService knowledge = mock(AiKnowledgeService.class);
    final AiConsultantService consultant = new AiConsultantService(products, "test-key", transport, new ObjectMapper(), knowledge);

    @Test void unsupportedPolicyQuestionOffersStaffWithoutInventingAnAnswerOrCallingTheModel() {
        var reply = consultant.consultWithProducts("Chính sách đổi trả thế nào?", List.of());
        assertTrue(reply.supportOffered()); assertTrue(reply.products().isEmpty());
        assertTrue(reply.reply().contains("chưa có thông tin đã xác thực"));
        verifyNoInteractions(products, transport);
    }

    @Test void publishedGeneralPolicyStillAnswersNormally() {
        String question = "Chính sách bảo hành thế nào?";
        when(knowledge.referenceAnswer(question)).thenReturn(Optional.of("Chính sách đã được xác nhận."));
        var reply = consultant.consultWithProducts(question, List.of());
        assertFalse(reply.supportOffered()); assertTrue(reply.reply().contains("Chính sách đã được xác nhận."));
        verify(knowledge, times(1)).referenceAnswer(question);
        verifyNoInteractions(products, transport);
    }

    @Test void personalCasesNeedStaffEvenWhenThereIsAPublishedGenericPolicy() {
        var reply = consultant.consultWithProducts("Đơn hàng của tôi chưa nhận được, xin gặp nhân viên", List.of());
        assertTrue(reply.supportOffered()); verifyNoInteractions(products, transport);
    }

    @Test void supportFollowUpUsesOnlyTheCurrentConversationContext() {
        var history = List.of(new ChatMessageDTO("user", "Đơn hàng của tôi chưa giao"),
            new ChatMessageDTO("model", "Vui lòng liên hệ nhân viên"));
        assertTrue(consultant.consultWithProducts("Vậy làm sao?", history).supportOffered());
        assertFalse(consultant.consultWithProducts("Vậy làm sao?", List.of()).supportOffered());
    }

    @Test void shoppingRequestsMentioningSupportOrWarrantyKeepTheExistingProductFlow() {
        assertFalse(consultant.consultWithProducts("Tìm laptop gaming hỗ trợ đồ họa, bảo hành 24 tháng dưới 20 triệu", List.of()).supportOffered());
        verify(products).findAvailableProductsForAiAfter(eq(0L), any(org.springframework.data.domain.Pageable.class)); verifyNoInteractions(transport);
    }
}
