/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { useEffect, useMemo, useState } from 'react';
import classNames from 'classnames';

import { PlateTemplate, WellGroup } from '../models';
import { MultiCreateDialog } from './MultiCreateDialog';
import { TabButton } from './TabButton';

interface GroupTypesPanelProps {
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
}: GroupTypesPanelProps): JSX.Element {
    const [newGroupName, setNewGroupName] = useState('');
    const [renamingId, setRenamingId] = useState<number | null>(null);
    const [renameValue, setRenameValue] = useState('');
    const [renameError, setRenameError] = useState<string | null>(null);
    const [multiCreateOpen, setMultiCreateOpen] = useState(false);
    // rowId of the group awaiting inline delete confirmation; null when no confirmation is pending.
    const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);

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

    // Reset create-input and transient UI state when tab changes
    useEffect(() => {
        setNewGroupName(unusedDefaults[0] ?? '');
        setRenamingId(null);
        setConfirmDeleteId(null);
    }, [activeTab]); // eslint-disable-line react-hooks/exhaustive-deps

    // Advance to next unused default when the current one gets used
    useEffect(() => {
        if (unusedDefaults.length > 0 && !unusedDefaults.includes(newGroupName)) {
            setNewGroupName(unusedDefaults[0]);
        }
    }, [unusedDefaults]); // eslint-disable-line react-hooks/exhaustive-deps

    const handleCreate = () => {
        const trimmed = newGroupName.trim();
        if (!trimmed || createNameConflicts) return;
        onAddGroup(activeTab, trimmed);
        setNewGroupName('');
    };

    const handleDeleteClick = (e: React.MouseEvent, group: WellGroup) => {
        e.stopPropagation();
        setConfirmDeleteId(group.rowId);
    };

    const handleDeleteConfirm = (e: React.MouseEvent, rowId: number) => {
        e.stopPropagation();
        onDeleteGroup(rowId);
        setConfirmDeleteId(null);
    };

    const handleDeleteCancel = (e: React.MouseEvent) => {
        e.stopPropagation();
        setConfirmDeleteId(null);
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
            <div
                className="group-types-panel__tabs"
                role="tablist"
                onKeyDown={(e: React.KeyboardEvent<HTMLDivElement>) => {
                    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
                    const tabs = Array.from(e.currentTarget.querySelectorAll<HTMLButtonElement>('[role="tab"]'));
                    const currentIndex = tabs.findIndex(t => t === document.activeElement);
                    if (currentIndex === -1) return;
                    e.preventDefault();
                    const next = e.key === 'ArrowLeft'
                        ? (currentIndex - 1 + tabs.length) % tabs.length
                        : (currentIndex + 1) % tabs.length;
                    tabs[next].click();
                    tabs[next].focus();
                }}
            >
                {plate.groupTypes.map(type => (
                    <TabButton
                        key={type}
                        id={`group-tab-${type}`}
                        panelId={`group-panel-${type}`}
                        isActive={type === activeTab}
                        baseClass="group-types-panel__tab"
                        onClick={() => onTabChange(type)}
                    >
                        {type}
                    </TabButton>
                ))}
            </div>
            {plate.groupTypes.map(type => (
                <div
                    key={type}
                    id={`group-panel-${type}`}
                    className="group-types-panel__tab-body"
                    role="tabpanel"
                    aria-labelledby={`group-tab-${type}`}
                    hidden={type !== activeTab}
                >
                    {type === activeTab && (
                        <>
                            <div className="group-types-panel__groups" role="listbox" aria-label="Well groups">
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
                                                role="option"
                                                aria-selected={isActive}
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
                                                    aria-hidden="true"
                                                />
                                                {isRenaming ? (
                                                    <input
                                                        autoFocus
                                                        aria-label={`Rename ${group.name}`}
                                                        aria-describedby={renameError ? `rename-error-${group.rowId}` : undefined}
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
                                                {/* Rename/delete actions: always rendered when the type
                                                    allows it, so every row stays the same height.
                                                    The --hidden modifier keeps them invisible and
                                                    non-interactive on unselected / renaming rows. */}
                                                {group.allowNewGroups && (
                                                    confirmDeleteId === group.rowId ? (
                                                        // Inline confirmation replaces the normal action buttons.
                                                        <span className="group-types-panel__group-actions">
                                                            <span className="group-types-panel__confirm-text">Delete?</span>
                                                            <button
                                                                className="group-types-panel__action-btn group-types-panel__action-btn--delete"
                                                                aria-label={`Confirm delete ${group.name}`}
                                                                onClick={e => handleDeleteConfirm(e, group.rowId)}
                                                            >
                                                                Yes
                                                            </button>
                                                            <button
                                                                className="group-types-panel__action-btn"
                                                                aria-label={`Cancel delete ${group.name}`}
                                                                onClick={handleDeleteCancel}
                                                            >
                                                                No
                                                            </button>
                                                        </span>
                                                    ) : (
                                                        <span
                                                            className={classNames('group-types-panel__group-actions', {
                                                                'group-types-panel__group-actions--hidden': !isActive || isRenaming,
                                                            })}
                                                            aria-hidden={(!isActive || isRenaming) ? true : undefined}
                                                        >
                                                            <button
                                                                className="group-types-panel__action-btn"
                                                                title="Rename"
                                                                aria-label={`Rename ${group.name}`}
                                                                tabIndex={(!isActive || isRenaming) ? -1 : 0}
                                                                onClick={e => handleRenameClick(e, group)}
                                                            >
                                                                <span className="fa fa-pencil" aria-hidden="true" />
                                                            </button>
                                                            <button
                                                                className="group-types-panel__action-btn group-types-panel__action-btn--delete"
                                                                title="Delete"
                                                                aria-label={`Delete ${group.name}`}
                                                                tabIndex={(!isActive || isRenaming) ? -1 : 0}
                                                                onClick={e => handleDeleteClick(e, group)}
                                                            >
                                                                <span className="fa fa-trash-o" aria-hidden="true" />
                                                            </button>
                                                        </span>
                                                    )
                                                )}
                                            </div>
                                            {isRenaming && renameError && (
                                                <div id={`rename-error-${group.rowId}`} className="group-types-panel__name-error">{renameError}</div>
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
                                                onClick={() => setMultiCreateOpen(true)}
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
                        </>
                    )}
                </div>
            ))}
            {multiCreateOpen && (
                <MultiCreateDialog
                    initialBaseName={newGroupName.trim()}
                    existingNames={new Set(groupsOfType.map(g => g.name))}
                    onClose={() => setMultiCreateOpen(false)}
                    onConfirm={names => {
                        names.forEach(name => onAddGroup(activeTab, name));
                        setMultiCreateOpen(false);
                    }}
                />
            )}
        </div>
    );
}
