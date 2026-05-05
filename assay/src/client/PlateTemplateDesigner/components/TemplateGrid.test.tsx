/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';

import { PlateTemplate, WellGroup } from '../models';
import { TemplateGrid } from './TemplateGrid';

function makePlate(rows = 4, cols = 4, groups: WellGroup[] = []): PlateTemplate {
    return {
        rowId: 1,
        name: 'Test',
        type: 'assay',
        rows,
        cols,
        groupTypes: ['SPECIMEN'],
        canCreateGroupsByType: {},
        groups,
        plateProperties: {},
        typesToDefaultGroups: {},
        showWarningPanel: false,
        existingTemplateNames: [],
        copyMode: false,
        defaultPlateName: '',
    };
}

function renderGrid(overrides: Partial<React.ComponentProps<typeof TemplateGrid>> = {}) {
    const props = {
        plate: makePlate(),
        activeGroup: null,
        activeTab: 'SPECIMEN',
        colorMap: new Map<number, { color: string; colorIndex: number }>(),
        highlightedGroupId: null as number | null,
        onDragRect: jest.fn(),
        onCellToggle: jest.fn(),
        onWellHover: jest.fn(),
        ...overrides,
    };
    render(<TemplateGrid {...props} />);
    return props;
}

// Helpers for getting cells by their aria-label (e.g. "A1", "B3")
function getCell(label: string) {
    return screen.getByLabelText(label);
}

describe('TemplateGrid — rendering', () => {
    test('renders the correct number of data cells', () => {
        renderGrid({ plate: makePlate(3, 4) });
        // 3 rows × 4 cols = 12 cells, all labeled A1..C4
        expect(screen.getByLabelText('A1')).toBeInTheDocument();
        expect(screen.getByLabelText('C4')).toBeInTheDocument();
    });

    test('renders column headers 1..cols', () => {
        renderGrid({ plate: makePlate(2, 3) });
        expect(screen.getByText('1')).toBeInTheDocument();
        expect(screen.getByText('2')).toBeInTheDocument();
        expect(screen.getByText('3')).toBeInTheDocument();
    });

    test('renders row headers A..H for 8 rows', () => {
        renderGrid({ plate: makePlate(8, 1) });
        ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'].forEach(letter => {
            expect(screen.getAllByText(letter)[0]).toBeInTheDocument();
        });
    });

    test('cell aria-label includes group name when cell is assigned', () => {
        const group: WellGroup = {
            rowId: 1, type: 'SPECIMEN', name: 'Sample 1',
            positions: [{ row: 0, col: 0 }], properties: {}, allowNewGroups: false,
        };
        renderGrid({
            plate: makePlate(2, 2, [group]),
            colorMap: new Map([[1, { color: '#ff0000', colorIndex: 1 }]]),
        });
        expect(screen.getByLabelText('A1: Sample 1')).toBeInTheDocument();
    });

    test('cell tooltip is just the location when unassigned', () => {
        renderGrid({ plate: makePlate(2, 2) });
        expect(screen.getByLabelText('B2')).toBeInTheDocument();
    });
});

describe('TemplateGrid — roving tabindex', () => {
    test('cell (0,0) has tabIndex=0 before any focus interaction', () => {
        renderGrid();
        expect(getCell('A1')).toHaveAttribute('tabindex', '0');
        expect(getCell('A2')).toHaveAttribute('tabindex', '-1');
    });

    test('all other cells have tabIndex=-1 initially', () => {
        renderGrid({ plate: makePlate(2, 2) });
        ['A2', 'B1', 'B2'].forEach(label => {
            expect(getCell(label)).toHaveAttribute('tabindex', '-1');
        });
    });

    test('focusing a cell updates the tab stop to that cell', () => {
        renderGrid({ plate: makePlate(2, 2) });
        fireEvent.focus(getCell('B2'));
        expect(getCell('B2')).toHaveAttribute('tabindex', '0');
        expect(getCell('A1')).toHaveAttribute('tabindex', '-1');
    });
});

