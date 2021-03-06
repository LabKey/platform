/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.StatsService;

/**
 * Created by klum on 10/10/2014.
 */
public class NoCurveFit extends DefaultCurveFit<CurveFit.Parameters> implements CurveFit<CurveFit.Parameters>
{
    public NoCurveFit(DoublePoint[] data)
    {
        super(data);
    }

    @Override
    public StatsService.CurveFitType getType()
    {
        return StatsService.CurveFitType.NONE;
    }

    @Override
    protected Parameters computeParameters()
    {
        return null;
    }

    @Override
    public double fitCurve(double x)
    {
        return 0;
    }

    @Override
    public double solveForX(double y)
    {
        return 0;
    }

    @Override
    public double fitCurve(double x, Parameters parameters)
    {
        return 0;
    }
}
