/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.vis = {};
require('../../../webapp/vis/src/statistics.js');

describe('LABKEY.vis.Stats', () => {
    test('getMean', () => {
        expect(LABKEY.vis.Stat.getMean([1, 2, 3, 4, 5])).toBe(3);
        expect(LABKEY.vis.Stat.getMean([-1, 0, 1])).toBe(0);
        expect(LABKEY.vis.Stat.getMean([1.123])).toBe(1.123);
    });

    test('getStdDev', () => {
        expect(LABKEY.vis.Stat.getStdDev([1, 2, 3, 4, 5])).toBeCloseTo(1.4142, 4);
        expect(LABKEY.vis.Stat.getStdDev([1, 2, 3, 4, 5], true)).toBeCloseTo(1.5811, 4);
        expect(LABKEY.vis.Stat.getStdDev([-1, 0, 1])).toBeCloseTo(0.8165, 4);
        expect(LABKEY.vis.Stat.getStdDev([-1, 0, 1], true)).toBeCloseTo(1, 4);
        expect(LABKEY.vis.Stat.getStdDev([1.123])).toBe(0);

        expect(LABKEY.vis.Stat.getStdDev([])).toBe(undefined);
        expect(LABKEY.vis.Stat.getStdDev([1.123], true)).toBe(undefined);
    });

    test('getStdErr', () => {
        expect(LABKEY.vis.Stat.getStdErr([1, 2, 3, 4, 5])).toBeCloseTo(0.6325, 4);
        expect(LABKEY.vis.Stat.getStdErr([1, 2, 3, 4, 5], true)).toBeCloseTo(0.7071, 4);
        expect(LABKEY.vis.Stat.getStdErr([-1, 0, 1])).toBeCloseTo(0.4714, 4);
        expect(LABKEY.vis.Stat.getStdErr([-1, 0, 1], true)).toBeCloseTo(0.5774, 4);
        expect(LABKEY.vis.Stat.getStdErr([1.123])).toBe(0);

        expect(LABKEY.vis.Stat.getStdErr([])).toBe(undefined);
        expect(LABKEY.vis.Stat.getStdErr([1.123], true)).toBe(undefined);
    });
});