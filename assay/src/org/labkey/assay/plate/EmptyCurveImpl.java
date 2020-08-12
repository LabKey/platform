/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.assay.plate;

import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;

import java.util.List;

/**
 * Created by klum on 10/10/2014.
 */
public class EmptyCurveImpl extends WellGroupCurveImpl
{
    public EmptyCurveImpl(List<WellGroup> wellGroups, boolean assumeDecreasing, PercentCalculator percentCalculator) throws FitFailedException
    {
        super(wellGroups, assumeDecreasing, percentCalculator, StatsService.CurveFitType.NONE);
    }

    @Override
    protected CurveFit createCurveFit(StatsService.CurveFitType type) throws FitFailedException
    {
        ensureWellSummaries();
        DoublePoint[] data = new DoublePoint[_wellSummaries.length];
        int i=0;
        for (WellSummary well : _wellSummaries)
        {
            // we want to express the neutralization values as percentages
            data[i++] = new DoublePoint(well.getDilution(), well.getNeutralization() * 100);
        }
        StatsService service = StatsService.get();
        return service.getCurveFit(type, data);
    }
}
