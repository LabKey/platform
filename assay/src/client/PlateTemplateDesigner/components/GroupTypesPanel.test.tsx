/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
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
        colorMap: new Map<number, string>(),
        onGroupSelect: jest.fn(),
        onTabChange: jest.fn(),
        onAddGroup: jest.fn(),
        onDeleteGroup: jest.fn(),
        onRenameGroup: jest.fn(),
        ...overrides,
    };
    const result = render(<GroupTypesPanel {...props} />);
    return { ...result, props };
}

describe('GroupTypesPanel — create row: select vs input', () => {
    test('shows a <select> when unused default names remain', () => {
        renderPanel({
            plate: makePlate({ typesToDefaultGroups: { SPECIMEN: ['Virus', 'Cell Control'] } }),
        });
        expect(screen.getByRole('combobox', { name: 'Group name' })).toBeInTheDocument();
        expect(screen.queryByRole('textbox', { name: 'Group name' })).toBeNull();
    });

    test('<select> contains only the unused defaults', () => {
        renderPanel({
            plate: makePlate({
                typesToDefaultGroups: { SPECIMEN: ['Virus', 'Cell Control'] },
                groups: [makeGroup({ name: 'Virus' })],  // 'Virus' already used
            }),
        });
        const select = screen.getByRole('combobox', { name: 'Group name' });
        const options = Array.from(select.querySelectorAll('option')).map(o => o.textContent);
        expect(options).toEqual(['Cell Control']);
    });

    test('shows a text <input> when all defaults are used', () => {
        renderPanel({
            plate: makePlate({
                typesToDefaultGroups: { SPECIMEN: ['Virus'] },
                groups: [makeGroup({ name: 'Virus' })],
            }),
        });
        expect(screen.getByRole('textbox', { name: 'Group name' })).toBeInTheDocument();
        expect(screen.queryByRole('combobox', { name: 'Group name' })).toBeNull();
    });

    test('shows a text <input> when no defaults are configured for the type', () => {
        renderPanel({ plate: makePlate({ typesToDefaultGroups: {} }) });
        expect(screen.getByRole('textbox', { name: 'Group name' })).toBeInTheDocument();
    });
});

describe('GroupTypesPanel — create row: name conflict detection', () => {
    test('Create button is enabled for a unique name', async () => {
        renderPanel({ plate: makePlate({ groups: [makeGroup({ name: 'Existing' })] }) });
        await userEvent.type(screen.getByRole('textbox', { name: 'Group name' }), 'New Group');
        expect(screen.getByRole('button', { name: 'Create' })).toBeEnabled();
    });

    test('Create button is disabled and error shown when name conflicts', async () => {
        renderPanel({ plate: makePlate({ groups: [makeGroup({ name: 'Existing' })] }) });
        await userEvent.type(screen.getByRole('textbox', { name: 'Group name' }), 'Existing');
        expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled();
        expect(screen.getByText(/already exists in this type/i)).toBeInTheDocument();
    });

    test('conflict only checks groups of the same type', async () => {
        // A group named 'Shared Name' in CONTROL should not conflict with SPECIMEN create input
        renderPanel({
            plate: makePlate({
                groups: [makeGroup({ rowId: 2, type: 'CONTROL', name: 'Shared Name' })],
            }),
        });
        await userEvent.type(screen.getByRole('textbox', { name: 'Group name' }), 'Shared Name');
        expect(screen.getByRole('button', { name: 'Create' })).toBeEnabled();
        expect(screen.queryByText(/already exists/i)).toBeNull();
    });

    test('clicking Create calls onAddGroup with the trimmed name', async () => {
        const { props } = renderPanel();
        await userEvent.type(screen.getByRole('textbox', { name: 'Group name' }), '  My Group  ');
        await userEvent.click(screen.getByRole('button', { name: 'Create' }));
        expect(props.onAddGroup).toHaveBeenCalledWith('SPECIMEN', 'My Group');
    });

    test('pressing Enter in the name input calls onAddGroup', async () => {
        const { props } = renderPanel();
        await userEvent.type(screen.getByRole('textbox', { name: 'Group name' }), 'My Group{Enter}');
        expect(props.onAddGroup).toHaveBeenCalledWith('SPECIMEN', 'My Group');
    });

    test('Create button is disabled when name is empty', () => {
        renderPanel();
        expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled();
    });
});

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

describe('GroupTypesPanel — tab switching resets create input', () => {
    test('switching activeTab resets the create name to empty (no defaults)', async () => {
        const { rerender, props } = renderPanel({
            plate: makePlate({ canCreateGroupsByType: { SPECIMEN: true, CONTROL: true } }),
            activeTab: 'SPECIMEN',
        });
        await userEvent.type(screen.getByRole('textbox', { name: 'Group name' }), 'In Progress');
        expect(screen.getByRole('textbox', { name: 'Group name' })).toHaveValue('In Progress');

        rerender(
            <GroupTypesPanel
                {...props}
                plate={makePlate({ canCreateGroupsByType: { SPECIMEN: true, CONTROL: true } })}
                activeTab="CONTROL"
            />
        );
        expect(screen.getByRole('textbox', { name: 'Group name' })).toHaveValue('');
    });

    test('switching activeTab resets to the first unused default of the new tab', () => {
        const plate = makePlate({
            canCreateGroupsByType: { SPECIMEN: true, CONTROL: true },
            typesToDefaultGroups: { CONTROL: ['Positive', 'Negative'] },
        });
        const { rerender, props } = renderPanel({ plate, activeTab: 'SPECIMEN' });

        rerender(<GroupTypesPanel {...props} plate={plate} activeTab="CONTROL" />);

        const select = screen.getByRole('combobox', { name: 'Group name' });
        expect(select).toHaveValue('Positive');
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

describe('GroupTypesPanel — multi-create dialog', () => {
    // Give the SPECIMEN type a default name so newGroupName starts as 'Virus',
    // which becomes the dialog's initialBaseName.
    function renderWithDefault() {
        return renderPanel({
            plate: makePlate({ typesToDefaultGroups: { SPECIMEN: ['Virus'] } }),
        });
    }

    test('clicking "Create multiple..." opens the dialog', async () => {
        renderWithDefault();
        await userEvent.click(screen.getByRole('button', { name: 'Create multiple...' }));
        expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    test('confirming multi-create calls onAddGroup for each generated name', async () => {
        const { props } = renderWithDefault();
        await userEvent.click(screen.getByRole('button', { name: 'Create multiple...' }));
        // Dialog opens with initialBaseName='Virus', default count=2
        await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Create' }));
        expect(props.onAddGroup).toHaveBeenCalledWith('SPECIMEN', 'Virus 1');
        expect(props.onAddGroup).toHaveBeenCalledWith('SPECIMEN', 'Virus 2');
    });

    test('closing the dialog with Cancel hides it', async () => {
        renderWithDefault();
        await userEvent.click(screen.getByRole('button', { name: 'Create multiple...' }));
        expect(screen.getByRole('dialog')).toBeInTheDocument();
        await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }));
        expect(screen.queryByRole('dialog')).toBeNull();
    });
});
