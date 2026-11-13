const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
class Element {
    constructor(tag) { this.tag = tag; this.children = []; this.handlers = {}; this.attributes = {}; this.value = ''; }
    append(...nodes) { this.children.push(...nodes); }
    appendChild(node) { this.append(node); }
    setAttribute(key, value) { this.attributes[key] = value; }
    addEventListener(event, callback) { this.handlers[event] = callback; }
}
function setup(fetch) {
    const context = {window: {}, document: {createElement: tag => new Element(tag)}, fetch};
    vm.createContext(context);
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/chatbot-feedback.js'), 'utf8'), context);
    const bubble = new Element('div');
    return { bubble, attach: (...args) => context.window.LapzoneAiFeedback.attach(bubble, ...args) };
}
function ok(data) { return {ok:true, json: async () => data}; }
test('negative feedback requires a Vietnamese reason and posts exact message reference with CSRF', async () => {
    const calls = [];
    const {bubble, attach} = setup(async (url, options) => { calls.push([url, options]); return ok(url.endsWith('csrf') ? {headerName:'X-CSRF-TOKEN', token:'test-token'} : {rating:'NEGATIVE'}); });
    attach(42, 'conversation-A');
    const [positive, negative, panel, status] = bubble.children[0].children;
    await negative.handlers.click(); assert.equal(panel.hidden, false);
    const [select, submit] = panel.children;
    assert.deepEqual(select.children.slice(1).map(x => x.textContent), ['Thông tin sai.', 'Không đúng nhu cầu.', 'Gợi ý sản phẩm không phù hợp.', 'Câu trả lời khó hiểu.', 'Khác.']);
    await submit.handlers.click(); assert.equal(calls.length, 0); assert.equal(status.textContent, 'Vui lòng chọn lý do.');
    select.value = 'INCORRECT'; await submit.handlers.click();
    assert.equal(calls[1][0], '/api/ai/conversations/conversation-A/messages/42/feedback');
    assert.equal(calls[1][1].headers['X-CSRF-TOKEN'], 'test-token');
    assert.deepEqual(JSON.parse(calls[1][1].body), {rating:'NEGATIVE', reason:'INCORRECT'});
    assert.equal(positive.disabled, true); assert.equal(negative.attributes['aria-pressed'], 'true');
});
test('pending and saved controls prevent duplicate submissions; restored feedback remains locked', async () => {
    let release, posts = 0;
    const gate = new Promise(resolve => release = resolve);
    const {bubble, attach} = setup(async url => { if (url.endsWith('csrf')) return ok({headerName:'X-CSRF-TOKEN', token:'t'}); posts++; await gate; return ok({rating:'POSITIVE'}); });
    attach(9, 'own-conversation'); const positive = bubble.children[0].children[0];
    const first = positive.handlers.click(); await positive.handlers.click();
    await new Promise(resolve => setImmediate(resolve)); assert.equal(posts, 1);
    release(); await first; await positive.handlers.click(); assert.equal(posts, 1);
    const restored = setup(async () => { throw new Error('unexpected request'); });
    restored.attach(9, 'own-conversation', {rating:'NEGATIVE'});
    assert.equal(restored.bubble.children[0].children[1].disabled, true);
});
test('submission failure is visible in Vietnamese and does not discard cards or message', async () => {
    const {bubble, attach} = setup(async () => ({ok:false, json:async()=>({message:'Không tìm thấy tin nhắn hoặc đánh giá.'})}));
    const card = new Element('article'); bubble.appendChild(card); attach(7, 'c');
    const box = bubble.children[1]; await box.children[0].handlers.click();
    assert.equal(bubble.children[0], card); assert.equal(box.children[0].disabled, false);
    assert.equal(box.children[3].textContent, 'Không tìm thấy tin nhắn hoặc đánh giá.');
});
test('network errors use Vietnamese text', async () => {
    const {bubble, attach} = setup(async () => { throw new TypeError('Failed to fetch'); });
    attach(7, 'c'); await bubble.children[0].children[0].handlers.click();
    assert.equal(bubble.children[0].children[3].textContent, 'Không thể kết nối máy chủ. Vui lòng thử lại.');
});
