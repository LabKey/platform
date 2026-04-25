/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActionURL, Ajax, Utils } from '@labkey/api';

import { PlateTemplate, WellGroup, computeWarnings } from './models';
import { StatusBar } from './components/StatusBar';
import { GroupTypesPanel } from './components/GroupTypesPanel';
import { ShiftPanel } from './components/ShiftPanel';
import { TemplateGrid } from './components/TemplateGrid';
import { WellGroupProperties } from './components/WellGroupProperties';
import { WarningPanel } from './components/WarningPanel';

import './PlateTemplateDesigner.scss';

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
    const plateNameRef = useRef<string>('');
    const statusTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const nextGroupIdRef = useRef(-1);

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

    const handleCellAssign = useCallback((row: number, col: number) => {
        if (!activeGroup || !plate) return;
        setPlate(prev => {
            if (!prev) return null;
            const updatedGroups = prev.groups.map(g => {
                if (g.rowId === activeGroup.rowId) {
                    const alreadyHas = g.positions.some(p => p.row === row && p.col === col);
                    if (alreadyHas) return g;
                    return { ...g, positions: [...g.positions, { row, col }] };
                }
                if (g.type === activeGroup.type) {
                    // Remove from other groups of the same type to avoid conflicts
                    return { ...g, positions: g.positions.filter(p => !(p.row === row && p.col === col)) };
                }
                return g;
            });
            return { ...prev, groups: updatedGroups };
        });
        setIsDirty(true);
    }, [activeGroup, plate]);

    const handleCellToggle = useCallback((row: number, col: number) => {
        if (!activeGroup || !plate) return;
        setPlate(prev => {
            if (!prev) return null;
            const updatedGroups = prev.groups.map(g => {
                if (g.rowId === activeGroup.rowId) {
                    const hasCell = g.positions.some(p => p.row === row && p.col === col);
                    if (hasCell) {
                        return { ...g, positions: g.positions.filter(p => !(p.row === row && p.col === col)) };
                    }
                    return { ...g, positions: [...g.positions, { row, col }] };
                }
                if (g.type === activeGroup.type) {
                    return { ...g, positions: g.positions.filter(p => !(p.row === row && p.col === col)) };
                }
                return g;
            });
            return { ...prev, groups: updatedGroups };
        });
        setIsDirty(true);
    }, [activeGroup, plate]);

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
        setColorMap(prev => {
            const next = new Map(prev);
            next.set(rowId, COLORS[prev.size % COLORS.length]);
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

    const handleSave = useCallback(() => {
        if (!plate) return;
        setStatus('Saving...');
        Ajax.request({
            url: ActionURL.buildURL('plate', 'saveTemplate.api'),
            method: 'POST',
            jsonData: plate,
            success: Utils.getCallbackWrapper((response: { data: { rowId: number } }) => {
                const rowId = response.data.rowId;
                setIsDirty(false);
                setPlate(prev => prev ? { ...prev, rowId } : null);
                // Update URL to canonical form so a refresh reloads this plate
                const url = new URL(window.location.href);
                url.search = '';
                url.searchParams.set('templateName', plate.name);
                url.searchParams.set('plateId', String(rowId));
                window.history.replaceState(null, '', url.toString());
                setStatus('Saved.');
                if (statusTimerRef.current) clearTimeout(statusTimerRef.current);
                statusTimerRef.current = setTimeout(() => setStatus(''), 5000);
            }),
            failure: Utils.getCallbackWrapper((response: any) => {
                setStatus('Save failed: ' + (response?.exception ?? 'unknown error'));
            }, null, true),
        });
    }, [plate]);

    const handleSaveAndClose = useCallback(() => {
        if (!plate) return;
        if (!isDirty) {
            navigateAway();
            return;
        }
        setStatus('Saving...');
        Ajax.request({
            url: ActionURL.buildURL('plate', 'saveTemplate.api'),
            method: 'POST',
            jsonData: plate,
            success: Utils.getCallbackWrapper(() => {
                setIsDirty(false);
                navigateAway();
            }),
            failure: Utils.getCallbackWrapper((response: any) => {
                setStatus('Save failed: ' + (response?.exception ?? 'unknown error'));
            }, null, true),
        });
    }, [plate, isDirty, navigateAway]);

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

    // Keep activeGroup in sync when plate changes
    useEffect(() => {
        if (activeGroup && plate) {
            const updated = plate.groups.find(g => g.rowId === activeGroup.rowId);
            if (updated) setActiveGroup(updated);
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
                        <div className="plate-grid-area">
                            <TemplateGrid
                                plate={plate}
                                activeGroup={activeGroup}
                                activeTab={activeTab}
                                colorMap={colorMap}
                                onCellAssign={handleCellAssign}
                                onCellToggle={handleCellToggle}
                            />
                            <ShiftPanel onShift={handleShift} />
                        </div>
                    </GroupTypesPanel>
                </div>
                <div className="plate-template-designer__right">
                    {plate.showWarningPanel && (
                        <div className="right-panel-tabs">
                            <button
                                className={'right-panel-tabs__tab' + (rightTab === 'properties' ? ' right-panel-tabs__tab--active' : '')}
                                onClick={() => setRightTab('properties')}
                            >
                                Well Group Properties
                            </button>
                            <button
                                className={'right-panel-tabs__tab' + (rightTab === 'warnings' ? ' right-panel-tabs__tab--active' : '') + (warningCount > 0 ? ' right-panel-tabs__tab--warn' : '')}
                                onClick={() => setRightTab('warnings')}
                            >
                                {warningCount > 0 ? `Warnings (${warningCount})` : 'Warnings'}
                            </button>
                        </div>
                    )}
                    {(!plate.showWarningPanel || rightTab === 'properties') && (
                        <WellGroupProperties
                            activeGroup={activeGroup}
                            onPropertyChange={handlePropertyChange}
                            onDeleteProperty={handleDeleteProperty}
                        />
                    )}
                    {plate.showWarningPanel && rightTab === 'warnings' && (
                        <WarningPanel plate={plate} />
                    )}
                </div>
            </div>
        </div>
    );
}
