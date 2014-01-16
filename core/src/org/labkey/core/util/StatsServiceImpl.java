package org.labkey.core.util;

import org.labkey.api.data.statistics.MathStat;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.view.Stats;

/**
 * Created by klum on 1/14/14.
 */
public class StatsServiceImpl implements StatsService
{
    @Override
    public MathStat getStats(double[] data)
    {
        return new Stats.DoubleStats(data);
    }
}
