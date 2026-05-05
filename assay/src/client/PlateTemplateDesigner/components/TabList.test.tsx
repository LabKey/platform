/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';

import { TabList } from './TabList';

function renderTabList(tabLabels = ['Tab A', 'Tab B', 'Tab C']) {
    const onTabClick = jest.fn();
    render(
        <TabList className="test-tablist">
            {tabLabels.map(label => (
                <button key={label} role="tab" onClick={onTabClick}>
                    {label}
                </button>
            ))}
        </TabList>
    );
    return {
        onTabClick,
        tabs: screen.getAllByRole('tab'),
        tablist: screen.getByRole('tablist'),
    };
}

describe('TabList', () => {
    describe('rendering', () => {
        test('renders with role="tablist"', () => {
            renderTabList();
            expect(screen.getByRole('tablist')).toBeInTheDocument();
        });

        test('applies className to the container', () => {
            renderTabList();
            expect(screen.getByRole('tablist')).toHaveClass('test-tablist');
        });

        test('renders child tabs', () => {
            renderTabList();
            expect(screen.getAllByRole('tab')).toHaveLength(3);
        });
    });

    describe('ArrowRight navigation', () => {
        test('moves focus from first tab to second', () => {
            const { tabs, tablist } = renderTabList();
            tabs[0].focus();
            fireEvent.keyDown(tablist, { key: 'ArrowRight' });
            expect(document.activeElement).toBe(tabs[1]);
        });

        test('moves focus from second tab to third', () => {
            const { tabs, tablist } = renderTabList();
            tabs[1].focus();
            fireEvent.keyDown(tablist, { key: 'ArrowRight' });
            expect(document.activeElement).toBe(tabs[2]);
        });

        test('wraps from last tab back to first', () => {
            const { tabs, tablist } = renderTabList();
            tabs[2].focus();
            fireEvent.keyDown(tablist, { key: 'ArrowRight' });
            expect(document.activeElement).toBe(tabs[0]);
        });
    });

    describe('ArrowLeft navigation', () => {
        test('moves focus from third tab to second', () => {
            const { tabs, tablist } = renderTabList();
            tabs[2].focus();
            fireEvent.keyDown(tablist, { key: 'ArrowLeft' });
            expect(document.activeElement).toBe(tabs[1]);
        });

        test('moves focus from second tab to first', () => {
            const { tabs, tablist } = renderTabList();
            tabs[1].focus();
            fireEvent.keyDown(tablist, { key: 'ArrowLeft' });
            expect(document.activeElement).toBe(tabs[0]);
        });

        test('wraps from first tab back to last', () => {
            const { tabs, tablist } = renderTabList();
            tabs[0].focus();
            fireEvent.keyDown(tablist, { key: 'ArrowLeft' });
            expect(document.activeElement).toBe(tabs[2]);
        });
    });

    describe('other keys', () => {
        test('non-arrow keys do not change focus', () => {
            const { tabs, tablist } = renderTabList();
            tabs[0].focus();
            fireEvent.keyDown(tablist, { key: 'Enter' });
            expect(document.activeElement).toBe(tabs[0]);
        });

        test('does not throw when no tab is currently focused', () => {
            const { tablist } = renderTabList();
            expect(() => fireEvent.keyDown(tablist, { key: 'ArrowRight' })).not.toThrow();
        });
    });
});
