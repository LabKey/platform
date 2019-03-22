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
package org.labkey.api.data.statistics;

import org.labkey.api.assay.dilution.DilutionCurve;

import java.util.Map;

/**
 * Created by klum on 1/16/14.
 */
public interface CurveFit
{
    interface Parameters
    {
        /**
         * Returns a map representation of the parameters, used for
         * serialization of parameter information in the Client API
         */
        public Map<String, Object> toMap();
    }

    /**
     * Sets the data that this curve fit will be applied to.
     * @param data
     */
    void setData(DoublePoint[] data);

    /**
     * Returns the data associated with this curve fit.
     * @return
     */
    DoublePoint[] getData();

    /**
     * Returns the parameters necessary to represent the fitted curve
     * @return
     */
    Parameters getParameters() throws FitFailedException;

    /**
     * Sets whether the curve is assumed to be decreasing by default.  It's used as an optimization during 4&5
     * parameter curve fitting and used only if the data points are too chaotic to provide a reasonable guess.
     * @param decreasing the default value is true
     */
    void setAssumeCurveDecreasing(boolean decreasing);

    /**
     * Sets whether all calculations and points generated are performed using a log base 10 X axis scale. By default
     * this is set to true.
     * @param logXScale
     */
    void setLogXScale(boolean logXScale);

    /**
     * Returns the corresponding y value for the specified value on the x axis using
     * the passed in curve fit parameters
     * @param x
     * @return
     */
    double fitCurve(double x);
    double fitCurve(double x, Parameters parameters);

    /**
     * Calculates the fit error : r squared (or coefficient of determination) of the fitted curve
     * @return
     */
    double getFitError() throws FitFailedException;

    /**
     * Returns the array of x/y pairs to render the fitted curve. The min and max x range
     * will be calculated as the min and max of the data currently applied to this curve fit.
     *
     * @param totalPoints The number of points in the array to return
     */
    DoublePoint[] renderCurve(int totalPoints) throws FitFailedException;

    /**
     * Returns the array of x/y pairs to render the fitted curve. The min and max x range
     * will be calculated as the min and max of the data currently applied to this curve fit.
     *
     * @param totalPoints The number of points in the array to return
     * @param startX The starting x value to begin generating points for
     * @param endX The ending x value to generate points for
     */
    DoublePoint[] renderCurve(int totalPoints, double startX, double endX) throws FitFailedException;

    /**
     * Calculates the area under the curve represented by this curve fit. The type of AUC calculation can be configured
     * to be either {@code NORMAL}, {@code POSITIVE}, or {@code NEGATIVE}.
     *
     * The min and max x range
     * will be calculated as the min and max of the data currently applied to this curve fit.
     *
     * @param type
     *          <p>{@code NORMAL} - the AUC is computed for both positive and negative segments of the curve.
     *          <br>{@code POSITIVE} - AUC is computed only for the segments of the curve where the y value is positive.
     *          <br>{@code NEGATIVE} - AUC is computed only for the segments of the curve where the y value is negative.
     *
     * @return The integrated area under the curve.
     */
    double calculateAUC(StatsService.AUCType type) throws FitFailedException;

    /**
     * Calculates the area under the curve represented by this curve fit. The type of AUC calculation can be configured
     * to be either {@code NORMAL}, {@code POSITIVE}, or {@code NEGATIVE}.
     *
     * @param type
     *          <p>{@code NORMAL} - the AUC is computed for both positive and negative segments of the curve.
     *          <br>{@code POSITIVE} - AUC is computed only for the segments of the curve where the y value is positive.
     *          <br>{@code NEGATIVE} - AUC is computed only for the segments of the curve where the y value is negative.
     *
     * @param startX The beggining x position on the curve to start the computation.
     * @param endX The ending x position on the curve to compute AUC.
     * @return The integrated area under the curve.
     */
    double calculateAUC(StatsService.AUCType type, double startX, double endX) throws FitFailedException;
}
