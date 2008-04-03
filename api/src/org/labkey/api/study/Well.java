package org.labkey.api.study;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 10:17:39 AM
 */
public interface Well extends WellData, Position
{
    double getValue();
}
