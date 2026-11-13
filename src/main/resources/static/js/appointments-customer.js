(function () {
    'use strict';

    const bookingPanel = document.querySelector('[data-appointment-panel="booking"]');
    const historyPanel = document.querySelector('[data-appointment-panel="history"]');
    if (!bookingPanel || !historyPanel) return;

    const links = Array.from(document.querySelectorAll('[data-appointment-tab]'));
    const filterForm = document.getElementById('appointment-history-filter');
    const statusSelect = document.getElementById('appointment-history-status');
    const notice = document.getElementById('appointment-history-notice');
    const noticeText = document.getElementById('appointment-history-notice-text');
    const loginLink = document.getElementById('appointment-history-login');
    const feedback = document.getElementById('appointment-history-feedback');
    const list = document.getElementById('appointment-history-list');
    const count = document.getElementById('appointment-history-count');
    const pagination = document.getElementById('appointment-history-pagination');
    const previous = document.getElementById('appointment-history-previous');
    const next = document.getElementById('appointment-history-next');
    const pageLabel = document.getElementById('appointment-history-page');
    const itemTemplate = document.getElementById('appointment-history-item');
    const cancelDialog = document.getElementById('appointment-cancel-dialog');
    const cancelForm = document.getElementById('appointment-cancel-form');
    const cancelReason = document.getElementById('appointment-cancel-reason');
    const cancelError = document.getElementById('appointment-cancel-error');
    const cancelSubmit = document.getElementById('appointment-cancel-submit');
    const apiBase = historyPanel.dataset.appointmentsUrl;
    const csrfUrl = historyPanel.dataset.csrfUrl;
    const statuses = new Set(['PENDING', 'CONFIRMED', 'COMPLETED', 'REJECTED', 'CANCELLED']);
    let currentStatus = '';
    let currentPage = 0;
    let activeTab = 'booking';
    let requestNumber = 0;
    let cancelId = null;

    function dateText(value) {
        const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value || ''));
        return match ? `${match[3]}/${match[2]}/${match[1]}` : 'Chưa ghi nhận';
    }

    function timeText(value) {
        const match = /^(\d{2}:\d{2})/.exec(String(value || ''));
        return match ? match[1] : 'Chưa ghi nhận';
    }

    function dateTimeText(value) {
        if (!value || !String(value).includes('T')) return 'Chưa ghi nhận';
        const [date, time] = String(value).split('T');
        return `${dateText(date)} ${timeText(time)}`;
    }

    function showNotice(message, kind, signIn) {
        notice.hidden = false;
        notice.dataset.kind = kind || 'info';
        noticeText.textContent = message;
        loginLink.hidden = !signIn;
    }

    function clearHistory() {
        list.replaceChildren();
        count.hidden = true;
        pagination.hidden = true;
    }

    function showSignIn() {
        clearHistory();
        showNotice('Vui lòng đăng nhập để xem lịch hẹn của bạn. Lịch đặt khi chưa đăng nhập không tự gắn với tài khoản theo email hoặc số điện thoại.', 'info', true);
    }

    async function requestJson(url, options) {
        const { headers: extraHeaders = {}, ...requestOptions } = options || {};
        const response = await fetch(url, {
            credentials: 'same-origin',
            cache: 'no-store',
            ...requestOptions,
            headers: { Accept: 'application/json', ...extraHeaders }
        });
        let body = null;
        try { body = await response.json(); } catch (_) { /* The status still explains the failure. */ }
        if (!response.ok) {
            const error = new Error(body && typeof body.message === 'string' ? body.message : 'Không thể xử lý yêu cầu. Vui lòng thử lại.');
            error.status = response.status;
            throw error;
        }
        return body;
    }

    function updateUrl(tab) {
        const url = new URL(window.location.href);
        url.searchParams.set('tab', tab);
        if (tab === 'history') {
            if (currentStatus) url.searchParams.set('status', currentStatus);
            else url.searchParams.delete('status');
            if (currentPage > 0) url.searchParams.set('page', String(currentPage));
            else url.searchParams.delete('page');
        } else {
            url.searchParams.delete('status');
            url.searchParams.delete('page');
        }
        window.history.pushState(null, '', url.pathname + url.search + url.hash);
    }

    function switchTab(tab, updateHistory) {
        if (activeTab !== tab) feedback.hidden = true;
        activeTab = tab;
        bookingPanel.hidden = tab !== 'booking';
        historyPanel.hidden = tab !== 'history';
        links.forEach(link => {
            const selected = link.dataset.appointmentTab === tab;
            link.classList.toggle('is-active', selected);
            if (selected) link.setAttribute('aria-current', 'page');
            else link.removeAttribute('aria-current');
        });
        if (updateHistory) updateUrl(tab);
        if (tab === 'history') loadHistory();
        else requestNumber++; // A response from a hidden history panel must not replace its current state.
    }

    function readLocation() {
        const params = new URLSearchParams(window.location.search);
        const status = params.get('status') || '';
        currentStatus = statuses.has(status) ? status : '';
        const requestedPage = params.get('page') || '0';
        currentPage = /^\d{1,6}$/.test(requestedPage) ? Math.min(Number(requestedPage), 100000) : 0;
        statusSelect.value = currentStatus;
        switchTab(params.get('tab') === 'history' ? 'history' : 'booking', false);
    }

    function fillItem(item) {
        const fragment = itemTemplate.content.cloneNode(true);
        const card = fragment.querySelector('.appointment-history-item');
        card.querySelector('[data-history-code]').textContent = `#${item.id}`;
        const badge = card.querySelector('[data-history-status]');
        badge.textContent = item.statusLabel || 'Chưa rõ trạng thái';
        badge.dataset.status = item.status || '';
        card.querySelector('[data-history-date]').textContent = dateText(item.appointmentDate);
        card.querySelector('[data-history-time]').textContent = timeText(item.appointmentTime);
        card.querySelector('[data-history-type]').textContent = item.supportType || 'Chưa ghi nhận';
        card.querySelector('[data-history-showroom]').textContent = item.showroom || 'Chưa chọn cửa hàng';
        const reason = card.querySelector('[data-history-reason]');
        if (item.statusReason) {
            reason.querySelector('span').textContent = item.statusReason;
            reason.hidden = false;
        }
        card.querySelector('[data-history-detail]').addEventListener('click', () => toggleDetail(card, item.id));
        const cancelButton = card.querySelector('[data-history-cancel]');
        if (item.canCancel === true) {
            cancelButton.hidden = false;
            cancelButton.addEventListener('click', () => openCancellation(item.id));
        }
        return fragment;
    }

    async function loadHistory() {
        if (activeTab !== 'history') return;
        const requestId = ++requestNumber;
        clearHistory();
        showNotice('Đang tải lịch hẹn của bạn…');
        const url = new URL(apiBase, window.location.origin);
        url.searchParams.set('page', String(currentPage));
        url.searchParams.set('size', '10');
        if (currentStatus) url.searchParams.set('status', currentStatus);
        try {
            const data = await requestJson(url.toString());
            if (requestId !== requestNumber || activeTab !== 'history') return;
            if (!data || !Array.isArray(data.content)) throw new Error('Dữ liệu lịch hẹn chưa hợp lệ. Vui lòng thử lại.');
            currentPage = Number(data.number) || 0;
            if (data.content.length === 0 && currentPage > 0 && currentPage >= Number(data.totalPages)) {
                currentPage = Math.max(0, Number(data.totalPages) - 1);
                updateUrl('history');
                loadHistory();
                return;
            }
            count.textContent = `${data.totalElements} lịch hẹn`;
            count.hidden = false;
            if (data.content.length === 0) {
                showNotice(currentStatus ? 'Không có lịch hẹn phù hợp với trạng thái đã chọn.' : 'Bạn chưa có lịch hẹn nào trong tài khoản này.');
            } else {
                notice.hidden = true;
                const fragment = document.createDocumentFragment();
                data.content.forEach(item => fragment.appendChild(fillItem(item)));
                list.replaceChildren(fragment);
            }
            pagination.hidden = !data.hasPrevious && !data.hasNext;
            previous.disabled = !data.hasPrevious;
            next.disabled = !data.hasNext;
            pageLabel.textContent = `Trang ${currentPage + 1} / ${Math.max(1, Number(data.totalPages) || 1)}`;
        } catch (error) {
            if (requestId !== requestNumber || activeTab !== 'history') return;
            clearHistory();
            if (error.status === 401) showSignIn();
            else showNotice(error.message || 'Không tải được lịch hẹn. Vui lòng thử lại.', 'error');
        }
    }

    function addFact(list, label, value) {
        const row = document.createElement('div');
        const term = document.createElement('dt');
        const description = document.createElement('dd');
        term.textContent = label;
        description.textContent = value || 'Chưa ghi nhận';
        row.append(term, description);
        list.appendChild(row);
    }

    function renderDetail(panel, data) {
        panel.replaceChildren();
        const item = data.appointment;
        const title = document.createElement('h4'); title.textContent = 'Thông tin chi tiết';
        const facts = document.createElement('dl');
        addFact(facts, 'Họ và tên', item.fullName);
        addFact(facts, 'Số điện thoại', item.phone);
        addFact(facts, 'Email', item.email);
        addFact(facts, 'Nội dung hỗ trợ', item.description);
        addFact(facts, 'Ngày gửi yêu cầu', dateTimeText(item.createdAt));
        addFact(facts, 'Cập nhật gần nhất', dateTimeText(item.updatedAt));
        panel.append(title, facts);
        if (Array.isArray(data.history) && data.history.length) {
            const historyTitle = document.createElement('h4');
            historyTitle.textContent = 'Lịch sử trạng thái';
            historyTitle.style.marginTop = '18px';
            const events = document.createElement('ol');
            const actors = { ADMIN: 'Nhân viên LAPZONE', USER: 'Khách hàng', GUEST: 'Khách vãng lai' };
            data.history.forEach(event => {
                const line = document.createElement('li');
                const actor = actors[event.actorRole] || 'Người cập nhật';
                line.textContent = `${event.statusLabel || 'Trạng thái chưa xác định'} · ${dateTimeText(event.changedAt)} · ${actor}${event.reason ? ' · Lý do: ' + event.reason : ''}`;
                events.appendChild(line);
            });
            panel.append(historyTitle, events);
        }
    }

    async function toggleDetail(card, id) {
        const button = card.querySelector('[data-history-detail]');
        const panel = card.querySelector('[data-history-detail-panel]');
        if (!panel.hidden) {
            panel.hidden = true;
            button.textContent = 'Xem chi tiết';
            button.setAttribute('aria-expanded', 'false');
            return;
        }
        panel.hidden = false;
        button.textContent = 'Thu gọn chi tiết';
        button.setAttribute('aria-expanded', 'true');
        panel.textContent = 'Đang tải chi tiết…';
        try {
            const data = await requestJson(`${apiBase}/${encodeURIComponent(id)}`);
            if (!card.isConnected || panel.hidden) return;
            if (!data || !data.appointment) throw new Error('Thông tin chi tiết chưa hợp lệ. Vui lòng thử lại.');
            renderDetail(panel, data);
        } catch (error) {
            if (!card.isConnected || panel.hidden) return;
            if (error.status === 401) showSignIn();
            else panel.textContent = error.message || 'Không tải được chi tiết. Vui lòng thử lại.';
        }
    }

    function openCancellation(id) {
        cancelId = id;
        cancelForm.reset();
        cancelError.hidden = true;
        cancelDialog.showModal();
        cancelReason.focus();
    }

    cancelForm.addEventListener('submit', async event => {
        event.preventDefault();
        if (cancelId == null) return;
        const reason = cancelReason.value.trim();
        if (!reason || reason.length > 2000) {
            cancelError.textContent = 'Vui lòng nhập lý do hủy từ 1 đến 2.000 ký tự.';
            cancelError.hidden = false;
            return;
        }
        cancelSubmit.disabled = true;
        cancelError.hidden = true;
        try {
            const csrf = await requestJson(csrfUrl);
            if (!csrf || !csrf.headerName || !csrf.token) throw new Error('Không lấy được mã bảo vệ. Vui lòng thử lại.');
            await requestJson(`${apiBase}/${encodeURIComponent(cancelId)}/cancel`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
                body: JSON.stringify({ reason })
            });
            const completedId = cancelId;
            cancelDialog.close();
            await loadHistory();
            feedback.textContent = `Đã hủy lịch hẹn #${completedId}.`;
            feedback.dataset.kind = 'success';
            feedback.hidden = false;
        } catch (error) {
            cancelError.textContent = error.message || 'Không thể hủy lịch hẹn. Vui lòng thử lại.';
            cancelError.hidden = false;
            if (error.status === 401) {
                cancelDialog.close();
                showSignIn();
            }
        } finally {
            cancelSubmit.disabled = false;
        }
    });
    document.getElementById('appointment-cancel-close').addEventListener('click', () => cancelDialog.close());
    cancelDialog.addEventListener('close', () => { cancelId = null; cancelForm.reset(); cancelError.hidden = true; });

    links.forEach(link => link.addEventListener('click', event => {
        if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        switchTab(link.dataset.appointmentTab, true);
    }));
    filterForm.addEventListener('submit', event => {
        event.preventDefault();
        currentStatus = statuses.has(statusSelect.value) ? statusSelect.value : '';
        currentPage = 0;
        feedback.hidden = true;
        updateUrl('history');
        loadHistory();
    });
    previous.addEventListener('click', () => {
        if (currentPage === 0) return;
        currentPage--;
        updateUrl('history');
        loadHistory();
    });
    next.addEventListener('click', () => {
        currentPage++;
        updateUrl('history');
        loadHistory();
    });
    window.addEventListener('popstate', readLocation);
    readLocation();
})();
