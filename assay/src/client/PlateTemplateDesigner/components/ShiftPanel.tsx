/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { FC, useCallback } from 'react';

interface ShiftPanelProps {
    onShift: (verticalShift: number, horizontalShift: number) => void;
}

/**
 * A compass-rose control that shifts all wells of the currently active group type one step in any
 * cardinal direction. The shift wraps around plate edges (toroidal), so wells that fall off the
 * bottom reappear at the top, etc.
 *
 * Sign convention (matches the modular arithmetic in PlateTemplateDesigner.handleShift):
 *   verticalShift > 0  → cells move UP   (row index decreases: row = (row - shift + rows) % rows)
 *   verticalShift < 0  → cells move DOWN
 *   horizontalShift > 0 → cells move LEFT  (col index decreases)
 *   horizontalShift < 0 → cells move RIGHT
 *
 * Shifts apply to every group of the active type simultaneously, preserving relative layout
 * between groups. Only the active tab's type is affected; other types are unchanged.
 */
export const ShiftPanel: FC<ShiftPanelProps> = ({ onShift }) => {
    const handleShiftUp = useCallback(() => onShift(1, 0), [onShift]);
    const handleShiftDown = useCallback(() => onShift(-1, 0), [onShift]);
    const handleShiftLeft = useCallback(() => onShift(0, 1), [onShift]);
    const handleShiftRight = useCallback(() => onShift(0, -1), [onShift]);

    return (
        <div className="shift-panel">
            <div className="shift-panel__grid">
                <span />
                <button aria-label="Shift up" className="shift-panel__btn" onClick={handleShiftUp} title="Shift up">
                    ↑
                </button>
                <span />
                <button
                    aria-label="Shift left"
                    className="shift-panel__btn"
                    onClick={handleShiftLeft}
                    title="Shift left"
                >
                    ←
                </button>
                <span className="shift-panel__label">Shift</span>
                <button
                    aria-label="Shift right"
                    className="shift-panel__btn"
                    onClick={handleShiftRight}
                    title="Shift right"
                >
                    →
                </button>
                <span />
                <button
                    aria-label="Shift down"
                    className="shift-panel__btn"
                    onClick={handleShiftDown}
                    title="Shift down"
                >
                    ↓
                </button>
                <span />
            </div>
        </div>
    );
};
ShiftPanel.displayName = 'ShiftPanel';
