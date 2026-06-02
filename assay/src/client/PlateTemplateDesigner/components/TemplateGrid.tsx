/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { FC, useCallback, useMemo, useRef, useState } from 'react';
import classNames from 'classnames';

import { PlateTemplate, Position, WellGroup } from '../models';

interface TemplateGridProps {
    activeGroup: null | WellGroup;
    activeTab: string;
    colorMap: Map<number, { color: string; colorIndex: number }>;
    highlightedGroupId: null | number;
    onCellToggle: (row: number, col: number) => void;
    onDragRect: (
        r1: number,
        c1: number,
        r2: number,
        c2: number,
        isUnselect: boolean,
        preDragPositions: Position[]
    ) => void;
    onWellHover: (groupRowId: null | number) => void;
    plate: PlateTemplate;
}

function getRowLabel(row: number): string {
    return String.fromCharCode(65 + row);
}

interface GridCellProps {
    cellRefs: React.MutableRefObject<Map<string, HTMLTableCellElement>>;
    col: number;
    color: string;
    colorIndex: number;
    isActive: boolean;
    isTabStop: boolean;
    label: string;
    onFocus: (row: number, col: number) => void;
    onKeyDown: (row: number, col: number, e: React.KeyboardEvent) => void;
    onMouseDown: (row: number, col: number, e: React.MouseEvent) => void;
    onMouseEnter: (row: number, col: number) => void;
    onMouseUp: (row: number, col: number) => void;
    row: number;
}

const GridCell: FC<GridCellProps> = ({
    row,
    col,
    color,
    colorIndex,
    label,
    isActive,
    isTabStop,
    cellRefs,
    onMouseDown,
    onMouseEnter,
    onMouseUp,
    onFocus,
    onKeyDown,
}) => {
    // row and col are stable for a given cell instance (position never changes), so these
    // callbacks remain stable as long as the parent handlers are stable useCallback refs.
    const handleMouseDown = useCallback((e: React.MouseEvent) => onMouseDown(row, col, e), [onMouseDown, row, col]);
    const handleMouseEnter = useCallback(() => onMouseEnter(row, col), [onMouseEnter, row, col]);
    const handleMouseUp = useCallback(() => onMouseUp(row, col), [onMouseUp, row, col]);
    const handleFocus = useCallback(() => onFocus(row, col), [onFocus, row, col]);
    const handleKeyDown = useCallback((e: React.KeyboardEvent) => onKeyDown(row, col, e), [onKeyDown, row, col]);
    // Callback refs don't need useCallback — React calls them on mount/unmount regardless of
    // function identity. Wrapping in useCallback causes a React Compiler error because the
    // compiler correctly infers the real dependency is cellRefs.current, not cellRefs.
    const handleRef = (el: HTMLTableCellElement | null) => {
        const key = `${row},${col}`;
        if (el) cellRefs.current.set(key, el);
        else cellRefs.current.delete(key);
    };

    return (
        <td
            aria-label={label}
            className={classNames('template-grid__cell', {
                'template-grid__cell--active': isActive,
                [`template-grid__cell--pattern-${colorIndex}`]: colorIndex >= 0,
            })}
            onFocus={handleFocus}
            onKeyDown={handleKeyDown}
            onMouseDown={handleMouseDown}
            onMouseEnter={handleMouseEnter}
            onMouseUp={handleMouseUp}
            ref={handleRef}
            role="gridcell"
            style={{ backgroundColor: color }}
            tabIndex={isTabStop ? 0 : -1}
            title={label}
        />
    );
};
GridCell.displayName = 'GridCell';

/**
 * A scrollable well grid that lets the user paint cells onto the active well group.
 * Users can click on an individual well to toggle its membership in the selected group
 * or click/drag to set a range of wells at once.
 */
