/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import { WellGroup } from './models';
import { assignColors, toggleCell } from './PlateTemplateDesigner';

function makeGroup(rowId: number): WellGroup {
    return { rowId, type: 'SPECIMEN', name: `Group ${rowId}`, positions: [], properties: {}, allowNewGroups: false };
}

function makeGroupAt(rowId: number, type: string, coords: [number, number][]): WellGroup {
    return {
        rowId,
        type,
        name: `Group ${rowId}`,
        positions: coords.map(([row, col]) => ({ row, col })),
        properties: {},
        allowNewGroups: false,
    };
}

describe('assignColors', () => {
    test('returns an empty map for an empty group list', () => {
        expect(assignColors([])).toEqual(new Map());
    });

    test('assigns a color entry for each group', () => {
        const groups = [makeGroup(1), makeGroup(2), makeGroup(3)];
        const map = assignColors(groups);
        expect(map.size).toBe(3);
        expect(map.get(1)).toBeDefined();
        expect(map.get(2)).toBeDefined();
        expect(map.get(3)).toBeDefined();
        expect(map.get(1)?.colorIndex).toBe(0);
        expect(map.get(2)?.colorIndex).toBe(1);
        expect(map.get(3)?.colorIndex).toBe(2);
    });

    test('uses group rowId as the map key, not the array index', () => {
        const groups = [makeGroup(10), makeGroup(20)];
        const map = assignColors(groups);
        expect(map.has(10)).toBe(true);
        expect(map.has(20)).toBe(true);
        expect(map.has(0)).toBe(false);
    });

    test('assigns distinct colors to the first 20 groups', () => {
        const groups = Array.from({ length: 20 }, (_, i) => makeGroup(i + 1));
        const map = assignColors(groups);
        const colors = Array.from(map.values()).map(v => v.color);
        expect(new Set(colors).size).toBe(20);
    });

    test('wraps color assignment after 20 groups (21st group gets same color as 1st)', () => {
        const groups = Array.from({ length: 21 }, (_, i) => makeGroup(i + 1));
        const map = assignColors(groups);
        expect(map.get(21)?.color).toBe(map.get(1)?.color);
        expect(map.get(21)?.colorIndex).toBe(0);
    });

    test('assigns colors in array order, not by rowId value', () => {
        // rowId 99 comes first in the array, so it gets the first color
        const groups = [makeGroup(99), makeGroup(1)];
        const map = assignColors(groups);
        const firstColor = assignColors([makeGroup(1)]).get(1);
        expect(map.get(99)?.color).toBe(firstColor?.color);
        expect(map.get(99)?.colorIndex).toBe(0);
    });
});

describe('toggleCell', () => {
    test('adds cell to active group when the cell is absent', () => {
        const groups = [makeGroupAt(1, 'SPECIMEN', [])];
        const result = toggleCell(groups, 1, 0, 0);
        expect(result[0].positions).toEqual([{ row: 0, col: 0 }]);
    });

    test('removes cell from active group when the cell is already present', () => {
        const groups = [makeGroupAt(1, 'SPECIMEN', [[0, 0]])];
        const result = toggleCell(groups, 1, 0, 0);
        expect(result[0].positions).toEqual([]);
    });

    test('evicts cell from a sibling group of the same type when adding', () => {
        const groups = [
            makeGroupAt(1, 'SPECIMEN', []), // active — does not have the cell
            makeGroupAt(2, 'SPECIMEN', [[0, 0]]), // sibling — owns the cell
        ];
        const result = toggleCell(groups, 1, 0, 0);
        expect(result[0].positions).toEqual([{ row: 0, col: 0 }]); // added to active
        expect(result[1].positions).toEqual([]); // evicted from sibling
    });

    test('does not evict from a group of a different type when adding', () => {
        const groups = [
            makeGroupAt(1, 'SPECIMEN', []),
            makeGroupAt(2, 'CONTROL', [[0, 0]]), // different type — must be untouched
        ];
        const result = toggleCell(groups, 1, 0, 0);
        expect(result[0].positions).toEqual([{ row: 0, col: 0 }]);
        expect(result[1].positions).toEqual([{ row: 0, col: 0 }]); // unchanged
    });

    test('does not evict from sibling groups when removing (toggle off)', () => {
        const groups = [
            makeGroupAt(1, 'SPECIMEN', [[0, 0]]), // active — owns the cell
            makeGroupAt(2, 'SPECIMEN', [[0, 0]]), // sibling — also owns the cell (edge case)
        ];
        const result = toggleCell(groups, 1, 0, 0);
        expect(result[0].positions).toEqual([]); // removed from active
        expect(result[1].positions).toEqual([{ row: 0, col: 0 }]); // sibling unchanged
    });

    test('leaves unrelated cells in the sibling group intact', () => {
        const groups = [
            makeGroupAt(1, 'SPECIMEN', []),
            makeGroupAt(2, 'SPECIMEN', [
                [0, 0],
                [1, 1],
            ]), // sibling owns (0,0) and (1,1)
        ];
        const result = toggleCell(groups, 1, 0, 0);
        expect(result[0].positions).toEqual([{ row: 0, col: 0 }]);
        expect(result[1].positions).toEqual([{ row: 1, col: 1 }]); // only (0,0) evicted
    });

    test('returns groups unchanged when activeGroupRowId is not found', () => {
        const groups = [makeGroupAt(1, 'SPECIMEN', [])];
        expect(toggleCell(groups, 99, 0, 0)).toEqual(groups);
    });
});
