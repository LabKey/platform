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
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.WellGroup;

import java.util.List;

/**
 * User: klum
 * Date: Sep 2, 2009
 */
public class PolynomialCurveImpl extends WellGroupCurveImpl
{
    private CurveFit _curveFit;

    public PolynomialCurveImpl(List<WellGroup> wellGroups, boolean assumeDecreasing, PercentCalculator percentCalculator) throws FitFailedException
    {
        super(wellGroups, assumeDecreasing, percentCalculator);

        ensureWellSummaries();
        DoublePoint[] data = new DoublePoint[_wellSummaries.length];
        int i=0;
        for (WellSummary well : _wellSummaries)
        {
            // we want to express the neutralization values as percentages
            data[i++] = new DoublePoint(well.getDilution(), well.getNeutralization() * 100);
        }
        StatsService service = ServiceRegistry.get().getService(StatsService.class);
        _curveFit = service.getCurveFit(StatsService.CurveFitType.POLYNOMIAL, data);
    }

    protected DoublePoint[] renderCurve() throws FitFailedException
    {
        return _curveFit.renderCurve(CURVE_SEGMENT_COUNT);
    }

    public double fitCurve(double x, CurveFit.Parameters curveParameters)
    {
        return _curveFit.fitCurve(x);
    }

    public CurveFit.Parameters getParameters() throws FitFailedException
    {
        return _curveFit.getParameters();
    }

    @Override
    public double getFitError()
    {
        return _curveFit.getFitError();
    }

    @Override
    public double calculateAUC(StatsService.AUCType type) throws FitFailedException
    {
        return _curveFit.calculateAUC(type);
    }
}
