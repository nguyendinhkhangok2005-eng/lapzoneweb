(function () {
    'use strict';

    const initialize = () => {
        const sidebar = document.querySelector('.sidebar');
        const group = sidebar?.querySelector('[data-admin-ai-navigation]');
        if (!sidebar || !group) return;

        const toggle = group.querySelector('.admin-ai-toggle');
        const submenu = group.querySelector('.admin-ai-submenu');
        const links = Array.from(group.querySelectorAll('.admin-ai-submenu__item'));
        const routes = Array.from(sidebar.querySelectorAll('[data-admin-route]'));
        const currentPath = window.location.pathname.replace(/\/+$/, '') || '/';
        // Segment boundaries avoid highlighting /admin/orders for /admin/orders-other.
        // Longest match keeps nested AI routes on their most specific submenu.
        const activeLink = routes
            .filter(link => {
                const route = link.dataset.adminRoute;
                return currentPath === route ||
                    (link.dataset.adminRouteMode !== 'exact' && currentPath.startsWith(route + '/'));
            })
            .sort((left, right) => right.dataset.adminRoute.length - left.dataset.adminRoute.length)[0];

        routes.forEach(link => link.removeAttribute('aria-current'));
        if (activeLink) activeLink.setAttribute('aria-current', 'page');
        const aiActive = activeLink?.closest('[data-admin-ai-navigation]') === group ||
            currentPath.startsWith('/admin/ai/');
        group.classList.toggle('has-active-page', aiActive);

        const mobile = () => window.matchMedia('(max-width: 760px)').matches;
        const placeMobileSubmenu = () => {
            if (!mobile()) {
                submenu.style.removeProperty('--admin-ai-submenu-top');
                return;
            }
            const picker = document.querySelector('[data-admin-theme-picker] summary');
            const bottom = Math.max(sidebar.getBoundingClientRect().bottom,
                picker?.getBoundingClientRect().bottom || 0);
            submenu.style.setProperty('--admin-ai-submenu-top', Math.max(8, bottom + 8) + 'px');
        };
        const revealMobileGroup = () => {
            if (!mobile() || !aiActive) return;
            const left = group.getBoundingClientRect().left - sidebar.getBoundingClientRect().left + sidebar.scrollLeft;
            sidebar.scrollLeft = Math.max(0, left - (sidebar.clientWidth - group.clientWidth) / 2);
        };

        const setExpanded = expanded => {
            if (expanded) placeMobileSubmenu();
            toggle.setAttribute('aria-expanded', String(expanded));
            submenu.setAttribute('aria-hidden', String(!expanded));
            submenu.inert = !expanded;
            submenu.classList.toggle('is-expanded', expanded);
            group.classList.toggle('is-expanded', expanded);
        };

        setExpanded(aiActive);
        window.addEventListener('resize', () => {
            placeMobileSubmenu();
            revealMobileGroup();
        });
        window.addEventListener('scroll', () => {
            if (!mobile() || toggle.getAttribute('aria-expanded') !== 'true') return;
            if (sidebar.getBoundingClientRect().bottom <= 0) setExpanded(false);
            else placeMobileSubmenu();
        }, {passive: true});
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
        if (activeLink) {
            if (mobile() && aiActive) {
                revealMobileGroup();
            } else {
                requestAnimationFrame(() => activeLink.scrollIntoView({block: 'nearest'}));
            }
        }
    };

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize, {once: true});
    else initialize();
})();
