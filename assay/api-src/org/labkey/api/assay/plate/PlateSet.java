package org.labkey.api.assay.plate;

import org.labkey.api.exp.Identifiable;

import java.util.List;

public interface PlateSet extends Identifiable
{
    int MAX_PLATES = 60;
    int MAX_PLATE_WELL_SIZE = 384;
    int MAX_PLATE_SET_WELLS = MAX_PLATES * MAX_PLATE_WELL_SIZE;

    Long getRowId();

    String getDescription();

    String getPlateSetId();

    boolean isArchived();

    boolean isAssay();

    boolean isPrimary();

    boolean isStandalone();

    boolean isTemplate();

    List<? extends Plate> getPlates();

    PlateSetType getType();

    Long getRootPlateSetId();
}
