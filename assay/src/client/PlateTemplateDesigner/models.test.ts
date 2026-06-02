/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import {
    computeWarnings,
    GROUP_TYPE_CONTROL,
    GROUP_TYPE_REPLICATE,
    GROUP_TYPE_SPECIMEN,
    PlateTemplate,
    WellGroup,
} from './models';

function makePlate(groups: Partial<WellGroup>[]): PlateTemplate {
    return {
        rowId: 1,
        name: 'Test Plate',
        type: 'assay',
        rows: 8,
        cols: 12,
        groupTypes: [GROUP_TYPE_SPECIMEN, GROUP_TYPE_CONTROL, GROUP_TYPE_REPLICATE],
        canCreateGroupsByType: {},
        groups: groups.map((g, i) => ({
            rowId: i + 1,
            type: GROUP_TYPE_SPECIMEN,
            name: `Group ${i + 1}`,
            positions: [],
            properties: {},
            allowNewGroups: false,
            ...g,
        })),
        plateProperties: {},
        typesToDefaultGroups: {},
        showWarningPanel: true,
        existingTemplateNames: [],
        copyMode: false,
        defaultPlateName: '',
    };
}

describe('computeWarnings', () => {
    test('returns no warnings for an empty plate', () => {
        expect(computeWarnings(makePlate([]))).toEqual([]);
    });

    test('returns no warnings when no cells are assigned', () => {
        const plate = makePlate([
            { type: GROUP_TYPE_REPLICATE, positions: [] },
            { type: GROUP_TYPE_SPECIMEN, positions: [] },
        ]);
        expect(computeWarnings(plate)).toEqual([]);
    });

    test('warns when a REPLICATE cell has no SPECIMEN or CONTROL', () => {
        const plate = makePlate([{ type: GROUP_TYPE_REPLICATE, positions: [{ row: 0, col: 0 }] }]);
        const warnings = computeWarnings(plate);
        expect(warnings).toHaveLength(1);
        expect(warnings[0]).toContain('A1');
        expect(warnings[0]).toContain('replicate');
    });

    test('no warning when REPLICATE cell is also in a SPECIMEN group', () => {
        const plate = makePlate([
            { type: GROUP_TYPE_REPLICATE, positions: [{ row: 0, col: 0 }] },
            { type: GROUP_TYPE_SPECIMEN, positions: [{ row: 0, col: 0 }] },
        ]);
        expect(computeWarnings(plate)).toEqual([]);
    });

    test('no warning when REPLICATE cell is also in a CONTROL group', () => {
        const plate = makePlate([
            { type: GROUP_TYPE_REPLICATE, positions: [{ row: 0, col: 0 }] },
            { type: GROUP_TYPE_CONTROL, positions: [{ row: 0, col: 0 }] },
        ]);
        expect(computeWarnings(plate)).toEqual([]);
    });

    test('warns when a cell is in both SPECIMEN and CONTROL groups', () => {
        const plate = makePlate([
            { type: GROUP_TYPE_SPECIMEN, positions: [{ row: 1, col: 2 }] },
            { type: GROUP_TYPE_CONTROL, positions: [{ row: 1, col: 2 }] },
        ]);
        const warnings = computeWarnings(plate);
        expect(warnings).toHaveLength(1);
        expect(warnings[0]).toContain('B3');
        expect(warnings[0]).toContain('specimen');
        expect(warnings[0]).toContain('control');
    });

    test('cell in SPECIMEN + CONTROL + REPLICATE produces only the specimen/control warning', () => {
        // REPLICATE warning suppressed because CONTROL is present
        const plate = makePlate([
            { type: GROUP_TYPE_SPECIMEN, positions: [{ row: 0, col: 0 }] },
            { type: GROUP_TYPE_CONTROL, positions: [{ row: 0, col: 0 }] },
            { type: GROUP_TYPE_REPLICATE, positions: [{ row: 0, col: 0 }] },
        ]);
        const warnings = computeWarnings(plate);
        expect(warnings).toHaveLength(1);
        expect(warnings[0]).toContain('specimen');
        expect(warnings[0]).toContain('control');
    });

    test('cell in an unrelated group type produces no warning', () => {
        const plate = makePlate([{ type: 'UNKNOWN', positions: [{ row: 0, col: 0 }] }]);
        expect(computeWarnings(plate)).toEqual([]);
    });

    test('produces separate warnings for multiple problem cells', () => {
        const plate = makePlate([
            // Two orphan replicates
            {
                type: GROUP_TYPE_REPLICATE,
                positions: [
                    { row: 0, col: 0 },
                    { row: 0, col: 1 },
                ],
            },
        ]);
        const warnings = computeWarnings(plate);
        expect(warnings).toHaveLength(2);
    });

    test('uses correct spreadsheet cell labels', () => {
        // row 0 col 0  → A1
        // row 1 col 11 → B12
        // row 7 col 11 → H12
        const plate = makePlate([
            {
                type: GROUP_TYPE_REPLICATE,
                positions: [
                    { row: 0, col: 0 },
                    { row: 1, col: 11 },
                    { row: 7, col: 11 },
                ],
            },
        ]);
        const warnings = computeWarnings(plate);
        const labels = warnings.map(w => w.split(':')[0]);
        expect(labels).toContain('A1');
        expect(labels).toContain('B12');
        expect(labels).toContain('H12');
    });

    test('a cell in the same group type twice (two groups, same type) still counts as one type', () => {
        // Two REPLICATE groups covering the same cell — the cell is still only REPLICATE-typed
        const plate = makePlate([
            { type: GROUP_TYPE_REPLICATE, positions: [{ row: 0, col: 0 }] },
            { type: GROUP_TYPE_REPLICATE, positions: [{ row: 0, col: 0 }] },
        ]);
        const warnings = computeWarnings(plate);
        // Only one warning for the cell (not two)
        expect(warnings).toHaveLength(1);
    });
});
