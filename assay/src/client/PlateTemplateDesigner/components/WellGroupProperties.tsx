/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { FC, useCallback, useEffect, useState } from 'react';

import { WellGroup } from '../models';

interface WellGroupPropertiesProps {
    activeGroup: null | WellGroup;
    onDeleteProperty: (groupRowId: number, key: string) => void;
    onPropertyChange: (groupRowId: number, key: string, value: string) => void;
}

interface PropertyRowProps {
    onDeleteProperty: (key: string) => void;
    onPropertyChange: (key: string, value: string) => void;
    propKey: string;
    value: string;
}

const PropertyRow: FC<PropertyRowProps> = ({ propKey, value, onPropertyChange, onDeleteProperty }) => {
    const handleChange = useCallback(
        (e: React.ChangeEvent<HTMLInputElement>) => onPropertyChange(propKey, e.target.value),
        [onPropertyChange, propKey]
    );
    const handleDelete = useCallback(() => onDeleteProperty(propKey), [onDeleteProperty, propKey]);

    return (
        <tr>
            <td className="well-group-properties__key">{propKey}</td>
            <td className="well-group-properties__value-cell">
                <input
                    aria-label={propKey}
                    className="well-group-properties__value"
                    onChange={handleChange}
                    type="text"
                    value={value}
                />
            </td>
            <td className="well-group-properties__action-cell">
                <button
                    aria-label={`Delete property ${propKey}`}
                    className="well-group-properties__delete-btn"
                    onClick={handleDelete}
                    title="Delete property"
                >
                    <span aria-hidden="true" className="fa fa-trash-o" />
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
export const WellGroupProperties: FC<WellGroupPropertiesProps> = ({
    activeGroup,
    onPropertyChange,
    onDeleteProperty,
}) => {
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

    const handleNewKeyChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setNewKey(e.target.value), []);
    const handleNewValueChange = useCallback(
        (e: React.ChangeEvent<HTMLInputElement>) => setNewValue(e.target.value),
        []
    );
    const handleNewKeyKeyDown = useCallback(
        (e: React.KeyboardEvent<HTMLInputElement>) => {
            if (e.key === 'Enter' && newKey.trim()) handleAdd();
        },
        [newKey, handleAdd]
    );
    const handleNewValueKeyDown = useCallback(
        (e: React.KeyboardEvent<HTMLInputElement>) => {
            if (e.key === 'Enter' && newKey.trim()) handleAdd();
        },
        [newKey, handleAdd]
    );

    // Adapters that prepend groupRowId, needed because PropertyRow doesn't know the group context.
    const handlePropertyChange = useCallback(
        (key: string, value: string) => onPropertyChange(activeGroup!.rowId, key, value),
        [onPropertyChange, activeGroup]
    );
    const handleDeleteProperty = useCallback(
        (key: string) => onDeleteProperty(activeGroup!.rowId, key),
        [onDeleteProperty, activeGroup]
    );

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
                        <th className="well-group-properties__key" scope="col">
                            Property
                        </th>
                        <th scope="col">Value</th>
                        <th scope="col">
                            <span className="sr-only">Actions</span>
                        </th>
                    </tr>
                </thead>
                <tbody>
                    {propEntries.length === 0 && (
                        <tr>
                            <td className="well-group-properties__no-props" colSpan={3}>
                                No properties defined.
                            </td>
                        </tr>
                    )}
                    {propEntries.map(([key, value]) => (
                        <PropertyRow
                            key={key}
                            onDeleteProperty={handleDeleteProperty}
                            onPropertyChange={handlePropertyChange}
                            propKey={key}
                            value={value}
                        />
                    ))}
                </tbody>
                <tfoot>
                    <tr className="well-group-properties__add-row">
                        <td className="well-group-properties__key">
                            <input
                                aria-label="Property name"
                                className="well-group-properties__new-key"
                                onChange={handleNewKeyChange}
                                onKeyDown={handleNewKeyKeyDown}
                                placeholder="Property name"
                                type="text"
                                value={newKey}
                            />
                        </td>
                        <td className="well-group-properties__value-cell">
                            <input
                                aria-label="Property value"
                                className="well-group-properties__new-value"
                                onChange={handleNewValueChange}
                                onKeyDown={handleNewValueKeyDown}
                                placeholder="Value"
                                type="text"
                                value={newValue}
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
