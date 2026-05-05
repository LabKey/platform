/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';

import { useEnterEscape } from '@labkey/components';
import classNames from 'classnames';

import { PlateTemplate, WellGroup } from '../models';
import { CreateGroupRow } from './CreateGroupRow';
import { TabButton } from './TabButton';
import { TabList } from './TabList';

interface GroupTypesPanelProps {
    plate: PlateTemplate;
    activeGroup: WellGroup | null;
    activeTab: string;
    colorMap: Map<number, string>;
    hoveredWellGroupId: number | null;
    onGroupSelect: (group: WellGroup) => void;
    onTabChange: (tab: string) => void;
    onAddGroup: (type: string, name: string) => void;
    onDeleteGroup: (rowId: number) => void;
    onRenameGroup: (rowId: number, newName: string) => void;
    onGroupHover: (groupId: number | null) => void;
    children?: React.ReactNode;
}

interface GroupRowProps {
    group: WellGroup;
    color: string | undefined;
    isActive: boolean;
    isHighlighted: boolean;
    isRenaming: boolean;
    isConfirmingDelete: boolean;
    existingGroupNames: string[];
    onGroupHover: (groupId: number | null) => void;
    onGroupSelect: (group: WellGroup) => void;
    onRenameCommit: (rowId: number, newName: string) => void;
    onRenameCancel: () => void;
    onRenameClick: (e: React.MouseEvent, group: WellGroup) => void;
    onDeleteClick: (e: React.MouseEvent, group: WellGroup) => void;
    onDeleteConfirm: (e: React.MouseEvent, rowId: number) => void;
    onDeleteCancel: (e: React.MouseEvent) => void;
}

