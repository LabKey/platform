/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.statistics.CurveFit;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.study.Plate;
import org.labkey.api.study.Position;
import org.labkey.api.study.WellData;
import org.labkey.api.study.WellGroup;

import java.util.List;

/**
 * User: brittp
 * Date: Oct 25, 2006
 * Time: 4:58:12 PM
 */
public interface DilutionCurve
{
    DoublePoint[] getCurve() throws FitFailedException;

    CurveFit.Parameters getParameters() throws FitFailedException;

    double getFitError() throws FitFailedException;

    double getCutoffDilution(double percent) throws FitFailedException;

    double getInterpolatedCutoffDilution(double percent);

    double getMinDilution() throws FitFailedException;

    double getMaxDilution() throws FitFailedException;

    double fitCurve(double x, CurveFit.Parameters curveParameters);

    double calculateAUC(StatsService.AUCType type) throws FitFailedException;

    /**
     * Is there enough data to render a dilution curve
     * @return
     */
    boolean isValid();

    interface PercentCalculator
    {
        double getPercent(WellGroup group, WellData data) throws FitFailedException;

        /**
         * Returns the WellGroup for the cell control wells. An optional list of positions can be specified for the
         * case where control wells may be specific to a particular virus well group (or other type of well group).
         * The positions describe the sample well locations that the control wells are sought for.
         *
         * For the case where control wells have no affinity to other well groups on the plate, null can be
         * specified for the data positions and by default all cell control wells will be returned.
         */
        @Nullable
        WellGroup getCellControlWells(Plate plate, @Nullable List<Position> dataPositions);

        /**
         * Returns the WellGroup for the virus control wells. An optional list of positions can be specified for the
         * case where control wells may be specific to a particular virus well group (or other type of well group).
         * The positions describe the sample well locations that the control wells are sought for.
         *
         * For the case where control wells have no affinity to other well groups on the plate, null can be
         * specified for the data positions and by default all virus control wells will be returned.
         */
        @Nullable
        WellGroup getVirusControlWells(Plate plate, @Nullable List<Position> dataPositions);
    }
}
