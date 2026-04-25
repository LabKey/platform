/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';

interface Props {
    onShift: (verticalShift: number, horizontalShift: number) => void;
}

export function ShiftPanel({ onShift }: Props): JSX.Element {
    return (
        <div className="shift-panel">
            <div className="shift-panel__grid">
                <span />
                <button className="shift-panel__btn" title="Shift up" onClick={() => onShift(1, 0)}>↑</button>
                <span />
                <button className="shift-panel__btn" title="Shift left" onClick={() => onShift(0, 1)}>←</button>
                <span className="shift-panel__label">Shift</span>
                <button className="shift-panel__btn" title="Shift right" onClick={() => onShift(0, -1)}>→</button>
                <span />
                <button className="shift-panel__btn" title="Shift down" onClick={() => onShift(-1, 0)}>↓</button>
                <span />
            </div>
        </div>
    );
}
