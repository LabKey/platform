/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';

import { WellGroup } from '../models';
import { WellGroupProperties } from './WellGroupProperties';

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

function renderProps(activeGroup: WellGroup | null, overrides: Partial<React.ComponentProps<typeof WellGroupProperties>> = {}) {
    const props = {
        activeGroup,
        onPropertyChange: jest.fn(),
        onDeleteProperty: jest.fn(),
        ...overrides,
    };
    render(<WellGroupProperties {...props} />);
    return props;
}

describe('WellGroupProperties', () => {
    describe('empty state', () => {
        test('shows placeholder text when no group is selected', () => {
            renderProps(null);
            expect(screen.getByText(/select a well group/i)).toBeInTheDocument();
        });

        test('does not render a table when no group is selected', () => {
            renderProps(null);
            expect(screen.queryByRole('table')).toBeNull();
        });
    });

    describe('group display', () => {
        test('renders the group name as a title', () => {
            renderProps(makeGroup({ name: 'My Group' }));
            expect(screen.getByText('My Group')).toBeInTheDocument();
        });

        test('shows "No properties defined" when group has no properties', () => {
            renderProps(makeGroup({ properties: {} }));
            expect(screen.getByText(/No properties defined/i)).toBeInTheDocument();
        });

        test('renders a row for each existing property', () => {
            renderProps(makeGroup({ properties: { conc: '1.0', dilution: '2x' } }));
            expect(screen.getByText('conc')).toBeInTheDocument();
            expect(screen.getByDisplayValue('1.0')).toBeInTheDocument();
            expect(screen.getByText('dilution')).toBeInTheDocument();
            expect(screen.getByDisplayValue('2x')).toBeInTheDocument();
        });

        test('renders a delete button per property', () => {
            renderProps(makeGroup({ properties: { a: '1', b: '2' } }));
            expect(screen.getAllByRole('button', { name: /Delete property/i })).toHaveLength(2);
        });
    });

    describe('editing existing properties', () => {
        test('changing a value input calls onPropertyChange with groupRowId, key, and new value', () => {
            const group = makeGroup({ properties: { conc: '1.0' } });
            const { onPropertyChange } = renderProps(group);
            // The input is controlled (value comes from activeGroup.properties), so use
            // fireEvent.change to fire a single synthetic event without the re-render cycle
            // that makes userEvent.type fight the controlled value.
            fireEvent.change(screen.getByLabelText('conc'), { target: { value: '5.0' } });
            expect(onPropertyChange).toHaveBeenCalledWith(group.rowId, 'conc', '5.0');
        });

        test('clicking the delete button calls onDeleteProperty with groupRowId and key', async () => {
            const group = makeGroup({ properties: { conc: '1.0' } });
            const { onDeleteProperty } = renderProps(group);
            await userEvent.click(screen.getByRole('button', { name: 'Delete property conc' }));
            expect(onDeleteProperty).toHaveBeenCalledWith(group.rowId, 'conc');
        });
    });

    describe('adding a new property', () => {
        test('Add button is disabled when the key input is empty', () => {
            renderProps(makeGroup());
            expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled();
        });

        test('Add button is enabled when a key is typed', async () => {
            renderProps(makeGroup());
            await userEvent.type(screen.getByLabelText('Property name'), 'newKey');
            expect(screen.getByRole('button', { name: 'Add' })).toBeEnabled();
        });

        test('clicking Add calls onPropertyChange with the new key and value then clears inputs', async () => {
            const group = makeGroup();
            const { onPropertyChange } = renderProps(group);
            await userEvent.type(screen.getByLabelText('Property name'), 'dose');
            await userEvent.type(screen.getByLabelText('Property value'), '10mg');
            await userEvent.click(screen.getByRole('button', { name: 'Add' }));
            expect(onPropertyChange).toHaveBeenCalledWith(group.rowId, 'dose', '10mg');
            expect(screen.getByLabelText('Property name')).toHaveValue('');
            expect(screen.getByLabelText('Property value')).toHaveValue('');
        });

        test('pressing Enter in the key input calls onPropertyChange', async () => {
            const group = makeGroup();
            const { onPropertyChange } = renderProps(group);
            await userEvent.type(screen.getByLabelText('Property name'), 'dose');
            await userEvent.type(screen.getByLabelText('Property value'), '10mg');
            await userEvent.type(screen.getByLabelText('Property name'), '{Enter}');
            expect(onPropertyChange).toHaveBeenCalledWith(group.rowId, 'dose', '10mg');
        });

        test('pressing Enter in the value input calls onPropertyChange', async () => {
            const group = makeGroup();
            const { onPropertyChange } = renderProps(group);
            await userEvent.type(screen.getByLabelText('Property name'), 'dose');
            await userEvent.type(screen.getByLabelText('Property value'), '10mg');
            await userEvent.type(screen.getByLabelText('Property value'), '{Enter}');
            expect(onPropertyChange).toHaveBeenCalledWith(group.rowId, 'dose', '10mg');
        });

        test('Add does not fire when key is whitespace-only', async () => {
            const { onPropertyChange } = renderProps(makeGroup());
            await userEvent.type(screen.getByLabelText('Property name'), '   ');
            await userEvent.click(screen.getByRole('button', { name: 'Add' }));
            expect(onPropertyChange).not.toHaveBeenCalled();
        });

        test('pressing Enter with a whitespace-only key does not call onPropertyChange', async () => {
            // The Add button is disabled for whitespace, but the Enter key handler on the
            // input calls handleAdd() directly, which must guard against empty trimmed keys.
            const { onPropertyChange } = renderProps(makeGroup());
            await userEvent.type(screen.getByLabelText('Property name'), '   {Enter}');
            expect(onPropertyChange).not.toHaveBeenCalled();
        });
    });

    describe('known bug: inputs not reset when active group changes', () => {
        // This documents the existing behavior where newKey/newValue are NOT reset
        // when the active group prop changes (see review finding #15).
        // The inputs retain their values across group switches until the component unmounts.
        test('newKey input retains value when activeGroup prop changes', async () => {
            const group1 = makeGroup({ rowId: 1, name: 'Group 1' });
            const group2 = makeGroup({ rowId: 2, name: 'Group 2' });
            const { rerender } = render(
                <WellGroupProperties activeGroup={group1} onPropertyChange={jest.fn()} onDeleteProperty={jest.fn()} />
            );
            await userEvent.type(screen.getByLabelText('Property name'), 'stale-key');
            rerender(
                <WellGroupProperties activeGroup={group2} onPropertyChange={jest.fn()} onDeleteProperty={jest.fn()} />
            );
            // Bug: input still shows the value from group1's editing session
            expect(screen.getByLabelText('Property name')).toHaveValue('stale-key');
        });
    });
});
