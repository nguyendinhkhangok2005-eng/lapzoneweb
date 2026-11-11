(function () {
    'use strict';
    const form = document.getElementById('checkout-form');
    if (!form) return;
    const input = document.getElementById('voucher-code');
    const button = document.getElementById('voucher-preview');
    const message = document.getElementById('voucher-message');
    const subtotal = document.getElementById('voucher-subtotal');
    const discount = document.getElementById('voucher-discount');
    const total = document.getElementById('voucher-total');
    const originalSubtotal = subtotal.textContent;
    const originalTotal = total.textContent;
    const choices = document.querySelectorAll('[data-voucher-code]');
    const money = value => new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(value) + ' đ';
    let generation = 0;
    input.addEventListener('keydown', event => {
        if (event.key === 'Enter') { event.preventDefault(); if (!button.disabled) button.click(); }
    });
    input.addEventListener('input', () => {
        generation++;
        button.disabled = false;
        message.textContent = '';
        delete message.dataset.state;
        choices.forEach(choice => choice.setAttribute('aria-pressed', String(choice.dataset.voucherCode === input.value.trim().toUpperCase())));
        discount.textContent = '0 đ';
        subtotal.textContent = originalSubtotal;
        total.textContent = originalTotal;
    });
    choices.forEach(choice => choice.addEventListener('click', () => {
        input.value = choice.dataset.voucherCode;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        button.click();
    }));
    button.addEventListener('click', async () => {
        if (!input.value.trim()) {
            message.dataset.state = 'error';
            message.textContent = 'Vui lòng nhập mã giảm giá.';
            return;
        }
        const requestGeneration = ++generation;
        button.disabled = true;
        delete message.dataset.state;
        message.textContent = 'Đang kiểm tra mã…';
        try {
            const response = await fetch(form.dataset.voucherPreviewUrl, {
                method: 'POST', body: new FormData(form), headers: { Accept: 'application/json' }
            });
            const result = await response.json();
            if (requestGeneration !== generation) return;
            if (!response.ok) throw new Error(result.message || 'Không thể kiểm tra mã. Vui lòng tải lại trang.');
            subtotal.textContent = money(result.subtotal);
            discount.textContent = '−' + money(result.discount);
            total.textContent = money(result.total);
            message.textContent = 'Mã hợp lệ tại thời điểm kiểm tra. Lượt sử dụng chỉ được giữ khi đặt hàng thành công.';
            message.dataset.state = 'success';
        } catch (error) {
            if (requestGeneration !== generation) return;
            discount.textContent = '0 đ';
            subtotal.textContent = originalSubtotal;
            total.textContent = originalTotal;
            message.textContent = error instanceof SyntaxError ? 'Phiên đã hết hạn. Vui lòng tải lại trang.' : error.message;
            message.dataset.state = 'error';
        } finally { if (requestGeneration === generation) button.disabled = false; }
    });
})();
