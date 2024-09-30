package org.labkey.api.assay.plate;

import org.labkey.api.data.Container;

import java.util.List;

public interface PlateSet
{
    int MAX_PLATES = 60;
    int MAX_PLATE_WELL_SIZE = 384;
    int MAX_PLATE_SET_WELLS = MAX_PLATES * MAX_PLATE_WELL_SIZE;

    Integer getRowId();

    Container getContainer();

    String getDescription();

    String getName();

    String getPlateSetId();

    boolean isArchived();

    boolean isAssay();

    boolean isPrimary();

    boolean isStandalone();

    boolean isTemplate();

    List<Plate> getPlates();

    PlateSetType getType();

    Integer getRootPlateSetId();
}
