const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const test = require('node:test');
const path = require('node:path');

const html = fs.readFileSync(path.join(__dirname, '../../main/resources/templates/fragments/chatbot.html'), 'utf8');
const start = html.indexOf('        function renderBotMessage(');
const end = html.indexOf('        function escapeHtml(', start);
assert.ok(start >= 0 && end > start);

class Element {
    constructor(tag) { this.tag = tag; this.children = []; this.handlers = {}; }
    append(...nodes) { this.children.push(...nodes); }
    appendChild(node) { this.append(node); }
    addEventListener(event, fn) { this.handlers[event] = fn; }
}
function render(products) {
    const context = {
        document: { createElement: tag => new Element(tag) },
        messages: { scrollHeight: 100 }, formatBotReply: text => text, Intl
    };
    vm.createContext(context);
    vm.runInContext(html.slice(start, end), context);
    const bubble = new Element('div');
    context.renderBotMessage(bubble, 'Server verified reply', products);
    return bubble.children[0];
}
const product = {
    id: 1001, name: '<img onerror=alert(1)>', price: 18000000,
    imageUrl: '/assets/real.jpg', detailUrl: '/product_detail?id=1001',
    available: false, stock: 0, specifications: { RAM: '16GB' }
};

test('chatbot script parses', () => {
    for (const script of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)) new Function(script[1]);
});
test('structured cards escape facts, reject invalid references and label unavailable stock', () => {
    const cards = render([product, { ...product, id: -1 }, { ...product, id: 1002, detailUrl: 'javascript:alert(1)' }]);
    assert.equal(cards.children.length, 1);
    const card = cards.children[0];
    assert.equal(card.children[0].children[1].textContent, product.name);
    assert.equal(card.children[0].children[2].textContent, new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(product.price));
    assert.equal(card.children[1].textContent, 'Hiện không còn hàng');
});
test('missing and broken images show text without invented fallback URLs', () => {
    const imageBox = render([product]).children[0].children[0].children[0];
    assert.equal(imageBox.children[0].src, '/assets/real.jpg');
    imageBox.children[0].handlers.error();
    assert.equal(imageBox.textContent, 'Chưa có ảnh');
    for (const imageUrl of [null, 'https://invented/image.jpg']) {
        const missing = render([{ ...product, imageUrl }]).children[0].children[0].children[0];
        assert.equal(missing.textContent, 'Chưa có ảnh');
        assert.equal(missing.children.length, 0);
    }
});
test('promotion badges appear only for a verified backend percentage', () => {
    const promoted = render([{ ...product, promotionPercent: 10 }]).children[0];
    assert.equal(promoted.children[2].textContent, 'Ưu đãi 10%');
    assert.equal(render([{ ...product, promotionPercent: 0 }]).children[0].children[2].tag, 'dl');
    assert.equal(render([{ ...product, promotionPercent: 101 }]).children[0].children[2].tag, 'dl');
});
