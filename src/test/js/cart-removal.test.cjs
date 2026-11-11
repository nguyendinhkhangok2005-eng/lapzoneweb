const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

// Exercise the actual template script without adding frontend dependencies.
const html = fs.readFileSync(path.join(__dirname, '../../main/resources/templates/cart.html'), 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];

function fixture(items = [{ id: 1, price: 100, checked: true }, { id: 2, price: 400, checked: true }]) {
    const elements = Object.fromEntries(['selectAll', 'cartTotalDisplay', 'checkoutButton',
        'cartContents', 'emptyCart', 'cartRemovalFeedback'].map(id => [id, {
        hidden: id === 'emptyCart', disabled: false, dataset: { removeUrl: '/cart/remove' },
        classList: { add() {}, remove() {} }, setAttribute() {}, addEventListener() {}
    }]));
    const controls = [{ disabled: false }, { disabled: true }];
    const rows = items.map(item => ({
        id: item.id, removed: false,
        checkbox: { checked: item.checked, dataset: { price: String(item.price) }, addEventListener() {} },
        remove() { this.removed = true; }
    }));
    let calls = 0;
    let resolveRequest;
    const document = {
        getElementById: id => elements[id],
        addEventListener: (_, fn) => fn(),
        querySelectorAll(selector) {
            if (selector === '.btn-delete, .qty-btn') return controls;
            if (selector === '.cart-count') return [elements.badge];
            const checkboxes = rows.filter(row => !row.removed).map(row => row.checkbox);
            return selector.endsWith(':checked') ? checkboxes.filter(cb => cb.checked) : checkboxes;
        },
        querySelector(selector) {
            if (selector === '.item-checkbox') return rows.find(row => !row.removed)?.checkbox;
            const id = Number(selector.match(/"(\d+)"/)[1]);
            return rows.find(row => row.id === id && !row.removed);
        }
    };
    elements.badge = { textContent: 3 };
    const context = vm.createContext({ document, URLSearchParams,
        confirm: () => true, alert: () => {},
        fetch(url, options) {
            calls++;
            assert.equal(url, '/cart/remove');
            assert.equal(options.headers['X-Requested-With'], 'XMLHttpRequest');
            assert.equal(options.body.get('cartItemId'), '1');
            return new Promise(resolve => { resolveRequest = resolve; });
        }
    });
    vm.runInContext(script, context);
    return { context, rows, elements, controls, calls: () => calls,
        respond: (ok, result) => resolveRequest({ ok, json: async () => result }) };
}

test('cancel preserves cart and sends no request', async () => {
    const f = fixture();
    f.context.confirm = () => false;
    await f.context.removeItem(1);
    assert.equal(f.calls(), 0);
    assert.ok(f.rows.every(row => !row.removed));
    assert.equal(f.elements.cartTotalDisplay.innerText, '500 ₫');
});

test('waits for success, suppresses duplicate requests, updates total and header', async () => {
    const f = fixture();
    const pending = f.context.removeItem(1);
    await f.context.removeItem(1);
    assert.equal(f.calls(), 1);
    assert.equal(f.rows[0].removed, false);
    assert.equal(f.elements.checkoutButton.disabled, true);
    assert.equal(f.context.validateCartForm(), false);
    f.respond(true, { message: 'Đã xóa sản phẩm khỏi giỏ hàng.', cartItemCount: 2 });
    await pending;
    assert.equal(f.rows[0].removed, true);
    assert.equal(f.rows[1].removed, false);
    assert.equal(f.elements.cartTotalDisplay.innerText, '400 ₫');
    assert.equal(f.elements.badge.textContent, 2);
    assert.equal(f.elements.checkoutButton.disabled, false);
    assert.equal(f.elements.selectAll.checked, true);
    assert.equal(f.controls[0].disabled, false);
    assert.equal(f.controls[1].disabled, true);
    assert.equal(f.elements.cartRemovalFeedback.textContent, 'Đã xóa sản phẩm khỏi giỏ hàng.');
});

test('backend failure preserves row, choices, header and totals and allows retry', async () => {
    const f = fixture();
    const pending = f.context.removeItem(1);
    f.respond(false, { message: 'Không thể xóa sản phẩm. Vui lòng thử lại.' });
    await pending;
    assert.ok(f.rows.every(row => !row.removed));
    assert.equal(f.elements.cartTotalDisplay.innerText, '500 ₫');
    assert.equal(f.elements.badge.textContent, 3);
    assert.equal(f.elements.cartRemovalFeedback.hidden, false);
    assert.equal(f.elements.cartRemovalFeedback.textContent, 'Không thể xóa sản phẩm. Vui lòng thử lại.');
    const retry = f.context.removeItem(1);
    assert.equal(f.calls(), 2);
    f.respond(true, { message: 'Đã xóa sản phẩm khỏi giỏ hàng.', cartItemCount: 2 });
    await retry;
    assert.equal(f.rows[0].removed, true);
});

test('network failure preserves cart and shows Vietnamese fallback', async () => {
    const f = fixture();
    f.context.fetch = async () => { throw new TypeError('Failed to fetch'); };
    await f.context.removeItem(1);
    assert.equal(f.rows[0].removed, false);
    assert.match(f.elements.cartRemovalFeedback.textContent, /^Không thể xóa sản phẩm/);
    assert.equal(f.elements.checkoutButton.disabled, false);
});

test('last removal shows empty cart, clears total and disables checkout', async () => {
    const f = fixture([{ id: 1, price: 100, checked: true }]);
    const pending = f.context.removeItem(1);
    f.respond(true, { message: 'Đã xóa sản phẩm khỏi giỏ hàng.', cartItemCount: 0 });
    await pending;
    assert.equal(f.elements.cartContents.hidden, true);
    assert.equal(f.elements.emptyCart.hidden, false);
    assert.equal(f.elements.cartTotalDisplay.innerText, '0 ₫');
    assert.equal(f.elements.badge.textContent, 0);
    assert.equal(f.elements.checkoutButton.disabled, true);
    assert.equal(f.elements.selectAll.checked, false);
    assert.equal(f.elements.selectAll.indeterminate, false);
    assert.equal(f.context.validateCartForm(), false);
});

test('selected removal preserves unselected items and recalculates checkbox state', async () => {
    const f = fixture([{ id: 1, price: 100, checked: true }, { id: 2, price: 400, checked: false }]);
    assert.equal(f.elements.selectAll.indeterminate, true);
    const pending = f.context.removeItem(1);
    f.respond(true, { message: 'Đã xóa sản phẩm khỏi giỏ hàng.', cartItemCount: 2 });
    await pending;
    assert.equal(f.rows[1].checkbox.checked, false);
    assert.equal(f.elements.cartTotalDisplay.innerText, '0 ₫');
    assert.equal(f.elements.selectAll.indeterminate, false);
    assert.equal(f.elements.selectAll.checked, false);
    assert.equal(f.elements.checkoutButton.disabled, true);
});
