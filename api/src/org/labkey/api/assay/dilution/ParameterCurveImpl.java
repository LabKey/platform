/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

package org.labkey.api.assay.dilution;

import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.WellGroup;

import java.util.List;

/**
 * User: klum
 * Date: Sep 2, 2009
 */
public abstract class ParameterCurveImpl extends WellGroupCurveImpl
{
    public ParameterCurveImpl(List<? extends WellGroup> wellGroups, boolean assumeDecreasing, DilutionCurve.PercentCalculator percentCalculator, StatsService.CurveFitType fitType) throws FitFailedException
    {
        super(wellGroups, assumeDecreasing, percentCalculator, fitType);
    }

    @Override
    protected CurveFit createCurveFit(StatsService.CurveFitType fitType) throws FitFailedException
    {
        ensureWellSummaries();
        DoublePoint[] data = new DoublePoint[_wellSummaries.length];
        int i=0;
        for (WellSummary well : _wellSummaries)
        {
            // we want to express the neutralization values as percentages
            data[i++] = new DoublePoint(well.getDilution(), well.getNeutralization() * 100);
        }
        StatsService service = ServiceRegistry.get().getService(StatsService.class);

        CurveFit curveFit = service.getCurveFit(fitType, data);
        curveFit.setAssumeCurveDecreasing(assumeDecreasing);

        return curveFit;
    }

    public static class FourParameterCurve extends ParameterCurveImpl
    {
        public FourParameterCurve(List<? extends WellGroup> wellGroups, boolean assumeDecreasing, DilutionCurve.PercentCalculator percentCalculator) throws FitFailedException
        {
            super(wellGroups, assumeDecreasing, percentCalculator, StatsService.CurveFitType.FOUR_PARAMETER);
        }
    }

    public static class FiveParameterCurve extends ParameterCurveImpl
    {
        public FiveParameterCurve(List<? extends WellGroup> wellGroups, boolean assumeDecreasing, DilutionCurve.PercentCalculator percentCalculator) throws FitFailedException
        {
            super(wellGroups, assumeDecreasing, percentCalculator, StatsService.CurveFitType.FIVE_PARAMETER);
        }
    }
}
