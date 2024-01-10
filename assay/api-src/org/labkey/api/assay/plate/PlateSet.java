package org.labkey.api.assay.plate;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;

public interface PlateSet
{
    int getRowId();

    Container getContainer();

    String getName();

    boolean isArchived();

    List<Plate> getPlates(User user);
}
