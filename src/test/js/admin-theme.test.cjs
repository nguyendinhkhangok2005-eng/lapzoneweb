const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const resources = path.resolve(__dirname, '../../main/resources');
const source = fs.readFileSync(path.join(resources, 'static/js/admin-theme.js'), 'utf8');

function bootstrap({ admin, customer = 'ocean', blocked = false, writeBlocked = false } = {}) {
    const attributes = { 'data-theme': customer };
    const storage = new Map([['lapzone-theme', customer]]);
    if (admin !== undefined) storage.set('lapzone-admin-theme', admin);
    const listeners = {};
    const window = {
        localStorage: {
            getItem(key) { if (blocked) throw Error('blocked'); return storage.get(key) ?? null; },
            setItem(key, value) { if (blocked || writeBlocked) throw Error('blocked'); storage.set(key, value); }
        },
        addEventListener(name, callback) { (listeners[name] ??= []).push(callback); },
        dispatchEvent(event) { (listeners[event.type] || []).forEach(callback => callback(event)); }
    };
    const document = {
        documentElement: {
            setAttribute(key, value) { attributes[key] = value; },
            getAttribute(key) { return attributes[key]; }
        },
        addEventListener() {}
    };
    vm.runInNewContext(source, {
        window, document,
        CustomEvent: class { constructor(type, init) { this.type = type; this.detail = init.detail; } }
    });
    return { api: window.LapzoneAdminTheme, window, storage, attributes };
}

test('Classic defaults independently of every storefront preference', () => {
    for (const customer of ['classic', 'dark', 'gaming', 'ocean']) {
        const state = bootstrap({ customer });
        assert.equal(state.api.get(), 'classic');
        assert.equal(state.attributes['data-theme'], customer);
        assert.equal(state.storage.get('lapzone-theme'), customer);
        assert.equal(state.storage.has('lapzone-admin-theme'), false);
    }
});

test('exactly four Admin themes are available and persisted', () => {
    const state = bootstrap();
    assert.deepEqual(Array.from(state.api.themes, item => item.id), ['classic', 'dark', 'gaming', 'aurora']);
    for (const { id } of state.api.themes) {
        assert.equal(bootstrap({ admin: id }).api.get(), id);
        assert.equal(state.api.set(id), true);
        assert.equal(state.api.get(), id);
        assert.equal(state.storage.get('lapzone-admin-theme'), id);
    }
    assert.ok(Object.isFrozen(state.api));
    assert.ok(Object.isFrozen(state.api.themes));
});

test('unsupported preferences fall back and invalid updates are ignored', () => {
    for (const admin of ['', 'invalid', 'AURORA']) assert.equal(bootstrap({ admin }).api.get(), 'classic');
    const { api } = bootstrap({ admin: 'gaming' });
    for (const id of [null, undefined, 'ocean', {}, '']) assert.equal(api.set(id), false);
    assert.equal(api.get(), 'gaming');
});

test('obsolete Admin Ocean preference migrates without reading the storefront theme', () => {
    const state = bootstrap({ admin: 'ocean', customer: 'dark' });
    assert.equal(state.api.get(), 'aurora');
    assert.equal(state.storage.get('lapzone-admin-theme'), 'aurora');
    assert.equal(state.storage.get('lapzone-theme'), 'dark');
    const readOnly = bootstrap({ admin: 'ocean', writeBlocked: true });
    assert.equal(readOnly.api.get(), 'aurora');
});

test('blocked storage preserves fallback and allows the current page to change', () => {
    const { api } = bootstrap({ admin: 'dark', blocked: true });
    assert.equal(api.get(), 'classic');
    assert.equal(api.set('aurora'), true);
    assert.equal(api.get(), 'aurora');
});

test('theme events and other Admin tabs synchronize without customer storage changes', () => {
    const { api, window, storage } = bootstrap();
    let selected;
    window.addEventListener('lapzone:admin-theme-change', event => { selected = event.detail.theme; });
    api.set('aurora');
    assert.equal(selected, 'aurora');
    storage.set('lapzone-admin-theme', 'gaming');
    window.dispatchEvent({ type: 'storage', key: 'lapzone-theme' });
    assert.equal(api.get(), 'aurora');
    window.dispatchEvent({ type: 'storage', key: 'lapzone-admin-theme' });
    assert.equal(api.get(), 'gaming');
    storage.delete('lapzone-admin-theme');
    window.dispatchEvent({ type: 'storage', key: null });
    assert.equal(api.get(), 'classic');
    assert.equal(storage.get('lapzone-theme'), 'ocean');
});

test('every interactive Admin template loads the theme directly or through its shared head', () => {
    for (const name of fs.readdirSync(path.join(resources, 'templates/admin'))) {
        const html = fs.readFileSync(path.join(resources, 'templates/admin', name), 'utf8');
        if (!html.trim() || ['sidebar.html', 'theme.html'].includes(name)) continue;
        assert.match(html, /admin\/theme :: head|admin\/ai-knowledge-list :: knowledgeHead/, name);
        assert.doesNotMatch(html, /fragments\/theme :: head/, name);
    }
    for (const name of ['inbox.html', 'thread.html']) {
        const html = fs.readFileSync(path.join(resources, 'templates/support', name), 'utf8');
        assert.match(html, /th:if="\$\{adminView\}"><th:block th:replace="~\{admin\/theme :: head\}"/);
        assert.match(html, /th:unless="\$\{adminView\}"><th:block th:replace="~\{fragments\/theme :: head\}"/);
    }
});

test('one reusable header supplies exactly four Admin choices through the shared sidebar', () => {
    const fragment = fs.readFileSync(path.join(resources, 'templates/admin/theme.html'), 'utf8');
    const choices = [...fragment.matchAll(/data-admin-theme-choice="([^"]+)"/g)].map(match => match[1]);
    assert.deepEqual(choices, ['classic', 'dark', 'gaming', 'aurora']);
    assert.match(fragment, /th:fragment="toolbar"/);
    const sidebar = fs.readFileSync(path.join(resources, 'templates/admin/sidebar.html'), 'utf8');
    assert.match(sidebar, /admin\/theme :: toolbar/);
    const report = fs.readFileSync(path.join(resources, 'templates/admin/print_report.html'), 'utf8');
    assert.match(report, /admin\/theme :: toolbar/);
});
