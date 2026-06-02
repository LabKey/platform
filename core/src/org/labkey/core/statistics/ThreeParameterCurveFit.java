/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.core.statistics;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.statistics.DoublePoint;

import static org.labkey.api.data.statistics.StatsService.CurveFitType.THREE_PARAMETER;

/* Based on the equation from SigmaPlot */
public class ThreeParameterCurveFit extends ParameterCurveFit
{
    public ThreeParameterCurveFit(DoublePoint[] data, @Nullable Double asymptoteMax)
    {
        super(data, THREE_PARAMETER, 0.0, asymptoteMax);
    }

    @Override
    public double solveForX(double y)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public double fitCurve(double x, SigmoidalParameters params)
    {
        if (params != null)
        {
            if (x <= 0)
            {
                if (params.getSlope() < 0)
                    return 0;
                else
                    return params.getMax();
            }
            else
            {
                if (params.getSlope() > 0)
                    return params.getMax() / (1 + Math.pow(Math.abs(x / params.getInflection()), params.getSlope()));
                else
                    return params.getMax() * Math.pow(Math.abs(x / params.getInflection()), Math.abs(params.getSlope())) /
                            (1 + Math.pow(Math.abs(x / params.getInflection()), Math.abs(params.getSlope())));
            }

        }
        throw new IllegalArgumentException("No curve fit parameters for " + _fitType.name());
    }
}
