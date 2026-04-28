/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';

import { RightPanel } from './RightPanel';

function renderPanel(overrides: Partial<React.ComponentProps<typeof RightPanel>> = {}) {
    const props = {
        showWarningPanel: false,
        rightTab: 'properties' as const,
        onRightTabChange: jest.fn(),
        warnings: [],
        activeGroup: null,
        onPropertyChange: jest.fn(),
        onDeleteProperty: jest.fn(),
        ...overrides,
    };
    render(<RightPanel {...props} />);
    return props;
}

describe('RightPanel — without warning panel', () => {
    test('renders WellGroupProperties with no tab strip', () => {
        renderPanel({ showWarningPanel: false });
        expect(screen.queryByRole('tablist')).toBeNull();
        expect(screen.queryByRole('tabpanel')).toBeNull();
        // WellGroupProperties empty-state text is present
        expect(screen.getByText(/select a well group/i)).toBeInTheDocument();
    });

    test('properties div has no role or aria-labelledby when showWarningPanel is false', () => {
        renderPanel({ showWarningPanel: false });
        // The wrapper div around WellGroupProperties should have no role
        const tabpanels = document.querySelectorAll('[role="tabpanel"]');
        expect(tabpanels).toHaveLength(0);
    });
});

describe('RightPanel — with warning panel, properties tab', () => {
    test('renders a tablist with two tabs', () => {
        renderPanel({ showWarningPanel: true, rightTab: 'properties' });
        expect(screen.getByRole('tablist')).toBeInTheDocument();
        expect(screen.getAllByRole('tab')).toHaveLength(2);
    });

    test('properties tab is aria-selected, warnings tab is not', () => {
        renderPanel({ showWarningPanel: true, rightTab: 'properties' });
        expect(screen.getByRole('tab', { name: 'Well Group Properties' })).toHaveAttribute('aria-selected', 'true');
        expect(screen.getByRole('tab', { name: 'Warnings' })).toHaveAttribute('aria-selected', 'false');
    });

    test('properties tabpanel is rendered with correct id and aria-labelledby', () => {
        renderPanel({ showWarningPanel: true, rightTab: 'properties' });
        const panel = document.getElementById('right-panel-properties');
        expect(panel).toBeInTheDocument();
        expect(panel).toHaveAttribute('role', 'tabpanel');
        expect(panel).toHaveAttribute('aria-labelledby', 'right-tab-properties');
    });

    test('warnings tabpanel is hidden when rightTab is "properties"', () => {
        renderPanel({ showWarningPanel: true, rightTab: 'properties' });
        expect(document.getElementById('right-panel-warnings')).toHaveAttribute('hidden');
    });

    test('warnings tab label shows count badge when warnings exist', () => {
        renderPanel({ showWarningPanel: true, rightTab: 'properties', warnings: ['w1', 'w2', 'w3'] });
        expect(screen.getByRole('tab', { name: 'Warnings (3)' })).toBeInTheDocument();
    });

    test('warnings tab label shows plain "Warnings" when list is empty', () => {
        renderPanel({ showWarningPanel: true, rightTab: 'properties', warnings: [] });
        expect(screen.getByRole('tab', { name: 'Warnings' })).toBeInTheDocument();
    });

    test('clicking warnings tab calls onRightTabChange with "warnings"', async () => {
        const { onRightTabChange } = renderPanel({ showWarningPanel: true, rightTab: 'properties' });
        await userEvent.click(screen.getByRole('tab', { name: 'Warnings' }));
        expect(onRightTabChange).toHaveBeenCalledWith('warnings');
    });

    test('clicking properties tab calls onRightTabChange with "properties"', async () => {
        const { onRightTabChange } = renderPanel({ showWarningPanel: true, rightTab: 'warnings' });
        await userEvent.click(screen.getByRole('tab', { name: 'Well Group Properties' }));
        expect(onRightTabChange).toHaveBeenCalledWith('properties');
    });
});

describe('RightPanel — with warning panel, warnings tab', () => {
    test('renders warnings tabpanel; properties tabpanel is hidden', () => {
        renderPanel({ showWarningPanel: true, rightTab: 'warnings', warnings: ['A1: warning'] });
        expect(document.getElementById('right-panel-warnings')).toBeInTheDocument();
        expect(document.getElementById('right-panel-properties')).toHaveAttribute('hidden');
    });

    test('warnings tab is aria-selected, properties tab is not', () => {
        renderPanel({ showWarningPanel: true, rightTab: 'warnings' });
        expect(screen.getByRole('tab', { name: 'Warnings' })).toHaveAttribute('aria-selected', 'true');
        expect(screen.getByRole('tab', { name: 'Well Group Properties' })).toHaveAttribute('aria-selected', 'false');
    });

    test('warnings tabpanel has correct aria-labelledby', () => {
        renderPanel({ showWarningPanel: true, rightTab: 'warnings' });
        const panel = document.getElementById('right-panel-warnings');
        expect(panel).toHaveAttribute('aria-labelledby', 'right-tab-warnings');
    });

    test('WarningPanel content is visible in warnings tab', () => {
        renderPanel({ showWarningPanel: true, rightTab: 'warnings', warnings: [] });
        expect(screen.getByText('No warnings.')).toBeInTheDocument();
    });
});
