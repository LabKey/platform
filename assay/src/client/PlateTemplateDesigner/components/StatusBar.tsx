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
            {isDirty && <span className="status-bar__dirty">Unsaved changes</span>}
            {status && <span className="status-bar__status">{status}</span>}
        </div>
    );
}
