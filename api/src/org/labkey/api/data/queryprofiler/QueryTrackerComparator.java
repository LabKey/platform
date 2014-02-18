package org.labkey.api.data.queryprofiler;

import org.labkey.api.util.Formats;

import java.util.Comparator;

/**
* User: jeckels
* Date: 2/13/14
*/ // Comparator that allows defining a primary and secondary sort order, and ensures the Set
// "consistent with equals" requirement.  If we didn't compare the sql, the set would reject new
// queries where a statistic happens to match the value of that statistic in an existing query.
abstract class QueryTrackerComparator implements Comparator<QueryTracker>
{
    public int compare(QueryTracker qt1, QueryTracker qt2)
    {
        // Can use simple subtraction here since we won't have MAX_VALUE, MIN_VALUE, etc.
        int ret = Long.signum(getPrimaryStatisticValue(qt1) - getPrimaryStatisticValue(qt2));

        if (0 == ret)
            ret = Long.signum(getSecondaryStatisticValue(qt1) - getSecondaryStatisticValue(qt2));

        if (0 == ret)
            ret = qt1.getSql().compareTo(qt2.getSql());

        return ret;
    }

    String getFormattedPrimaryStatistic(QueryTracker qt)
    {
        return Formats.commaf0.format(getPrimaryStatisticValue(qt));
    }

    abstract long getPrimaryStatisticValue(QueryTracker qt);
    abstract long getSecondaryStatisticValue(QueryTracker qt);
}
