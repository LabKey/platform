package org.labkey.api.data;

/**
 * User: adam
 * Date: 4/6/13
 * Time: 2:16 PM
 */
public class DbSequence
{
    private final int _rowId;

    DbSequence(int rowId)
    {
        _rowId = rowId;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public int current()
    {
        return DbSequenceManager.current(this);
    }

    public int next()
    {
        return DbSequenceManager.next(this);
    }

    public void ensureMinimum(int value)
    {
        DbSequenceManager.ensureMinimum(this, value);
    }
}