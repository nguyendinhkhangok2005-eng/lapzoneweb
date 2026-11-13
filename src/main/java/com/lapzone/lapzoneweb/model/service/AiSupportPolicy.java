package com.lapzone.lapzoneweb.model.service;

/** Conservative server-side routing; no model confidence score or private account lookup. */
public final class AiSupportPolicy {
    private AiSupportPolicy() {}

    public static boolean isSupportQuestion(String question) {
        String text = AiProductRequirements.normalize(question == null ? "" : question);
        return text.matches("(?s).*\\b(bao hanh|doi tra|doi hang|tra hang|hoan tien|don hang|giao hang|van chuyen|thanh toan|ho tro|khieu nai|sua chua|bi loi|khong len nguon|nhan vien)\\b.*");
    }

    public static boolean needsAccountOrHuman(String question) {
        String text = AiProductRequirements.normalize(question == null ? "" : question);
        return text.matches("(?s).*\\b(cua toi|cua minh|ma don|da dat|chua nhan|chua giao|bi loi|khong len nguon|yeu cau hoan|khieu nai|nhan vien|nguoi that)\\b.*");
    }

    public static boolean isProductSearch(String question) {
        String text = AiProductRequirements.normalize(question == null ? "" : question);
        return text.matches("(?s).*\\b(tim|goi y|tu van|chon|mua)\\b.*\\b(laptop|may tinh|asus|acer|dell|lenovo|macbook)\\b.*")
            || text.matches("(?s).*\\b(cpu|gpu|ram|ssd|rtx|gaming|do hoa|lap trinh|van phong)\\b.*")
            || text.matches("(?s).*\\d+\\s*(trieu|tr|vnd)\\b.*");
    }

    public static boolean isSupportFollowUp(String question, java.util.List<com.lapzone.lapzoneweb.model.dto.ChatMessageDTO> history) {
        if (history == null || isProductSearch(question)) return false;
        String text = AiProductRequirements.normalize(question == null ? "" : question);
        if (!text.matches("(?s).*\\b(vay|the thi|van chua|chua duoc|lam sao|lam the nao|khong duoc|giup toi|giup minh)\\b.*")) return false;
        return history.stream().filter(m -> m != null && "user".equals(m.role())).reduce((a, b) -> b)
            .map(m -> isSupportQuestion(m.text())).orElse(false);
    }
}
