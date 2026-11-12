(function () {
    'use strict';
    const form = document.querySelector('.support-read-form');
    if (!form) return;
    let pending = false;
    async function markVisibleRead() {
        if (document.visibilityState !== 'visible' || pending || form.hidden) return;
        pending = true;
        try {
            const response = await fetch(form.action, {
                method: 'POST', body: new FormData(form),
                headers: { 'X-Requested-With': 'XMLHttpRequest' }, credentials: 'same-origin'
            });
            if (response.status === 204) {
                form.hidden = true;
                document.querySelector('.support-read-status').textContent = 'Đã đọc các tin nhắn đang hiển thị.';
            }
        } catch (error) {
            // Keep the normal HTTP form available if automatic marking fails.
        } finally { pending = false; }
    }
    document.addEventListener('visibilitychange', markVisibleRead);
    markVisibleRead();
})();
