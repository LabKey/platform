/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { render, screen } from '@testing-library/react';

import { WarningPanel } from './WarningPanel';

describe('WarningPanel', () => {
    test('shows "No warnings." and no list when warnings is empty', () => {
        render(<WarningPanel warnings={[]} />);
        expect(screen.getByText('No warnings.')).toBeInTheDocument();
        expect(screen.queryByRole('list')).toBeNull();
    });

    test('renders a list item for each warning', () => {
        const warnings = ['A1: replicate with no specimen', 'B3: in both specimen and control'];
        render(<WarningPanel warnings={warnings} />);
        expect(screen.queryByText('No warnings.')).toBeNull();
        expect(screen.getAllByRole('listitem')).toHaveLength(2);
        expect(screen.getByText('A1: replicate with no specimen')).toBeInTheDocument();
        expect(screen.getByText('B3: in both specimen and control')).toBeInTheDocument();
    });

    test('renders a single warning without "No warnings."', () => {
        render(<WarningPanel warnings={['H12: a warning']} />);
        expect(screen.getAllByRole('listitem')).toHaveLength(1);
        expect(screen.queryByText('No warnings.')).toBeNull();
    });

    test('switches from "No warnings." to a list when warnings change', () => {
        const { rerender } = render(<WarningPanel warnings={[]} />);
        expect(screen.getByText('No warnings.')).toBeInTheDocument();

        rerender(<WarningPanel warnings={['A1: some warning']} />);
        expect(screen.queryByText('No warnings.')).toBeNull();
        expect(screen.getByText('A1: some warning')).toBeInTheDocument();
    });
});
