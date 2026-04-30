/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { useEffect, useState } from 'react';

interface StatusBarProps {
    isDirty: boolean;
    status: string;
    plateName: string;
    onSaveAndClose: () => void;
    onSave: () => void;
    onCancel: () => void;
}

/**
 * Persistent action bar pinned to the top of the designer.
 *
 * Button behavior:
 *  - "Save & Close": saves if dirty, then navigates to the returnURL (or plate list).
 *    Always enabled so users can leave even when clean.
 *  - "Save": persists the current state and updates the page URL to the canonical
 *    ?templateName=...&plateId=... form so a browser refresh reloads the same plate.
 *    Disabled when the plate is clean to prevent redundant requests.
 *  - "Cancel": navigates away without saving. The browser's beforeunload handler
 *    will prompt if there are unsaved changes.
 *
 * The "Unsaved changes" indicator and transient status text ("Saving…", "Saved.")
 * use `role="status"` so screen readers announce them as they appear.
 */
export function StatusBar({ isDirty, status, plateName, onSaveAndClose, onSave, onCancel }: StatusBarProps): JSX.Element {
    const [error, setError] = useState<string | null>(null);

    // Clear stale validation error once the user has filled in the plate name
    useEffect(() => {
        if (plateName.trim()) setError(null);
    }, [plateName]);

    const validate = (): boolean => {
        if (!plateName.trim()) {
            setError('Please enter a plate name before saving.');
            return false;
        }
        setError(null);
        return true;
    };

    const validateAndSave = () => { if (validate()) onSave(); };

    // Skip validation when there is nothing to save — the parent will navigate away without writing.
    const validateAndSaveAndClose = () => { if (!isDirty || validate()) onSaveAndClose(); };

    return (
        <div className="status-bar">
            <button className="save-button btn btn-primary" onClick={validateAndSaveAndClose}>
                Save &amp; Close
            </button>
            <button className="save-button btn btn-default" onClick={validateAndSave} disabled={!isDirty}>
                Save
            </button>
            <button className="cancel-button btn btn-default" onClick={onCancel}>
                Cancel
            </button>
            <span role="status" className="status-bar__dirty">{isDirty ? 'Unsaved changes' : ''}</span>
            <span role="status" className="status-bar__status">{status}</span>
            {error && <span role="alert" className="status-bar__error">{error}</span>}
        </div>
    );
}
