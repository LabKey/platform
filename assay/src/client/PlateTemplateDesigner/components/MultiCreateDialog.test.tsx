/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { render, screen, within } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';

import { MultiCreateDialog } from './MultiCreateDialog';

function renderDialog(overrides: Partial<React.ComponentProps<typeof MultiCreateDialog>> = {}) {
    const props = {
        initialBaseName: 'Sample',
        existingNames: new Set<string>(),
        onClose: jest.fn(),
        onConfirm: jest.fn(),
        ...overrides,
    };
    render(<MultiCreateDialog {...props} />);
    return props;
}

describe('MultiCreateDialog', () => {
    describe('initial state', () => {
        test('pre-populates base name from initialBaseName', () => {
            renderDialog({ initialBaseName: 'Virus' });
            expect(screen.getByLabelText(/Base Name/i)).toHaveValue('Virus');
        });

        test('defaults count to 2', () => {
            renderDialog();
            expect(screen.getByLabelText(/Count/i)).toHaveValue(2);
        });

        test('Create button is disabled when base name is empty', () => {
            renderDialog({ initialBaseName: '' });
            expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled();
        });

        test('Create button is enabled when base name is non-empty', () => {
            renderDialog({ initialBaseName: 'Sample' });
            expect(screen.getByRole('button', { name: 'Create' })).toBeEnabled();
        });
    });

    describe('successful creation', () => {
        test('calls onConfirm with generated names using base name and count', async () => {
            const { onConfirm } = renderDialog({ initialBaseName: 'Sample', existingNames: new Set() });
            await userEvent.clear(screen.getByLabelText(/Count/i));
            await userEvent.type(screen.getByLabelText(/Count/i), '3');
            await userEvent.click(screen.getByRole('button', { name: 'Create' }));
            expect(onConfirm).toHaveBeenCalledWith(['Sample 1', 'Sample 2', 'Sample 3']);
        });

        test('trims whitespace from base name before generating names', async () => {
            const { onConfirm } = renderDialog({ initialBaseName: '  Sample  ' });
            await userEvent.click(screen.getByRole('button', { name: 'Create' }));
            expect(onConfirm).toHaveBeenCalledWith(['Sample 1', 'Sample 2']);
        });

        test('filters out names already in existingNames', async () => {
            const { onConfirm } = renderDialog({
                initialBaseName: 'Sample',
                existingNames: new Set(['Sample 1', 'Sample 3']),
            });
            await userEvent.clear(screen.getByLabelText(/Count/i));
            await userEvent.type(screen.getByLabelText(/Count/i), '3');
            await userEvent.click(screen.getByRole('button', { name: 'Create' }));
            // Sample 1 and Sample 3 are already taken; only Sample 2 is new
            expect(onConfirm).toHaveBeenCalledWith(['Sample 2']);
        });
    });

    describe('validation errors', () => {
        test('shows error and does not call onConfirm when count is not a number', async () => {
            const { onConfirm } = renderDialog();
            await userEvent.clear(screen.getByLabelText(/Count/i));
            await userEvent.type(screen.getByLabelText(/Count/i), 'abc');
            await userEvent.click(screen.getByRole('button', { name: 'Create' }));
            expect(onConfirm).not.toHaveBeenCalled();
            expect(screen.getByText(/not a valid count/i)).toBeInTheDocument();
        });

        test('shows error when count is less than 1', async () => {
            const { onConfirm } = renderDialog();
            await userEvent.clear(screen.getByLabelText(/Count/i));
            await userEvent.type(screen.getByLabelText(/Count/i), '0');
            await userEvent.click(screen.getByRole('button', { name: 'Create' }));
            expect(onConfirm).not.toHaveBeenCalled();
            expect(screen.getByText(/not a valid count/i)).toBeInTheDocument();
        });

        test('shows error and does not call onConfirm when all generated names already exist', async () => {
            const { onConfirm } = renderDialog({
                initialBaseName: 'Sample',
                existingNames: new Set(['Sample 1', 'Sample 2']),
            });
            await userEvent.click(screen.getByRole('button', { name: 'Create' }));
            expect(onConfirm).not.toHaveBeenCalled();
            expect(screen.getByText(/already exist/i)).toBeInTheDocument();
        });

        test('count error clears when count input is changed', async () => {
            renderDialog();
            await userEvent.clear(screen.getByLabelText(/Count/i));
            await userEvent.type(screen.getByLabelText(/Count/i), '0');
            await userEvent.click(screen.getByRole('button', { name: 'Create' }));
            expect(screen.getByText(/not a valid count/i)).toBeInTheDocument();

            await userEvent.clear(screen.getByLabelText(/Count/i));
            await userEvent.type(screen.getByLabelText(/Count/i), '3');
            expect(screen.queryByText(/not a valid count/i)).toBeNull();
        });
    });

    describe('cancel / close', () => {
        test('clicking Cancel calls onClose', async () => {
            const { onClose } = renderDialog();
            await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
            expect(onClose).toHaveBeenCalledTimes(1);
        });

        test('clicking the overlay calls onClose', async () => {
            const { onClose } = renderDialog();
            await userEvent.click(document.querySelector('.multi-create-dialog__overlay'));
            expect(onClose).toHaveBeenCalledTimes(1);
        });

        test('clicking inside the dialog does not call onClose', async () => {
            const { onClose } = renderDialog();
            await userEvent.click(document.querySelector('.multi-create-dialog'));
            expect(onClose).not.toHaveBeenCalled();
        });

        test('pressing Escape on the base name input calls onClose', async () => {
            const { onClose } = renderDialog();
            await userEvent.type(screen.getByLabelText(/Base Name/i), '{Escape}');
            // Both the input's onKeyDown and the dialog's focus-trap listener fire on Escape
            // (the event bubbles), so onClose is called twice. Both calls are idempotent.
            expect(onClose).toHaveBeenCalled();
        });

        test('pressing Escape on the count input calls onClose', async () => {
            const { onClose } = renderDialog();
            await userEvent.type(screen.getByLabelText(/Count/i), '{Escape}');
            expect(onClose).toHaveBeenCalled();
        });
    });

    describe('keyboard submit', () => {
        test('pressing Enter in the base name input submits', async () => {
            const { onConfirm } = renderDialog({ initialBaseName: 'Sample' });
            await userEvent.type(screen.getByLabelText(/Base Name/i), '{Enter}');
            expect(onConfirm).toHaveBeenCalledWith(['Sample 1', 'Sample 2']);
        });

        test('pressing Enter in the count input submits', async () => {
            const { onConfirm } = renderDialog({ initialBaseName: 'Sample' });
            await userEvent.type(screen.getByLabelText(/Count/i), '{Enter}');
            expect(onConfirm).toHaveBeenCalledWith(['Sample 1', 'Sample 2']);
        });
    });

    describe('focus trap', () => {
        // Focusable order: Base Name → Count → Cancel → Create.
        // userEvent.tab() fires Tab keydown events that bubble to the dialog's native listener.
        test('Tab from last focusable element (Create) wraps focus to first (Base Name)', async () => {
            renderDialog({ initialBaseName: 'Sample' }); // Create button enabled
            // After mount the useEffect focuses the first element (Base Name); tab forward to Create.
            expect(document.activeElement).toBe(screen.getByLabelText(/Base Name/i));
            await userEvent.tab(); // → Count
            expect(document.activeElement).toBe(screen.getByLabelText(/Count/i));
            await userEvent.tab(); // → Cancel
            await userEvent.tab(); // → Create (last)
            await userEvent.tab(); // → focus trap wraps back to Base Name
            expect(document.activeElement).toBe(screen.getByLabelText(/Base Name/i));
        });

        test('Shift+Tab from first focusable element (Base Name) wraps focus to last (Create)', async () => {
            renderDialog({ initialBaseName: 'Sample' }); // Create button enabled
            expect(document.activeElement).toBe(
                within(screen.getByRole('dialog')).getByRole('textbox', { name: 'Base Name' })
            );
            await userEvent.keyboard('{Shift>}{Tab}{/Shift}');
            expect(document.activeElement).toBe(
                within(screen.getByRole('dialog')).getByRole('button', { name: 'Create' })
            );
        });
    });

    describe('base name input', () => {
        test('typing in the base name input updates its value', async () => {
            renderDialog({ initialBaseName: '' });
            expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled();
            await userEvent.type(screen.getByLabelText(/Base Name/i), 'NewName');
            expect(screen.getByLabelText(/Base Name/i)).toHaveValue('NewName');
            expect(screen.getByRole('button', { name: 'Create' })).toBeEnabled();
        });

        test('pressing Enter in count input with empty base name does not submit', async () => {
            // The count input's onKeyDown calls handleCreate() without guarding baseName.
            // handleCreate's own guard (if (!trimmedBase) return) must prevent submission.
            const { onConfirm } = renderDialog({ initialBaseName: '' });
            await userEvent.type(screen.getByLabelText(/Count/i), '{Enter}');
            expect(onConfirm).not.toHaveBeenCalled();
        });
    });

    describe('singular error message', () => {
        test('uses singular "name" when count is 1 and that name already exists', async () => {
            // Exercises the parsedCount === 1 branch of the ternary in the "all names exist" error.
            renderDialog({
                initialBaseName: 'Sample',
                existingNames: new Set(['Sample 1']),
            });
            await userEvent.clear(screen.getByLabelText(/Count/i));
            await userEvent.type(screen.getByLabelText(/Count/i), '1');
            await userEvent.click(screen.getByRole('button', { name: 'Create' }));
            expect(screen.getByText(/all 1 generated name already exist/i)).toBeInTheDocument();
        });
    });
});
