package org.labkey.api.assay.plate;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.view.ActionURL;

import java.util.List;

public interface PlateSet extends Identifiable
{
    int MAX_PLATES = 60;
    int MAX_PLATE_WELL_SIZE = 384;
    int MAX_PLATE_SET_WELLS = MAX_PLATES * MAX_PLATE_WELL_SIZE;

    Integer getRowId();

    @Override
    Container getContainer();

    String getDescription();

    @Override
    @Nullable ActionURL detailsURL();

    @Override
    String getName();

    String getPlateSetId();

    @Override
    String getLSID();

    boolean isArchived();

    boolean isAssay();

    boolean isPrimary();

    boolean isStandalone();

    boolean isTemplate();

    List<Plate> getPlates();

    PlateSetType getType();

    Integer getRootPlateSetId();
}
