/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { render, screen, within } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';

import { CreateGroupRow } from './CreateGroupRow';

function renderRow(overrides: Partial<React.ComponentProps<typeof CreateGroupRow>> = {}) {
    const props = {
        unusedDefaults: [] as string[],
        existingGroupNames: [] as string[],
        activeTab: 'SPECIMEN',
        onAddGroup: jest.fn(),
        ...overrides,
    };
    const result = render(<CreateGroupRow {...props} />);
    return { ...result, props };
}

describe('CreateGroupRow — select vs input', () => {
    test('shows a <select> when unused default names remain', () => {
        renderRow({ unusedDefaults: ['Virus', 'Cell Control'] });
        expect(screen.getByRole('combobox', { name: 'Group name' })).toBeInTheDocument();
        expect(screen.queryByRole('textbox', { name: 'Group name' })).toBeNull();
    });

    test('<select> lists exactly the provided unused defaults', () => {
        renderRow({ unusedDefaults: ['Cell Control'] });
        const select = screen.getByRole('combobox', { name: 'Group name' });
        const options = Array.from(select.querySelectorAll('option')).map(o => o.textContent);
        expect(options).toEqual(['Cell Control']);
    });

    test('shows a text <input> when unusedDefaults is empty', () => {
        renderRow({ unusedDefaults: [] });
        expect(screen.getByRole('textbox', { name: 'Group name' })).toBeInTheDocument();
        expect(screen.queryByRole('combobox', { name: 'Group name' })).toBeNull();
    });
});

describe('CreateGroupRow — name conflict detection', () => {
    test('Create button is enabled for a unique name', async () => {
        renderRow({ existingGroupNames: ['Existing'] });
        expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled();
        await userEvent.type(screen.getByRole('textbox', { name: 'Group name' }), 'New Group');
        expect(screen.getByRole('button', { name: 'Create' })).toBeEnabled();
    });

    test('Create button is disabled and error shown when name conflicts', async () => {
        renderRow({ existingGroupNames: ['Existing'] });
        await userEvent.type(screen.getByRole('textbox', { name: 'Group name' }), 'Existing');
        expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled();
        expect(screen.getByText(/already exists in this type/i)).toBeInTheDocument();
    });

    test('clicking Create calls onAddGroup with the trimmed name', async () => {
        const { props } = renderRow();
        await userEvent.type(screen.getByRole('textbox', { name: 'Group name' }), '  My Group  ');
        await userEvent.click(screen.getByRole('button', { name: 'Create' }));
        expect(props.onAddGroup).toHaveBeenCalledWith('SPECIMEN', 'My Group');
    });

    test('pressing Enter in the name input calls onAddGroup', async () => {
        const { props } = renderRow();
        await userEvent.type(screen.getByRole('textbox', { name: 'Group name' }), 'My Group{Enter}');
        expect(props.onAddGroup).toHaveBeenCalledWith('SPECIMEN', 'My Group');
    });

    test('Create button is disabled when name is empty', () => {
        renderRow();
        expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled();
    });
});

describe('CreateGroupRow — tab switching resets create input', () => {
    test('switching activeTab resets the create name to empty (no defaults)', async () => {
        const { rerender, props } = renderRow({ unusedDefaults: [], activeTab: 'SPECIMEN' });
        await userEvent.type(screen.getByRole('textbox', { name: 'Group name' }), 'In Progress');
        expect(screen.getByRole('textbox', { name: 'Group name' })).toHaveValue('In Progress');

        rerender(<CreateGroupRow {...props} activeTab="CONTROL" unusedDefaults={[]} />);
        expect(screen.getByRole('textbox', { name: 'Group name' })).toHaveValue('');
    });

    test('switching activeTab resets to the first unused default of the new tab', () => {
        const { rerender, props } = renderRow({ unusedDefaults: [], activeTab: 'SPECIMEN' });
        rerender(<CreateGroupRow {...props} activeTab="CONTROL" unusedDefaults={['Positive', 'Negative']} />);
        expect(screen.getByRole('combobox', { name: 'Group name' })).toHaveValue('Positive');
    });
});

describe('CreateGroupRow — multi-create dialog', () => {
    function renderWithDefault() {
        return renderRow({ unusedDefaults: ['Virus'] });
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
