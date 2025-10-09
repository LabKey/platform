LABKEY.vis = {};
require('../../../webapp/vis/src/statistics.js');
require('../../../webapp/vis/src/utils.js');

describe('LABKEY.vis.getValue', () => {
    test('value not object', () => {
        expect(LABKEY.vis.getValue()).toBeUndefined();
        expect(LABKEY.vis.getValue(undefined)).toBeUndefined();
        expect(LABKEY.vis.getValue(null)).toBeNull();
        expect(LABKEY.vis.getValue(5)).toBe(5);
        expect(LABKEY.vis.getValue('test')).toBe('test');
    });

    test('value is object', () => {
        expect(LABKEY.vis.getValue({})).toBeUndefined();
        expect(LABKEY.vis.getValue({ value: undefined })).toBeUndefined();
        expect(LABKEY.vis.getValue({ value: null })).toBeNull();
        expect(LABKEY.vis.getValue({ value: 5 })).toBe(5);
        expect(LABKEY.vis.getValue({ value: 'test' })).toBe('test');
        expect(LABKEY.vis.getValue({ value: 'test', other: 1 })).toBe('test');
    });

    test('formattedValue, displayValue, preferredProp', () => {
        expect(LABKEY.vis.getValue({ formattedValue: 'formatted', displayValue: 'display', value: 'value' })).toBe('formatted');
        expect(LABKEY.vis.getValue({ formattedValue: null, displayValue: 'display', value: 'value' })).toBe(null);
        expect(LABKEY.vis.getValue({ formattedValue: undefined, displayValue: 'display', value: 'value' })).toBe(undefined);
        expect(LABKEY.vis.getValue({ displayValue: 'display', value: 'value' })).toBe('display');
        expect(LABKEY.vis.getValue({ displayValue: null, value: 'value' })).toBe(null);
        expect(LABKEY.vis.getValue({ displayValue: undefined, value: 'value' })).toBe(undefined);
        expect(LABKEY.vis.getValue({ value: 'value' })).toBe('value');
        expect(LABKEY.vis.getValue({ value: null })).toBeNull();
        expect(LABKEY.vis.getValue({ value: undefined })).toBeUndefined();

        expect(LABKEY.vis.getValue({ formattedValue: 'formatted', displayValue: 'display', value: 'value' }, 'bogus')).toBe('formatted');
        expect(LABKEY.vis.getValue({ formattedValue: 'formatted', displayValue: 'display', value: 'value' }, 'formattedValue')).toBe('formatted');
        expect(LABKEY.vis.getValue({ formattedValue: 'formatted', displayValue: 'display', value: 'value' }, 'displayValue')).toBe('display');
        expect(LABKEY.vis.getValue({ formattedValue: 'formatted', displayValue: 'display', value: 'value' }, 'value')).toBe('value');
    });
});

