import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { formatMoney, formatMoneyInput } from '../src/utils/money.js';

const MONEY_VECTORS = [
    [39.91, '39.91'], ['39.91', '39.91'], ['39.9100', '39.91'],
    [39.90999999999999, '39.91'], ['39.90999999999999', '39.9099'],
    [0.1 + 0.2, '0.30'], [9.1132, '9.1132'], [0.0001, '0.0001'],
    [-39.91, '-39.91'], ['-9.1132', '-9.1132'], [1000, '1,000.00'],
    ['1234567.89', '1,234,567.89'], [9999999999.9999, '9,999,999,999.9999'],
    ['9999999999.9999', '9,999,999,999.9999'], ['9999999999.9999999', '9,999,999,999.9999'],
    [1.2345999, '1.2345'], ['1.2345999', '1.2345'], ['-1.2345999', '-1.2345'],
    ['00039.9100', '39.91'], ['.0056', '0.0056'], ['39.', '39.00'], ['0', '0.00'], [-0.00001, '0.00'],
];
const SCIENTIFIC_VECTORS = [
  ['3.991e1', '39.91'], ['-3.991e1', '-39.91'], ['1e3', '1,000.00'], ['1e-4', '0.0001'],
  ['1.2345999e0', '1.2345'], ['8.658e-1', '0.8658'], ['1e-5', '0.00'],
  ['1e-1000000000', '0.00'], ['0e+1000000000', '0.00'],
];

test('money display cleans Number tail noise while preserving exact decimal strings and real four-place values', () => {
  for (const [input, expected] of MONEY_VECTORS) {
    assert.equal(formatMoney(input), expected, `formatMoney(${String(input)})`);
    assert.equal(formatMoneyInput(input), expected.replaceAll(',', ''), `formatMoneyInput(${String(input)})`);
  }
});

test('scientific money notation expands without changing the truncation rule or allocating enormous exponent strings', () => {
  for (const [input, expected] of SCIENTIFIC_VECTORS) assert.equal(formatMoney(input), expected);
  for (const invalid of [NaN, Infinity, -Infinity, undefined, null, {}, '', 'NaN', 'Infinity', '1e1000000000',
    '39.91元', '1,000.00', '0x10', '1.2.3']) {
    assert.equal(formatMoney(invalid), '0.00'); assert.equal(formatMoneyInput(invalid), '0.00');
  }
});

test('Web and API format the same decimal vectors consistently without loading database modules', () => {
  const apiRequire = createRequire(new URL('../../api/package.json', import.meta.url));
  const { formatCNY } = apiRequire('./src/common/utils/currency.js');
  for (const [input, expected] of [...MONEY_VECTORS, ...SCIENTIFIC_VECTORS]) {
    assert.equal(formatCNY(input), expected); assert.equal(formatMoney(input), formatCNY(input));
  }
});

test('all cent values through 100 retain their exact amount rather than losing a ten-thousandth', () => {
  for (let cents = 1; cents <= 10000; cents++) {
    const expected = `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, '0')}`;
    assert.equal(formatMoney(cents / 100), expected);
  }
});

test('the actual full-repayment button fills ungrouped values that the actual submit function sends exactly', async () => {
  const source = await readFile(new URL('../src/views/BankCard/repay/Add.vue', import.meta.url), 'utf8');
  const fillBody = source.match(/const fillFullAmount = \(\) => \{([\s\S]*?)\n\};/)[1];
  const submitBody = source.slice(source.indexOf('const onSubmit = async () => {') + 'const onSubmit = async () => {'.length,
    source.indexOf('\n};', source.indexOf('const onSubmit = async () => {')));
  const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
  for (const [amount, expected] of [[39.91, 39.91], [1000, 1000], [1234.5678, 1234.5678],
    [39.90999999999999, 39.91], [0.1 + 0.7, 0.8]]) {
    const formData = { repayAmount: '0', cardId: 'fixture-credit', billId: 'fixture-bill', repayMethod: 'cash',
      repayTime: '2026-11-06', billMonth: '', remark: '' };
    const oweAmount = { value: amount };
    Function('formatMoney', 'formatMoneyInput', 'formData', 'oweAmount', fillBody)(formatMoney, formatMoneyInput, formData, oweAmount);
    assert.ok(!formData.repayAmount.includes(','));
    const submissions = [];
    const toast = value => { if (typeof value === 'string') throw new Error('Valid repayment must not be rejected: ' + value); };
    const submit = new AsyncFunction('formData', 'oweAmount', 'isMergeMode', 'loading', 'showToast', 'showLoadingToast',
      'closeToast', 'formatMoney', 'formatMoneyInput', 'createRepay', 'router', 'route', 'mergeRepay', 'apiErrorToast', submitBody);
    await submit(formData, oweAmount, { value: false }, { value: false }, toast, () => {}, () => {}, formatMoney, formatMoneyInput,
      async payload => { submissions.push(payload); }, { back() {} }, { query: {} }, async () => {
        throw new Error('Single repayment must not call merge');
      }, error => { throw error; });
    assert.equal(submissions.length, 1);
    assert.equal(submissions[0].repayAmount, expected);
    assert.equal(submissions[0].billId, 'fixture-bill');
  }
});

test('shared repayment loads an ungrouped total but leaves the actual amount calculation to the merge endpoint', async () => {
  const source = await readFile(new URL('../src/views/BankCard/repay/Add.vue', import.meta.url), 'utf8');
  const functionBody = name => {
    const declaration = `const ${name} = async () => {`;
    const start = source.indexOf(declaration);
    assert.ok(start >= 0);
    return source.slice(start + declaration.length, source.indexOf('\n};', start));
  };
  const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
  const formData = { repayAmount: '0', repayMethod: 'cash', repayTime: '2026-11-06', remark: '' };
  const route = { query: { mergePoolId: 'fixture-pool' } };
  const oweAmount = { value: 0 }; const mergePool = { value: null };
  const selectedCardName = { value: '' }; const selectedBillName = { value: '' };
  const loadDependencies = { route, formData, oweAmount, mergePool, selectedCardName, selectedBillName,
    formatMoney, formatMoneyInput, console: { error(error) { throw error; } },
    getCreditPools: async () => ({ data: [{ id: 'fixture-pool', bank_name: '测试银行' }] }),
    getCardList: async () => ({ data: [{ id: 'fixture-card', share_pool_id: 'fixture-pool' }] }),
    getBillList: async () => ({ data: [{ id: 'fixture-bill', card_id: 'fixture-card', need_repay: '1039.9100' }] }) };
  await new AsyncFunction(...Object.keys(loadDependencies), functionBody('loadBillData'))(...Object.values(loadDependencies));
  assert.equal(oweAmount.value, 1039.91); assert.equal(formData.repayAmount, '1039.91');
  assert.equal(selectedBillName.value, '共享额度待还 ¥1,039.91');
  const submissions = [];
  const submitDependencies = { formData, route, oweAmount, isMergeMode: { value: true }, loading: { value: false },
    formatMoney, formatMoneyInput, router: { back() {} }, closeToast() {}, showLoadingToast() {},
    showToast(value) { if (typeof value === 'string') throw new Error(value); }, apiErrorToast(error) { throw error; },
    createRepay() { throw new Error('Shared repayment must not submit a single repayment amount'); },
    mergeRepay: async payload => { submissions.push(payload); } };
  await new AsyncFunction(...Object.keys(submitDependencies), functionBody('onSubmit'))(...Object.values(submitDependencies));
  assert.deepEqual(submissions, [{ poolId: 'fixture-pool', repayMethod: 'cash', repayTime: '2026-11-06' }]);
});
