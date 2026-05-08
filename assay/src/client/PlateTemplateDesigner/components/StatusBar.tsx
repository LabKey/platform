/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { FC, useCallback, useEffect, useState } from 'react';

interface StatusBarProps {
    isDirty: boolean;
    onCancel: () => void;
    onSave: () => void;
    onSaveAndClose: () => void;
    plateName: string;
    status: string;
}

/**
 * Persistent action bar pinned to the top of the designer.
 * "Save" persists the current state and updates the page URL to the canonical
 *    ?templateName=...&plateId=... form so a browser refresh reloads the same plate.
 * Dirty state and saving status are shown next to the buttons.
 */
export const StatusBar: FC<StatusBarProps> = ({ isDirty, status, plateName, onSaveAndClose, onSave, onCancel }) => {
    const [error, setError] = useState<null | string>(null);

    // Clear stale validation error once the user has filled in the plate name
    useEffect(() => {
        if (plateName.trim()) setError(null);
    }, [plateName]);

    const validate = useCallback((): boolean => {
        if (!plateName.trim()) {
            setError('Please enter a plate name before saving.');
            return false;
        }
        setError(null);
        return true;
    }, [plateName]);

    const validateAndSave = useCallback(() => {
        if (validate()) onSave();
    }, [validate, onSave]);

    // Skip validation when there is nothing to save — the parent will navigate away without writing.
    const validateAndSaveAndClose = useCallback(() => {
        if (!isDirty || validate()) onSaveAndClose();
    }, [isDirty, validate, onSaveAndClose]);

    return (
        <div className="status-bar">
            <button className="status-bar__save-and-close-btn btn btn-primary" onClick={validateAndSaveAndClose}>
                Save &amp; Close
            </button>
            <button className="status-bar__save-btn btn btn-default" disabled={!isDirty} onClick={validateAndSave}>
                Save
            </button>
            <button className="status-bar__cancel-btn btn btn-default" onClick={onCancel}>
                Cancel
            </button>
            <span className="status-bar__dirty" role="status">
                {isDirty ? 'Unsaved changes' : ''}
            </span>
            <span className="status-bar__status" role="status">
                {status}
            </span>
            {error && (
                <span className="status-bar__error" role="alert">
                    {error}
                </span>
            )}
        </div>
    );
};
StatusBar.displayName = 'StatusBar';
