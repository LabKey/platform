package org.labkey.api.exp;

/**
 * User: jeckels
 * Date: Nov 5, 2007
 */
public class DuplicateMaterialException extends ExperimentException
{
    private String _colName;

    public DuplicateMaterialException(String message, String colName)
    {
        super(message);
        _colName = colName;
    }

    public String getColName()
    {
        return _colName;
    }
}