export const TemplateGrid: FC<TemplateGridProps> = ({
    plate,
    activeGroup,
    activeTab,
    colorMap,
    highlightedGroupId,
    onDragRect,
    onCellToggle,
    onWellHover,
}) => {
    const isDragging = useRef(false);
    const hasMoved = useRef(false);
    const startCell = useRef<null | { col: number; row: number }>(null);
    const dragIsUnselect = useRef(false); // true when the drag started on a cell already in the active group
    const preDragPositions = useRef<Position[]>([]); // snapshot of activeGroup.positions at mousedown

    // Roving-tabindex state: tracks which cell holds tabIndex=0. Null means no cell has been
    // focused yet, in which case (0,0) is the tab entry point.
    const [focusedCell, setFocusedCell] = useState<null | { col: number; row: number }>(null);
    const cellRefs = useRef<Map<string, HTMLTableCellElement>>(new Map());

    // Pre-compute a "row,col" → {color, groupName, groupRowId} map for the active tab type.
    // This lets each cell do an O(1) lookup rather than scanning all groups and
    // positions on every render (which would be O(groups × positions) per cell).
    const positionMap = useMemo(() => {
        const map = new Map<string, { color: string; colorIndex: number; groupName: string; groupRowId: number }>();
        for (const group of plate.groups) {
            if (group.type !== activeTab) continue;
            const entry = colorMap.get(group.rowId);
            const color = entry?.color ?? '#f5f5f5';
            const colorIndex = entry?.colorIndex ?? -1;
            for (const p of group.positions) {
                map.set(`${p.row},${p.col}`, { color, colorIndex, groupName: group.name, groupRowId: group.rowId });
            }
        }
        return map;
    }, [plate, activeTab, colorMap]);

    // Pre-compute a Set of "row,col" keys for the highlighted group's positions.
    // The highlighted group is either the one being hovered in the group list, or
    // the active group when nothing is being hovered, so each cell does an O(1) check.
    const highlightedGroupPositionSet = useMemo(() => {
        const set = new Set<string>();
        if (highlightedGroupId === null) return set;
        for (const group of plate.groups) {
            if (group.rowId === highlightedGroupId) {
                for (const p of group.positions) set.add(`${p.row},${p.col}`);
                break;
            }
        }
        return set;
    }, [highlightedGroupId, plate.groups]);

    const handleMouseDown = useCallback(
        (row: number, col: number, e: React.MouseEvent) => {
            if (e.button !== 0) return;
            isDragging.current = true;
            hasMoved.current = false;
            startCell.current = { row, col };
            dragIsUnselect.current = activeGroup?.positions.some(p => p.row === row && p.col === col) ?? false;
            // Snapshot the current positions NOW, from the prop, before any drag events can modify state.
            preDragPositions.current = activeGroup?.positions ?? [];
            // Note: text selection during drag is already prevented by `user-select: none` in CSS,
            // so e.preventDefault() is not needed here and is intentionally omitted so the browser's
            // default focus-on-mousedown behaviour is preserved.
        },
        [activeGroup]
    );

    const handleMouseEnter = useCallback(
        (row: number, col: number) => {
            if (isDragging.current && startCell.current) {
                hasMoved.current = true;
                onDragRect(
                    startCell.current.row,
                    startCell.current.col,
                    row,
                    col,
                    dragIsUnselect.current,
                    preDragPositions.current
                );
            } else {
                // Not dragging: report which group this well belongs to for list highlighting.
                const entry = positionMap.get(`${row},${col}`);
                onWellHover(entry?.groupRowId ?? null);
            }
        },
        [onDragRect, onWellHover, positionMap]
    );

    // Called on mouseup over a specific cell — handles click-toggle
    const handleCellMouseUp = useCallback(
        (row: number, col: number) => {
            if (isDragging.current && !hasMoved.current) {
                // Explicitly move focus to the clicked cell so arrow-key navigation
                // picks up from the correct position after a mouse interaction.
                cellRefs.current.get(`${row},${col}`)?.focus();
                onCellToggle(row, col);
            }
        },
        [onCellToggle]
    );

    // Called on the wrapper div — cleans up drag state and clears well hover
    const handleDragEnd = useCallback(() => {
        isDragging.current = false;
        hasMoved.current = false;
        startCell.current = null;
        dragIsUnselect.current = false;
        onWellHover(null);
    }, [onWellHover]);

    const handleCellFocus = useCallback(
        (row: number, col: number) => {
            setFocusedCell({ row, col });
            const entry = positionMap.get(`${row},${col}`);
            onWellHover(entry?.groupRowId ?? null);
        },
        [positionMap, onWellHover]
    );

    // Keyboard interaction for grid cells:
    //   Space / Enter → toggle the cell (same as a click with no drag)
    //   Arrow keys    → move focus to the adjacent cell (wraps are intentionally prevented
    //                   at plate edges to avoid confusing wrap-around focus jumps)
    const handleCellKeyDown = useCallback(
        (row: number, col: number, e: React.KeyboardEvent) => {
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
                case 'ArrowDown':
                    if (row < plate.rows - 1) moveFocus(row + 1, col);
                    break;
                case 'ArrowLeft':
                    if (col > 0) moveFocus(row, col - 1);
                    break;
                case 'ArrowRight':
                    if (col < plate.cols - 1) moveFocus(row, col + 1);
                    break;
                case 'ArrowUp':
                    if (row > 0) moveFocus(row - 1, col);
                    break;
            }
        },
        [onCellToggle, plate.rows, plate.cols]
    );

    const handleGridBlur = useCallback(
        (e: React.FocusEvent<HTMLDivElement>) => {
            // Clear well hover when keyboard focus leaves the grid entirely
            if (!e.currentTarget.contains(e.relatedTarget as Node)) onWellHover(null);
        },
        [onWellHover]
    );

    return (
        <div className="template-grid" onBlur={handleGridBlur} onMouseLeave={handleDragEnd} onMouseUp={handleDragEnd}>
            <table aria-label="Plate template grid" className="template-grid__table" role="grid">
                <thead>
                    <tr>
                        <th className="template-grid__corner" />
                        {Array.from({ length: plate.cols }, (_, col) => (
                            <th className="template-grid__col-header" key={col} scope="col">
                                {col + 1}
                            </th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                    {Array.from({ length: plate.rows }, (_, row) => (
                        <tr key={row}>
                            <th className="template-grid__row-header" scope="row">
                                {getRowLabel(row)}
                            </th>
                            {Array.from({ length: plate.cols }, (_, col) => {
                                const entry = positionMap.get(`${row},${col}`);
                                const isHighlightedGroupCell = highlightedGroupPositionSet.has(`${row},${col}`);
                                const location = `${getRowLabel(row)}${col + 1}`;
                                const tooltip = entry ? `${location}: ${entry.groupName}` : location;
                                const isTabStop = focusedCell
                                    ? focusedCell.row === row && focusedCell.col === col
                                    : row === 0 && col === 0;
                                return (
                                    <GridCell
                                        cellRefs={cellRefs}
                                        col={col}
                                        color={entry?.color ?? '#f5f5f5'}
                                        colorIndex={entry?.colorIndex ?? -1}
                                        isActive={isHighlightedGroupCell}
                                        isTabStop={isTabStop}
                                        key={col}
                                        label={tooltip}
                                        onFocus={handleCellFocus}
                                        onKeyDown={handleCellKeyDown}
                                        onMouseDown={handleMouseDown}
                                        onMouseEnter={handleMouseEnter}
                                        onMouseUp={handleCellMouseUp}
                                        row={row}
                                    />
                                );
                            })}
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
};
TemplateGrid.displayName = 'TemplateGrid';
