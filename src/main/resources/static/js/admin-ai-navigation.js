(function () {
    'use strict';

    const initialize = () => {
        const sidebar = document.querySelector('.sidebar');
        const group = sidebar?.querySelector('[data-admin-ai-navigation]');
        if (!sidebar || !group) return;

        const toggle = group.querySelector('.admin-ai-toggle');
        const submenu = group.querySelector('.admin-ai-submenu');
        const links = Array.from(group.querySelectorAll('[data-ai-route]'));
        const currentPath = window.location.pathname.replace(/\/$/, '') || '/';
        const activeLink = links.find(link => {
            const route = link.dataset.aiRoute;
            return currentPath === route || (link.dataset.aiRouteMode !== 'exact' && currentPath.startsWith(route + '/'));
        });

        if (activeLink) {
            activeLink.setAttribute('aria-current', 'page');
            group.classList.add('has-active-page');
        }

        const setExpanded = expanded => {
            toggle.setAttribute('aria-expanded', String(expanded));
            submenu.setAttribute('aria-hidden', String(!expanded));
            submenu.inert = !expanded;
            submenu.classList.toggle('is-expanded', expanded);
            group.classList.toggle('is-expanded', expanded);
        };

        setExpanded(Boolean(activeLink));
        toggle.addEventListener('click', () => setExpanded(toggle.getAttribute('aria-expanded') !== 'true'));

        toggle.addEventListener('keydown', event => {
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                setExpanded(true);
                (event.key === 'ArrowDown' ? links[0] : links[links.length - 1])?.focus();
            }
        });

        links.forEach((link, index) => link.addEventListener('keydown', event => {
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                const offset = event.key === 'ArrowDown' ? 1 : -1;
                links[(index + offset + links.length) % links.length]?.focus();
            } else if (event.key === 'Home' || event.key === 'End') {
                event.preventDefault();
                links[event.key === 'Home' ? 0 : links.length - 1]?.focus();
            } else if (event.key === 'ArrowLeft') {
                event.preventDefault();
                setExpanded(false);
                toggle.focus();
            }
        }));

        const scrollKey = 'lapzone-admin-sidebar-scroll';
        try {
            const savedScrollTop = Number(window.sessionStorage.getItem(scrollKey));
            if (Number.isFinite(savedScrollTop)) sidebar.scrollTop = savedScrollTop;
        } catch (_) {
            // Sidebar navigation still works when session storage is unavailable.
        }
        window.addEventListener('pagehide', () => {
            try { window.sessionStorage.setItem(scrollKey, String(sidebar.scrollTop)); }
            catch (_) { /* Scroll restoration is best-effort. */ }
        });
        if (activeLink) requestAnimationFrame(() => activeLink.scrollIntoView({block: 'nearest'}));
    };

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize, {once: true});
    else initialize();
})();
