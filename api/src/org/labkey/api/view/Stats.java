/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
package org.labkey.api.view;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.statistics.MathStat;
import org.labkey.api.util.PageFlowUtil;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: migra
 * Date: Mar 3, 2006
 * Time: 8:42:04 AM
 */
public abstract class Stats
{
    public abstract int getCount();

    public abstract Object getMin();

    public abstract Object getMax();

    public abstract Object getStat(StatDefinition stat);

    public abstract String getFormattedStat(StatDefinition stat);

    protected Set<StatDefinition> requestedStats;

    public static Stats getStats(Object[] data, Set<StatDefinition> requestedStats)
    {
        if (data instanceof String[])
            return new StringStats((String[]) data, requestedStats);
        else if (data instanceof Number[])
        {
            double[] doubleData = new double[data.length];
            Number[] numData = (Number[]) data;
            for (int i = 0; i < data.length; i++)
                doubleData[i] = data[i] == null ? Double.NaN : numData[i].doubleValue();

            return new DoubleStats(doubleData, requestedStats);
        }
        else
            throw new IllegalArgumentException("Non number/string data handed to stats");
    }

    public static Stats getStats(Object[] data)
    {
        return getStats(data, ALL_STATS);
    }



    protected Stats()
    {
        requestedStats = ALL_STATS;
    }

    protected Stats(Set<StatDefinition> requestedStats)
    {
        this.requestedStats = requestedStats;
    }

    public Set<StatDefinition> getRequestedStats()
    {
        return requestedStats;
    }

    public static interface Statistic
    {
        Object getValue();

        StatDefinition getDefinition();
    }

    public static class StatDefinition
    {
        private String name;

        private StatDefinition(String name)
        {
            this.name = name;
        }

        public String getName()
        {
            return name;
        }

        public boolean equals(Object o)
        {
            return null != o && ((StatDefinition) o).name.equals(name);
        }

        public int hashCode()
        {
            return name.hashCode();
        }

        public String toString()
        {
            return getName();
        }
    }

    public static class PercentileDefinition extends StatDefinition
    {
        private double percentile; //0-1

        public PercentileDefinition(double percentile)
        {
            super("Quantile: " + String.format("%2.0f", percentile) + "%");
            this.percentile = percentile;
        }

        public double getPercentile()
        {
            return percentile;
        }
    }

    public static class MedianDefinition extends PercentileDefinition
    {
        private MedianDefinition()
        {
            super(.5);
        }

        public String getName()
        {
            return "Median";
        }

    }


    public static final StatDefinition COUNT = new StatDefinition("Count");
    public static final StatDefinition SUM = new StatDefinition("Sum");
    public static final StatDefinition MEAN = new StatDefinition("Mean");
    public static final StatDefinition GEOMETRIC_MEAN = new StatDefinition("GeomMean");
    //public static final StatDefinition MODE = new StatDefinition("Mode");
    public static final StatDefinition MIN = new StatDefinition("Min");
    public static final StatDefinition MAX = new StatDefinition("Max");
    public static final StatDefinition STDDEV = new StatDefinition("StdDev");
    public static final StatDefinition VAR = new StatDefinition("Var");
    public static final StatDefinition MEDIAN = new MedianDefinition();
    public static final StatDefinition MEDIAN_ABS_DEV = new StatDefinition("MAD");

    public static final Set<StatDefinition> ALL_STATS = new LinkedHashSet<>();
    public static final Set<StatDefinition> STRING_STATS = new LinkedHashSet<>();
    static
    {
        ALL_STATS.add(COUNT);
        ALL_STATS.add(SUM);
        ALL_STATS.add(MEAN);
        ALL_STATS.add(GEOMETRIC_MEAN);
        //ALL_STATS.add(MODE);
        ALL_STATS.add(MIN);
        ALL_STATS.add(MAX);
        ALL_STATS.add(STDDEV);
        ALL_STATS.add(VAR);
        ALL_STATS.add(MEDIAN);
        ALL_STATS.add(MEDIAN_ABS_DEV);


        STRING_STATS.add(COUNT);
        STRING_STATS.add(MAX);
        STRING_STATS.add(MIN);

    }

