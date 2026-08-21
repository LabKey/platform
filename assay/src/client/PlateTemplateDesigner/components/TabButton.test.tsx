/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';

import { TabButton } from './TabButton';

function renderTab(overrides: Partial<React.ComponentProps<typeof TabButton>> = {}) {
    const props = {
        id: 'my-tab',
        panelId: 'my-panel',
        isActive: false,
        baseClass: 'my-tab',
        onClick: jest.fn(),
        children: 'Tab Label',
        ...overrides,
    };
    render(<TabButton {...props} />);
    return props;
}

describe('TabButton', () => {
    test('renders with role="tab"', () => {
        renderTab();
        expect(screen.getByRole('tab')).toBeInTheDocument();
    });

    test('sets id on the button element', () => {
        renderTab({ id: 'right-tab-properties' });
        expect(screen.getByRole('tab')).toHaveAttribute('id', 'right-tab-properties');
    });

    test('sets aria-controls to panelId', () => {
        renderTab({ panelId: 'right-panel-properties' });
        expect(screen.getByRole('tab')).toHaveAttribute('aria-controls', 'right-panel-properties');
    });

    test('aria-selected is true when isActive is true', () => {
        renderTab({ isActive: true });
        expect(screen.getByRole('tab', { selected: true })).toBeInTheDocument();
    });

    test('aria-selected is false when isActive is false', () => {
        renderTab({ isActive: false });
        expect(screen.getByRole('tab', { selected: false })).toBeInTheDocument();
    });

    test('applies baseClass to the button', () => {
        renderTab({ baseClass: 'my-tab' });
        expect(screen.getByRole('tab')).toHaveClass('my-tab');
    });

    test('adds <baseClass>--active when isActive is true', () => {
        renderTab({ baseClass: 'my-tab', isActive: true });
        expect(screen.getByRole('tab')).toHaveClass('my-tab--active');
    });

    test('does not add <baseClass>--active when isActive is false', () => {
        renderTab({ baseClass: 'my-tab', isActive: false });
        expect(screen.getByRole('tab')).not.toHaveClass('my-tab--active');
    });

    test('applies extraClassName when provided', () => {
        renderTab({ extraClassName: 'my-tab--warn' });
        expect(screen.getByRole('tab')).toHaveClass('my-tab--warn');
    });

    test('does not add unexpected classes when extraClassName is omitted', () => {
        renderTab({ baseClass: 'my-tab', isActive: false });
        expect(screen.getByRole('tab')).toHaveClass('my-tab');
        expect(screen.getByRole('tab').className.trim()).toBe('my-tab');
    });

    test('renders children', () => {
        renderTab({ children: 'Well Group Properties' });
        expect(screen.getByText('Well Group Properties')).toBeInTheDocument();
    });

    test('calls onClick when clicked', async () => {
        const { onClick } = renderTab();
        await userEvent.click(screen.getByRole('tab'));
        expect(onClick).toHaveBeenCalledTimes(1);
    });
});
