/*
 * Copyright (c) 2014-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.data.queryprofiler;

import org.labkey.api.util.Formats;

import java.util.Comparator;

/**
 * Comparator that allows defining a primary and secondary sort order, and ensures the Set
 * "consistent with equals" requirement. If we didn't include the SQL in the comparison, the set would reject new
 * queries where a statistic happens to match the value of that statistic in an existing query.
 * User: jeckels
 * Date: 2/13/14
 */
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
