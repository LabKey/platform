/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { useCallback, useRef } from 'react';

import { PlateTemplate, WellGroup } from '../models';

interface Props {
    plate: PlateTemplate;
    activeGroup: WellGroup | null;
    activeTab: string;
    colorMap: Map<number, string>;
    onCellAssign: (row: number, col: number) => void;
    onCellToggle: (row: number, col: number) => void;
}

function getRowLabel(row: number): string {
    return String.fromCharCode(65 + row);
}

function getCellColor(row: number, col: number, activeTab: string, plate: PlateTemplate, colorMap: Map<number, string>): string | undefined {
    // Only color cells belonging to groups of the currently active tab type,
    // matching the GWT behavior of showing one type's layout at a time.
    let color: string | undefined;
    for (const group of plate.groups) {
        if (group.type === activeTab && group.positions.some(p => p.row === row && p.col === col)) {
            color = colorMap.get(group.rowId);
        }
    }
    return color;
}

export function TemplateGrid({ plate, activeGroup, activeTab, colorMap, onCellAssign, onCellToggle }: Props): JSX.Element {
    const isDragging = useRef(false);
    const hasMoved = useRef(false);
    const startCell = useRef<{ row: number; col: number } | null>(null);
    const dragCells = useRef<Set<string>>(new Set());

    const handleMouseDown = useCallback((row: number, col: number, e: React.MouseEvent) => {
        if (e.button !== 0) return;
        isDragging.current = true;
        hasMoved.current = false;
        startCell.current = { row, col };
        dragCells.current = new Set([`${row},${col}`]);
        e.preventDefault();
    }, []);

    const handleMouseEnter = useCallback((row: number, col: number) => {
        if (!isDragging.current) return;
        if (!hasMoved.current) {
            hasMoved.current = true;
            // Deferred: assign the mousedown cell now that we know it's a drag
            if (startCell.current) {
                onCellAssign(startCell.current.row, startCell.current.col);
            }
        }
        const key = `${row},${col}`;
        if (!dragCells.current.has(key)) {
            dragCells.current.add(key);
            onCellAssign(row, col);
        }
    }, [onCellAssign]);

    // Called on mouseup over a specific cell — handles click-toggle
    const handleCellMouseUp = useCallback((row: number, col: number) => {
        if (isDragging.current && !hasMoved.current) {
            onCellToggle(row, col);
        }
    }, [onCellToggle]);

    // Called on the wrapper div — cleans up drag state
    const handleDragEnd = useCallback(() => {
        isDragging.current = false;
        hasMoved.current = false;
        startCell.current = null;
        dragCells.current = new Set();
    }, []);

    return (
        <div className="template-grid" onMouseLeave={handleDragEnd} onMouseUp={handleDragEnd}>
            <table className="template-grid__table">
                <thead>
                    <tr>
                        <th className="template-grid__corner" />
                        {Array.from({ length: plate.cols }, (_, col) => (
                            <th key={col} className="template-grid__col-header">{col + 1}</th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                    {Array.from({ length: plate.rows }, (_, row) => (
                        <tr key={row}>
                            <td className="template-grid__row-header">{getRowLabel(row)}</td>
                            {Array.from({ length: plate.cols }, (_, col) => {
                                const color = getCellColor(row, col, activeTab, plate, colorMap);
                                const isActiveGroupCell = activeGroup?.positions.some(p => p.row === row && p.col === col);
                                const location = `${getRowLabel(row)}${col + 1}`;
                                const groupForCell = plate.groups.find(
                                    g => g.type === activeTab && g.positions.some(p => p.row === row && p.col === col)
                                );
                                const tooltip = groupForCell ? `${location}: ${groupForCell.name}` : location;
                                return (
                                    <td
                                        key={col}
                                        className={
                                            'template-grid__cell' +
                                            (isActiveGroupCell ? ' template-grid__cell--active' : '')
                                        }
                                        style={{ backgroundColor: color ?? '#f5f5f5' }}
                                        title={tooltip}
                                        onMouseDown={e => handleMouseDown(row, col, e)}
                                        onMouseEnter={() => handleMouseEnter(row, col)}
                                        onMouseUp={() => handleCellMouseUp(row, col)}
                                    />
                                );
                            })}
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}
