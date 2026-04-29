/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';

import { StatusBar } from './StatusBar';

function renderStatusBar(overrides: Partial<React.ComponentProps<typeof StatusBar>> = {}) {
    const props = {
        isDirty: false,
        status: '',
        plateName: 'My Plate',
        onSaveAndClose: jest.fn(),
        onSave: jest.fn(),
        onCancel: jest.fn(),
        ...overrides,
    };
    render(<StatusBar {...props} />);
    return props;
}

describe('StatusBar', () => {
    describe('button states', () => {
        test('Save button is disabled when plate is not dirty', () => {
            renderStatusBar({ isDirty: false });
            expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
        });

        test('Save button is enabled when plate is dirty', () => {
            renderStatusBar({ isDirty: true });
            expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
        });

        test('Save & Close and Cancel are always enabled', () => {
            renderStatusBar({ isDirty: false });
            expect(screen.getByRole('button', { name: /Save & Close/i })).toBeEnabled();
            expect(screen.getByRole('button', { name: 'Cancel' })).toBeEnabled();
        });
    });

    describe('dirty indicator', () => {
        test('shows "Unsaved changes" when dirty', () => {
            renderStatusBar({ isDirty: true });
            expect(screen.getByText('Unsaved changes')).toBeInTheDocument();
        });

        test('shows nothing in dirty indicator when clean', () => {
            renderStatusBar({ isDirty: false });
            // The element is present but empty
            const statuses = document.querySelectorAll('.status-bar__dirty');
            expect(statuses).toHaveLength(1);
            expect(statuses[0]).toHaveTextContent('');
        });

        test('displays transient status text', () => {
            renderStatusBar({ status: 'Saved.' });
            expect(screen.getByText('Saved.')).toBeInTheDocument();
        });
    });

    describe('validation', () => {
        test('clicking Save with empty plate name shows error and does not call onSave', async () => {
            const { onSave } = renderStatusBar({ plateName: '', isDirty: true });
            await userEvent.click(screen.getByRole('button', { name: 'Save' }));
            expect(onSave).not.toHaveBeenCalled();
            expect(screen.getByRole('alert')).toHaveTextContent(/plate name/i);
        });

        test('clicking Save with whitespace-only plate name shows error', async () => {
            const { onSave } = renderStatusBar({ plateName: '   ', isDirty: true });
            await userEvent.click(screen.getByRole('button', { name: 'Save' }));
            expect(onSave).not.toHaveBeenCalled();
            expect(screen.getByRole('alert')).toBeInTheDocument();
        });

        test('clicking Save with valid plate name calls onSave and shows no error', async () => {
            const { onSave } = renderStatusBar({ plateName: 'My Plate', isDirty: true });
            await userEvent.click(screen.getByRole('button', { name: 'Save' }));
            expect(onSave).toHaveBeenCalledTimes(1);
            expect(screen.queryByRole('alert')).toBeNull();
        });

        test('clicking Save & Close with empty name shows error and does not call onSaveAndClose', async () => {
            const { onSaveAndClose } = renderStatusBar({ plateName: '', isDirty: true });
            await userEvent.click(screen.getByRole('button', { name: /Save & Close/i }));
            expect(onSaveAndClose).not.toHaveBeenCalled();
            expect(screen.getByRole('alert')).toBeInTheDocument();
        });

        test('clicking Save & Close with valid name calls onSaveAndClose', async () => {
            const { onSaveAndClose } = renderStatusBar({ plateName: 'My Plate' });
            await userEvent.click(screen.getByRole('button', { name: /Save & Close/i }));
            expect(onSaveAndClose).toHaveBeenCalledTimes(1);
        });

        test('error clears on a subsequent successful save', async () => {
            // First render with empty name to trigger error
            const { rerender } = render(
                <StatusBar isDirty={true} status="" plateName="" onSaveAndClose={jest.fn()} onSave={jest.fn()} onCancel={jest.fn()} />
            );
            await userEvent.click(screen.getByRole('button', { name: 'Save' }));
            expect(screen.getByRole('alert')).toBeInTheDocument();

            // Re-render with a valid name — the error clears on the next successful validate
            const onSave = jest.fn();
            rerender(
                <StatusBar isDirty={true} status="" plateName="Fixed Name" onSaveAndClose={jest.fn()} onSave={onSave} onCancel={jest.fn()} />
            );
            await userEvent.click(screen.getByRole('button', { name: 'Save' }));
            expect(onSave).toHaveBeenCalledTimes(1);
            expect(screen.queryByRole('alert')).toBeNull();
        });
    });

    describe('Cancel', () => {
        test('clicking Cancel calls onCancel', async () => {
            const { onCancel } = renderStatusBar();
            await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
            expect(onCancel).toHaveBeenCalledTimes(1);
        });

        test('Cancel does not call onSave or onSaveAndClose', async () => {
            const { onSave, onSaveAndClose } = renderStatusBar();
            await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
            expect(onSave).not.toHaveBeenCalled();
            expect(onSaveAndClose).not.toHaveBeenCalled();
        });
    });
});
