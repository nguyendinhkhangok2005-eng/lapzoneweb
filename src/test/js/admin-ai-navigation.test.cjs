const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

class ClassList {
    constructor(values = []) { this.values = new Set(values); }
    add(value) { this.values.add(value); }
    remove(value) { this.values.delete(value); }
    toggle(value, force) {
        const add = force === undefined ? !this.values.has(value) : force;
        if (add) this.add(value); else this.remove(value);
        return add;
    }
    contains(value) { return this.values.has(value); }
}
class Element {
    constructor(dataset = {}) { this.dataset = dataset; this.attributes = {}; this.handlers = {}; this.classList = new ClassList(); this.focused = false; }
    addEventListener(name, fn) { this.handlers[name] = fn; }
    setAttribute(name, value) { this.attributes[name] = value; }
    getAttribute(name) { return this.attributes[name]; }
    focus() { this.focused = true; }
    scrollIntoView(options) { this.scrolled = options; }
}
function setup(pathname) {
    const routes = ['/admin/ai', '/admin/ai/feedback', '/admin/ai/knowledge', '/admin/ai/inspector', '/admin/ai/settings'];
    const links = routes.map((route, index) => new Element({
        aiRoute: route, ...(index === 0 ? {aiRouteMode: 'exact'} : {})
    }));
    const toggle = new Element();
    const submenu = new Element();
    const group = new Element();
    group.querySelector = selector => selector === '.admin-ai-toggle' ? toggle : submenu;
    group.querySelectorAll = selector => selector === '[data-ai-route]' ? links : [];
    const sidebar = new Element(); sidebar.scrollTop = 125;
    sidebar.querySelector = selector => selector === '[data-admin-ai-navigation]' ? group : null;
    const saved = new Map([['lapzone-admin-sidebar-scroll', '44']]);
    const window = {
        location: {pathname},
        sessionStorage: {getItem: key => saved.get(key), setItem: (key, value) => saved.set(key, value)},
        addEventListener: (name, fn) => { window.handlers ||= {}; window.handlers[name] = fn; }
    };
    const document = {readyState: 'complete', querySelector: selector => selector === '.sidebar' ? sidebar : null};
    const context = {window, document, requestAnimationFrame: fn => fn()};
    vm.createContext(context);
    const scriptPath = path.join(__dirname, '../../main/resources/static/js/admin-ai-navigation.js');
    vm.runInContext(fs.readFileSync(scriptPath, 'utf8'), context);
    return {sidebar, group, toggle, submenu, links, saved, window};
}

test('all five AI destinations highlight the right link and expand the parent, including nested pages', () => {
    const paths = ['/admin/ai', '/admin/ai/feedback', '/admin/ai/knowledge/4/history', '/admin/ai/inspector/12', '/admin/ai/settings/history'];
    for (const current of paths) {
        const state = setup(current);
        const index = current === '/admin/ai' ? 0 : state.links.findIndex((link, i) => i > 0 && current.startsWith(link.dataset.aiRoute));
        assert.equal(state.links[index].getAttribute('aria-current'), 'page', current);
        assert.equal(state.group.classList.contains('has-active-page'), true, current);
        assert.equal(state.toggle.getAttribute('aria-expanded'), 'true', current);
        assert.equal(state.submenu.getAttribute('aria-hidden'), 'false', current);
        assert.equal(state.submenu.inert, false, current);
        assert.equal(state.links[index].scrolled.block, 'nearest', current);
    }
});

test('button toggles the submenu and keyboard navigation can open, traverse, and close it', () => {
    const {toggle, submenu, links} = setup('/admin/orders');
    assert.equal(toggle.getAttribute('aria-expanded'), 'false');
    assert.equal(submenu.getAttribute('aria-hidden'), 'true');
    toggle.handlers.click();
    assert.equal(toggle.getAttribute('aria-expanded'), 'true');
    toggle.handlers.click();
    let prevented = false;
    toggle.handlers.keydown({key: 'ArrowDown', preventDefault: () => {prevented = true;}});
    assert.equal(prevented, true); assert.equal(links[0].focused, true);
    links[0].handlers.keydown({key: 'ArrowDown', preventDefault() {}});
    assert.equal(links[1].focused, true);
    links[1].handlers.keydown({key: 'End', preventDefault() {}});
    assert.equal(links[4].focused, true);
    links[4].handlers.keydown({key: 'ArrowLeft', preventDefault() {}});
    assert.equal(toggle.getAttribute('aria-expanded'), 'false'); assert.equal(toggle.focused, true);
});

test('sidebar scroll is restored and saved across page navigation', () => {
    const {sidebar, saved, window} = setup('/admin/ai');
    assert.equal(sidebar.scrollTop, 44);
    sidebar.scrollTop = 208;
    window.handlers.pagehide();
    assert.equal(saved.get('lapzone-admin-sidebar-scroll'), '208');
});

test('sidebar template contains one copy of each AI destination and Vietnamese labels', () => {
    const html = fs.readFileSync(path.join(__dirname, '../../main/resources/templates/admin/sidebar.html'), 'utf8');
    const expected = [
        ['/admin/ai', 'Tổng quan AI'], ['/admin/ai/feedback', 'Phản hồi khách hàng'],
        ['/admin/ai/knowledge', 'Kiến thức AI'], ['/admin/ai/inspector', 'Kiểm tra gợi ý AI'],
        ['/admin/ai/settings', 'Cấu hình AI']
    ];
    for (const [route, label] of expected) {
        assert.equal([...html.matchAll(new RegExp(`data-ai-route="${route.replaceAll('/', '\\/')}"`, 'g'))].length, 1, route);
        assert.ok(html.includes(label), label);
    }
    assert.equal((html.match(/Quản lý Chatbot AI/g) || []).length, 1);
});

test('shared navigation styles include a mobile dropdown and reduced-motion support', () => {
    const css = fs.readFileSync(path.join(__dirname, '../../main/resources/static/css/admin-ai-navigation.css'), 'utf8');
    assert.ok(css.includes('@media (max-width: 760px)'));
    assert.ok(css.includes('position: absolute'));
    assert.ok(css.includes('@media (prefers-reduced-motion: reduce)'));
});
