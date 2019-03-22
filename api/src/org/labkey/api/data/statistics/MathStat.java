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
     * <pre>MAD = median( { | x_i - median | } )</pre>
     */
    double getMedianAbsoluteDeviation();
}
