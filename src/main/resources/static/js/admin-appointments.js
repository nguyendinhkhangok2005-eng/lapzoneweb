(function () {
    'use strict';
    const forms = Array.from(document.querySelectorAll('[data-appointment-status-form]'));
    const labels = {
        CONFIRMED: 'Xác nhận lịch hẹn',
        REJECTED: 'Từ chối lịch hẹn',
        COMPLETED: 'Hoàn thành lịch hẹn'
    };

    forms.forEach(form => {
        const status = form.querySelector('[name="status"]');
        const reason = form.querySelector('[name="reason"]');
        const showroom = form.querySelector('[name="showroom"]');
        const submit = form.querySelector('button[type="submit"]');
        const sync = () => {
            reason.required = status.value === 'REJECTED';
            reason.setCustomValidity('');
            if (showroom) showroom.disabled = status.value !== 'CONFIRMED';
            submit.dataset.action = status.value;
            if (form.dataset.submitting !== 'true') submit.textContent = labels[status.value] || 'Lưu trạng thái';
        };
        status.addEventListener('change', sync);
        reason.addEventListener('input', () => reason.setCustomValidity(''));
        reason.addEventListener('invalid', () => {
            if (reason.required && !reason.value.trim()) reason.setCustomValidity('Vui lòng nhập lý do từ chối lịch hẹn.');
        });
        form.addEventListener('submit', event => {
            if (form.dataset.submitting === 'true') {
                event.preventDefault();
                return;
            }
            sync();
            if (reason.required && !reason.value.trim()) reason.setCustomValidity('Vui lòng nhập lý do từ chối lịch hẹn.');
            if (!form.reportValidity()) {
                event.preventDefault();
                return;
            }
            const code = form.dataset.appointmentId;
            const question = status.value === 'REJECTED'
                ? `Từ chối lịch hẹn #${code} với lý do đã nhập?`
                : status.value === 'COMPLETED'
                    ? `Đánh dấu lịch hẹn #${code} đã hoàn thành?`
                    : `Xác nhận lịch hẹn #${code} cho khách hàng?`;
            if (!window.confirm(question)) {
                event.preventDefault();
                return;
            }
            form.dataset.submitting = 'true';
            form.setAttribute('aria-busy', 'true');
            submit.disabled = true;
            submit.textContent = 'Đang cập nhật…';
        });
        window.addEventListener('pageshow', () => {
            form.dataset.submitting = 'false';
            form.removeAttribute('aria-busy');
            submit.disabled = false;
            sync();
        });
        sync();
    });
})();
