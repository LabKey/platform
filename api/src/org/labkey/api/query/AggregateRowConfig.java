package org.labkey.api.query;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 2/10/12
 * Time: 12:36 PM
 */
public class AggregateRowConfig
{
    private boolean _aggregateRowFirst;
    private boolean _aggregateRowLast;

    public AggregateRowConfig()
    {
        _aggregateRowFirst = false;
        _aggregateRowLast = true;
    }

    public AggregateRowConfig(boolean first, boolean last)
    {
        _aggregateRowFirst = first;
        _aggregateRowLast = last;
    }

    public boolean getAggregateRowFirst()
    {
        return _aggregateRowFirst;
    }

    public void setAggregateRowFirst(boolean first)
    {
        _aggregateRowFirst = first;
    }

    public boolean getAggregateRowLast()
    {
        return _aggregateRowLast;
    }

    public void setAggregateRowLast(boolean last)
    {
        _aggregateRowLast = last;
    }
}
