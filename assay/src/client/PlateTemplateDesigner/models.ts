/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

export interface Position {
    col: number;
    row: number;
}

export interface WellGroup {
    allowNewGroups: boolean; // Whether the user can create/rename/delete groups of this type
    name: string;
    positions: Position[];
    properties: Record<string, string>;
    rowId: number; // Positive = server-assigned; negative = client-side temp ID (see nextGroupIdRef)
    type: string; // Group type key, e.g. "CONTROL", "SPECIMEN", "REPLICATE"
}

export interface PlateTemplate {
    canCreateGroupsByType: Record<string, boolean>; // Which types expose the create-group UI
    cols: number;
    copyMode: boolean; // True when the plate was loaded as a copy; starts the editor in dirty state
    defaultPlateName: string;
    existingTemplateNames: string[];
    groups: WellGroup[];
    groupTypes: string[]; // Ordered list of type keys; drives the tab strip
    name: string;
    plateProperties: Record<string, string>;
    rowId: number;
    rows: number;
    showWarningPanel: boolean; // Set by the server based on assay type config
    type: string;
    typesToDefaultGroups: Record<string, string[]>; // Predefined slot names per type (e.g. "Virus", "Cell Control")
}

/**
 * Two conditions produce warnings:
 *  1. A REPLICATE well that belongs to neither a SPECIMEN nor a CONTROL group is almost certainly
 *     a configuration error — replicates are only meaningful relative to a specimen or control.
 *  2. A well assigned to both a SPECIMEN and a CONTROL group is contradictory; those roles are
 *     mutually exclusive in LabKey assay semantics.
 *
 * Notes:
 * - Warnings are per-cell, not per-group.
 * - A cell can appear in multiple groups of different types (e.g. SPECIMEN + REPLICATE together is fine).
 * - Cell labels use spreadsheet notation: row → letter (A=0, B=1, …), col → 1-based number.
 */
export const GROUP_TYPE_REPLICATE = 'REPLICATE';
export const GROUP_TYPE_SPECIMEN = 'SPECIMEN';
export const GROUP_TYPE_CONTROL = 'CONTROL';

export function computeWarnings(plate: PlateTemplate): string[] {
    // Build a map from cell position → set of group types that include it.
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
        const hasReplicate = types.has(GROUP_TYPE_REPLICATE);
        const hasSpecimen = types.has(GROUP_TYPE_SPECIMEN);
        const hasControl = types.has(GROUP_TYPE_CONTROL);
        if (hasReplicate && !(hasSpecimen || hasControl)) {
            warnings.push(`${cellLabel}: Well is a replicate, but is not part of a specimen or control group.`);
        }
        if (hasControl && hasSpecimen) {
            warnings.push(`${cellLabel}: Well is in both a specimen and a control group.`);
        }
    }
    return warnings;
}
