/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';

import { PlateTemplate, computeWarnings } from '../models';

interface Props {
    plate: PlateTemplate;
}

export function WarningPanel({ plate }: Props): JSX.Element {
    const warnings = computeWarnings(plate);

    return (
        <div className="warning-panel">
            <div className="warning-panel__title">Warnings</div>
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
}
