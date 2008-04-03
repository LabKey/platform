package org.labkey.api.study;

import java.util.List;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 10:15:44 AM
 */
public interface Plate extends PlateTemplate
{
    Well getWell(int row, int col);

    WellGroup getWellGroup(WellGroup.Type type, String wellGroupName);

    List<? extends WellGroup> getWellGroups(WellGroup.Type type);

    List<? extends WellGroup> getWellGroups(Position position);

    List<? extends WellGroup> getWellGroups();
}
