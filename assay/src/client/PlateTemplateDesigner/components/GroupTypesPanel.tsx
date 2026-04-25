/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { useEffect, useMemo, useRef, useState } from 'react';
import classNames from 'classnames';

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

/**
 * Left-hand panel that manages group types (tabs) and individual well groups.
 *
 * ─── Layout ────────────────────────────────────────────────────────────────────
 * The panel is split into two side-by-side areas via a flex row:
 *   Left column  – the group list + create controls (fixed width)
 *   Right area   – children (the TemplateGrid + ShiftPanel), passed in from the parent
 *
 * This composition pattern keeps the grid visually anchored inside the panel boundary
 * while letting the tab strip and group list scroll independently.
 *
 * ─── Tab switching ─────────────────────────────────────────────────────────────
 * Each tab corresponds to a group type key (e.g. "CONTROL", "SPECIMEN", "REPLICATE").
 * Switching tabs:
 *   - Clears the active group selection (the parent sets activeGroup to null).
 *   - Updates the grid to show only that type's colour layout.
 *   - Resets the create-name input to the first unused default for the new type.
 *
 * ─── Group selection ───────────────────────────────────────────────────────────
 * Clicking a group row makes it the "active group". Once active, clicking or
 * dragging cells on the TemplateGrid paints them onto that group. The active group
 * is highlighted with a blue border and shows inline rename/delete actions.
 *
 * ─── Creating groups ───────────────────────────────────────────────────────────
 * Some group types come with predefined slot names (`typesToDefaultGroups`), e.g.
 * "Virus" and "Cell Control" for certain assay types. While unused defaults remain,
 * a <select> lets the user pick from them. Once all are used, a free-text <input>
 * appears for custom names.
 *
 * "Create multiple…" opens a modal dialog that batch-creates N numbered groups
 * (e.g. "Sample 1" through "Sample 8") from a base name and count. Useful for
 * assays with many specimens or replicates.
 *
 * ─── Renaming ──────────────────────────────────────────────────────────────────
 * The pencil button activates an inline rename input in place of the group name.
 * Blur or Enter commits the change; Escape discards it.
 *
 * ─── Modal focus trap ──────────────────────────────────────────────────────────
 * When the multi-create dialog opens, a useEffect traps Tab/Shift-Tab focus inside
 * the dialog and moves initial focus to the first focusable element. Escape closes
 * the dialog from anywhere within it.
 */
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
    const [renameError, setRenameError] = useState<string | null>(null);
    const [multiCreateOpen, setMultiCreateOpen] = useState(false);
    const [multiBaseName, setMultiBaseName] = useState('');
    const [multiCount, setMultiCount] = useState('2');
    const [multiCountError, setMultiCountError] = useState('');
    const multiBaseNameRef = useRef<HTMLInputElement>(null);
    const dialogRef = useRef<HTMLDivElement>(null);

    // Stable derived list — memoized so useMemo and useEffect deps are stable.
    const groupsOfType = useMemo(() => plate.groups.filter(g => g.type === activeTab), [plate, activeTab]);
    const canAdd = plate.canCreateGroupsByType?.[activeTab] ?? false;

    // True when the current create-input value is already taken by a group of this type.
    const createNameConflicts = newGroupName.trim() !== '' && groupsOfType.some(g => g.name === newGroupName.trim());

    // Predefined slot names not yet occupied by an existing group of this type.
    // Drives the <select> vs free-text <input> toggle in the create row.
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

    // Focus trap for multi-create dialog
    useEffect(() => {
        if (!multiCreateOpen || !dialogRef.current) return;
        const dialog = dialogRef.current;
        const focusableSelectors = 'button, input, select, textarea, [tabindex]:not([tabindex="-1"])';
        const getFocusable = () => Array.from(dialog.querySelectorAll<HTMLElement>(focusableSelectors));

        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                setMultiCreateOpen(false);
                return;
            }
            if (e.key !== 'Tab') return;
            const focusable = getFocusable();
            const first = focusable[0];
            const last = focusable[focusable.length - 1];
            if (e.shiftKey) {
                if (document.activeElement === first) { e.preventDefault(); last?.focus(); }
            } else {
                if (document.activeElement === last) { e.preventDefault(); first?.focus(); }
            }
        };

        dialog.addEventListener('keydown', handleKeyDown);
        getFocusable()[0]?.focus();
        return () => dialog.removeEventListener('keydown', handleKeyDown);
    }, [multiCreateOpen]);

    const handleCreate = () => {
        const trimmed = newGroupName.trim();
        if (!trimmed || createNameConflicts) return;
        onAddGroup(activeTab, trimmed);
        setNewGroupName('');
    };

    const openMultiCreate = () => {
        setMultiBaseName(newGroupName.trim());
        setMultiCount('2');
        setMultiCountError('');
        setMultiCreateOpen(true);
        // Focus is handled by the focus-trap effect above
    };

    const handleMultiCreate = () => {
        const count = parseInt(multiCount, 10);
        if (isNaN(count) || count < 1) {
            setMultiCountError(`"${multiCount}" is not a valid count.`);
            return;
        }
        const baseName = multiBaseName.trim();
        if (!baseName) return;
        const existingNames = new Set(groupsOfType.map(g => g.name));
        const namesToCreate = Array.from({ length: count }, (_, i) => `${baseName} ${i + 1}`)
            .filter(name => !existingNames.has(name));
        if (namesToCreate.length === 0) {
            setMultiCountError(`All ${count} generated name${count === 1 ? '' : 's'} already exist in this type.`);
            return;
        }
        namesToCreate.forEach(name => onAddGroup(activeTab, name));
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
        setRenameError(null);
    };

    // revertOnConflict=true: silently discard (used on blur so moving focus away doesn't leave the input frozen).
    // revertOnConflict=false: show an inline error and keep the input open (used on Enter so the user sees feedback).
    const handleRenameCommit = (rowId: number, revertOnConflict: boolean) => {
        const trimmed = renameValue.trim();
        if (trimmed && groupsOfType.some(g => g.rowId !== rowId && g.name === trimmed)) {
            if (revertOnConflict) {
                setRenamingId(null);
                setRenameError(null);
            } else {
                setRenameError(`"${trimmed}" is already used by another group of this type.`);
            }
            return;
        }
        if (trimmed) onRenameGroup(rowId, trimmed);
        setRenamingId(null);
        setRenameError(null);
    };

    return (
        <div className="group-types-panel">
            <div className="group-types-panel__tabs" role="tablist">
                {plate.groupTypes.map(type => (
                    <button
                        key={type}
                        id={`group-tab-${type}`}
                        role="tab"
                        aria-selected={type === activeTab}
                        className={classNames('group-types-panel__tab', {
                            'group-types-panel__tab--active': type === activeTab,
                        })}
                        onClick={() => onTabChange(type)}
                    >
                        {type}
                    </button>
                ))}
            </div>
            <div
                className="group-types-panel__tab-body"
                role="tabpanel"
                aria-labelledby={`group-tab-${activeTab}`}
            >
                <div className="group-types-panel__groups">
                    {groupsOfType.map(group => {
                        const color = colorMap.get(group.rowId);
                        const isActive = activeGroup?.rowId === group.rowId;
                        const isRenaming = renamingId === group.rowId;
                        return (
                            <React.Fragment key={group.rowId}>
                                <div
                                    className={classNames('group-types-panel__group', {
                                        'group-types-panel__group--active': isActive,
                                    })}
                                    tabIndex={0}
                                    onClick={() => { if (!isRenaming) onGroupSelect(group); }}
                                    onKeyDown={e => {
                                        if (!isRenaming && (e.key === 'Enter' || e.key === ' ')) {
                                            e.preventDefault();
                                            onGroupSelect(group);
                                        }
                                    }}
                                >
                                    <span
                                        className="group-types-panel__color-swatch"
                                        style={{ backgroundColor: color ?? '#ccc' }}
                                    />
                                    {isRenaming ? (
                                        <input
                                            autoFocus
                                            aria-label={`Rename ${group.name}`}
                                            aria-describedby={renameError ? 'rename-error' : undefined}
                                            aria-invalid={!!renameError}
                                            className={classNames('group-types-panel__rename-input', {
                                                'group-types-panel__rename-input--error': !!renameError,
                                            })}
                                            value={renameValue}
                                            onChange={e => { setRenameValue(e.target.value); setRenameError(null); }}
                                            onKeyDown={e => {
                                                if (e.key === 'Enter') handleRenameCommit(group.rowId, false);
                                                if (e.key === 'Escape') { setRenamingId(null); setRenameError(null); }
                                            }}
                                            onBlur={() => handleRenameCommit(group.rowId, true)}
                                            onClick={e => e.stopPropagation()}
                                        />
                                    ) : (
                                        <span className="group-types-panel__group-name">{group.name}</span>
                                    )}
                                    {/* Rename/delete actions appear only on the active group row */}
                                    {isActive && !isRenaming && group.allowNewGroups && (
                                        <span className="group-types-panel__group-actions">
                                            <button
                                                className="group-types-panel__action-btn"
                                                title="Rename"
                                                aria-label={`Rename ${group.name}`}
                                                onClick={e => handleRenameClick(e, group)}
                                            >
                                                <span className="fa fa-pencil" aria-hidden="true" />
                                            </button>
                                            <button
                                                className="group-types-panel__action-btn group-types-panel__action-btn--delete"
                                                title="Delete"
                                                aria-label={`Delete ${group.name}`}
                                                onClick={e => handleDeleteClick(e, group)}
                                            >
                                                <span className="fa fa-trash-o" aria-hidden="true" />
                                            </button>
                                        </span>
                                    )}
                                </div>
                                {isRenaming && renameError && (
                                    <div id="rename-error" className="group-types-panel__name-error">{renameError}</div>
                                )}
                            </React.Fragment>
                        );
                    })}
                    {canAdd && (
                        <>
                            <div className="group-types-panel__create-row">
                                {/*
                                 * Show a <select> while predefined defaults remain (prevents typos and
                                 * ensures canonical names). Switch to a free-text <input> once all
                                 * defaults are consumed or if there are none defined for this type.
                                 */}
                                {unusedDefaults.length > 0 ? (
                                    <select
                                        aria-label="Group name"
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
                                        aria-label="Group name"
                                        aria-describedby={createNameConflicts ? 'create-name-error' : undefined}
                                        aria-invalid={createNameConflicts}
                                        className="group-types-panel__new-name-input"
                                        placeholder="Group name"
                                        value={newGroupName}
                                        onChange={e => setNewGroupName(e.target.value)}
                                        onKeyDown={e => { if (e.key === 'Enter' && newGroupName.trim() && !createNameConflicts) handleCreate(); }}
                                    />
                                )}
                                <button
                                    className="group-types-panel__add-btn"
                                    disabled={!newGroupName.trim() || createNameConflicts}
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
                            {createNameConflicts && (
                                <div id="create-name-error" className="group-types-panel__name-error">
                                    A group named "{newGroupName.trim()}" already exists in this type.
                                </div>
                            )}
                        </>
                    )}
                </div>
                {children}
            </div>
            {multiCreateOpen && (
                <div className="multi-create-dialog__overlay" onClick={() => setMultiCreateOpen(false)}>
                    <div
                        ref={dialogRef}
                        className="multi-create-dialog"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="multi-create-title"
                        onClick={e => e.stopPropagation()}
                    >
                        <div id="multi-create-title" className="multi-create-dialog__title">Create Multiple Groups</div>
                        <table className="multi-create-dialog__table">
                            <tbody>
                                <tr>
                                    <td id="multi-create-base-name-label" className="multi-create-dialog__label">Base Name</td>
                                    <td>
                                        <input
                                            ref={multiBaseNameRef}
                                            className="multi-create-dialog__input"
                                            type="text"
                                            aria-labelledby="multi-create-base-name-label"
                                            value={multiBaseName}
                                            onChange={e => setMultiBaseName(e.target.value)}
                                            onKeyDown={e => { if (e.key === 'Enter') handleMultiCreate(); if (e.key === 'Escape') setMultiCreateOpen(false); }}
                                        />
                                    </td>
                                </tr>
                                <tr>
                                    <td id="multi-create-count-label" className="multi-create-dialog__label">Count</td>
                                    <td>
                                        <input
                                            className="multi-create-dialog__input multi-create-dialog__input--count"
                                            type="number"
                                            min="1"
                                            aria-labelledby="multi-create-count-label"
                                            aria-describedby={multiCountError ? 'multi-create-count-error' : undefined}
                                            aria-invalid={!!multiCountError}
                                            value={multiCount}
                                            onChange={e => { setMultiCount(e.target.value); setMultiCountError(''); }}
                                            onKeyDown={e => { if (e.key === 'Enter') handleMultiCreate(); if (e.key === 'Escape') setMultiCreateOpen(false); }}
                                        />
                                        {multiCountError && <div id="multi-create-count-error" className="multi-create-dialog__error">{multiCountError}</div>}
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
