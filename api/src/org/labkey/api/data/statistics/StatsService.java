package org.labkey.api.data.statistics;

import org.labkey.api.view.Stats;

import java.util.Set;

/**
 * Created by klum on 1/14/14.
 */
public interface StatsService
{
    /**
     * Factory to return a statistics instance for the specified data
     * @param data an array of data to compute statistics over
     * @return
     */
    MathStat getStats(double[] data);
}