describe('TemplateGrid — keyboard navigation', () => {
    test('ArrowRight moves focus from A1 to A2', () => {
        renderGrid();
        fireEvent.keyDown(getCell('A1'), { key: 'ArrowRight' });
        expect(getCell('A2')).toHaveAttribute('tabindex', '0');
        expect(getCell('A1')).toHaveAttribute('tabindex', '-1');
    });

    test('ArrowDown moves focus from A1 to B1', () => {
        renderGrid();
        fireEvent.keyDown(getCell('A1'), { key: 'ArrowDown' });
        expect(getCell('B1')).toHaveAttribute('tabindex', '0');
    });

    test('ArrowLeft moves focus from A2 to A1', () => {
        renderGrid();
        fireEvent.focus(getCell('A2'));
        fireEvent.keyDown(getCell('A2'), { key: 'ArrowLeft' });
        expect(getCell('A1')).toHaveAttribute('tabindex', '0');
    });

    test('ArrowUp moves focus from B1 to A1', () => {
        renderGrid();
        fireEvent.focus(getCell('B1'));
        fireEvent.keyDown(getCell('B1'), { key: 'ArrowUp' });
        expect(getCell('A1')).toHaveAttribute('tabindex', '0');
    });

    test('ArrowLeft at column 0 does nothing', () => {
        renderGrid();
        fireEvent.keyDown(getCell('A1'), { key: 'ArrowLeft' });
        // A1 should remain the tab stop
        expect(getCell('A1')).toHaveAttribute('tabindex', '0');
    });

    test('ArrowUp at row 0 does nothing', () => {
        renderGrid();
        fireEvent.keyDown(getCell('A1'), { key: 'ArrowUp' });
        expect(getCell('A1')).toHaveAttribute('tabindex', '0');
    });

    test('ArrowDown at last row does nothing', () => {
        renderGrid({ plate: makePlate(4, 4) });
        fireEvent.focus(getCell('D1'));
        fireEvent.keyDown(getCell('D1'), { key: 'ArrowDown' });
        expect(getCell('D1')).toHaveAttribute('tabindex', '0');
    });

    test('ArrowRight at last column does nothing', () => {
        renderGrid({ plate: makePlate(4, 4) });
        fireEvent.focus(getCell('A4'));
        fireEvent.keyDown(getCell('A4'), { key: 'ArrowRight' });
        expect(getCell('A4')).toHaveAttribute('tabindex', '0');
    });
});

describe('TemplateGrid — keyboard cell toggle', () => {
    test('Space key calls onCellToggle with the cell coordinates', () => {
        const { onCellToggle } = renderGrid();
        fireEvent.keyDown(getCell('A1'), { key: ' ' });
        expect(onCellToggle).toHaveBeenCalledWith(0, 0);
    });

    test('Enter key calls onCellToggle with the cell coordinates', () => {
        const { onCellToggle } = renderGrid();
        fireEvent.keyDown(getCell('B3'), { key: 'Enter' });
        expect(onCellToggle).toHaveBeenCalledWith(1, 2);
    });

    test('other keys do not call onCellToggle', () => {
        const { onCellToggle } = renderGrid();
        fireEvent.keyDown(getCell('A1'), { key: 'Tab' });
        expect(onCellToggle).not.toHaveBeenCalled();
    });
});

describe('TemplateGrid — mouse click (no drag)', () => {
    test('mousedown + mouseup on the same cell calls onCellToggle', () => {
        const { onCellToggle } = renderGrid();
        const cell = getCell('A1');
        fireEvent.mouseDown(cell, { button: 0 });
        fireEvent.mouseUp(cell);
        expect(onCellToggle).toHaveBeenCalledWith(0, 0);
    });

    test('right-click (button !== 0) does not start a drag', () => {
        const { onCellToggle, onDragRect } = renderGrid();
        const cell = getCell('A1');
        fireEvent.mouseDown(cell, { button: 2 });
        fireEvent.mouseUp(cell);
        expect(onCellToggle).not.toHaveBeenCalled();
        expect(onDragRect).not.toHaveBeenCalled();
    });
});

describe('TemplateGrid — mouse drag', () => {
    test('mousedown then mouseenter a different cell calls onDragRect (not onCellToggle)', () => {
        const { onDragRect, onCellToggle } = renderGrid();
        fireEvent.mouseDown(getCell('A1'), { button: 0 });
        fireEvent.mouseEnter(getCell('B2'));
        expect(onDragRect).toHaveBeenCalledWith(0, 0, 1, 1, false, []);
        expect(onCellToggle).not.toHaveBeenCalled();
    });

    test('drag started on a cell already in the active group uses unselect mode', () => {
        const activeGroup: WellGroup = {
            rowId: 1, type: 'SPECIMEN', name: 'Sample 1',
            positions: [{ row: 0, col: 0 }], properties: {}, allowNewGroups: false,
        };
        const { onDragRect } = renderGrid({ activeGroup });
        fireEvent.mouseDown(getCell('A1'), { button: 0 });
        fireEvent.mouseEnter(getCell('B2'));
        // isUnselect should be true since A1 is already in the active group
        expect(onDragRect).toHaveBeenCalledWith(0, 0, 1, 1, true, activeGroup.positions);
    });

    test('drag started on an empty cell uses select mode', () => {
        const activeGroup: WellGroup = {
            rowId: 1, type: 'SPECIMEN', name: 'Sample 1',
            positions: [], properties: {}, allowNewGroups: false,
        };
        const { onDragRect } = renderGrid({ activeGroup });
        fireEvent.mouseDown(getCell('A1'), { button: 0 });
        fireEvent.mouseEnter(getCell('B2'));
        expect(onDragRect).toHaveBeenCalledWith(0, 0, 1, 1, false, []);
    });

    test('mouseleave the grid resets drag state so subsequent mouseenter does not fire', () => {
        const { onDragRect } = renderGrid();
        const grid = document.querySelector('.template-grid') as HTMLElement;
        fireEvent.mouseDown(getCell('A1'), { button: 0 });
        fireEvent.mouseLeave(grid);  // drag cancelled
        fireEvent.mouseEnter(getCell('B2'));  // should be ignored
        expect(onDragRect).not.toHaveBeenCalled();
    });

    test('mouseup after drag does not call onCellToggle', () => {
        const { onCellToggle } = renderGrid();
        fireEvent.mouseDown(getCell('A1'), { button: 0 });
        fireEvent.mouseEnter(getCell('B2'));
        fireEvent.mouseUp(getCell('B2'));
        expect(onCellToggle).not.toHaveBeenCalled();
    });
});

