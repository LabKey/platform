/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { FC } from 'react';

interface WarningPanelProps {
    warnings: string[];
}

/**
 * Displays the list of validation warnings for the current plate layout.
 *
 * Warnings are computed in the parent (PlateTemplateDesigner) and passed as a prop so the
 * computation is not duplicated between the count badge and this panel.
 * The panel is only shown when `plate.showWarningPanel` is true, which is controlled by the
 * server-side assay type configuration (not all assay types use the REPLICATE/SPECIMEN/CONTROL
 * group semantics that produce warnings).
 */
export const WarningPanel: FC<WarningPanelProps> = ({ warnings }) => (
    <div className="warning-panel">
        {warnings.length === 0 ? (
            <div className="warning-panel__none">No warnings.</div>
        ) : (
            <ul className="warning-panel__list">
                {warnings.map((w, i) => (
                    <li key={i} className="warning-panel__item">{w}</li>
                ))}
            </ul>
        )}
    </div>
);
WarningPanel.displayName = 'WarningPanel';
