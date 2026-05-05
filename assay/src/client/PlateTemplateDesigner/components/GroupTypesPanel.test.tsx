/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';

import { PlateTemplate, WellGroup } from '../models';
import { GroupTypesPanel } from './GroupTypesPanel';

function makeGroup(overrides: Partial<WellGroup> = {}): WellGroup {
    return {
        rowId: 1,
        type: 'SPECIMEN',
        name: 'Group A',
        positions: [],
        properties: {},
        allowNewGroups: true,
        ...overrides,
    };
}

function makePlate(overrides: Partial<PlateTemplate> = {}): PlateTemplate {
    return {
        rowId: 0,
        name: 'Test Plate',
        type: 'assay',
        rows: 8,
        cols: 12,
        groupTypes: ['SPECIMEN', 'CONTROL'],
        canCreateGroupsByType: { SPECIMEN: true, CONTROL: true },
        groups: [],
        plateProperties: {},
        typesToDefaultGroups: {},
        showWarningPanel: false,
        existingTemplateNames: [],
        copyMode: false,
        defaultPlateName: '',
        ...overrides,
    };
}

function renderPanel(overrides: Partial<React.ComponentProps<typeof GroupTypesPanel>> = {}) {
    const props = {
        plate: makePlate(),
        activeGroup: null,
        activeTab: 'SPECIMEN',
        colorMap: new Map<number, { color: string; colorIndex: number }>(),
        hoveredWellGroupId: null as number | null,
        onGroupSelect: jest.fn(),
        onTabChange: jest.fn(),
        onAddGroup: jest.fn(),
        onDeleteGroup: jest.fn(),
        onRenameGroup: jest.fn(),
        onGroupHover: jest.fn(),
        ...overrides,
    };
    const result = render(<GroupTypesPanel {...props} />);
    return { ...result, props };
}

describe('GroupTypesPanel — inline rename', () => {
    function renderWithActiveGroup() {
        const group1 = makeGroup({ rowId: 1, name: 'Group A' });
        const group2 = makeGroup({ rowId: 2, name: 'Group B' });
        return renderPanel({
            plate: makePlate({ groups: [group1, group2] }),
            activeGroup: group1,
        });
    }

    test('rename button is visible for the active group when allowNewGroups is true', () => {
        renderWithActiveGroup();
        expect(screen.getByRole('button', { name: 'Rename Group A' })).toBeInTheDocument();
    });

    test('clicking rename shows an inline input', async () => {
        renderWithActiveGroup();
        await userEvent.click(screen.getByRole('button', { name: 'Rename Group A' }));
        expect(screen.getByRole('textbox', { name: 'Rename Group A' })).toBeInTheDocument();
    });

    test('pressing Enter with a non-conflicting name calls onRenameGroup', async () => {
        const { props } = renderWithActiveGroup();
        await userEvent.click(screen.getByRole('button', { name: 'Rename Group A' }));
        const input = screen.getByRole('textbox', { name: 'Rename Group A' });
        await userEvent.clear(input);
        await userEvent.type(input, 'Group C{Enter}');
        expect(props.onRenameGroup).toHaveBeenCalledWith(1, 'Group C');
    });

    test('pressing Enter with a conflicting name shows an error and does not rename', async () => {
        const { props } = renderWithActiveGroup();
        await userEvent.click(screen.getByRole('button', { name: 'Rename Group A' }));
        const input = screen.getByRole('textbox', { name: 'Rename Group A' });
        await userEvent.clear(input);
        await userEvent.type(input, 'Group B{Enter}');
        expect(props.onRenameGroup).not.toHaveBeenCalled();
        expect(screen.getByText(/"Group B" is already used/i)).toBeInTheDocument();
    });

    test('blurring with a conflicting name reverts silently (no error, no rename)', async () => {
        const { props } = renderWithActiveGroup();
        await userEvent.click(screen.getByRole('button', { name: 'Rename Group A' }));
        const input = screen.getByRole('textbox', { name: 'Rename Group A' });
        await userEvent.clear(input);
        await userEvent.type(input, 'Group B');
        await userEvent.tab();  // blur the input
        expect(props.onRenameGroup).not.toHaveBeenCalled();
        expect(screen.queryByText(/"Group B" is already used/i)).toBeNull();
        expect(screen.queryByRole('textbox', { name: 'Rename Group A' })).toBeNull();
    });

    test('pressing Escape cancels rename without calling onRenameGroup', async () => {
        const { props } = renderWithActiveGroup();
        await userEvent.click(screen.getByRole('button', { name: 'Rename Group A' }));
        const input = screen.getByRole('textbox', { name: 'Rename Group A' });
        await userEvent.clear(input);
        await userEvent.type(input, 'Something{Escape}');
        expect(props.onRenameGroup).not.toHaveBeenCalled();
        expect(screen.queryByRole('textbox', { name: 'Rename Group A' })).toBeNull();
    });
});

describe('GroupTypesPanel — tab click', () => {
    test('clicking a tab calls onTabChange with the tab key', async () => {
        const { props } = renderPanel({ activeTab: 'SPECIMEN' });
        await userEvent.click(screen.getByRole('tab', { name: 'CONTROL' }));
        expect(props.onTabChange).toHaveBeenCalledWith('CONTROL');
    });
});

