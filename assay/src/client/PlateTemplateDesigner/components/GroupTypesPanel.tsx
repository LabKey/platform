/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { useEffect, useMemo, useRef, useState } from 'react';

import { PlateTemplate, WellGroup } from '../models';

interface Props {
    plate: PlateTemplate;
    activeGroup: WellGroup | null;
    activeTab: string;
    colorMap: Map<number, string>;
    onGroupSelect: (group: WellGroup) => void;
    onTabChange: (tab: string) => void;
    onAddGroup: (type: string, name: string) => void;
    onDeleteGroup: (rowId: number) => void;
    onRenameGroup: (rowId: number, newName: string) => void;
    children?: React.ReactNode;
}

export function GroupTypesPanel({
    plate,
    activeGroup,
    activeTab,
    colorMap,
    onGroupSelect,
    onTabChange,
    onAddGroup,
    onDeleteGroup,
    onRenameGroup,
    children,
}: Props): JSX.Element {
    const [newGroupName, setNewGroupName] = useState('');
    const [renamingId, setRenamingId] = useState<number | null>(null);
    const [renameValue, setRenameValue] = useState('');
    const [multiCreateOpen, setMultiCreateOpen] = useState(false);
    const [multiBaseName, setMultiBaseName] = useState('');
    const [multiCount, setMultiCount] = useState('2');
    const [multiCountError, setMultiCountError] = useState('');
    const multiBaseNameRef = useRef<HTMLInputElement>(null);

    const groupsOfType = plate.groups.filter(g => g.type === activeTab);
    const canAdd = plate.canCreateGroupsByType?.[activeTab] ?? false;

    const unusedDefaults = useMemo(() => {
        const defaults = plate.typesToDefaultGroups[activeTab] ?? [];
        return defaults.filter(d => !groupsOfType.some(g => g.name === d));
    }, [plate, activeTab, groupsOfType]);

    // Reset create-input when tab changes
    useEffect(() => {
        setNewGroupName(unusedDefaults[0] ?? '');
        setRenamingId(null);
    }, [activeTab]); // eslint-disable-line react-hooks/exhaustive-deps

    // Advance to next unused default when the current one gets used
    useEffect(() => {
        if (unusedDefaults.length > 0 && !unusedDefaults.includes(newGroupName)) {
            setNewGroupName(unusedDefaults[0]);
        }
    }, [unusedDefaults]); // eslint-disable-line react-hooks/exhaustive-deps

    const handleCreate = () => {
        const trimmed = newGroupName.trim();
        if (!trimmed) return;
        onAddGroup(activeTab, trimmed);
    };

    const openMultiCreate = () => {
        setMultiBaseName(newGroupName.trim());
        setMultiCount('2');
        setMultiCountError('');
        setMultiCreateOpen(true);
        // Focus the base name input after the modal renders
        setTimeout(() => multiBaseNameRef.current?.select(), 0);
    };

    const handleMultiCreate = () => {
        const count = parseInt(multiCount, 10);
        if (isNaN(count) || count < 1) {
            setMultiCountError(`"${multiCount}" is not a valid count.`);
            return;
        }
        const baseName = multiBaseName.trim();
        if (!baseName) return;
        for (let i = 1; i <= count; i++) {
            onAddGroup(activeTab, `${baseName} ${i}`);
        }
        setMultiCreateOpen(false);
    };

    const handleDeleteClick = (e: React.MouseEvent, group: WellGroup) => {
        e.stopPropagation();
        if (window.confirm(`Delete well group "${group.name}"?`)) {
            onDeleteGroup(group.rowId);
        }
    };

    const handleRenameClick = (e: React.MouseEvent, group: WellGroup) => {
        e.stopPropagation();
        setRenamingId(group.rowId);
        setRenameValue(group.name);
    };

    const handleRenameCommit = (rowId: number) => {
        const trimmed = renameValue.trim();
        if (trimmed) onRenameGroup(rowId, trimmed);
        setRenamingId(null);
    };

    return (
        <div className="group-types-panel">
            <div className="group-types-panel__tabs">
                {plate.groupTypes.map(type => (
                    <button
                        key={type}
                        className={
                            'group-types-panel__tab' +
                            (type === activeTab ? ' group-types-panel__tab--active' : '')
                        }
                        onClick={() => onTabChange(type)}
                    >
                        {type}
                    </button>
                ))}
            </div>
            <div className="group-types-panel__tab-body">
                <div className="group-types-panel__groups">
                    {groupsOfType.map(group => {
                        const color = colorMap.get(group.rowId);
                        const isActive = activeGroup?.rowId === group.rowId;
                        const isRenaming = renamingId === group.rowId;
                        return (
                            <div
                                key={group.rowId}
                                className={
                                    'group-types-panel__group' +
                                    (isActive ? ' group-types-panel__group--active' : '')
                                }
                                onClick={() => { if (!isRenaming) onGroupSelect(group); }}
                            >
                                <span
                                    className="group-types-panel__color-swatch"
                                    style={{ backgroundColor: color ?? '#ccc' }}
                                />
                                {isRenaming ? (
                                    <input
                                        autoFocus
                                        className="group-types-panel__rename-input"
                                        value={renameValue}
                                        onChange={e => setRenameValue(e.target.value)}
                                        onKeyDown={e => {
                                            if (e.key === 'Enter') handleRenameCommit(group.rowId);
                                            if (e.key === 'Escape') setRenamingId(null);
                                        }}
                                        onBlur={() => handleRenameCommit(group.rowId)}
                                        onClick={e => e.stopPropagation()}
                                    />
                                ) : (
                                    <span className="group-types-panel__group-name">{group.name}</span>
                                )}
                                {isActive && !isRenaming && group.allowNewGroups && (
                                    <span className="group-types-panel__group-actions">
                                        <button
                                            className="group-types-panel__action-btn"
                                            title="Rename"
                                            onClick={e => handleRenameClick(e, group)}
                                        >
                                            <span className="fa fa-pencil" />
                                        </button>
                                        <button
                                            className="group-types-panel__action-btn group-types-panel__action-btn--delete"
                                            title="Delete"
                                            onClick={e => handleDeleteClick(e, group)}
                                        >
                                            <span className="fa fa-trash-o" />
                                        </button>
                                    </span>
                                )}
                            </div>
                        );
                    })}
                    {canAdd && (
                        <div className="group-types-panel__create-row">
                            {unusedDefaults.length > 0 ? (
                                <select
                                    className="group-types-panel__new-name-input"
                                    value={newGroupName}
                                    onChange={e => setNewGroupName(e.target.value)}
                                >
                                    {unusedDefaults.map(d => (
                                        <option key={d} value={d}>{d}</option>
                                    ))}
                                </select>
                            ) : (
                                <input
                                    type="text"
                                    className="group-types-panel__new-name-input"
                                    placeholder="Group name"
                                    value={newGroupName}
                                    onChange={e => setNewGroupName(e.target.value)}
                                    onKeyDown={e => { if (e.key === 'Enter' && newGroupName.trim()) handleCreate(); }}
                                />
                            )}
                            <button
                                className="group-types-panel__add-btn"
                                disabled={!newGroupName.trim()}
                                onClick={handleCreate}
                            >
                                Create
                            </button>
                            <button
                                className="group-types-panel__add-btn"
                                onClick={openMultiCreate}
                            >
                                Create multiple...
                            </button>
                        </div>
                    )}
                </div>
                {children}
            </div>
            {multiCreateOpen && (
                <div className="multi-create-dialog__overlay" onClick={() => setMultiCreateOpen(false)}>
                    <div className="multi-create-dialog" onClick={e => e.stopPropagation()}>
                        <div className="multi-create-dialog__title">Create Multiple Groups</div>
                        <table className="multi-create-dialog__table">
                            <tbody>
                                <tr>
                                    <td className="multi-create-dialog__label">Base Name</td>
                                    <td>
                                        <input
                                            ref={multiBaseNameRef}
                                            className="multi-create-dialog__input"
                                            type="text"
                                            value={multiBaseName}
                                            onChange={e => setMultiBaseName(e.target.value)}
                                            onKeyDown={e => { if (e.key === 'Enter') handleMultiCreate(); if (e.key === 'Escape') setMultiCreateOpen(false); }}
                                        />
                                    </td>
                                </tr>
                                <tr>
                                    <td className="multi-create-dialog__label">Count</td>
                                    <td>
                                        <input
                                            className="multi-create-dialog__input multi-create-dialog__input--count"
                                            type="number"
                                            min="1"
                                            value={multiCount}
                                            onChange={e => { setMultiCount(e.target.value); setMultiCountError(''); }}
                                            onKeyDown={e => { if (e.key === 'Enter') handleMultiCreate(); if (e.key === 'Escape') setMultiCreateOpen(false); }}
                                        />
                                        {multiCountError && <div className="multi-create-dialog__error">{multiCountError}</div>}
                                    </td>
                                </tr>
                                <tr>
                                    <td />
                                    <td className="multi-create-dialog__buttons">
                                        <button className="group-types-panel__add-btn" onClick={() => setMultiCreateOpen(false)}>
                                            Cancel
                                        </button>
                                        <button
                                            className="group-types-panel__add-btn group-types-panel__add-btn--primary"
                                            disabled={!multiBaseName.trim()}
                                            onClick={handleMultiCreate}
                                        >
                                            Create
                                        </button>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </div>
    );
}
