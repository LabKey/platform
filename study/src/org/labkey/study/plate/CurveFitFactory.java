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

package org.labkey.study.plate;

import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.assay.dilution.EmptyCurveImpl;
import org.labkey.api.assay.dilution.ParameterCurveImpl;
import org.labkey.api.assay.dilution.PolynomialCurveImpl;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.study.WellGroup;

import java.util.Collections;
import java.util.List;

/**
 * User: klum
 * Date: Sep 2, 2009
 */
public class CurveFitFactory
{
    private CurveFitFactory(){}

    public static DilutionCurve getCurveImpl(WellGroup wellGroup, boolean assumeDecreasing, DilutionCurve.PercentCalculator percentCalculator, StatsService.CurveFitType type) throws FitFailedException
    {
        return getCurveImpl(Collections.singletonList(wellGroup), assumeDecreasing, percentCalculator, type);
    }

    public static DilutionCurve getCurveImpl(List<WellGroup> wellGroups, boolean assumeDecreasing, DilutionCurve.PercentCalculator percentCalculator, StatsService.CurveFitType type) throws FitFailedException
    {
        switch (type)
        {
            case FOUR_PARAMETER:
                return new ParameterCurveImpl.FourParameterCurve(wellGroups, assumeDecreasing, percentCalculator);
            case FIVE_PARAMETER:
                return new ParameterCurveImpl.FiveParameterCurve(wellGroups, assumeDecreasing, percentCalculator);
            case POLYNOMIAL:
                return new PolynomialCurveImpl(wellGroups, assumeDecreasing, percentCalculator);
            case NONE:
                return new EmptyCurveImpl(wellGroups, assumeDecreasing, percentCalculator);
        }
        throw new IllegalArgumentException("Unable to find a DilutionCurve implementation for type: " + type.getLabel());
    }
}