    public static StatDefinition getStatFromString(String stat)
    {
        for (StatDefinition def : ALL_STATS)
            if (def.getName().equals(stat))
                return def;

        if (stat.startsWith("percentile("))
        {
            double percentile = Double.parseDouble(stat.substring("percentile(".length(), stat.length() -1));
            return new PercentileDefinition(percentile/100);
        }

        throw new IllegalArgumentException("Unknown stat: " + stat);
    }
    
    public static Set<StatDefinition> statSet(StatDefinition... stats)
    {
        Set<StatDefinition> set = new HashSet<>();
        for (StatDefinition stat : stats)
            set.add(stat);

        return set;
    }

    public static class StringStats extends Stats
    {
        int count;
        String min = null;
        String max = null;

        public StringStats(String[] data, Set<StatDefinition> requestedStatDefinitions)
        {
            super(requestedStatDefinitions);

            for (String str : data)
            {
                if (null != str)
                {
                    if (null == min || str.compareTo(min) < 0)
                        min = str;

                    if (null == max || str.compareTo(max) > 0)
                        max = str;

                    count++;
                }
            }
        }

        public StringStats(String[] data)
        {
            this(data, STRING_STATS);
        }

        public int getCount()
        {
            return count;
        }

        public String getMin()
        {
            return min;
        }

        public String getMax()
        {
            return max;
        }

        public Object getStat(StatDefinition stat)
        {
            if (stat == MIN)
                return getMin();
            if (stat == MAX)
                return getMax();
            if (stat == COUNT)
                return getCount();
            else
                return null;
        }

        public String getFormattedStat(StatDefinition stat)
        {
            if (stat == MIN)
                return StringUtils.trimToEmpty(PageFlowUtil.filter(getMin()));
            if (stat == MAX)
                return StringUtils.trimToEmpty(PageFlowUtil.filter(getMax()));
            if (stat == COUNT)
                return String.valueOf(getCount());
            else
                return "";
        }
    }

    @Deprecated // Use StatsService.getStats(double[]) instead
    public static class DoubleStats extends Stats implements MathStat
    {
        protected double mean;
        protected double min;
        protected double max;
        protected double var;
        protected double stdDev;
        protected double geomMean;
        protected int count;
        protected double sum;
        protected double[] data;
        private boolean sorted = false;
        private DecimalFormat formatter = new DecimalFormat("0.###");

        private Double mode;
        private Double median;
        private Double mad;

        public DoubleStats(double[] data)
        {
            this(data, ALL_STATS);
        }

        public DoubleStats(double[] data, Set<StatDefinition> requestedStats)
        {
            super(requestedStats);
            this.data = data;

            if (null == data || data.length == 0)
            {
                count = 0;
                sum = 0;
                max = Double.NaN;
                min = Double.NaN;
                mean = Double.NaN;
                mode = Double.NaN;
                stdDev = Double.NaN;
                geomMean = Double.NaN;
                return;
            }

            if (data.length == 1)
            {
                mean = data[0];
                mode = data[0];
                stdDev = Double.NaN;
                var = Double.NaN;
                geomMean = Double.NaN;
                min = data[0];
                max = data[0];
                sum = data[0];
                count = Double.isNaN(data[0]) ? 0 : 1;
                return;
            }

            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            final int n = data.length;
            double sumLogs = 0;
            double sumSquares = 0;
            int positiveCount = 0;
            mean = 0.0;
            for (int i = 0; i < n; i++)
            {
                double d = data[i];
                if (Double.isNaN(d))
                    continue;

                count ++;
                sum += d;
                sumSquares += (d * d);
                if (d > 0)
                {
                    positiveCount++;
                    sumLogs += Math.log10(d);
                }
                if (d < min)
                    min = d;
                if (d > max)
                    max = d;
            }
            this.min = min;
            this.max = max;

            // calculate the variance, stardard deviation, and geomtric mean
            if (count > 0)
            {
                mean = sum / count;
            }
            if (count > 1)
            {
                var = (sumSquares - count * (mean * mean)) / (count - 1);
                stdDev = Math.sqrt(var);
                geomMean = Math.pow(10.0d, sumLogs / positiveCount);
            }
        }

        public int getCount()
        {
            return count;
        }

        public double getMean()
        {
            return mean;
        }

        public double getGeometricMean()
        {
            return geomMean;
        }

