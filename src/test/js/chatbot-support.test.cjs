const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

class Element {
    constructor(tag) { this.tag = tag; this.children = []; this.handlers = {}; this.attributes = {}; this.checked = false; }
    append(...nodes) { this.children.push(...nodes); }
    appendChild(node) { this.append(node); }
    replaceChildren(...nodes) { this.children = nodes; }
    setAttribute(key, value) { this.attributes[key] = value; }
    addEventListener(event, callback) { this.handlers[event] = callback; }
}
function all(node, predicate) { return [node, ...node.children.flatMap(child => all(child, predicate))].filter(predicate); }
function find(node, predicate) { return all(node, predicate)[0]; }
function button(node, text) { return find(node, n => n.tag === 'button' && n.textContent === text); }
const preview = {
    question: {id: 41, text: 'Đơn hàng của tôi chưa giao'},
    context: [{id: 10, role: 'user', text: 'Mã đơn: 123'}, {id: 11, role: 'model', text: 'Nội dung không đồng ý chia sẻ'}], request: null
};
function ok(data) { return {ok: true, json: async () => data}; }
function setup(fetch) {
    const context = {window: {}, document: {createElement: tag => new Element(tag)}, fetch};
    vm.createContext(context);
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/chatbot-support.js'), 'utf8'), context);
    const bubble = new Element('div');
    return {bubble, attach: (offered = true) => context.window.LapzoneAiSupport.attach(bubble, 42, 'conversation-A', offered)};
}
async function open(bubble) { await button(bubble, 'Liên hệ nhân viên hỗ trợ').handlers.click(); }
function consent(bubble) {
    const input = find(bubble, n => n.className === 'chatbot-support-consent').children[0];
    input.checked = true; input.handlers.change();
}

test('offer opens a preview without posting and requires explicit consent to the exact selected context', async () => {
    const calls = [];
    const {bubble, attach} = setup(async (url, options) => {
        calls.push([url, options]);
        return ok(options.method === 'POST' ? {id: 7, statusLabel: 'Chờ phản hồi'} : {preview, headerName: 'X-CSRF-TOKEN', token: 't'});
    });
    const card = new Element('article'); bubble.append(card); attach(); await open(bubble);
    assert.equal(calls.length, 1); assert.equal(calls[0][0], '/api/ai/conversations/conversation-A/messages/42/support');
    assert.equal(calls[0][1].cache, 'no-store');
    const choices = find(bubble, n => n.className === 'chatbot-support-context');
    assert.ok(choices.children.every(label => !label.children[0].checked));
    const submit = button(bubble, 'Xác nhận gửi yêu cầu');
    await submit.handlers.click(); assert.equal(calls.length, 1);
    choices.children[0].children[0].checked = true; choices.children[0].children[0].handlers.change();
    const outgoing = find(bubble, n => n.tag === 'pre');
    assert.ok(outgoing.textContent.includes('Mã đơn: 123')); assert.ok(!outgoing.textContent.includes(preview.context[1].text));
    consent(bubble); assert.equal(submit.disabled, false); await submit.handlers.click();
    assert.deepEqual(JSON.parse(calls[1][1].body), {confirmed: true, contextMessageIds: [10]});
    assert.equal(calls[1][1].headers['X-CSRF-TOKEN'], 't');
    assert.equal(find(bubble, n => n.tag === 'a').href, '/support/7');
    assert.equal(bubble.children[0], card);
});

test('cancel shares nothing and reopening starts with no selected context or consent', async () => {
    let posts = 0;
    const {bubble, attach} = setup(async (_, options) => { if (options.method === 'POST') posts++; return ok({preview, headerName: 'X-CSRF-TOKEN', token: 't'}); });
    attach(); await open(bubble); consent(bubble);
    button(bubble, 'Hủy').handlers.click(); assert.equal(posts, 0);
    await open(bubble);
    assert.equal(button(bubble, 'Xác nhận gửi yêu cầu').disabled, true);
    assert.ok(all(bubble, n => n.tag === 'input').every(input => !input.checked));
});

test('pending and repeated clicks cannot duplicate a support submission', async () => {
    let release, posts = 0; const gate = new Promise(resolve => {release = resolve;});
    const {bubble, attach} = setup(async (_, options) => {
        if (options.method !== 'POST') return ok({preview, headerName: 'X-CSRF-TOKEN', token: 't'});
        posts++; await gate; return ok({id: 8, statusLabel: 'Chờ phản hồi'});
    });
    attach(); await open(bubble); consent(bubble);
    const submit = button(bubble, 'Xác nhận gửi yêu cầu'); const first = submit.handlers.click();
    await submit.handlers.click(); assert.equal(posts, 1);
    release(); await first; await submit.handlers.click(); assert.equal(posts, 1);
});

test('restored requests show current status and staff replies as text and refresh without posting', async () => {
    let calls = 0;
    const reply = '<img src=x onerror=alert(1)> Nhân viên đang kiểm tra';
    const {bubble, attach} = setup(async (_, options) => {
        assert.notEqual(options.method, 'POST'); calls++;
        return ok({preview: {...preview, request: {id: 9, statusLabel: calls === 1 ? 'Chờ phản hồi' : 'Đã phản hồi', latestReply: calls === 1 ? null : reply}}});
    });
    attach(); await open(bubble);
    assert.equal(button(bubble, 'Xác nhận gửi yêu cầu'), undefined);
    await button(bubble, 'Cập nhật trạng thái').handlers.click();
    assert.equal(calls, 2); assert.equal(find(bubble, n => n.tag === 'blockquote').textContent, reply);
    assert.equal(find(bubble, n => n.tag === 'blockquote').innerHTML, undefined);
    assert.equal(find(bubble, n => n.tag === 'strong').textContent, 'Yêu cầu #9: Đã phản hồi');
});

test('unavailable offers do not mount controls; authentication and network errors use Vietnamese text', async () => {
    const hidden = setup(async () => {throw Error('unexpected');}); hidden.attach(false); assert.equal(hidden.bubble.children.length, 0);
    const login = setup(async () => ({ok: false, status: 401, json: async () => ({message: 'Vui lòng đăng nhập để liên hệ nhân viên hỗ trợ.'})}));
    login.attach(); await open(login.bubble); assert.equal(find(login.bubble, n => n.tag === 'a').href, '/dangnhap');
    assert.equal(button(login.bubble, 'Xác nhận gửi yêu cầu'), undefined);
    const failed = setup(async () => {throw TypeError('Failed to fetch');}); failed.attach(); await open(failed.bubble);
    assert.equal(find(failed.bubble, n => n.attributes.role === 'status').textContent, 'Không thể kết nối máy chủ. Vui lòng thử lại.');
});