function GroupRow({
    group,
    color,
    isActive,
    isHighlighted,
    isRenaming,
    isConfirmingDelete,
    existingGroupNames,
    onGroupHover,
    onGroupSelect,
    onRenameCommit,
    onRenameCancel,
    onRenameClick,
    onDeleteClick,
    onDeleteConfirm,
    onDeleteCancel,
}: GroupRowProps) {
    const [renameValue, setRenameValue] = useState(group.name);
    const [renameError, setRenameError] = useState<string | null>(null);

    useEffect(() => {
        if (isRenaming) {
            setRenameValue(group.name);
            setRenameError(null);
        }
    }, [isRenaming]); // eslint-disable-line react-hooks/exhaustive-deps

    const handleRenameValueChange = useCallback((value: string) => {
        setRenameValue(value);
        setRenameError(null);
    }, []);

    // revertOnConflict=true: silently discard (blur — moving focus away shouldn't leave the input frozen).
    // revertOnConflict=false: show inline error (Enter — user expects feedback).
    const commitRename = useCallback((revertOnConflict: boolean) => {
        const trimmed = renameValue.trim();
        if (trimmed && existingGroupNames.some(n => n !== group.name && n === trimmed)) {
            if (revertOnConflict) {
                onRenameCancel();
            } else {
                setRenameError(`"${trimmed}" is already used by another group of this type.`);
            }
            return;
        }
        if (trimmed) onRenameCommit(group.rowId, trimmed);
        else onRenameCancel();
    }, [renameValue, existingGroupNames, group.name, group.rowId, onRenameCommit, onRenameCancel]);

    const handleRenameKeyDown = useEnterEscape(() => commitRename(false), onRenameCancel);

    return (
        <React.Fragment>
            <div
                className={classNames('group-types-panel__group', {
                    'group-types-panel__group--active': isActive,
                    'group-types-panel__group--highlighted': isHighlighted && !isActive,
                })}
                onMouseEnter={() => onGroupHover(group.rowId)}
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
                        onChange={e => handleRenameValueChange(e.target.value)}
                        onKeyDown={handleRenameKeyDown}
                        onBlur={() => commitRename(true)}
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
                    isConfirmingDelete ? (
                        <span className="group-types-panel__group-actions">
                            <span className="group-types-panel__confirm-text">Delete?</span>
                            <button
                                className="group-types-panel__action-btn group-types-panel__action-btn--delete"
                                aria-label={`Confirm delete ${group.name}`}
                                onClick={e => onDeleteConfirm(e, group.rowId)}
                            >
                                Yes
                            </button>
                            <button
                                className="group-types-panel__action-btn"
                                aria-label={`Cancel delete ${group.name}`}
                                onClick={onDeleteCancel}
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
                                onClick={e => onRenameClick(e, group)}
                            >
                                <span className="fa fa-pencil" aria-hidden="true" />
                            </button>
                            <button
                                className="group-types-panel__action-btn group-types-panel__action-btn--delete"
                                title="Delete"
                                aria-label={`Delete ${group.name}`}
                                tabIndex={(!isActive || isRenaming) ? -1 : 0}
                                onClick={e => onDeleteClick(e, group)}
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
}

/**
 * Left-hand panel for managing group types and well groups. Supports selecting the active
 * group (which the grid paints onto), creating groups individually or in bulk, and renaming
 * or deleting existing groups. The TemplateGrid is passed as children and rendered inline.
 */
export function GroupTypesPanel({
    plate,
    activeGroup,
    activeTab,
    colorMap,
    hoveredWellGroupId,
    onGroupSelect,
    onTabChange,
    onAddGroup,
    onDeleteGroup,
    onRenameGroup,
    onGroupHover,
    children,
}: GroupTypesPanelProps): JSX.Element {
    const [renamingId, setRenamingId] = useState<number | null>(null);
    // rowId of the group awaiting inline delete confirmation; null when no confirmation is pending.
    const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);

    // Stable derived list — memoized so useMemo and useEffect deps are stable.
    const groupsOfType = useMemo(() => plate.groups.filter(g => g.type === activeTab), [plate, activeTab]);
    const groupNames = useMemo(() => groupsOfType.map(g => g.name), [groupsOfType]);
    const canAdd = plate.canCreateGroupsByType?.[activeTab] ?? false;

    // Predefined slot names not yet occupied by an existing group of this type.
    const unusedDefaults = useMemo(() => {
        const defaults = plate.typesToDefaultGroups[activeTab] ?? [];
        return defaults.filter(d => !groupsOfType.some(g => g.name === d));
    }, [plate, activeTab, groupsOfType]);

    // Reset transient UI state when tab changes
    useEffect(() => {
        setRenamingId(null);
        setConfirmDeleteId(null);
    }, [activeTab]);

    const handleGroupsMouseLeave = useCallback(() => onGroupHover(null), [onGroupHover]);

    const handleDeleteClick = useCallback((e: React.MouseEvent, group: WellGroup) => {
        e.stopPropagation();
        setConfirmDeleteId(group.rowId);
    }, []);

    const handleDeleteConfirm = useCallback((e: React.MouseEvent, rowId: number) => {
        e.stopPropagation();
        onDeleteGroup(rowId);
        setConfirmDeleteId(null);
    }, [onDeleteGroup]);

    const handleDeleteCancel = useCallback((e: React.MouseEvent) => {
        e.stopPropagation();
        setConfirmDeleteId(null);
    }, []);

    const handleRenameClick = useCallback((e: React.MouseEvent, group: WellGroup) => {
        e.stopPropagation();
        setRenamingId(group.rowId);
    }, []);

    const handleRenameCommit = useCallback((rowId: number, newName: string) => {
        onRenameGroup(rowId, newName);
        setRenamingId(null);
    }, [onRenameGroup]);

    const handleRenameCancel = useCallback(() => setRenamingId(null), []);

    return (
        <div className="group-types-panel">
            <TabList className="group-types-panel__tabs">
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
            </TabList>
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
                            <div
                                className="group-types-panel__groups"
                                role="listbox"
                                aria-label="Well groups"
                                onMouseLeave={handleGroupsMouseLeave}
                            >
                                {groupsOfType.map(group => (
                                    <GroupRow
                                        key={group.rowId}
                                        group={group}
                                        color={colorMap.get(group.rowId)}
                                        isActive={activeGroup?.rowId === group.rowId}
                                        isHighlighted={hoveredWellGroupId === group.rowId}
                                        isRenaming={renamingId === group.rowId}
                                        isConfirmingDelete={confirmDeleteId === group.rowId}
                                        existingGroupNames={groupNames}
                                        onGroupHover={onGroupHover}
                                        onGroupSelect={onGroupSelect}
                                        onRenameCommit={handleRenameCommit}
                                        onRenameCancel={handleRenameCancel}
                                        onRenameClick={handleRenameClick}
                                        onDeleteClick={handleDeleteClick}
                                        onDeleteConfirm={handleDeleteConfirm}
                                        onDeleteCancel={handleDeleteCancel}
                                    />
                                ))}
                                {canAdd && (
                                    <CreateGroupRow
                                        unusedDefaults={unusedDefaults}
                                        existingGroupNames={groupNames}
                                        activeTab={activeTab}
                                        onAddGroup={onAddGroup}
                                    />
                                )}
                            </div>
                            {children}
                        </>
                    )}
                </div>
            ))}
        </div>
    );
}
