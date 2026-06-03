/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { FC, useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useEnterEscape } from '../useEnterEscape';
import classNames from 'classnames';

import { PlateTemplate, WellGroup } from '../models';
import { CreateGroupRow } from './CreateGroupRow';
import { TabButton } from './TabButton';
import { TabList } from './TabList';

interface SharedProps {
    colorMap: Map<number, { color: string; colorIndex: number }>;
    onAddGroup: (type: string, name: string) => void;
    onDeleteGroup: (rowId: number) => void;
    onGroupHover: (groupId: null | number) => void;
    onGroupSelect: (group: WellGroup) => void;
    onRenameGroup: (rowId: number, newName: string) => void;
}

interface GroupTypesPanelProps extends SharedProps {
    activeGroup: null | WellGroup;
    activeTab: string;
    children?: React.ReactNode;
    hoveredWellGroupId: null | number;
    onTabChange: (tab: string) => void;
    plate: PlateTemplate;
}

interface GroupRowProps extends SharedProps {
    existingGroupNames: string[];
    group: WellGroup;
    isActive: boolean;
    isHighlighted: boolean;
}

const GroupRow: FC<GroupRowProps> = ({
    group,
    colorMap,
    isActive,
    isHighlighted,
    existingGroupNames,
    onDeleteGroup,
    onGroupHover,
    onGroupSelect,
    onRenameGroup,
}) => {
    const color = colorMap.get(group.rowId)?.color;
    const colorIndex = colorMap.get(group.rowId)?.colorIndex;
    const [isRenaming, setIsRenaming] = useState<boolean>(false);
    const [isConfirmingDelete, setIsConfirmingDelete] = useState<boolean>(false);
    const [renameValue, setRenameValue] = useState(group.name);
    const [renameError, setRenameError] = useState<null | string>(null);

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

    // Guards handleRenameBlur from running when the input is unmounted by a cancel/Escape,
    // which causes the browser to fire blur on the focused input during DOM removal.
    const isCancellingRef = useRef(false);

    const cancelRename = useCallback(() => {
        isCancellingRef.current = true;
        setIsRenaming(false);
    }, []);
    const cancelDelete = useCallback(() => setIsConfirmingDelete(false), []);

    // revertOnConflict=true: silently discard (blur — moving focus away shouldn't leave the input frozen).
    // revertOnConflict=false: show inline error (Enter — user expects feedback).
    const commitRename = useCallback(
        (revertOnConflict: boolean) => {
            const trimmed = renameValue.trim();
            if (trimmed && existingGroupNames.some(n => n !== group.name && n === trimmed)) {
                if (revertOnConflict) {
                    setIsRenaming(false);
                } else {
                    setRenameError(`"${trimmed}" is already used by another group of this type.`);
                }
                return;
            }
            if (trimmed) {
                onRenameGroup(group.rowId, trimmed);
                setIsRenaming(false);
            } else {
                setIsRenaming(false);
            }
        },
        [renameValue, existingGroupNames, onRenameGroup, group.rowId, group.name]
    );

    const handleRenameKeyDown = useEnterEscape(() => commitRename(false), cancelRename);

    const handleMouseEnter = useCallback(() => onGroupHover(group.rowId), [onGroupHover, group.rowId]);
    const handleGroupClick = useCallback(() => {
        if (!isRenaming) onGroupSelect(group);
    }, [isRenaming, onGroupSelect, group]);
    const handleGroupKeyDown = useCallback(
        (e: React.KeyboardEvent) => {
            if (!isRenaming && (e.key === 'Enter' || e.key === ' ')) {
                e.preventDefault();
                onGroupSelect(group);
            }
        },
        [isRenaming, onGroupSelect, group]
    );
    const handleRenameBlur = useCallback(() => {
        if (isCancellingRef.current) {
            isCancellingRef.current = false;
            return;
        }
        commitRename(true);
    }, [commitRename]);
    const handleRenameInputClick = useCallback((e: React.MouseEvent) => e.stopPropagation(), []);
    const onDeleteConfirm = useCallback(
        (e: React.MouseEvent) => {
            e.stopPropagation();
            onDeleteGroup(group.rowId);
            setIsConfirmingDelete(false);
        },
        [onDeleteGroup, group.rowId]
    );
    const onRenameClicked = useCallback((e: React.MouseEvent) => {
        e.stopPropagation();
        setIsRenaming(true);
    }, []);
    const onDeleteClicked = useCallback((e: React.MouseEvent) => {
        e.stopPropagation();
        setIsConfirmingDelete(true);
    }, []);

    return (
        <React.Fragment>
            <div
                aria-selected={isActive}
                className={classNames('group-types-panel__group', {
                    'group-types-panel__group--active': isActive,
                    'group-types-panel__group--highlighted': isHighlighted && !isActive,
                })}
                onClick={handleGroupClick}
                onKeyDown={handleGroupKeyDown}
                onMouseEnter={handleMouseEnter}
                role="option"
                tabIndex={0}
            >
                <span
                    aria-hidden="true"
                    className={classNames('group-types-panel__color-swatch', {
                        [`group-types-panel__color-swatch--pattern-${colorIndex}`]:
                            colorIndex != null && colorIndex >= 0,
                    })}
                    style={{ backgroundColor: color ?? '#ccc' }}
                />
                {isRenaming ? (
                    <input
                        aria-describedby={renameError ? `rename-error-${group.rowId}` : undefined}
                        aria-invalid={!!renameError}
                        aria-label={`Rename ${group.name}`}
                        autoFocus
                        className={classNames('group-types-panel__rename-input', {
                            'group-types-panel__rename-input--error': !!renameError,
                        })}
                        onBlur={handleRenameBlur}
                        onChange={handleRenameValueChange}
                        onClick={handleRenameInputClick}
                        onKeyDown={handleRenameKeyDown}
                        value={renameValue}
                    />
                ) : (
                    <span className="group-types-panel__group-name">{group.name}</span>
                )}
                {/* Rename/delete actions: always rendered when the type
                    allows it, so every row stays the same height.
                    The --hidden modifier keeps them invisible and
                    non-interactive on unselected / renaming rows. */}
                {group.allowNewGroups &&
                    (isConfirmingDelete ? (
                        <span className="group-types-panel__group-actions">
                            <span className="group-types-panel__confirm-text">Delete?</span>
                            <button
                                aria-label={`Confirm delete ${group.name}`}
                                className="group-types-panel__action-btn group-types-panel__action-btn--delete"
                                onClick={onDeleteConfirm}
                            >
                                Yes
                            </button>
                            <button
                                aria-label={`Cancel delete ${group.name}`}
                                className="group-types-panel__action-btn"
                                onClick={cancelDelete}
                            >
                                No
                            </button>
                        </span>
                    ) : (
                        <span
                            aria-hidden={!isActive || isRenaming ? true : undefined}
                            className={classNames('group-types-panel__group-actions', {
                                'group-types-panel__group-actions--hidden': !isActive || isRenaming,
                            })}
                        >
                            <button
                                aria-label={`Rename ${group.name}`}
                                className="group-types-panel__action-btn"
                                onClick={onRenameClicked}
                                tabIndex={!isActive || isRenaming ? -1 : 0}
                                title="Rename"
                            >
                                <span aria-hidden="true" className="fa fa-pencil" />
                            </button>
                            <button
                                aria-label={`Delete ${group.name}`}
                                className="group-types-panel__action-btn group-types-panel__action-btn--delete"
                                onClick={onDeleteClicked}
                                tabIndex={!isActive || isRenaming ? -1 : 0}
                                title="Delete"
                            >
                                <span aria-hidden="true" className="fa fa-trash-o" />
                            </button>
                        </span>
                    ))}
            </div>
            {isRenaming && renameError && (
                <div className="group-types-panel__name-error" id={`rename-error-${group.rowId}`}>
                    {renameError}
                </div>
            )}
        </React.Fragment>
    );
};
GroupRow.displayName = 'GroupRow';

