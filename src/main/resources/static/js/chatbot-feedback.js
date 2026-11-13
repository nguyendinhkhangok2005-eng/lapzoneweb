(function (root) {
    'use strict';
    const reasons = [
        ['INCORRECT', 'Thông tin sai.'], ['IRRELEVANT', 'Không đúng nhu cầu.'],
        ['BAD_RECOMMENDATION', 'Gợi ý sản phẩm không phù hợp.'], ['UNCLEAR', 'Câu trả lời khó hiểu.'], ['OTHER', 'Khác.']
    ];
    let csrfPromise;
    async function request(url, options) {
        let response;
        try { response = await fetch(url, Object.assign({ credentials: 'same-origin' }, options)); }
        catch (_) { throw new Error('Không thể kết nối máy chủ. Vui lòng thử lại.'); }
        let data;
        try { data = await response.json(); } catch (_) { data = {}; }
        if (!response.ok) throw new Error(data.message || 'Không thể gửi đánh giá. Vui lòng thử lại.');
        return data;
    }
    function attach(bubble, messageId, conversationId, existing, unavailable) {
        if (!Number.isSafeInteger(messageId) || messageId <= 0 || !conversationId) return;
        const box = document.createElement('div'); box.className = 'chatbot-feedback';
        const positive = document.createElement('button'); positive.type = 'button'; positive.textContent = '👍 Hữu ích';
        const negative = document.createElement('button'); negative.type = 'button'; negative.textContent = '👎 Chưa hữu ích';
        positive.setAttribute('aria-label', 'Đánh giá câu trả lời hữu ích');
        negative.setAttribute('aria-label', 'Đánh giá câu trả lời chưa hữu ích');
        const panel = document.createElement('div'); panel.className = 'chatbot-feedback-reasons'; panel.hidden = true;
        const select = document.createElement('select'); select.setAttribute('aria-label', 'Lý do đánh giá chưa hài lòng');
        const empty = document.createElement('option'); empty.value = ''; empty.textContent = 'Chọn lý do'; select.appendChild(empty);
        for (const [value, label] of reasons) {
            const option = document.createElement('option'); option.value = value; option.textContent = label; select.appendChild(option);
        }
        const submit = document.createElement('button'); submit.type = 'button'; submit.textContent = 'Gửi đánh giá';
        const status = document.createElement('small'); status.setAttribute('role', 'status');
        status.textContent = 'Chỉ chia sẻ mã tham chiếu, mức đánh giá và lý do. Nội dung chat không được chia sẻ cho quản trị viên.';
        panel.append(select, submit); box.append(positive, negative, panel, status); bubble.appendChild(box);
        let saved = false, pending = false;
        function lock(vote) {
            saved = true; positive.disabled = true; negative.disabled = true; submit.disabled = true; panel.hidden = true;
            positive.setAttribute('aria-pressed', String(vote.rating === 'POSITIVE'));
            negative.setAttribute('aria-pressed', String(vote.rating === 'NEGATIVE'));
            status.textContent = vote.rating === 'POSITIVE' ? 'Đã ghi nhận đánh giá hữu ích.' : 'Đã ghi nhận đánh giá chưa hài lòng.';
        }
        async function send(rating, reason) {
            if (pending || saved) return;
            if (rating === 'NEGATIVE' && !reason) { status.textContent = 'Vui lòng chọn lý do.'; return; }
            pending = true; positive.disabled = true; negative.disabled = true; submit.disabled = true;
            status.textContent = 'Đang gửi đánh giá…';
            try {
                if (!csrfPromise) csrfPromise = request('/api/ai/feedback/csrf');
                const token = await csrfPromise;
                const vote = await request(`/api/ai/conversations/${encodeURIComponent(conversationId)}/messages/${messageId}/feedback`, {
                    method: 'POST', headers: { 'Content-Type': 'application/json', [token.headerName]: token.token },
                    body: JSON.stringify({ rating, reason: reason || null })
                });
                lock(vote);
            } catch (error) {
                csrfPromise = null; status.textContent = error.message;
            } finally {
                pending = false;
                if (!saved) { positive.disabled = false; negative.disabled = false; submit.disabled = false; }
            }
        }
        positive.setAttribute('aria-pressed', 'false'); negative.setAttribute('aria-pressed', 'false');
        positive.addEventListener('click', () => send('POSITIVE', null));
        negative.addEventListener('click', () => { if (!saved && !pending) panel.hidden = !panel.hidden; });
        submit.addEventListener('click', () => send('NEGATIVE', select.value));
        if (existing) lock(existing);
        if (unavailable) {
            positive.disabled = true; negative.disabled = true;
            status.textContent = 'Chưa tải được đánh giá. Vui lòng mở lại hội thoại để đánh giá.';
        }
    }
    root.LapzoneAiFeedback = { attach };
})(window);
