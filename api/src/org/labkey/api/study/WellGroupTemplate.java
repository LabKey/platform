package org.labkey.api.study;

import java.util.List;

/**
 * User: brittp
 * Date: Oct 23, 2006
 * Time: 1:33:19 PM
 */
public interface WellGroupTemplate extends PropertySet
{
    Integer getRowId();

    List<Position> getPositions();

    WellGroup.Type getType();

    String getName();

    boolean contains(Position position);
}
