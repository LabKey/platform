package org.labkey.api.data.statistics;

import org.labkey.api.view.Stats;

/**
 * Created by klum on 1/16/14.
 */
public interface MathStat
{
    int getCount();
    double getMean();
    double getGeometricMean();
    double getMinimum();
    double getMaximum();

    String getFormattedStat(Stats.StatDefinition stat);

    double getStdDev();
    double getVar();
    double getSum();
    double getMedian();
    double getMode();
    double getPercentile(double percentile);

    /**
     * <pre>MAD = median( { | x_i - median | } ) * 1.4826</pre>
     */
    double getMedianAbsoluteDeviation();
}
