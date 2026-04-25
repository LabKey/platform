/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { useState } from 'react';

import { WellGroup } from '../models';

interface Props {
    activeGroup: WellGroup | null;
    onPropertyChange: (groupRowId: number, key: string, value: string) => void;
    onDeleteProperty: (groupRowId: number, key: string) => void;
}

export function WellGroupProperties({ activeGroup, onPropertyChange, onDeleteProperty }: Props): JSX.Element {
    const [newKey, setNewKey] = useState('');
    const [newValue, setNewValue] = useState('');

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
                                    value={value ?? ''}
                                    onChange={e => onPropertyChange(activeGroup.rowId, key, e.target.value)}
                                />
                            </td>
                            <td className="well-group-properties__action-cell">
                                <button
                                    className="well-group-properties__delete-btn"
                                    title="Delete property"
                                    onClick={() => onDeleteProperty(activeGroup.rowId, key)}
                                >
                                    <span className="fa fa-trash-o" />
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
