package org.labkey.api.data.queryprofiler;

import java.util.Comparator;
import java.util.TreeSet;

/**
* User: jeckels
* Date: 2/13/14
*/
public class QueryTrackerSet extends TreeSet<QueryTracker>
{
    public static final int STANDARD_LIMIT = 1000;

    private final String _caption;
    private final String _description;
    private final boolean _stable;   // Is this statistic stable, i.e., will it never change once a QueryTracker has been added to the set?
    private final boolean _display;  // Should we display this statistic in the report?

    QueryTrackerSet(String caption, String description, boolean stable, boolean display, Comparator<? super QueryTracker> comparator)
    {
        super(comparator);
        _caption = caption;
        _description = description;
        _display = display;
        _stable = stable;
    }

    public String getCaption()
    {
        return _caption;
    }

    public String getDescription()
    {
        return _description;
    }

    public boolean shouldDisplay()
    {
        return _display;
    }

    public void beforeUpdate(QueryTracker tracker)
    {
        // If the statistic changes at each update, then we need to remove and re-add it
        if (!_stable)
            remove(tracker);
    }

    public void update(QueryTracker tracker)
    {
        // If the statistic changes at each update, then we need to remove and re-add it
        if (!_stable)
            add(tracker);
    }

    protected int getLimit()
    {
        return STANDARD_LIMIT;
    }

    @Override
    public boolean add(QueryTracker tracker)
    {
        assert size() <= getLimit();

        if (size() == getLimit())
        {
            if (comparator().compare(tracker, first()) < 0)
                return false;

            remove(first());
        }

        return super.add(tracker);
    }

    @Override
    public String toString()
    {
        return getCaption();
    }
}
