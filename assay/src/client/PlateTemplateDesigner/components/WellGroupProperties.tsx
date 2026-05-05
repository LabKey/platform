/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { useCallback, useEffect, useState } from 'react';

import { WellGroup } from '../models';

interface WellGroupPropertiesProps {
    activeGroup: WellGroup | null;
    onPropertyChange: (groupRowId: number, key: string, value: string) => void;
    onDeleteProperty: (groupRowId: number, key: string) => void;
}

interface PropertyRowProps {
    propKey: string;
    value: string;
    onPropertyChange: (key: string, value: string) => void;
    onDeleteProperty: (key: string) => void;
}

const PropertyRow: React.FC<PropertyRowProps> = ({ propKey, value, onPropertyChange, onDeleteProperty }) => {
    const handleChange = useCallback(
        (e: React.ChangeEvent<HTMLInputElement>) => onPropertyChange(propKey, e.target.value),
        [onPropertyChange, propKey]);
    const handleDelete = useCallback(
        () => onDeleteProperty(propKey),
        [onDeleteProperty, propKey]);

    return (
        <tr>
            <td className="well-group-properties__key">{propKey}</td>
            <td className="well-group-properties__value-cell">
                <input
                    className="well-group-properties__value"
                    type="text"
                    aria-label={propKey}
                    value={value}
                    onChange={handleChange}
                />
            </td>
            <td className="well-group-properties__action-cell">
                <button
                    className="well-group-properties__delete-btn"
                    title="Delete property"
                    aria-label={`Delete property ${propKey}`}
                    onClick={handleDelete}
                >
                    <span className="fa fa-trash-o" aria-hidden="true" />
                </button>
            </td>
        </tr>
    );
};
PropertyRow.displayName = 'PropertyRow';

/**
 * Shows and edits the key/value property bag for the currently selected well group.
 *
 * Properties are assay-type-specific metadata attached to a group (e.g. concentration,
 * dilution factor, sample ID). They are stored as plain strings and round-tripped through
 * the server without interpretation by the designer.
 */
export const WellGroupProperties: React.FC<WellGroupPropertiesProps> = ({ activeGroup, onPropertyChange, onDeleteProperty }) => {
    const [newKey, setNewKey] = useState('');
    const [newValue, setNewValue] = useState('');

    // Reset draft inputs when the selected group changes so stale text from a
    // previous group cannot accidentally be committed to the newly selected one.
    useEffect(() => {
        setNewKey('');
        setNewValue('');
    }, [activeGroup?.rowId]);

    const handleAdd = useCallback(() => {
        if (!activeGroup) return;
        const key = newKey.trim();
        if (!key) return;
        onPropertyChange(activeGroup.rowId, key, newValue);
        setNewKey('');
        setNewValue('');
    }, [activeGroup, newKey, newValue, onPropertyChange]);

    const handleNewKeyChange = useCallback(
        (e: React.ChangeEvent<HTMLInputElement>) => setNewKey(e.target.value), []);
    const handleNewValueChange = useCallback(
        (e: React.ChangeEvent<HTMLInputElement>) => setNewValue(e.target.value), []);
    const handleNewKeyKeyDown = useCallback((e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'Enter' && newKey.trim()) handleAdd();
    }, [newKey, handleAdd]);
    const handleNewValueKeyDown = useCallback((e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'Enter' && newKey.trim()) handleAdd();
    }, [newKey, handleAdd]);

    // Adapters that prepend groupRowId, needed because PropertyRow doesn't know the group context.
    const handlePropertyChange = useCallback(
        (key: string, value: string) => onPropertyChange(activeGroup!.rowId, key, value),
        [onPropertyChange, activeGroup]);
    const handleDeleteProperty = useCallback(
        (key: string) => onDeleteProperty(activeGroup!.rowId, key),
        [onDeleteProperty, activeGroup]);

    if (!activeGroup) {
        return (
            <div className="well-group-properties well-group-properties--empty">
                Select a well group to view its properties.
            </div>
        );
    }

    const propEntries = Object.entries(activeGroup.properties);

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
                        <PropertyRow
                            key={key}
                            propKey={key}
                            value={value}
                            onPropertyChange={handlePropertyChange}
                            onDeleteProperty={handleDeleteProperty}
                        />
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
                                onChange={handleNewKeyChange}
                                onKeyDown={handleNewKeyKeyDown}
                            />
                        </td>
                        <td className="well-group-properties__value-cell">
                            <input
                                className="well-group-properties__new-value"
                                type="text"
                                placeholder="Value"
                                aria-label="Property value"
                                value={newValue}
                                onChange={handleNewValueChange}
                                onKeyDown={handleNewValueKeyDown}
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
};
WellGroupProperties.displayName = 'WellGroupProperties';
