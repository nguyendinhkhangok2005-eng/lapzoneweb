const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const resources = path.resolve(__dirname, '../../main/resources');
const fragment = fs.readFileSync(path.join(resources, 'templates/fragments/theme.html'), 'utf8');
const initialThemeScript = fragment.match(/<script>([\s\S]*?)<\/script>/)[1];
const pickerScript = fs.readFileSync(path.join(resources, 'static/js/customer-theme.js'), 'utf8');

function makePage(savedTheme, { readBlocked = false, writeBlocked = false } = {}) {
    const values = new Map([['lapzone-admin-theme', 'gaming']]);
    if (savedTheme !== undefined) values.set('lapzone-theme', savedTheme);
    const attributes = {};
    const classes = new Set();
    const document = {
        readyState: 'loading',
        documentElement: {
            classList: { add: value => classes.add(value), remove: value => classes.delete(value) },
            setAttribute: (key, value) => { attributes[key] = value; },
            getAttribute: key => attributes[key]
        },
        addEventListener() {},
        querySelectorAll() { return []; }
    };
    const window = {
        localStorage: {
            getItem(key) { if (readBlocked) throw Error('read blocked'); return values.get(key) ?? null; },
            setItem(key, value) { if (writeBlocked) throw Error('write blocked'); values.set(key, value); }
        },
        clearTimeout() {},
        setTimeout() { return 1; }
    };
    const context = { window, document };
    vm.runInNewContext(initialThemeScript, context);
    vm.runInNewContext(pickerScript, context);
    return { values, attributes, window };
}

test('Classic defaults when the preference is absent, invalid, or unreadable', () => {
    for (const saved of [undefined, '', 'invalid']) {
        assert.equal(makePage(saved).attributes['data-theme'], 'classic');
    }
    assert.equal(makePage('gaming', { readBlocked: true }).attributes['data-theme'], 'classic');
});

test('all four current preferences survive reload without affecting Admin', () => {
    for (const id of ['classic', 'dark', 'gaming', 'aurora']) {
        const page = makePage(id);
        assert.equal(page.attributes['data-theme'], id);
        assert.equal(page.values.get('lapzone-theme'), id);
        assert.equal(page.values.get('lapzone-admin-theme'), 'gaming');
    }
});

test('Ocean preference migrates to Aurora before the page is styled', () => {
    const page = makePage('ocean');
    assert.equal(page.attributes['data-theme'], 'aurora');
    assert.equal(page.values.get('lapzone-theme'), 'aurora');
    assert.equal(page.values.get('lapzone-admin-theme'), 'gaming');
});

test('Ocean still applies as Aurora when storage cannot be rewritten', () => {
    const page = makePage('ocean', { writeBlocked: true });
    assert.equal(page.attributes['data-theme'], 'aurora');
    assert.equal(page.values.get('lapzone-theme'), 'ocean');
});

test('legacy theme callers select Aurora without changing other preferences', () => {
    const page = makePage('classic');
    page.window.LapzoneTheme.setTheme('ocean');
    assert.equal(page.attributes['data-theme'], 'aurora');
    assert.equal(page.values.get('lapzone-theme'), 'aurora');
    assert.equal(page.values.get('lapzone-admin-theme'), 'gaming');
});
