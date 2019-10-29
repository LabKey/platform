package org.labkey.api.exp.api;

import org.labkey.api.exp.Identifiable;

/**
 * Marker interface for types that can participate in lineage.
 * Currently only ExpData, ExpMaterial, and ExpRun may participate directly in lineage.
 */
public interface ExpLineageItem extends Identifiable
{
}
