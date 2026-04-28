/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';

import { ShiftPanel } from './ShiftPanel';

describe('ShiftPanel', () => {
    test('renders four directional buttons', () => {
        render(<ShiftPanel onShift={jest.fn()} />);
        expect(screen.getByRole('button', { name: 'Shift up' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Shift down' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Shift left' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Shift right' })).toBeInTheDocument();
    });

    test('Shift up calls onShift(1, 0) — moves cells up (decreases row index)', async () => {
        const onShift = jest.fn();
        render(<ShiftPanel onShift={onShift} />);
        await userEvent.click(screen.getByRole('button', { name: 'Shift up' }));
        expect(onShift).toHaveBeenCalledWith(1, 0);
    });

    test('Shift down calls onShift(-1, 0) — moves cells down (increases row index)', async () => {
        const onShift = jest.fn();
        render(<ShiftPanel onShift={onShift} />);
        await userEvent.click(screen.getByRole('button', { name: 'Shift down' }));
        expect(onShift).toHaveBeenCalledWith(-1, 0);
    });

    test('Shift left calls onShift(0, 1) — moves cells left (decreases col index)', async () => {
        const onShift = jest.fn();
        render(<ShiftPanel onShift={onShift} />);
        await userEvent.click(screen.getByRole('button', { name: 'Shift left' }));
        expect(onShift).toHaveBeenCalledWith(0, 1);
    });

    test('Shift right calls onShift(0, -1) — moves cells right (increases col index)', async () => {
        const onShift = jest.fn();
        render(<ShiftPanel onShift={onShift} />);
        await userEvent.click(screen.getByRole('button', { name: 'Shift right' }));
        expect(onShift).toHaveBeenCalledWith(0, -1);
    });

    test('each button only fires once per click', async () => {
        const onShift = jest.fn();
        render(<ShiftPanel onShift={onShift} />);
        await userEvent.click(screen.getByRole('button', { name: 'Shift up' }));
        expect(onShift).toHaveBeenCalledTimes(1);
    });
});
