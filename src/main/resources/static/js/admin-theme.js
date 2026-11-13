(function () {
    'use strict';

    const storageKey = 'lapzone-admin-theme';
    const themes = Object.freeze([
        Object.freeze({ id: 'classic', name: 'Cổ điển (Classic)' }),
        Object.freeze({ id: 'dark', name: 'Tối (Dark)' }),
        Object.freeze({ id: 'gaming', name: 'Gaming' }),
        Object.freeze({ id: 'aurora', name: 'Cực Quang (Aurora Prism)' })
    ]);
    const isValid = id => themes.some(theme => theme.id === id);
    const root = document.documentElement;
    let picker;

    function readPreference() {
        try {
            const stored = window.localStorage.getItem(storageKey);
            if (stored === 'ocean') {
                try { window.localStorage.setItem(storageKey, 'aurora'); }
                catch (_) { /* Apply the migrated theme even when writes are blocked. */ }
                return 'aurora';
            }
            return isValid(stored) ? stored : 'classic';
        } catch (error) {
            return 'classic';
        }
    }

    function apply(id) {
        root.setAttribute('data-admin-theme', id);
        if (picker) updatePicker(id);
    }

    apply(readPreference());

    // Shared by the reusable header picker and any Admin presentation controls.
    window.LapzoneAdminTheme = Object.freeze({
        themes,
        storageKey,
        get: () => root.getAttribute('data-admin-theme'),
        set: function (id) {
            if (!isValid(id)) return false;
            apply(id);
            try {
                window.localStorage.setItem(storageKey, id);
            } catch (error) {
                // Keep the current page usable when storage is blocked or full.
            }
            window.dispatchEvent(new CustomEvent('lapzone:admin-theme-change', { detail: { theme: id } }));
            return true;
        }
    });

    window.addEventListener('storage', function (event) {
        if (event.key === storageKey || event.key === null) apply(readPreference());
    });

    function updatePicker(id) {
        picker.querySelectorAll('[data-admin-theme-choice]').forEach(function (choice) {
            const selected = choice.dataset.adminThemeChoice === id;
            choice.setAttribute('aria-pressed', String(selected));
            if (selected) {
                const label = choice.querySelector('[data-admin-theme-name]').textContent;
                picker.querySelector('[data-admin-theme-current-label]').textContent = label;
                picker.querySelector('summary').setAttribute('aria-label', 'Chọn giao diện quản trị, hiện tại: ' + label);
            }
        });
        picker.querySelector('[data-admin-theme-current-preview]').setAttribute('data-theme-preview', id);
    }

    document.addEventListener('DOMContentLoaded', function () {
        picker = document.querySelector('[data-admin-theme-picker]');
        if (!picker) return;
        const toolbar = document.querySelector('[data-admin-theme-toolbar]');
        const topBar = document.querySelector('.main-content .top-bar');
        if (topBar && toolbar) {
            topBar.classList.add('admin-theme-host');
            topBar.appendChild(picker);
            toolbar.hidden = true;
        }
        updatePicker(window.LapzoneAdminTheme.get());
        const summary = picker.querySelector('summary');
        picker.addEventListener('click', function (event) {
            const choice = event.target.closest('[data-admin-theme-choice]');
            if (!choice || !picker.contains(choice)) return;
            if (window.LapzoneAdminTheme.set(choice.dataset.adminThemeChoice)) {
                picker.open = false;
                summary.focus();
            }
        });
        picker.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && picker.open) {
                event.preventDefault();
                picker.open = false;
                summary.focus();
            }
        });
        document.addEventListener('click', function (event) {
            if (!picker.contains(event.target)) picker.open = false;
        });
    });

    // Canvas text cannot inherit CSS. Adapt Chart.js presentation without changing data.
    document.addEventListener('DOMContentLoaded', function () {
        if (!window.Chart || typeof window.Chart.register !== 'function') return;
        const originalColors = new WeakMap();
        window.Chart.register({
            id: 'lapzoneAdminTheme',
            beforeUpdate: function (chart) {
                const options = chart.options;
                const legend = options.plugins && options.plugins.legend;
                const scales = Object.values(options.scales || {});
                if (!originalColors.has(chart)) {
                    originalColors.set(chart, {
                        color: options.color,
                        legend: legend && legend.labels.color,
                        scales: scales.map(scale => ({ ticks: scale.ticks.color, grid: scale.grid.color }))
                    });
                }
                const original = originalColors.get(chart);
                const classic = root.getAttribute('data-admin-theme') === 'classic';
                const tokens = getComputedStyle(root);
                const text = tokens.getPropertyValue('--admin-text-muted').trim();
                const border = tokens.getPropertyValue('--admin-border').trim();
                options.color = classic ? original.color : text;
                if (legend) legend.labels.color = classic ? original.legend : text;
                scales.forEach(function (scale, index) {
                    const previous = original.scales[index];
                    scale.ticks.color = classic && previous ? previous.ticks : text;
                    scale.grid.color = classic && previous ? previous.grid : border;
                });
            }
        });
        function updateCharts() {
            Object.values(window.Chart.instances || {}).forEach(chart => chart.update('none'));
        }
        window.addEventListener('lapzone:admin-theme-change', updateCharts);
        window.addEventListener('storage', function (event) {
            if (event.key === storageKey || event.key === null) updateCharts();
        });
    });
})();