describe('GroupTypesPanel — group selection', () => {
    function renderWithGroup() {
        const group = makeGroup({ rowId: 1, name: 'Group A' });
        return renderPanel({
            plate: makePlate({ groups: [group] }),
            activeGroup: null,
        });
    }

    test('clicking a group row calls onGroupSelect with that group', async () => {
        const { props } = renderWithGroup();
        await userEvent.click(screen.getByRole('option', { name: 'Group A' }));
        expect(props.onGroupSelect).toHaveBeenCalledWith(
            expect.objectContaining({ rowId: 1, name: 'Group A' })
        );
    });

    test('pressing Enter on a group row calls onGroupSelect', () => {
        const { props } = renderWithGroup();
        fireEvent.keyDown(screen.getByRole('option', { name: 'Group A' }), { key: 'Enter' });
        expect(props.onGroupSelect).toHaveBeenCalledWith(
            expect.objectContaining({ rowId: 1, name: 'Group A' })
        );
    });

    test('pressing Space on a group row calls onGroupSelect', () => {
        const { props } = renderWithGroup();
        fireEvent.keyDown(screen.getByRole('option', { name: 'Group A' }), { key: ' ' });
        expect(props.onGroupSelect).toHaveBeenCalledWith(
            expect.objectContaining({ rowId: 1, name: 'Group A' })
        );
    });
});

describe('GroupTypesPanel — delete group', () => {
    function renderWithActiveGroup() {
        const group = makeGroup({ rowId: 1, name: 'Group A' });
        return renderPanel({
            plate: makePlate({ groups: [group] }),
            activeGroup: group,
        });
    }

    test('delete button calls onDeleteGroup when user confirms', async () => {
        const { props } = renderWithActiveGroup();
        await userEvent.click(screen.getByRole('button', { name: 'Delete Group A' }));
        // First click shows inline confirmation; click Yes to confirm
        await userEvent.click(screen.getByRole('button', { name: 'Confirm delete Group A' }));
        expect(props.onDeleteGroup).toHaveBeenCalledWith(1);
    });

    test('delete button does not call onDeleteGroup when user cancels', async () => {
        const { props } = renderWithActiveGroup();
        await userEvent.click(screen.getByRole('button', { name: 'Delete Group A' }));
        // First click shows inline confirmation; click No to cancel
        await userEvent.click(screen.getByRole('button', { name: 'Cancel delete Group A' }));
        expect(props.onDeleteGroup).not.toHaveBeenCalled();
    });
});

describe('GroupTypesPanel — group hover (onGroupHover)', () => {
    test('mousing into a group row calls onGroupHover with that group rowId', () => {
        const group = makeGroup({ rowId: 1, name: 'Group A' });
        const { props } = renderPanel({
            plate: makePlate({ groups: [group] }),
        });
        fireEvent.mouseEnter(screen.getByRole('option', { name: 'Group A' }));
        expect(props.onGroupHover).toHaveBeenCalledWith(1);
    });

    test('mousing out of the group list calls onGroupHover with null', () => {
        const group = makeGroup({ rowId: 1, name: 'Group A' });
        const { props } = renderPanel({
            plate: makePlate({ groups: [group] }),
        });
        fireEvent.mouseLeave(screen.getByRole('listbox', { name: 'Well groups' }));
        expect(props.onGroupHover).toHaveBeenCalledWith(null);
    });
});

describe('GroupTypesPanel — well-hover highlighting (hoveredWellGroupId)', () => {
    test('group row receives --highlighted class when hoveredWellGroupId matches its rowId', () => {
        const group = makeGroup({ rowId: 1, name: 'Group A' });
        renderPanel({
            plate: makePlate({ groups: [group] }),
            hoveredWellGroupId: 1,
        });
        expect(screen.getByRole('option', { name: 'Group A' })).toHaveClass(
            'group-types-panel__group--highlighted'
        );
    });

    test('group row does not receive --highlighted class when hoveredWellGroupId is null', () => {
        const group = makeGroup({ rowId: 1, name: 'Group A' });
        renderPanel({
            plate: makePlate({ groups: [group] }),
            hoveredWellGroupId: null,
        });
        expect(screen.getByRole('option', { name: 'Group A' })).not.toHaveClass(
            'group-types-panel__group--highlighted'
        );
    });

    test('group row does not receive --highlighted class when hoveredWellGroupId is a different rowId', () => {
        const group = makeGroup({ rowId: 1, name: 'Group A' });
        renderPanel({
            plate: makePlate({ groups: [group] }),
            hoveredWellGroupId: 99,
        });
        expect(screen.getByRole('option', { name: 'Group A' })).not.toHaveClass(
            'group-types-panel__group--highlighted'
        );
    });

    test('active group does not receive --highlighted class even when its rowId matches hoveredWellGroupId', () => {
        const group = makeGroup({ rowId: 1, name: 'Group A' });
        renderPanel({
            plate: makePlate({ groups: [group] }),
            activeGroup: group,
            hoveredWellGroupId: 1,
        });
        const row = screen.getByRole('option', { name: 'Group A' });
        expect(row).toHaveClass('group-types-panel__group--active');
        expect(row).not.toHaveClass('group-types-panel__group--highlighted');
    });
});
