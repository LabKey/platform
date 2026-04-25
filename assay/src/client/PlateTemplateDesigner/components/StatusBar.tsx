/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';

interface Props {
    isDirty: boolean;
    status: string;
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
export function StatusBar({ isDirty, status, onSaveAndClose, onSave, onCancel }: Props): JSX.Element {
    return (
        <div className="status-bar">
            <button className="status-bar__btn status-bar__btn--primary" onClick={onSaveAndClose}>
                Save &amp; Close
            </button>
            <button className="status-bar__btn" onClick={onSave} disabled={!isDirty}>
                Save
            </button>
            <button className="status-bar__btn" onClick={onCancel}>
                Cancel
            </button>
            <span role="status" className="status-bar__dirty">{isDirty ? 'Unsaved changes' : ''}</span>
            <span role="status" className="status-bar__status">{status}</span>
        </div>
    );
}
