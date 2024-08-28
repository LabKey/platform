package org.labkey.api.assay.plate;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;

public interface PlateSet
{
    int MAX_PLATES = 60;

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