interface GroupTypeTabButtonProps {
    isActive: boolean;
    onTabChange: (tab: string) => void;
    type: string;
}

const GroupTypeTabButton: FC<GroupTypeTabButtonProps> = ({ type, isActive, onTabChange }) => {
    const handleClick = useCallback(() => onTabChange(type), [onTabChange, type]);
    return (
        <TabButton
            baseClass="group-types-panel__tab"
            id={`group-tab-${type}`}
            isActive={isActive}
            onClick={handleClick}
            panelId={`group-panel-${type}`}
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
export const GroupTypesPanel: FC<GroupTypesPanelProps> = ({
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
    // Stable derived list — memoized so useMemo and useEffect deps are stable.
    const groupsOfType = useMemo(() => plate.groups.filter(g => g.type === activeTab), [plate, activeTab]);
    const groupNames = useMemo(() => groupsOfType.map(g => g.name), [groupsOfType]);
    const canAdd = plate.canCreateGroupsByType?.[activeTab] ?? false;

    // Predefined slot names not yet occupied by an existing group of this type.
    const unusedDefaults = useMemo(() => {
        const defaults = plate.typesToDefaultGroups[activeTab] ?? [];
        return defaults.filter(d => !groupsOfType.some(g => g.name === d));
    }, [plate, activeTab, groupsOfType]);

    const handleGroupsMouseLeave = useCallback(() => onGroupHover(null), [onGroupHover]);

    return (
        <div className="group-types-panel">
            <TabList className="group-types-panel__tabs">
                {plate.groupTypes.map(type => (
                    <GroupTypeTabButton
                        isActive={type === activeTab}
                        key={type}
                        onTabChange={onTabChange}
                        type={type}
                    />
                ))}
            </TabList>
            {plate.groupTypes.map(type => (
                <div
                    aria-labelledby={`group-tab-${type}`}
                    className="group-types-panel__tab-body"
                    hidden={type !== activeTab}
                    id={`group-panel-${type}`}
                    key={type}
                    role="tabpanel"
                >
                    {type === activeTab && (
                        <>
                            <div
                                aria-label="Well groups"
                                className="group-types-panel__groups"
                                onMouseLeave={handleGroupsMouseLeave}
                                role="listbox"
                            >
                                {groupsOfType.map(group => (
                                    <GroupRow
                                        colorMap={colorMap}
                                        existingGroupNames={groupNames}
                                        group={group}
                                        isActive={activeGroup?.rowId === group.rowId}
                                        isHighlighted={hoveredWellGroupId === group.rowId}
                                        key={group.rowId}
                                        onAddGroup={onAddGroup}
                                        onDeleteGroup={onDeleteGroup}
                                        onGroupHover={onGroupHover}
                                        onGroupSelect={onGroupSelect}
                                        onRenameGroup={onRenameGroup}
                                    />
                                ))}
                                {canAdd && (
                                    <CreateGroupRow
                                        activeTab={activeTab}
                                        existingGroupNames={groupNames}
                                        onAddGroup={onAddGroup}
                                        unusedDefaults={unusedDefaults}
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
