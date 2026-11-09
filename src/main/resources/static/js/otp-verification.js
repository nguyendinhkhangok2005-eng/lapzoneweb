(function () {
    'use strict';

    function initializeEntry(entry) {
        const form = entry.closest('form');
        const fields = Array.from(entry.querySelectorAll('[data-otp-digit]'));
        const valueField = entry.querySelector('[data-otp-value]');
        const status = entry.querySelector('[data-otp-status]');
        const tiles = Array.from(entry.querySelectorAll('[data-otp-tile]'));
        const submitButton = form.querySelector('[data-otp-submit]');
        let completeTimer;
        let isSubmitting = false;

        function setStatus(message) {
            status.textContent = message;
        }

        function synchronizeValue() {
            valueField.value = fields.map(field => field.value).join('');
        }

        function showErrorAsCleared() {
            const result = entry.querySelector('.otp-result--error');
            if (result) result.remove();
            entry.dataset.result = '';
        }

        function beginVerification() {
            if (isSubmitting || valueField.value.length !== fields.length) return;
            isSubmitting = true;
            window.clearTimeout(completeTimer);
            entry.dataset.state = 'verifying';
            setStatus('Đang xác minh...');
            fields.forEach(field => { field.disabled = true; });
            submitButton.disabled = true;
            submitButton.textContent = 'Đang xác minh...';

            const stage = entry.querySelector('.otp-motion');
            const stageRect = stage.getBoundingClientRect();
            const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
            const duration = reducedMotion ? 220 : 960;

            tiles.forEach((tile, index) => {
                tile.textContent = fields[index].value;
                const fieldRect = fields[index].getBoundingClientRect();
                const startX = fieldRect.left + fieldRect.width / 2 - (stageRect.left + stageRect.width / 2);
                const startY = fieldRect.top + fieldRect.height / 2 - (stageRect.top + stageRect.height / 2);
                const radius = Math.min(34, stageRect.height * 0.58);
                const initialAngle = -Math.PI / 2 + index * (Math.PI * 2 / fields.length);
                const delay = reducedMotion ? 0 : index * 18;
                const frame = (x, y, scale, rotation) => ({
                    transform: `translate(calc(-50% + ${x}px), calc(-50% + ${y}px)) scale(${scale}) rotate(${rotation}deg)`,
                    opacity: scale > 0.3 ? 1 : 0
                });
                const keyframes = reducedMotion
                    ? [frame(startX, startY, 1, 0), frame(0, 0, 0.12, 180)]
                    : [
                        frame(startX, startY, 1, 0),
                        frame(Math.cos(initialAngle) * radius, Math.sin(initialAngle) * radius, 0.9, 80),
                        ...[1, 2, 3, 4].map(step => {
                            const angle = initialAngle + step * Math.PI / 2;
                            return frame(Math.cos(angle) * radius, Math.sin(angle) * radius,
                                0.86 - step * 0.06, 80 + step * 100);
                        }),
                        frame(0, 0, 0.12, 600)
                    ];
                const offsets = reducedMotion
                    ? [0, 1]
                    : [0, 0.18, 0.34, 0.48, 0.62, 0.76, 1];
                if (typeof tile.animate === 'function') {
                    tile.animate(keyframes.map((keyframe, frameIndex) => ({
                        ...keyframe,
                        offset: offsets[frameIndex]
                    })), {
                        duration,
                        delay,
                        easing: 'cubic-bezier(0.22, 0.72, 0.2, 1)',
                        fill: 'forwards'
                    });
                }
            });

            const lastDelay = reducedMotion ? 0 : (fields.length - 1) * 18;
            window.setTimeout(() => {
                if (typeof HTMLFormElement.prototype.submit === 'function') {
                    HTMLFormElement.prototype.submit.call(form);
                }
            }, duration + lastDelay + 40);
        }

        function updateCompletion() {
            synchronizeValue();
            const isComplete = valueField.value.length === fields.length;
            entry.dataset.state = isComplete ? 'complete' : (valueField.value ? 'typing' : 'idle');

            if (isComplete) {
                setStatus('Đã nhập đủ mã xác thực.');
                window.clearTimeout(completeTimer);
                completeTimer = window.setTimeout(beginVerification, 280);
            } else {
                window.clearTimeout(completeTimer);
                setStatus('Nhập mã xác thực 6 số đã được gửi đến email của bạn.');
            }
        }

        function fillFrom(startIndex, digits) {
            const cleanDigits = digits.replace(/\D/g, '');
            const start = cleanDigits.length === fields.length ? 0 : startIndex;
            fields.forEach((field, index) => {
                field.value = index >= start && index < start + cleanDigits.length
                    ? cleanDigits[index - start]
                    : (index < start ? field.value : '');
            });
            showErrorAsCleared();
            updateCompletion();

            if (valueField.value.length === fields.length) {
                fields[fields.length - 1].focus();
            } else {
                fields[Math.min(start + cleanDigits.length, fields.length - 1)].focus();
            }
        }

        fields.forEach((field, index) => {
            field.addEventListener('input', () => {
                const digits = field.value.replace(/\D/g, '');
                if (digits.length > 1) {
                    fillFrom(index, digits);
                    return;
                }
                field.value = digits;
                showErrorAsCleared();
                if (digits && index < fields.length - 1) fields[index + 1].focus();
                updateCompletion();
            });

            field.addEventListener('keydown', event => {
                if (event.key === 'Backspace' && !field.value && index > 0) {
                    event.preventDefault();
                    fields[index - 1].value = '';
                    fields[index - 1].focus();
                    showErrorAsCleared();
                    updateCompletion();
                } else if (event.key === 'ArrowLeft' && index > 0) {
                    event.preventDefault();
                    fields[index - 1].focus();
                } else if (event.key === 'ArrowRight' && index < fields.length - 1) {
                    event.preventDefault();
                    fields[index + 1].focus();
                }
            });

            field.addEventListener('paste', event => {
                const pasted = event.clipboardData.getData('text');
                if (!pasted) return;
                event.preventDefault();
                fillFrom(index, pasted);
            });
        });

        form.addEventListener('submit', event => {
            if (isSubmitting) return;
            event.preventDefault();
            synchronizeValue();
            if (valueField.value.length !== fields.length) {
                const firstEmpty = fields.find(field => !field.value);
                setStatus('Vui lòng nhập đủ 6 chữ số.');
                (firstEmpty || fields[0]).focus();
                return;
            }
            beginVerification();
        });

        if (entry.dataset.result === 'error') {
            entry.dataset.state = 'error';
            setStatus('Mã xác thực không đúng. Vui lòng nhập lại.');
            fields[0].focus();
        }
    }

    function initializeResend(button) {
        const countdown = button.parentElement.querySelector('[data-otp-countdown]');
        let remaining = Number(button.dataset.wait || 0);

        function updateCountdown() {
            button.disabled = remaining > 0;
            if (countdown) {
                countdown.textContent = remaining > 0
                    ? `Có thể gửi lại mã sau ${remaining} giây.`
                    : '';
            }
            if (remaining > 0) {
                remaining -= 1;
                window.setTimeout(updateCountdown, 1000);
            }
        }

        updateCountdown();
    }

    document.querySelectorAll('[data-otp-entry]').forEach(initializeEntry);
    document.querySelectorAll('[data-otp-resend]').forEach(initializeResend);

    const successView = document.querySelector('[data-otp-success]');
    if (successView) {
        const continueUrl = successView.dataset.continueUrl;
        if (continueUrl) {
            window.setTimeout(() => window.location.assign(continueUrl), 2800);
        }
    }
})();
