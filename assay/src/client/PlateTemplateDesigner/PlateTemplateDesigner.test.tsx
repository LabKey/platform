/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { act, render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { Ajax } from '@labkey/api';

import { PlateTemplateDesigner } from './PlateTemplateDesigner';
import { PlateTemplate } from './models';

jest.mock('@labkey/api', () => ({
    ActionURL: {
        getParameter: jest.fn().mockReturnValue(null),
        buildURL: jest.fn().mockReturnValue('/mock-url'),
    },
    Ajax: {
        request: jest.fn(),
    },
    Utils: {
        // Pass the callback through unchanged so tests can invoke it directly.
        getCallbackWrapper: (fn: (...args: unknown[]) => unknown) => fn,
    },
}));

jest.mock('@labkey/components', () => ({
    redirect: jest.fn(),
}));

const mockPlate: PlateTemplate = {
    rowId: 1,
    name: 'Test Plate',
    type: 'assay',
    rows: 4,
    cols: 4,
    groupTypes: ['SPECIMEN', 'CONTROL'],
    canCreateGroupsByType: { SPECIMEN: true, CONTROL: true },
    groups: [],
    plateProperties: {},
    typesToDefaultGroups: {},
    showWarningPanel: false,
    existingTemplateNames: [],
    copyMode: false,
    defaultPlateName: '',
};

describe('PlateTemplateDesigner', () => {
    let successCallback: ((response: unknown) => void) | undefined;
    let failureCallback: ((response: unknown) => void) | undefined;

    beforeEach(() => {
        jest.clearAllMocks();
        (Ajax.request as jest.Mock).mockImplementation(({ success, failure }) => {
            successCallback = success;
            failureCallback = failure;
        });
    });

    afterEach(() => {
        successCallback = undefined;
        failureCallback = undefined;
    });

    describe('initial load', () => {
        test('shows loading state before data arrives', () => {
            render(<PlateTemplateDesigner />);
            expect(screen.getByText('Loading...')).toBeInTheDocument();
        });

        test('calls Ajax.request on mount', () => {
            render(<PlateTemplateDesigner />);
            expect(Ajax.request).toHaveBeenCalledTimes(1);
        });

        test('renders the plate name input after a successful load', () => {
            render(<PlateTemplateDesigner />);
            act(() => successCallback?.({ data: mockPlate }));
            expect(screen.getByDisplayValue('Test Plate')).toBeInTheDocument();
        });

        test('renders group type tabs after a successful load', () => {
            render(<PlateTemplateDesigner />);
            act(() => successCallback?.({ data: mockPlate }));
            expect(screen.getByRole('tab', { name: 'SPECIMEN' })).toBeInTheDocument();
            expect(screen.getByRole('tab', { name: 'CONTROL' })).toBeInTheDocument();
        });

        test('uses defaultPlateName as the initial plate name when provided', () => {
            render(<PlateTemplateDesigner />);
            act(() =>
                successCallback?.({
                    data: { ...mockPlate, defaultPlateName: 'Copy of Test Plate', name: 'Test Plate' },
                })
            );
            expect(screen.getByDisplayValue('Copy of Test Plate')).toBeInTheDocument();
        });

        test('starts in dirty state when copyMode is true', () => {
            render(<PlateTemplateDesigner />);
            act(() => successCallback?.({ data: { ...mockPlate, copyMode: true } }));
            expect(screen.getByText('Unsaved changes')).toBeInTheDocument();
        });
    });

    describe('load failure', () => {
        test('renders the server exception message on failure', () => {
            render(<PlateTemplateDesigner />);
            act(() => failureCallback?.({ exception: 'Server error' }));
            expect(screen.getByText('Server error')).toBeInTheDocument();
        });

        test('renders a fallback message when exception is missing', () => {
            render(<PlateTemplateDesigner />);
            act(() => failureCallback?.({}));
            expect(screen.getByText('Failed to load plate template.')).toBeInTheDocument();
        });
    });

    describe('plate name editing', () => {
        async function renderLoaded() {
            render(<PlateTemplateDesigner />);
            act(() => successCallback?.({ data: mockPlate }));
            return screen.getByDisplayValue('Test Plate');
        }

        test('changing the plate name marks the form dirty', async () => {
            const input = await renderLoaded();
            await userEvent.clear(input);
            await userEvent.type(input, 'My Plate');
            expect(screen.getByText('Unsaved changes')).toBeInTheDocument();
        });

        test('Save button is disabled when form is clean', async () => {
            await renderLoaded();
            expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
        });

        test('Save button is enabled after a name change', async () => {
            const input = await renderLoaded();
            await userEvent.clear(input);
            await userEvent.type(input, 'My Plate');
            expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
        });
    });
});