describe('TemplateGrid — colorMap fallback', () => {
    test('cell gets #f5f5f5 background when its group rowId is not in colorMap', () => {
        const group: WellGroup = {
            rowId: 1, type: 'SPECIMEN', name: 'Sample 1',
            positions: [{ row: 0, col: 0 }], properties: {}, allowNewGroups: false,
        };
        renderGrid({
            plate: makePlate(2, 2, [group]),
            colorMap: new Map(), // rowId 1 not in map → falls back to '#f5f5f5'
        });
        const cell = screen.getByLabelText('A1: Sample 1');
        expect(cell).toBeInTheDocument();
        expect(cell).toHaveStyle({ backgroundColor: '#f5f5f5' });
    });

    test('group of a type other than activeTab is excluded from position map', () => {
        const controlGroup: WellGroup = {
            rowId: 2, type: 'CONTROL', name: 'Virus',
            positions: [{ row: 0, col: 0 }], properties: {}, allowNewGroups: false,
        };
        renderGrid({
            plate: makePlate(2, 2, [controlGroup]),
            colorMap: new Map([[2, { color: '#00ff00', colorIndex: 2 }]]),
            activeTab: 'SPECIMEN', // CONTROL !== SPECIMEN → group is skipped
        });
        // Cell is not labeled with the CONTROL group name since that type is inactive
        expect(screen.getByLabelText('A1')).toBeInTheDocument();
        expect(screen.queryByLabelText('A1: Virus')).toBeNull();
    });
});

describe('TemplateGrid — well highlighting (highlightedGroupId)', () => {
    function makeGroupWithPositions(rowId: number): WellGroup {
        return {
            rowId,
            type: 'SPECIMEN',
            name: `Group ${rowId}`,
            positions: [{ row: 0, col: 0 }, { row: 0, col: 1 }],
            properties: {},
            allowNewGroups: false,
        };
    }

    test('cells belonging to the highlighted group receive the --active class', () => {
        const group = makeGroupWithPositions(1);
        renderGrid({
            plate: makePlate(2, 2, [group]),
            colorMap: new Map([[1, { color: '#ff0000', colorIndex: 1 }]]),
            highlightedGroupId: 1,
        });
        expect(getCell('A1: Group 1')).toHaveClass('template-grid__cell--active');
        expect(getCell('A2: Group 1')).toHaveClass('template-grid__cell--active');
    });

    test('cells not in the highlighted group do not receive --active class', () => {
        const group = makeGroupWithPositions(1);
        renderGrid({
            plate: makePlate(2, 2, [group]),
            colorMap: new Map([[1, { color: '#ff0000', colorIndex: 1 }]]),
            highlightedGroupId: 1,
        });
        // B1 and B2 are not in the group
        expect(getCell('B1')).not.toHaveClass('template-grid__cell--active');
        expect(getCell('B2')).not.toHaveClass('template-grid__cell--active');
    });

    test('no cells receive --active class when highlightedGroupId is null', () => {
        const group = makeGroupWithPositions(1);
        renderGrid({
            plate: makePlate(2, 2, [group]),
            colorMap: new Map([[1, { color: '#ff0000', colorIndex: 1 }]]),
            highlightedGroupId: null,
        });
        expect(getCell('A1: Group 1')).not.toHaveClass('template-grid__cell--active');
        expect(getCell('A2: Group 1')).not.toHaveClass('template-grid__cell--active');
    });

    test('only cells of the highlighted group are active when multiple groups exist', () => {
        const group1 = makeGroupWithPositions(1);
        const group2: WellGroup = {
            rowId: 2, type: 'SPECIMEN', name: 'Group 2',
            positions: [{ row: 1, col: 0 }, { row: 1, col: 1 }],
            properties: {}, allowNewGroups: false,
        };
        renderGrid({
            plate: makePlate(2, 2, [group1, group2]),
            colorMap: new Map([[1, { color: '#ff0000', colorIndex: 1 }], [2, { color: '#00ff00', colorIndex: 2 }]]),
            highlightedGroupId: 2,
        });
        expect(getCell('B1: Group 2')).toHaveClass('template-grid__cell--active');
        expect(getCell('B2: Group 2')).toHaveClass('template-grid__cell--active');
        expect(getCell('A1: Group 1')).not.toHaveClass('template-grid__cell--active');
        expect(getCell('A2: Group 1')).not.toHaveClass('template-grid__cell--active');
    });
});

