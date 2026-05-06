/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { FC, useCallback, useEffect, useMemo, useState } from 'react';

import { useEnterEscape } from '../useEnterEscape';

import { MultiCreateDialog } from './MultiCreateDialog';

interface CreateGroupRowProps {
    activeTab: string;
    existingGroupNames: string[];
    onAddGroup: (type: string, name: string) => void;
    unusedDefaults: string[];
}

/** Create-group controls for a single group type tab: a name input (or predefined-name select), Create and "Create multiple…" buttons, inline conflict error, and the MultiCreateDialog. */
export const CreateGroupRow: FC<CreateGroupRowProps> = ({
    unusedDefaults,
    existingGroupNames,
    activeTab,
    onAddGroup,
}) => {
    const [newGroupName, setNewGroupName] = useState(unusedDefaults[0] ?? '');
    const [multiCreateOpen, setMultiCreateOpen] = useState(false);

    const createNameConflicts = useMemo(
        () => newGroupName.trim() !== '' && existingGroupNames.includes(newGroupName.trim()),
        [newGroupName, existingGroupNames]
    );

    // Reset name input when switching tabs
    useEffect(() => {
        setNewGroupName(unusedDefaults[0] ?? '');
    }, [activeTab]); // eslint-disable-line react-hooks/exhaustive-deps

    // Advance to next unused default when the current one gets used
    useEffect(() => {
        if (unusedDefaults.length > 0 && !unusedDefaults.includes(newGroupName)) {
            setNewGroupName(unusedDefaults[0]);
        }
    }, [unusedDefaults]); // eslint-disable-line react-hooks/exhaustive-deps

    const handleCreate = useCallback(() => {
        const trimmed = newGroupName.trim();
        if (!trimmed || createNameConflicts) return;
        onAddGroup(activeTab, trimmed);
        setNewGroupName('');
    }, [newGroupName, createNameConflicts, onAddGroup, activeTab]);

    const handleNewGroupNameChange = useCallback((e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        setNewGroupName(e.target.value);
    }, []);

    const handleCreateKeyDown = useEnterEscape(createNameConflicts || !newGroupName.trim() ? undefined : handleCreate);

    const handleOpenMultiCreate = useCallback(() => setMultiCreateOpen(true), []);
    const handleCloseMultiCreate = useCallback(() => setMultiCreateOpen(false), []);

    const handleMultiCreateConfirm = useCallback(
        (names: string[]) => {
            names.forEach(name => onAddGroup(activeTab, name));
            setMultiCreateOpen(false);
        },
        [onAddGroup, activeTab]
    );

    return (
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
                        onChange={handleNewGroupNameChange}
                        value={newGroupName}
                    >
                        {unusedDefaults.map(d => (
                            <option key={d} value={d}>
                                {d}
                            </option>
                        ))}
                    </select>
                ) : (
                    <input
                        aria-describedby={createNameConflicts ? 'create-name-error' : undefined}
                        aria-invalid={createNameConflicts}
                        aria-label="Group name"
                        className="group-types-panel__new-name-input"
                        onChange={handleNewGroupNameChange}
                        onKeyDown={handleCreateKeyDown}
                        placeholder="Group name"
                        type="text"
                        value={newGroupName}
                    />
                )}
                <button
                    className="group-types-panel__add-btn"
                    disabled={!newGroupName.trim() || createNameConflicts}
                    onClick={handleCreate}
                >
                    Create
                </button>
                <button className="group-types-panel__add-btn" onClick={handleOpenMultiCreate}>
                    Create multiple...
                </button>
            </div>
            {createNameConflicts && (
                <div className="group-types-panel__name-error" id="create-name-error">
                    A group named "{newGroupName.trim()}" already exists in this type.
                </div>
            )}
            {multiCreateOpen && (
                <MultiCreateDialog
                    existingNames={new Set(existingGroupNames)}
                    initialBaseName={newGroupName.trim()}
                    onClose={handleCloseMultiCreate}
                    onConfirm={handleMultiCreateConfirm}
                />
            )}
        </>
    );
};
CreateGroupRow.displayName = 'CreateGroupRow';