        public Double getMin()
        {
            return new Double(min);
        }

        public Double getMax()
        {
            return max;
        }

        @Override
        public double getMinimum()
        {
            return min;
        }

        @Override
        public double getMaximum()
        {
            return max;
        }

        public Object getStat(StatDefinition stat)
        {
            if (stat == MIN)
                return getMin();
            if (stat == MAX)
                return getMax();
            if (stat == COUNT)
                return getCount();
            if (stat == STDDEV)
                return getStdDev();
            if (stat == MEAN)
                return getMean();
            if (stat == GEOMETRIC_MEAN)
                return getGeometricMean();
            if (stat == VAR)
                return getVar();
            if (stat == MEDIAN)
                return getMedian();
            //if (stat == MODE)
            //    return getMode();
            if (stat == SUM)
                return getSum();
            if (stat == MEDIAN_ABS_DEV)
                return getMedianAbsoluteDeviation();

            if (stat instanceof PercentileDefinition)
                return getPercentile(((PercentileDefinition) stat).getPercentile());

            throw new IllegalArgumentException("Undefined statistic: " + stat.toString());
        }

        public String getFormattedStat(StatDefinition stat)
        {
            if (stat == COUNT)
                return String.valueOf(getCount());

            if (Double.isNaN((Double)getStat(stat)))
                return "";
            
            return formatter.format(getStat(stat));
        }

        public double getStdDev()
        {
            return stdDev;
        }

        public double getVar()
        {
            return var;
        }

        public double getSum()
        {
            return sum;
        }

        private void ensureSorted()
        {
            if (sorted)
                return;
            Arrays.sort(data);
            sorted = true;
        }


        public double getMedian()
        {
            if (median == null)
            {
                median = _getMedian();
            }
            return median.doubleValue();
        }

        private double _getMedian()
        {
            ensureSorted();

            if (count == 0)
                return Double.NaN;

            if (count == 1)
                return data[0];

            if (count % 2 == 0)
                return (data[count / 2 - 1] + data[count / 2]) / 2;

            return data[(count -1) / 2];
        }

        public double getMode()
        {
            if (mode == null)
            {
                mode = _getMode();
            }
            return mode.doubleValue();
        }

        // UNDONE: Need to bucket into bins before calculating mode
        // CONSIDER: Return multiple values for multi-modal distributions.
        private double _getMode()
        {
            ensureSorted();

            if (count == 0)
                return Double.NaN;

            if (count == 1)
                return data[0];

            int modeCount = 0;
            double modeValue = 0d;

            int currentCount = 1;
            double currentValue = data[0];

            for (int i = 1; i < data.length; i++)
            {
                double nextValue = data[i];
                if (currentValue == nextValue)
                {
                    currentCount++;
                }
                else
                {
                    // Did we find a new mode candidate?
                    if (currentCount > modeCount)
                    {
                        modeCount = currentCount;
                        modeValue = currentValue;
                    }

                    currentCount = 1;
                    currentValue = nextValue;
                }
            }

            return modeValue;
        }

        public double getPercentile(double percentile)
        {
            ensureSorted();

            if (count == 0)
                return Double.NaN;

            int arrayOffset = (int) Math.round(count * percentile);
            if (arrayOffset >= count)
                arrayOffset = count -1;

            return data[arrayOffset];
        }

        /**
         * <pre>MAD = median( { | x_i - median | } )</pre>
         */
        public double getMedianAbsoluteDeviation()
        {
            if (mad == null)
            {
                mad = _getMedianAbsoluteDeviation();
            }
            return mad.doubleValue();
        }

        private double _getMedianAbsoluteDeviation()
        {
            if (count == 0)
                return Double.NaN;

            double median = getMedian();
            double[] diff = new double[data.length];

            int n = data.length;
            for (int i = 0; i < n; i++)
            {
                double d = data[i];
                if (Double.isNaN(d))
                    continue;
                diff[i] = Math.abs(d - median);
            }

            Arrays.sort(diff);

            // get scaled median of the difference
            if (count == 1)
                return diff[0];

            if (count % 2 == 0)
                return ((diff[count / 2 -1 ] + diff[count / 2]) / 2);

            return diff[(count -1) / 2];
        }
    }
}