describe('TemplateGrid — onWellHover', () => {
    test('mousing into a cell belonging to a group calls onWellHover with that group rowId', () => {
        const group: WellGroup = {
            rowId: 5, type: 'SPECIMEN', name: 'Sample 1',
            positions: [{ row: 0, col: 0 }], properties: {}, allowNewGroups: false,
        };
        const { onWellHover } = renderGrid({
            plate: makePlate(2, 2, [group]),
            colorMap: new Map([[5, { color: '#ff0000', colorIndex: 5 }]]),
        });
        fireEvent.mouseEnter(getCell('A1: Sample 1'));
        expect(onWellHover).toHaveBeenCalledWith(5);
    });

    test('mousing into an unassigned cell calls onWellHover with null', () => {
        const { onWellHover } = renderGrid();
        fireEvent.mouseEnter(getCell('A1'));
        expect(onWellHover).toHaveBeenCalledWith(null);
    });

    test('focusing a cell belonging to a group calls onWellHover with that group rowId', () => {
        const group: WellGroup = {
            rowId: 5, type: 'SPECIMEN', name: 'Sample 1',
            positions: [{ row: 0, col: 0 }], properties: {}, allowNewGroups: false,
        };
        const { onWellHover } = renderGrid({
            plate: makePlate(2, 2, [group]),
            colorMap: new Map([[5, { color: '#ff0000', colorIndex: 5 }]]),
        });
        fireEvent.focus(getCell('A1: Sample 1'));
        expect(onWellHover).toHaveBeenCalledWith(5);
    });

    test('focusing an unassigned cell calls onWellHover with null', () => {
        const { onWellHover } = renderGrid();
        fireEvent.focus(getCell('A1'));
        expect(onWellHover).toHaveBeenCalledWith(null);
    });

    test('mouse leaving the grid calls onWellHover with null', () => {
        const { onWellHover } = renderGrid();
        const grid = document.querySelector('.template-grid') as HTMLElement;
        fireEvent.mouseLeave(grid);
        expect(onWellHover).toHaveBeenCalledWith(null);
    });

    test('mousing into a cell during a drag does not call onWellHover', () => {
        const { onWellHover } = renderGrid();
        fireEvent.mouseDown(getCell('A1'), { button: 0 });
        onWellHover.mockClear(); // ignore any calls from before the drag started
        fireEvent.mouseEnter(getCell('B2'));
        expect(onWellHover).not.toHaveBeenCalled();
    });
});

describe('TemplateGrid — pattern classes (WCAG 1.4.1)', () => {
    test('assigned cell receives the pattern class matching its colorIndex', () => {
        const group: WellGroup = {
            rowId: 1, type: 'SPECIMEN', name: 'Sample 1',
            positions: [{ row: 0, col: 0 }], properties: {}, allowNewGroups: false,
        };
        renderGrid({
            plate: makePlate(2, 2, [group]),
            colorMap: new Map([[1, { color: '#ff0000', colorIndex: 3 }]]),
        });
        expect(getCell('A1: Sample 1')).toHaveClass('template-grid__cell--pattern-3');
    });

    test('unassigned cell (colorIndex = -1) does not receive any pattern class', () => {
        renderGrid({ plate: makePlate(2, 2) });
        const cell = getCell('A1');
        expect(cell.className).not.toMatch(/template-grid__cell--pattern-/);
    });

    test('colorMap fallback (rowId not in map) yields colorIndex -1 — no pattern class', () => {
        const group: WellGroup = {
            rowId: 1, type: 'SPECIMEN', name: 'Sample 1',
            positions: [{ row: 0, col: 0 }], properties: {}, allowNewGroups: false,
        };
        renderGrid({
            plate: makePlate(2, 2, [group]),
            colorMap: new Map(), // rowId 1 not in map
        });
        const cell = getCell('A1: Sample 1');
        expect(cell.className).not.toMatch(/template-grid__cell--pattern-/);
    });
});
