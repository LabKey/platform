/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { useCallback, useMemo, useRef, useState } from 'react';
import classNames from 'classnames';

import { PlateTemplate, Position, WellGroup } from '../models';

interface Props {
    plate: PlateTemplate;
    activeGroup: WellGroup | null;
    activeTab: string;
    colorMap: Map<number, string>;
    onDragRect: (r1: number, c1: number, r2: number, c2: number, isUnselect: boolean, preDragPositions: Position[]) => void;
    onCellToggle: (row: number, col: number) => void;
}

function getRowLabel(row: number): string {
    return String.fromCharCode(65 + row);
}

/**
 * A scrollable well grid that lets the user paint cells onto the active well group.
 *
 * ─── Coloring ──────────────────────────────────────────────────────────────────
 * Only wells belonging to groups of the *active tab type* are colored. Wells from
 * other types are invisible in the current view.
 *
 * ─── Drag / click interaction ──────────────────────────────────────────────────
 * Cell assignment uses a three-phase state machine tracked entirely via refs
 * (no re-renders on drag):
 *
 *   Phase 1 – mousedown on a cell:
 *     Enter drag mode. Record the start cell. Do NOT assign anything yet — we
 *     first need to know whether the user is clicking (toggle) or dragging (rect).
 *
 *   Phase 2 – mouseenter a *different* cell while dragging:
 *     We now know it's a drag. Call onDragRect with the axis-aligned rectangle
 *     defined by the mousedown cell and the current cell, plus the drag mode
 *     (select vs unselect) determined at mousedown. The parent replaces or removes
 *     cells on every call, so the selection dynamically resizes as the mouse moves.
 *
 *   Phase 3 – mouseup:
 *     If the pointer never left the start cell (hasMoved === false), treat the
 *     interaction as a click and toggle that cell (add if absent, remove if present).
 *     Either way, reset all drag state.
 *
 * Drag state is also cleaned up on mouseleave of the outer div, preventing stuck
 * drag state when the pointer exits the grid.
 */
export function TemplateGrid({ plate, activeGroup, activeTab, colorMap, onDragRect, onCellToggle }: Props): JSX.Element {
    const isDragging = useRef(false);
    const hasMoved = useRef(false);
    const startCell = useRef<{ row: number; col: number } | null>(null);
    const dragIsUnselect = useRef(false);  // true when the drag started on a cell already in the active group
    const preDragPositions = useRef<Position[]>([]);  // snapshot of activeGroup.positions at mousedown

    // Roving-tabindex state: tracks which cell holds tabIndex=0. Null means no cell has been
    // focused yet, in which case (0,0) is the tab entry point.
    const [focusedCell, setFocusedCell] = useState<{ row: number; col: number } | null>(null);
    const cellRefs = useRef<Map<string, HTMLTableCellElement>>(new Map());

    // Pre-compute a "row,col" → {color, groupName} map for the active tab type.
    // This lets each cell do an O(1) lookup rather than scanning all groups and
    // positions on every render (which would be O(groups × positions) per cell).
    const positionMap = useMemo(() => {
        const map = new Map<string, { color: string; groupName: string }>();
        for (const group of plate.groups) {
            if (group.type !== activeTab) continue;
            const color = colorMap.get(group.rowId) ?? '#f5f5f5';
            for (const p of group.positions) {
                map.set(`${p.row},${p.col}`, { color, groupName: group.name });
            }
        }
        return map;
    }, [plate, activeTab, colorMap]);

    const handleMouseDown = useCallback((row: number, col: number, e: React.MouseEvent) => {
        if (e.button !== 0) return;
        isDragging.current = true;
        hasMoved.current = false;
        startCell.current = { row, col };
        dragIsUnselect.current = activeGroup?.positions.some(p => p.row === row && p.col === col) ?? false;
        // Snapshot the current positions NOW, from the prop, before any drag events can modify state.
        preDragPositions.current = activeGroup?.positions ?? [];
        e.preventDefault();
    }, [activeGroup]);

    const handleMouseEnter = useCallback((row: number, col: number) => {
        if (!isDragging.current || !startCell.current) return;
        hasMoved.current = true;
        onDragRect(startCell.current.row, startCell.current.col, row, col, dragIsUnselect.current, preDragPositions.current);
    }, [onDragRect]);

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
        dragIsUnselect.current = false;
    }, []);

    const handleCellFocus = useCallback((row: number, col: number) => {
        setFocusedCell({ row, col });
    }, []);

    // Keyboard interaction for grid cells:
    //   Space / Enter → toggle the cell (same as a click with no drag)
    //   Arrow keys    → move focus to the adjacent cell (wraps are intentionally prevented
    //                   at plate edges to avoid confusing wrap-around focus jumps)
    const handleCellKeyDown = useCallback((row: number, col: number, e: React.KeyboardEvent) => {
        const moveFocus = (r: number, c: number) => {
            e.preventDefault();
            setFocusedCell({ row: r, col: c });
            cellRefs.current.get(`${r},${c}`)?.focus();
        };
        switch (e.key) {
            case ' ':
            case 'Enter':
                e.preventDefault();
                onCellToggle(row, col);
                break;
            case 'ArrowUp':
                if (row > 0) moveFocus(row - 1, col);
                break;
            case 'ArrowDown':
                if (row < plate.rows - 1) moveFocus(row + 1, col);
                break;
            case 'ArrowLeft':
                if (col > 0) moveFocus(row, col - 1);
                break;
            case 'ArrowRight':
                if (col < plate.cols - 1) moveFocus(row, col + 1);
                break;
        }
    }, [onCellToggle, plate.rows, plate.cols]);

    return (
        <div className="template-grid" onMouseLeave={handleDragEnd} onMouseUp={handleDragEnd}>
            <table className="template-grid__table" role="grid">
                <thead>
                    <tr>
                        <th className="template-grid__corner" />
                        {Array.from({ length: plate.cols }, (_, col) => (
                            <th key={col} scope="col" className="template-grid__col-header">{col + 1}</th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                    {Array.from({ length: plate.rows }, (_, row) => (
                        <tr key={row}>
                            <th scope="row" className="template-grid__row-header">{getRowLabel(row)}</th>
                            {Array.from({ length: plate.cols }, (_, col) => {
                                const entry = positionMap.get(`${row},${col}`);
                                const isActiveGroupCell = activeGroup?.positions.some(p => p.row === row && p.col === col);
                                const location = `${getRowLabel(row)}${col + 1}`;
                                const tooltip = entry ? `${location}: ${entry.groupName}` : location;
                                const isTabStop = focusedCell
                                    ? focusedCell.row === row && focusedCell.col === col
                                    : row === 0 && col === 0;
                                return (
                                    <td
                                        key={col}
                                        ref={el => {
                                            const key = `${row},${col}`;
                                            if (el) cellRefs.current.set(key, el);
                                            else cellRefs.current.delete(key);
                                        }}
                                        tabIndex={isTabStop ? 0 : -1}
                                        className={classNames('template-grid__cell', {
                                            'template-grid__cell--active': isActiveGroupCell,
                                        })}
                                        style={{ backgroundColor: entry?.color ?? '#f5f5f5' }}
                                        title={tooltip}
                                        aria-label={tooltip}
                                        onMouseDown={e => handleMouseDown(row, col, e)}
                                        onMouseEnter={() => handleMouseEnter(row, col)}
                                        onMouseUp={() => handleCellMouseUp(row, col)}
                                        onFocus={() => handleCellFocus(row, col)}
                                        onKeyDown={e => handleCellKeyDown(row, col, e)}
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
