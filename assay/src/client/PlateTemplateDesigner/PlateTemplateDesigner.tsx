/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import classNames from 'classnames';
import { ActionURL, Ajax, Utils } from '@labkey/api';

import { PlateTemplate, Position, WellGroup, computeWarnings } from './models';
import { StatusBar } from './components/StatusBar';
import { GroupTypesPanel } from './components/GroupTypesPanel';
import { ShiftPanel } from './components/ShiftPanel';
import { TemplateGrid } from './components/TemplateGrid';
import { WellGroupProperties } from './components/WellGroupProperties';
import { WarningPanel } from './components/WarningPanel';

import './PlateTemplateDesigner.scss';

/**
 * Root component of the Plate Template Designer.
 *
 * ─── User workflow ──────────────────────────────────────────────────────────────
 * 1. On mount, URL parameters are read (templateName, plateId, assayType, rowCount,
 *    colCount, copy) and the plate definition is fetched from the server.
 * 2. The user selects a group type tab (e.g. CONTROL, SPECIMEN, REPLICATE).
 * 3. Within that type, the user selects or creates a named group.
 * 4. The user clicks or drags wells on the grid to paint them onto the active group.
 * 5. The user optionally edits well group properties in the right panel.
 * 6. "Save" persists without leaving; "Save & Close" saves then navigates to returnURL
 *    (or the plate list). "Cancel" navigates away without saving.
 *
 * ─── State architecture ─────────────────────────────────────────────────────────
 * `plate` is the single source of truth for all template data. All mutations go
 * through `setPlate` with functional updaters to avoid stale-closure bugs.
 *
 * `activeGroup` is a denormalized mirror of the currently selected group, kept in
 * sync with `plate` via the sync effect below. It exists separately because:
 *   - Callbacks that use `setPlate(prev => ...)` don't have access to the current
 *     group data inside the updater; they use `activeGroup` from their closure.
 *   - Components that show the active group (WellGroupProperties, TemplateGrid
 *     cell highlighting) need a stable reference that doesn't require traversing
 *     `plate.groups` on every access.
 *
 * ─── ID conventions ─────────────────────────────────────────────────────────────
 * Server-assigned group IDs are positive integers. Client-side created groups
 * receive temporary negative IDs (nextGroupIdRef counts down from -1). This ensures
 * new groups never collide with existing ones before the first save. The server
 * replaces all IDs with permanent values on save; the client does not update
 * individual group IDs — only the top-level `plate.rowId` is updated after save.
 *
 * ─── Cell interaction ───────────────────────────────────────────────────────────
 * Two cell callbacks are distinguished:
 *   `handleCellAssign` — idempotent add; also evicts the cell from any other group
 *     of the same type (one cell can only belong to one group per type). Used during
 *     drag operations.
 *   `handleCellToggle` — pure on/off; does not steal from siblings. Used for
 *     single-click (no drag movement).
 */

const COLORS = [
    '#4e79a7', '#f28e2b', '#e15759', '#76b7b2', '#59a14f',
    '#edc948', '#b07aa1', '#ff9da7', '#9c755f', '#bab0ac',
    '#6ba3be', '#ffbe7d', '#ff9d9a', '#86bcb6', '#8cd17d',
    '#f1ce63', '#d4a6c8', '#ffb7c5', '#c7a97e', '#d7d5cf',
];

function assignColors(groups: WellGroup[]): Map<number, string> {
    const map = new Map<number, string>();
    groups.forEach((g, i) => {
        map.set(g.rowId, COLORS[i % COLORS.length]);
    });
    return map;
}