describe('LABKEY.vis.getAggregateData', () => {
    const data = [
        { main: 'A', sub: 'a', value: 10 },
        { main: 'A', sub: 'b', value: 20 },
        { main: 'A', sub: 'b', value: 15 },
        { main: 'B', sub: 'a', value: 30 },
    ];

    test('without subgroup', () => {
        let results = LABKEY.vis.getAggregateData(data, 'main', undefined, 'value');
        expect(results).toStrictEqual([
            { label: 'A', value: 3 },
            { label: 'B', value: 1 },
        ]);
        results = LABKEY.vis.getAggregateData(data, 'main', undefined, 'value', 'COUNT');
        expect(results).toStrictEqual([
            { label: 'A', value: 3 },
            { label: 'B', value: 1 },
        ]);
        results = LABKEY.vis.getAggregateData(data, 'main', undefined, 'value', 'MEAN');
        expect(results).toStrictEqual([
            { aggType: 'MEAN', label: 'A', value: 15 },
            { aggType: 'MEAN', label: 'B', value: 30 },
        ]);
    });

    test('with subgroup', () => {
        let results = LABKEY.vis.getAggregateData(data, 'main', 'sub', 'value');
        expect(results).toStrictEqual([
            { label: 'A', subLabel: 'a', value: 1 },
            { label: 'A', subLabel: 'b', value: 2 },
            { label: 'B', subLabel: 'a', value: 1 },
        ]);
        results = LABKEY.vis.getAggregateData(data, 'main', 'sub', 'value', 'MEAN');
        expect(results).toStrictEqual([
            { aggType: 'MEAN', label: 'A', subLabel: 'a', value: 10 },
            { aggType: 'MEAN', label: 'A', subLabel: 'b', value: 17.5 },
            { aggType: 'MEAN', label: 'B', subLabel: 'a', value: 30 },
        ]);
    });

    test('errorBarType', () => {
        const data2 = [
            { main: 'A', sub: 'a', value: 10 },
            { main: 'A', sub: 'b', value: 1 },
            { main: 'A', sub: 'b', value: 2 },
            { main: 'A', sub: 'b', value: 3 },
            { main: 'B', sub: 'a', value: 30 },
        ];
        let results = LABKEY.vis.getAggregateData(data2, 'main', 'sub', 'value', 'COUNT', undefined, false, 'SD');
        expect(results).toStrictEqual([
            { label: 'A', subLabel: 'a', value: 1 },
            { label: 'A', subLabel: 'b', value: 3 },
            { label: 'B', subLabel: 'a', value: 1 },
        ]);
        results = LABKEY.vis.getAggregateData(data2, 'main', 'sub', 'value', 'MEAN', undefined, false, 'SD');
        expect(results).toStrictEqual([
            { aggType: 'MEAN', label: 'A', subLabel: 'a', value: 10, errorType: 'SD', error: undefined },
            { aggType: 'MEAN', label: 'A', subLabel: 'b', value: 2, errorType: 'SD', error: 1 },
            { aggType: 'MEAN', label: 'B', subLabel: 'a', value: 30, errorType: 'SD', error: undefined },
        ]);
        results = LABKEY.vis.getAggregateData(data2, 'main', 'sub', 'value', 'MEAN', undefined, false, 'SEM');
        expect(results[1].errorType).toBe('SEM');
        expect(results[1].error).toBeCloseTo(0.5774, 4);
    });

    test('includeTotal', () => {
        let results = LABKEY.vis.getAggregateData(data, 'main', 'sub', 'value', 'COUNT', undefined, true);
        expect(results).toStrictEqual([
            { label: 'A', subLabel: 'a', value: 1, total: 1 },
            { label: 'A', subLabel: 'b', value: 2, total: 3 },
            { label: 'B', subLabel: 'a', value: 1, total: 4 },
        ]);
        results = LABKEY.vis.getAggregateData(data, 'main', 'sub', 'value', 'MEAN', undefined, true);
        expect(results).toStrictEqual([
            { aggType: 'MEAN', label: 'A', subLabel: 'a', value: 10, total: 1 },
            { aggType: 'MEAN', label: 'A', subLabel: 'b', value: 17.5, total: 3 },
            { aggType: 'MEAN', label: 'B', subLabel: 'a', value: 30, total: 4 },
        ]);
    });

    test('keepNames', () => {
        let results = LABKEY.vis.getAggregateData(data, 'main', 'sub', 'value', 'COUNT', undefined, false, undefined, true);
        expect(results).toStrictEqual([
            { label: 'A', main: { value: 'A' }, subLabel: 'a', sub: { value: 'a' }, value: { aggType: 'COUNT', value: 1 } },
            { label: 'A', main: { value: 'A' }, subLabel: 'b', sub: { value: 'b' }, value: { aggType: 'COUNT', value: 2 } },
            { label: 'B', main: { value: 'B' }, subLabel: 'a', sub: { value: 'a' }, value: { aggType: 'COUNT', value: 1 } },
        ]);
        results = LABKEY.vis.getAggregateData(data, 'main', 'sub', 'value', 'MEAN', undefined, false, undefined, true);
        expect(results).toStrictEqual([
            { label: 'A', main: { value: 'A' }, subLabel: 'a', sub: { value: 'a' }, value: { aggType: 'MEAN', value: 10 }, aggType: 'MEAN' },
            { label: 'A', main: { value: 'A' }, subLabel: 'b', sub: { value: 'b' }, value: { aggType: 'MEAN', value: 17.5 }, aggType: 'MEAN' },
            { label: 'B', main: { value: 'B' }, subLabel: 'a', sub: { value: 'a' }, value: { aggType: 'MEAN', value: 30 }, aggType: 'MEAN' },
        ]);
    });

    test('nullDisplayValue', () => {
        const dataWithNulls = [
            { main: 'A', sub: 'a', value: 10 },
            { main: null, sub: 'b', value: 20 },
            { main: 'A', sub: null, value: 15 },
            { main: 'B', sub: 'a', value: 30 },
            { main: null, sub: null, value: 5 },
        ];
        let results = LABKEY.vis.getAggregateData(dataWithNulls, 'main', 'sub', 'value', 'COUNT', '(empty)');
        expect(results).toStrictEqual([
            { label: 'A', subLabel: 'a', value: 1 },
            { label: 'A', subLabel: '(empty)', value: 1 },
            { label: '(empty)', subLabel: 'b', value: 1 },
            { label: '(empty)', subLabel: '(empty)', value: 1 },
            { label: 'B', subLabel: 'a', value: 1 },
        ]);
    });

    test('no values', () => {
        const dataWithNulls = [
            { main: 'A', sub: 'a', value: undefined },
        ];
        expect(LABKEY.vis.getAggregateData(dataWithNulls, 'main', 'sub', 'value', 'COUNT')).toStrictEqual([{ label: 'A', subLabel: 'a', value: 0 }]);
        expect(LABKEY.vis.getAggregateData(dataWithNulls, 'main', 'sub', 'value', 'SUM')).toStrictEqual([{ aggType: 'SUM', label: 'A', subLabel: 'a', value: null }]);
        expect(LABKEY.vis.getAggregateData(dataWithNulls, 'main', 'sub', 'value', 'MIN')).toStrictEqual([{ aggType: 'MIN', label: 'A', subLabel: 'a', value: null }]);
        expect(LABKEY.vis.getAggregateData(dataWithNulls, 'main', 'sub', 'value', 'MAX')).toStrictEqual([{ aggType: 'MAX', label: 'A', subLabel: 'a', value: null }]);
        expect(LABKEY.vis.getAggregateData(dataWithNulls, 'main', 'sub', 'value', 'MEAN')).toStrictEqual([{ aggType: 'MEAN', label: 'A', subLabel: 'a', value: null }]);
        expect(LABKEY.vis.getAggregateData(dataWithNulls, 'main', 'sub', 'value', 'MEDIAN')).toStrictEqual([{ aggType: 'MEDIAN', label: 'A', subLabel: 'a', value: null }]);
    });
});