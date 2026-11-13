(function (root) {
    'use strict';
    function element(tag, text) {
        const node = document.createElement(tag);
        if (text !== undefined) node.textContent = text;
        return node;
    }
    async function request(url, options) {
        let response;
        try { response = await fetch(url, Object.assign({credentials: 'same-origin', cache: 'no-store'}, options)); }
        catch (_) { throw new Error('Không thể kết nối máy chủ. Vui lòng thử lại.'); }
        let data;
        try { data = await response.json(); } catch (_) { data = {}; }
        if (!response.ok) {
            const error = new Error(data.message || 'Không thể xử lý yêu cầu hỗ trợ. Vui lòng thử lại.');
            error.login = response.status === 401;
            throw error;
        }
        return data;
    }
    function attach(bubble, messageId, conversationId, offered) {
        if (!offered || !Number.isSafeInteger(messageId) || messageId <= 0 || !conversationId) return;
        const url = `/api/ai/conversations/${encodeURIComponent(conversationId)}/messages/${messageId}/support`;
        const box = element('section'); box.className = 'chatbot-support';
        box.setAttribute('aria-label', 'Chuyển cho nhân viên hỗ trợ');
        const open = element('button', 'Liên hệ nhân viên hỗ trợ'); open.type = 'button';
        const panel = element('div'); panel.hidden = true;
        const status = element('p'); status.setAttribute('role', 'status'); status.setAttribute('aria-live', 'polite');
        box.append(open, panel, status); bubble.appendChild(box);
        let pending = false, saved = false, token, consent, send, selected = [], preview;

        function showRequest(result) {
            saved = true; panel.replaceChildren(); panel.hidden = false; open.hidden = true;
            panel.append(element('strong', `Yêu cầu #${result.id}: ${result.statusLabel}`));
            if (result.latestReply) {
                panel.append(element('p', 'Phản hồi gần nhất của nhân viên LAPZONE:'), element('blockquote', result.latestReply));
            }
            const link = element('a', 'Xem và trả lời trong hộp thư hỗ trợ');
            link.href = `/support/${encodeURIComponent(result.id)}`;
            const refresh = element('button', 'Cập nhật trạng thái'); refresh.type = 'button';
            refresh.addEventListener('click', () => load(true));
            panel.append(link, refresh);
            status.textContent = 'Chỉ nội dung đã xác nhận được gửi. Các tin nhắn AI mới không tự động được chia sẻ.';
        }
        function bodyText() {
            let text = 'Yêu cầu chuyển từ trợ lý AI\n\nCâu hỏi cần hỗ trợ:\n' + preview.question.text;
            for (const item of selected) if (item.input.checked)
                text += `\n\n${item.message.role === 'user' ? 'Khách hàng' : 'Trợ lý AI'}:\n${item.message.text}`;
            return text;
        }
        function showPreview(data) {
            preview = data; selected = []; panel.replaceChildren(); panel.hidden = false;
            panel.append(element('p', 'Chọn nội dung chia sẻ. Câu hỏi bên dưới sẽ được gửi; tin nhắn bổ sung mặc định không được chọn.'));
            const question = element('blockquote', preview.question.text); panel.append(question);
            const choices = element('div'); choices.className = 'chatbot-support-context';
            for (const message of preview.context || []) {
                const label = element('label'), input = element('input'); input.type = 'checkbox'; input.checked = false;
                label.append(input, element('span', `${message.role === 'user' ? 'Bạn' : 'Trợ lý AI'}: ${message.text}`));
                choices.append(label); selected.push({input, message});
            }
            if (selected.length) panel.append(element('strong', 'Tin nhắn bổ sung (tùy chọn)'), choices);
            const outgoing = element('pre'); outgoing.className = 'chatbot-support-preview';
            const count = element('small');
            const consentLabel = element('label'); consentLabel.className = 'chatbot-support-consent';
            consent = element('input'); consent.type = 'checkbox'; consent.checked = false;
            consentLabel.append(consent, element('span', 'Tôi đồng ý gửi câu hỏi và các tin nhắn đã chọn cho nhân viên LAPZONE.'));
            send = element('button', 'Xác nhận gửi yêu cầu'); send.type = 'button'; send.disabled = true;
            const cancel = element('button', 'Hủy'); cancel.type = 'button';
            cancel.addEventListener('click', () => { if (!pending) { panel.hidden = true; status.textContent = 'Chưa gửi yêu cầu hỗ trợ.'; } });
            function update() {
                const body = bodyText(); outgoing.textContent = body; count.textContent = `${body.length}/4.000 ký tự`;
                send.disabled = pending || !consent.checked || body.length > 4000;
            }
            selected.forEach(item => item.input.addEventListener('change', update));
            consent.addEventListener('change', update);
            send.addEventListener('click', async () => {
                if (pending || saved || !consent.checked || bodyText().length > 4000) return;
                pending = true; send.disabled = true; open.disabled = true; status.textContent = 'Đang gửi yêu cầu…';
                // Freeze the exact selected references at confirmation; the server fetches their original text.
                const ids = selected.filter(item => item.input.checked).map(item => item.message.id);
                try {
                    showRequest(await request(url, {method: 'POST', headers: {'Content-Type': 'application/json', [token.headerName]: token.token},
                        body: JSON.stringify({confirmed: true, contextMessageIds: ids})}));
                } catch (error) { status.textContent = error.message; }
                finally { pending = false; open.disabled = false; if (!saved) update(); }
            });
            panel.append(element('strong', 'Nội dung sẽ gửi'), outgoing, count, consentLabel, send, cancel);
            update(); status.textContent = 'Chưa gửi yêu cầu hỗ trợ.';
        }
        async function load(refresh) {
            if (pending) return;
            pending = true; open.disabled = true; status.textContent = 'Đang tải thông tin hỗ trợ…';
            try {
                const data = await request(url); token = {headerName: data.headerName, token: data.token};
                if (data.preview.request) showRequest(data.preview.request);
                else if (!refresh) showPreview(data.preview);
                else status.textContent = 'Yêu cầu chưa được gửi.';
            } catch (error) {
                status.textContent = error.message;
                if (error.login) {
                    panel.replaceChildren(); panel.hidden = false;
                    const login = element('a', 'Đăng nhập để dùng hộp thư hỗ trợ'); login.href = '/dangnhap'; panel.append(login);
                    panel.append(element('p', 'Lịch sử chat khách chưa đăng nhập không tự chuyển sang tài khoản. Bạn có thể gửi câu hỏi trong hộp thư sau khi đăng nhập.'));
                }
            } finally { pending = false; open.disabled = false; }
        }
        open.addEventListener('click', () => load(false));
    }
    root.LapzoneAiSupport = {attach};
})(window);
