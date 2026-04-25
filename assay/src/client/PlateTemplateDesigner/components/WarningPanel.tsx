/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { useMemo } from 'react';

import { PlateTemplate, computeWarnings } from '../models';

interface Props {
    plate: PlateTemplate;
}

/**
 * Displays the list of validation warnings for the current plate layout.
 *
 * Warnings are recomputed synchronously from the latest plate state on each render.
 * The panel is only shown when `plate.showWarningPanel` is true, which is controlled by the
 * server-side assay type configuration (not all assay types use the REPLICATE/SPECIMEN/CONTROL
 * group semantics that produce warnings).
 */
export function WarningPanel({ plate }: Props): JSX.Element {
    const warnings = useMemo(() => computeWarnings(plate), [plate]);

    return (
        <div className="warning-panel">
            <div className="warning-panel__title">Warnings</div>
            {warnings.length === 0 ? (
                <div className="warning-panel__none">No warnings.</div>
            ) : (
                <ul className="warning-panel__list">
                    {warnings.map((w) => (
                        <li key={w} className="warning-panel__item">{w}</li>
                    ))}
                </ul>
            )}
        </div>
    );
}