export function PlateTemplateDesigner(): JSX.Element {
    const [plate, setPlate] = useState<PlateTemplate | null>(null);
    const [activeGroup, setActiveGroup] = useState<WellGroup | null>(null);
    const [activeTab, setActiveTab] = useState<string>('');
    const [rightTab, setRightTab] = useState<'properties' | 'warnings'>('properties');
    const [isDirty, setIsDirty] = useState(false);
    const [status, setStatus] = useState('');
    const [colorMap, setColorMap] = useState<Map<number, string>>(new Map());
    const [error, setError] = useState<string | null>(null);
    const plateNameRef = useRef<string>('');  // Mirrors plate.name; used in save-success to update URL without stale closure
    const statusTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const nextGroupIdRef = useRef(-1);  // Temporary negative IDs for client-created groups (see ID conventions above)
    // Always-current ref so callbacks can read the latest activeGroup without stale-closure bugs.
    const activeGroupRef = useRef<WellGroup | null>(null);
    activeGroupRef.current = activeGroup;
    const nextColorIndexRef = useRef(0);  // Monotonically increasing; never decrements on delete so colors stay unique

    useEffect(() => {
        const templateName = ActionURL.getParameter('templateName');
        const plateIdStr = ActionURL.getParameter('plateId');
        const assayType = ActionURL.getParameter('assayType');
        const templateType = ActionURL.getParameter('templateType');
        const rowCountStr = ActionURL.getParameter('rowCount');
        const colCountStr = ActionURL.getParameter('colCount');
        const copy = ActionURL.getParameter('copy') === 'true' || ActionURL.getParameter('copyTemplate') === 'true';

        const params: Record<string, string | number | boolean> = {};
        if (templateName) params.templateName = templateName;
        if (plateIdStr) params.plateId = parseInt(plateIdStr, 10);
        if (assayType) params.assayType = assayType;
        if (templateType) params.templateType = templateType;
        if (rowCountStr) params.rowCount = parseInt(rowCountStr, 10);
        if (colCountStr) params.colCount = parseInt(colCountStr, 10);
        params.copy = copy;

        Ajax.request({
            url: ActionURL.buildURL('plate', 'getTemplateDefinition.api'),
            method: 'GET',
            params,
            success: Utils.getCallbackWrapper((response: { data: PlateTemplate }) => {
                const plate = response.data;
                plateNameRef.current = plate.defaultPlateName || plate.name || '';
                setPlate({ ...plate, name: plateNameRef.current });
                setColorMap(assignColors(plate.groups));
                nextColorIndexRef.current = plate.groups.length;
                // Initialize below the minimum server rowId to avoid collisions.
                // Server IDs should be positive, but guard against zero or negative values.
                const minRowId = plate.groups.reduce((min, g) => Math.min(min, g.rowId), 0);
                nextGroupIdRef.current = Math.min(-1, minRowId - 1);
                setActiveTab(plate.groupTypes[0] ?? '');
                if (plate.copyMode) setIsDirty(true);
            }),
            failure: Utils.getCallbackWrapper((response: any) => {
                setError(response?.exception ?? 'Failed to load plate template.');
            }, null, true),
        });
    }, []);

    const handleNameChange = useCallback((name: string) => {
        plateNameRef.current = name;
        setPlate(prev => prev ? { ...prev, name } : null);
        setIsDirty(true);
    }, []);

    const handleGroupSelect = useCallback((group: WellGroup) => {
        setActiveGroup(group);
    }, []);

    // Called on every mouseenter during a drag with the rectangle defined by the
    // mousedown cell and the current cell. preDragPositions is the snapshot of the
    // active group's positions taken at mousedown (in TemplateGrid), before any drag
    // events can modify state.
    //
    // Select mode (drag started on an empty cell): adds the rectangle to the group's
    // pre-drag positions, so existing wells outside the rectangle are preserved.
    // Also evicts rectangle cells from sibling groups of the same type.
    //
    // Unselect mode (drag started on a cell already in the group): removes all
    // rectangle cells from the pre-drag positions without affecting other groups.
    const handleDragRect = useCallback((r1: number, c1: number, r2: number, c2: number, isUnselect: boolean, preDragPositions: Position[]) => {
        const activeGroup = activeGroupRef.current;
        if (!activeGroup) return;
        const minRow = Math.min(r1, r2);
        const maxRow = Math.max(r1, r2);
        const minCol = Math.min(c1, c2);
        const maxCol = Math.max(c1, c2);
        const rectPositions: Position[] = [];
        for (let r = minRow; r <= maxRow; r++) {
            for (let c = minCol; c <= maxCol; c++) {
                rectPositions.push({ row: r, col: c });
            }
        }
        const rectKeys = new Set(rectPositions.map(p => `${p.row},${p.col}`));
        setPlate(prev => {
            if (!prev) return null;
            // Look up the active group's current type from prev to avoid stale-closure issues.
            const currentType = prev.groups.find(g => g.rowId === activeGroup.rowId)?.type;
            const updatedGroups = prev.groups.map(g => {
                if (g.rowId === activeGroup.rowId) {
                    if (isUnselect) {
                        // Remove rect from pre-drag snapshot
                        return { ...g, positions: preDragPositions.filter(p => !rectKeys.has(`${p.row},${p.col}`)) };
                    }
                    // Add rect to pre-drag snapshot (union, deduped)
                    const preDragKeys = new Set(preDragPositions.map(p => `${p.row},${p.col}`));
                    const added = rectPositions.filter(p => !preDragKeys.has(`${p.row},${p.col}`));
                    return { ...g, positions: [...preDragPositions, ...added] };
                }
                if (!isUnselect && currentType !== undefined && g.type === currentType) {
                    // Evict rectangle cells from sibling groups of the same type
                    return { ...g, positions: g.positions.filter(p => !rectKeys.has(`${p.row},${p.col}`)) };
                }
                return g;
            });
            return { ...prev, groups: updatedGroups };
        });
        setIsDirty(true);
    }, []);

    // Pure toggle: add the cell if absent, remove it if present
    const handleCellToggle = useCallback((row: number, col: number) => {
        const activeGroup = activeGroupRef.current;
        if (!activeGroup) return;
        setPlate(prev => {
            if (!prev) return null;
            const updatedGroups = prev.groups.map(g => {
                if (g.rowId !== activeGroup.rowId) return g;
                const hasCell = g.positions.some(p => p.row === row && p.col === col);
                if (hasCell) {
                    return { ...g, positions: g.positions.filter(p => !(p.row === row && p.col === col)) };
                }
                return { ...g, positions: [...g.positions, { row, col }] };
            });
            return { ...prev, groups: updatedGroups };
        });
        setIsDirty(true);
    }, []);

    const handleAddGroup = useCallback((type: string, name: string) => {
        if (!plate) return;
        const rowId = nextGroupIdRef.current--;
        const newGroup: WellGroup = {
            rowId,
            type,
            name,
            positions: [],
            properties: {},
            allowNewGroups: plate.canCreateGroupsByType?.[type] ?? false,
        };
        setPlate(prev => prev ? { ...prev, groups: [...prev.groups, newGroup] } : null);
        const colorIndex = nextColorIndexRef.current++;
        setColorMap(prev => {
            const next = new Map(prev);
            next.set(rowId, COLORS[colorIndex % COLORS.length]);
            return next;
        });
        setActiveGroup(newGroup);
        setIsDirty(true);
    }, [plate]);

    const handleShift = useCallback((verticalShift: number, horizontalShift: number) => {
        setPlate(prev => {
            if (!prev) return null;
            const { rows, cols } = prev;
            const updatedGroups = prev.groups.map(g => {
                if (g.type !== activeTab) return g;
                return {
                    ...g,
                    positions: g.positions.map(p => ({
                        row: ((p.row - verticalShift) % rows + rows) % rows,
                        col: ((p.col - horizontalShift) % cols + cols) % cols,
                    })),
                };
            });
            return { ...prev, groups: updatedGroups };
        });
        setIsDirty(true);
    }, [activeTab]);

    const handleDeleteGroup = useCallback((rowId: number) => {
        setPlate(prev => prev ? { ...prev, groups: prev.groups.filter(g => g.rowId !== rowId) } : null);
        setColorMap(prev => { const next = new Map(prev); next.delete(rowId); return next; });
        setActiveGroup(prev => prev?.rowId === rowId ? null : prev);
        setIsDirty(true);
    }, []);

    const handleRenameGroup = useCallback((rowId: number, newName: string) => {
        setPlate(prev => prev ? { ...prev, groups: prev.groups.map(g => g.rowId === rowId ? { ...g, name: newName } : g) } : null);
        setActiveGroup(prev => prev?.rowId === rowId ? { ...prev, name: newName } : prev);
        setIsDirty(true);
    }, []);

    const handlePropertyChange = useCallback((groupRowId: number, key: string, value: string) => {
        setPlate(prev => {
            if (!prev) return null;
            const updatedGroups = prev.groups.map(g =>
                g.rowId === groupRowId ? { ...g, properties: { ...g.properties, [key]: value } } : g
            );
            return { ...prev, groups: updatedGroups };
        });
        setActiveGroup(prev => prev?.rowId === groupRowId ? { ...prev, properties: { ...prev.properties, [key]: value } } : prev);
        setIsDirty(true);
    }, []);

    const handleDeleteProperty = useCallback((groupRowId: number, key: string) => {
        setPlate(prev => {
            if (!prev) return null;
            const updatedGroups = prev.groups.map(g => {
                if (g.rowId !== groupRowId) return g;
                const { [key]: _removed, ...rest } = g.properties;
                return { ...g, properties: rest };
            });
            return { ...prev, groups: updatedGroups };
        });
        setActiveGroup(prev => {
            if (prev?.rowId !== groupRowId) return prev;
            const { [key]: _removed, ...rest } = prev.properties;
            return { ...prev, properties: rest };
        });
        setIsDirty(true);
    }, []);

    const warningCount = useMemo(() => {
        if (!plate?.showWarningPanel) return 0;
        return computeWarnings(plate).length;
    }, [plate]);

    const navigateAway = useCallback(() => {
        const returnURL = ActionURL.getParameter('returnURL') || ActionURL.getParameter('returnUrl');
        const isSameOrigin = (url: string) => {
            try {
                return new URL(url, window.location.origin).origin === window.location.origin;
            } catch {
                return false;
            }
        };
        window.location.href = (returnURL && isSameOrigin(returnURL)) ? returnURL : ActionURL.buildURL('plate', 'plateList');
    }, []);

    /**
     * Shared Ajax save logic. Takes the plate snapshot and a success callback to avoid
     * duplicating the request setup and failure handler in handleSave / handleSaveAndClose.
     * The plate is passed as a parameter (rather than closed over) so callers can pass the
     * latest snapshot without worrying about stale state.
     */
    const requestSave = useCallback((currentPlate: PlateTemplate, onSuccess: (response: { data: { rowId: number } }) => void) => {
        setStatus('Saving...');
        Ajax.request({
            url: ActionURL.buildURL('plate', 'saveTemplate.api'),
            method: 'POST',
            jsonData: currentPlate,
            success: Utils.getCallbackWrapper(onSuccess),
            failure: Utils.getCallbackWrapper((response: any) => {
                setStatus('Save failed: ' + (response?.exception ?? 'unknown error'));
            }, null, true),
        });
    }, []);

    const handleSave = useCallback(() => {
        if (!plate) return;
        requestSave(plate, (response) => {
            const rowId = response.data.rowId;
            setIsDirty(false);
            setPlate(prev => prev ? { ...prev, rowId } : null);
            // Update URL to canonical form so a refresh reloads this plate
            const url = new URL(window.location.href);
            url.search = '';
            url.searchParams.set('templateName', plateNameRef.current);
            url.searchParams.set('plateId', String(rowId));
            window.history.replaceState(null, '', url.toString());
            setStatus('Saved.');
            if (statusTimerRef.current) clearTimeout(statusTimerRef.current);
            statusTimerRef.current = setTimeout(() => setStatus(''), 5000);
        });
    }, [plate, requestSave]);

    const handleSaveAndClose = useCallback(() => {
        if (!plate) return;
        if (!isDirty) {
            navigateAway();
            return;
        }
        requestSave(plate, () => {
            setIsDirty(false);
            navigateAway();
        });
    }, [plate, isDirty, navigateAway, requestSave]);

    const handleCancel = useCallback(() => {
        navigateAway();
    }, [navigateAway]);

    // Warn on unsaved navigation
    useEffect(() => {
        const handler = (e: BeforeUnloadEvent) => {
            if (isDirty) {
                e.preventDefault();
                e.returnValue = '';
            }
        };
        window.addEventListener('beforeunload', handler);
        return () => window.removeEventListener('beforeunload', handler);
    }, [isDirty]);

    // Keep activeGroup in sync when plate changes.
    //
    // Most plate mutations go through setPlate(prev => ...) updaters which don't have
    // access to the current activeGroup. After each plate update, this effect finds the
    // matching group by rowId and refreshes activeGroup so downstream components (e.g.
    // WellGroupProperties, TemplateGrid cell highlight) see the latest data.
    //
    // activeGroup is intentionally excluded from the deps array: adding it would cause
    // an infinite loop (effect sets activeGroup → triggers effect → sets activeGroup …).
    // handleDeleteGroup handles the "group no longer exists" case by explicitly setting
    // activeGroup to null before this effect can run.
    useEffect(() => {
        if (!plate) return;
        if (activeGroup) {
            const updated = plate.groups.find(g => g.rowId === activeGroup.rowId);
            if (updated) {
                setActiveGroup(updated);
            }
        }
    }, [plate]); // eslint-disable-line react-hooks/exhaustive-deps

    if (error) {
        return <div className="plate-template-designer__error">{error}</div>;
    }

    if (!plate) {
        return <div className="plate-template-designer__loading">Loading...</div>;
    }

    return (
        <div className="plate-template-designer">
            <StatusBar
                isDirty={isDirty}
                status={status}
                onSaveAndClose={handleSaveAndClose}
                onSave={handleSave}
                onCancel={handleCancel}
            />
            <div className="plate-template-designer__header">
                <label className="plate-template-designer__name-label">
                    Plate Name:
                    <input
                        className="plate-template-designer__name-input"
                        type="text"
                        value={plate.name}
                        onChange={e => handleNameChange(e.target.value)}
                    />
                </label>
            </div>
            <div className="plate-template-designer__body">
                <div className="plate-template-designer__left">
                    <GroupTypesPanel
                        plate={plate}
                        activeGroup={activeGroup}
                        activeTab={activeTab}
                        colorMap={colorMap}
                        onGroupSelect={handleGroupSelect}
                        onTabChange={(tab) => { setActiveTab(tab); setActiveGroup(null); }}
                        onAddGroup={handleAddGroup}
                        onDeleteGroup={handleDeleteGroup}
                        onRenameGroup={handleRenameGroup}
                    >
                        {/* The grid and shift panel are passed as children so they render
                            inside GroupTypesPanel's flex row, visually adjacent to the group list. */}
                        <div className="plate-grid-area">
                            <TemplateGrid
                                plate={plate}
                                activeGroup={activeGroup}
                                activeTab={activeTab}
                                colorMap={colorMap}
                                onDragRect={handleDragRect}
                                onCellToggle={handleCellToggle}
                            />
                            <ShiftPanel onShift={handleShift} />
                        </div>
                    </GroupTypesPanel>
                </div>
                {/* Right panel: WellGroupProperties and (if enabled) a Warnings tab.
                    The tab strip only renders when showWarningPanel is true; otherwise
                    WellGroupProperties fills the full right column without tabs. */}
                <div className="plate-template-designer__right">
                    {plate.showWarningPanel && (
                        <div className="right-panel-tabs" role="tablist">
                            <button
                                id="right-tab-properties"
                                role="tab"
                                aria-controls="right-panel-properties"
                                aria-selected={rightTab === 'properties'}
                                className={classNames('right-panel-tabs__tab', {
                                    'right-panel-tabs__tab--active': rightTab === 'properties',
                                })}
                                onClick={() => setRightTab('properties')}
                            >
                                Well Group Properties
                            </button>
                            <button
                                id="right-tab-warnings"
                                role="tab"
                                aria-controls="right-panel-warnings"
                                aria-selected={rightTab === 'warnings'}
                                className={classNames('right-panel-tabs__tab', {
                                    'right-panel-tabs__tab--active': rightTab === 'warnings',
                                    'right-panel-tabs__tab--warn': warningCount > 0,
                                })}
                                onClick={() => setRightTab('warnings')}
                            >
                                {warningCount > 0 ? `Warnings (${warningCount})` : 'Warnings'}
                            </button>
                        </div>
                    )}
                    {(!plate.showWarningPanel || rightTab === 'properties') && (
                        <div
                            id={plate.showWarningPanel ? 'right-panel-properties' : undefined}
                            role={plate.showWarningPanel ? 'tabpanel' : undefined}
                            aria-labelledby={plate.showWarningPanel ? 'right-tab-properties' : undefined}
                        >
                            <WellGroupProperties
                                activeGroup={activeGroup}
                                onPropertyChange={handlePropertyChange}
                                onDeleteProperty={handleDeleteProperty}
                            />
                        </div>
                    )}
                    {plate.showWarningPanel && rightTab === 'warnings' && (
                        <div id="right-panel-warnings" role="tabpanel" aria-labelledby="right-tab-warnings">
                            <WarningPanel plate={plate} />
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
