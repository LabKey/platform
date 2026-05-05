/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';

import { useEnterEscape } from '../useEnterEscape';
import classNames from 'classnames';

import { PlateTemplate, WellGroup } from '../models';
import { CreateGroupRow } from './CreateGroupRow';
import { TabButton } from './TabButton';
import { TabList } from './TabList';

interface GroupTypesPanelProps {
    plate: PlateTemplate;
    activeGroup: WellGroup | null;
    activeTab: string;
    colorMap: Map<number, { color: string; colorIndex: number }>;
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
    colorIndex: number | undefined;
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

const GroupRow: React.FC<GroupRowProps> = ({
    group,
    color,
    colorIndex,
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
}) => {
    const [renameValue, setRenameValue] = useState(group.name);
    const [renameError, setRenameError] = useState<string | null>(null);

    useEffect(() => {
        if (isRenaming) {
            setRenameValue(group.name);
            setRenameError(null);
        }
    }, [isRenaming]); // eslint-disable-line react-hooks/exhaustive-deps

    const handleRenameValueChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
        setRenameValue(e.target.value);
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

    const handleMouseEnter = useCallback(() => onGroupHover(group.rowId), [onGroupHover, group.rowId]);
    const handleGroupClick = useCallback(() => { if (!isRenaming) onGroupSelect(group); }, [isRenaming, onGroupSelect, group]);
    const handleGroupKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (!isRenaming && (e.key === 'Enter' || e.key === ' ')) {
            e.preventDefault();
            onGroupSelect(group);
        }
    }, [isRenaming, onGroupSelect, group]);
    const handleRenameBlur = useCallback(() => commitRename(true), [commitRename]);
    const handleRenameInputClick = useCallback((e: React.MouseEvent) => e.stopPropagation(), []);
    const handleDeleteConfirmClick = useCallback(
        (e: React.MouseEvent) => onDeleteConfirm(e, group.rowId), [onDeleteConfirm, group.rowId]);
    const handleRenameActionClick = useCallback(
        (e: React.MouseEvent) => onRenameClick(e, group), [onRenameClick, group]);
    const handleDeleteActionClick = useCallback(
        (e: React.MouseEvent) => onDeleteClick(e, group), [onDeleteClick, group]);

    return (
        <React.Fragment>
            <div
                className={classNames('group-types-panel__group', {
                    'group-types-panel__group--active': isActive,
                    'group-types-panel__group--highlighted': isHighlighted && !isActive,
                })}
                onMouseEnter={handleMouseEnter}
                role="option"
                aria-selected={isActive}
                tabIndex={0}
                onClick={handleGroupClick}
                onKeyDown={handleGroupKeyDown}
            >
                <span
                    className={classNames('group-types-panel__color-swatch', {
                        [`group-types-panel__color-swatch--pattern-${colorIndex}`]: colorIndex != null && colorIndex >= 0,
                    })}
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
                        onChange={handleRenameValueChange}
                        onKeyDown={handleRenameKeyDown}
                        onBlur={handleRenameBlur}
                        onClick={handleRenameInputClick}
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
                                onClick={handleDeleteConfirmClick}
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
                                onClick={handleRenameActionClick}
                            >
                                <span className="fa fa-pencil" aria-hidden="true" />
                            </button>
                            <button
                                className="group-types-panel__action-btn group-types-panel__action-btn--delete"
                                title="Delete"
                                aria-label={`Delete ${group.name}`}
                                tabIndex={(!isActive || isRenaming) ? -1 : 0}
                                onClick={handleDeleteActionClick}
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
};
GroupRow.displayName = 'GroupRow';

interface GroupTypeTabButtonProps {
    type: string;
    isActive: boolean;
    onTabChange: (tab: string) => void;
}

const GroupTypeTabButton: React.FC<GroupTypeTabButtonProps> = ({ type, isActive, onTabChange }) => {
    const handleClick = useCallback(() => onTabChange(type), [onTabChange, type]);
    return (
        <TabButton
            id={`group-tab-${type}`}
            panelId={`group-panel-${type}`}
            isActive={isActive}
            baseClass="group-types-panel__tab"
            onClick={handleClick}
        >
            {type}
        </TabButton>
    );
};
GroupTypeTabButton.displayName = 'GroupTypeTabButton';

/**
 * Left-hand panel for managing group types and well groups. Supports selecting the active
 * group (which the grid paints onto), creating groups individually or in bulk, and renaming
 * or deleting existing groups. The TemplateGrid is passed as children and rendered inline.
 */
export const GroupTypesPanel: React.FC<GroupTypesPanelProps> = ({
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
}) => {
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
                    <GroupTypeTabButton
                        key={type}
                        type={type}
                        isActive={type === activeTab}
                        onTabChange={onTabChange}
                    />
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
                                        color={colorMap.get(group.rowId)?.color}
                                        colorIndex={colorMap.get(group.rowId)?.colorIndex}
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
};
GroupTypesPanel.displayName = 'GroupTypesPanel';
