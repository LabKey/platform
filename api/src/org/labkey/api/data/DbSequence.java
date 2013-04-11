package org.labkey.api.data;

/**
 * User: adam
 * Date: 4/6/13
 * Time: 2:16 PM
 */
public class DbSequence
{
    private final Container _c;
    private final int _rowId;

    DbSequence(Container c, int rowId)
    {
        _c = c;
        _rowId = rowId;

    }

    public int getRowId()
    {
        return _rowId;
    }

    public Container getContainer()
    {
        return _c;
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