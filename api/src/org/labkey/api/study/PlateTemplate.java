package org.labkey.api.study;

import org.labkey.api.attachments.AttachmentParent;

import java.util.List;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 1:02:47 PM
 */
public interface PlateTemplate extends PropertySet, AttachmentParent
{
    String getName();

    void setName(String name);

    int getRows();

    int getColumns();

    public List<? extends WellGroupTemplate> getWellGroups();

    public List<? extends WellGroupTemplate> getWellGroups(Position position);

    WellGroupTemplate addWellGroup(String name, WellGroup.Type type, Position upperLeft, Position lowerRight);

    WellGroupTemplate addWellGroup(String name, WellGroup.Type type, List<Position> positions);

    Integer getRowId();

    Position getPosition(int row, int col);

    int getWellGroupCount();

    int getWellGroupCount(WellGroup.Type type);

    String getType();
}
