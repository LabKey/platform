/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { useEffect, useState } from 'react';

import { WellGroup } from '../models';

interface WellGroupPropertiesProps {
    activeGroup: WellGroup | null;
    onPropertyChange: (groupRowId: number, key: string, value: string) => void;
    onDeleteProperty: (groupRowId: number, key: string) => void;
}

/**
 * Shows and edits the key/value property bag for the currently selected well group.
 *
 * Properties are assay-type-specific metadata attached to a group (e.g. concentration,
 * dilution factor, sample ID). They are stored as plain strings and round-tripped through
 * the server without interpretation by the designer.
 *
 * Interaction pattern:
 *  - Existing properties: each row has an inline text input for the value; changes propagate
 *    immediately to the parent (no separate submit step) via onPropertyChange.
 *  - Deleting: the trash button removes a property key entirely.
 *  - Adding: the footer row accepts a new key + value; "Add" (or Enter) commits the pair.
 *    The new-key input is the gate — the Add button stays disabled until a key is typed.
 */
export function WellGroupProperties({ activeGroup, onPropertyChange, onDeleteProperty }: WellGroupPropertiesProps): JSX.Element {
    const [newKey, setNewKey] = useState('');
    const [newValue, setNewValue] = useState('');

    // Reset draft inputs when the selected group changes so stale text from a
    // previous group cannot accidentally be committed to the newly selected one.
    useEffect(() => {
        setNewKey('');
        setNewValue('');
    }, [activeGroup?.rowId]);

    if (!activeGroup) {
        return (
            <div className="well-group-properties well-group-properties--empty">
                Select a well group to view its properties.
            </div>
        );
    }

    const propEntries = Object.entries(activeGroup.properties);

    const handleAdd = () => {
        const key = newKey.trim();
        if (!key) return;
        onPropertyChange(activeGroup.rowId, key, newValue);
        setNewKey('');
        setNewValue('');
    };

    return (
        <div className="well-group-properties">
            <div className="well-group-properties__title">{activeGroup.name}</div>
            <table className="well-group-properties__table">
                <thead>
                    <tr>
                        <th scope="col" className="well-group-properties__key">Property</th>
                        <th scope="col">Value</th>
                        <th scope="col"><span className="sr-only">Actions</span></th>
                    </tr>
                </thead>
                <tbody>
                    {propEntries.length === 0 && (
                        <tr>
                            <td colSpan={3} className="well-group-properties__no-props">No properties defined.</td>
                        </tr>
                    )}
                    {propEntries.map(([key, value]) => (
                        <tr key={key}>
                            <td className="well-group-properties__key">{key}</td>
                            <td className="well-group-properties__value-cell">
                                <input
                                    className="well-group-properties__value"
                                    type="text"
                                    aria-label={key}
                                    value={value}
                                    onChange={e => onPropertyChange(activeGroup.rowId, key, e.target.value)}
                                />
                            </td>
                            <td className="well-group-properties__action-cell">
                                <button
                                    className="well-group-properties__delete-btn"
                                    title="Delete property"
                                    aria-label={`Delete property ${key}`}
                                    onClick={() => onDeleteProperty(activeGroup.rowId, key)}
                                >
                                    <span className="fa fa-trash-o" aria-hidden="true" />
                                </button>
                            </td>
                        </tr>
                    ))}
                </tbody>
                <tfoot>
                    <tr className="well-group-properties__add-row">
                        <td className="well-group-properties__key">
                            <input
                                className="well-group-properties__new-key"
                                type="text"
                                placeholder="Property name"
                                aria-label="Property name"
                                value={newKey}
                                onChange={e => setNewKey(e.target.value)}
                                onKeyDown={e => { if (e.key === 'Enter' && newKey.trim()) handleAdd(); }}
                            />
                        </td>
                        <td className="well-group-properties__value-cell">
                            <input
                                className="well-group-properties__new-value"
                                type="text"
                                placeholder="Value"
                                aria-label="Property value"
                                value={newValue}
                                onChange={e => setNewValue(e.target.value)}
                                onKeyDown={e => { if (e.key === 'Enter' && newKey.trim()) handleAdd(); }}
                            />
                        </td>
                        <td className="well-group-properties__action-cell">
                            <button
                                className="well-group-properties__add-btn"
                                disabled={!newKey.trim()}
                                onClick={handleAdd}
                            >
                                Add
                            </button>
                        </td>
                    </tr>
                </tfoot>
            </table>
        </div>
    );
}
