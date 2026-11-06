(function () {
    'use strict';

    const themes = [
        { id: 'classic', name: 'Cổ điển', color: '#2563eb' },
        { id: 'dark', name: 'Tối', color: '#374151' },
        { id: 'gaming', name: 'Gaming', color: '#a855f7' },
        { id: 'ocean', name: 'Đại dương', color: '#0369a1' }
    ];
    const validThemes = new Set(themes.map(theme => theme.id));
    let transitionTimer;

    function setTheme(themeId) {
        if (!validThemes.has(themeId)) return;

        const root = document.documentElement;
        root.classList.add('theme-changing');
        root.setAttribute('data-theme', themeId);
        window.clearTimeout(transitionTimer);
        transitionTimer = window.setTimeout(() => root.classList.remove('theme-changing'), 220);
        try {
            window.localStorage.setItem('lapzone-theme', themeId);
        } catch (error) {
            // The selected theme remains active for the current page when storage is unavailable.
        }
        updateSelection(themeId);
    }

    function updateSelection(themeId) {
        document.querySelectorAll('[data-theme-choice]').forEach(button => {
            const selected = button.dataset.themeChoice === themeId;
            button.setAttribute('aria-checked', String(selected));
            const indicator = button.querySelector('.theme-choice__check');
            if (indicator) indicator.textContent = selected ? '✓' : '';
        });
    }

    function createFallbackPicker() {
        const picker = document.createElement('div');
        picker.className = 'theme-picker theme-picker--floating';
        picker.id = 'theme-picker';
        picker.innerHTML = '<button class="theme-picker__trigger" type="button" aria-label="Chọn giao diện" title="Chọn giao diện" aria-haspopup="menu" aria-expanded="false" aria-controls="theme-menu"><span aria-hidden="true">◐</span></button><div class="theme-picker__menu" id="theme-menu" role="menu" aria-label="Chọn giao diện" hidden></div>';
        const menu = picker.querySelector('.theme-picker__menu');

        themes.forEach(theme => {
            const option = document.createElement('button');
            option.className = 'theme-choice';
            option.type = 'button';
            option.setAttribute('role', 'menuitemradio');
            option.dataset.themeChoice = theme.id;
            option.innerHTML = '<span class="theme-choice__swatch" aria-hidden="true"></span><span class="theme-choice__name"></span><span class="theme-choice__check" aria-hidden="true"></span>';
            option.querySelector('.theme-choice__swatch').style.backgroundColor = theme.color;
            option.querySelector('.theme-choice__name').textContent = theme.name;
            menu.appendChild(option);
        });
        document.body.appendChild(picker);
        return picker;
    }

    function initializePicker() {
        let picker = document.getElementById('theme-picker');
        if (!picker && document.body) picker = createFallbackPicker();
        if (!picker) return;

        const trigger = picker.querySelector('.theme-picker__trigger');
        const menu = picker.querySelector('.theme-picker__menu');
        if (!trigger || !menu) return;

        const closeMenu = (restoreFocus) => {
            menu.hidden = true;
            trigger.setAttribute('aria-expanded', 'false');
            if (restoreFocus) trigger.focus();
        };
        const openMenu = () => {
            menu.hidden = false;
            trigger.setAttribute('aria-expanded', 'true');
        };

        trigger.addEventListener('click', () => {
            if (menu.hidden) openMenu();
            else closeMenu(false);
        });

        menu.addEventListener('click', event => {
            const choice = event.target.closest('[data-theme-choice]');
            if (!choice) return;
            setTheme(choice.dataset.themeChoice);
            closeMenu(true);
        });

        picker.addEventListener('keydown', event => {
            if (event.key === 'Escape' && !menu.hidden) {
                event.preventDefault();
                closeMenu(true);
            }
        });

        document.addEventListener('click', event => {
            if (!picker.contains(event.target) && !menu.hidden) closeMenu(false);
        });

        updateSelection(document.documentElement.dataset.theme || 'classic');
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializePicker, { once: true });
    } else {
        initializePicker();
    }

    window.LapzoneTheme = Object.freeze({ setTheme });
})();
