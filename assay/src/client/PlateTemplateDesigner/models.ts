/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

export interface Position {
    row: number;
    col: number;
}

export interface WellGroup {
    rowId: number;
    type: string;
    name: string;
    positions: Position[];
    properties: Record<string, string>;
    allowNewGroups: boolean;
}

export interface PlateTemplate {
    rowId: number;
    name: string;
    type: string;
    rows: number;
    cols: number;
    groupTypes: string[];
    canCreateGroupsByType: Record<string, boolean>;
    groups: WellGroup[];
    plateProperties: Record<string, string>;
    typesToDefaultGroups: Record<string, string[]>;
    showWarningPanel: boolean;
    existingTemplateNames: string[];
    copyMode: boolean;
    defaultPlateName: string;
}

export interface SaveTemplateResponse {
    rowId: number;
}

// Matches GWT TemplateGridCell.getWarnings() logic exactly.
export function computeWarnings(plate: PlateTemplate): string[] {
    const cellTypes = new Map<string, Set<string>>();
    for (const group of plate.groups) {
        for (const pos of group.positions) {
            const key = `${pos.row},${pos.col}`;
            if (!cellTypes.has(key)) cellTypes.set(key, new Set());
            cellTypes.get(key).add(group.type);
        }
    }
    const warnings: string[] = [];
    for (const [key, types] of cellTypes.entries()) {
        const [row, col] = key.split(',').map(Number);
        const cellLabel = `${String.fromCharCode(65 + row)}${col + 1}`;
        const hasReplicate = types.has('REPLICATE');
        const hasSpecimen = types.has('SPECIMEN');
        const hasControl = types.has('CONTROL');
        if (hasReplicate && !(hasSpecimen || hasControl)) {
            warnings.push(`${cellLabel}: Well is a replicate, but is not part of a specimen or control group.`);
        }
        if (hasControl && hasSpecimen) {
            warnings.push(`${cellLabel}: Well is in both a specimen and a control group.`);
        }
    }
    return warnings;
}
