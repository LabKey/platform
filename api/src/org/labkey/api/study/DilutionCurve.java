/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.study;

import org.labkey.common.util.Pair;

/**
 * User: brittp
 * Date: Oct 25, 2006
 * Time: 4:58:12 PM
 */
public interface DilutionCurve
{
    public interface Parameters
    {
        double getAsymmetry();

        double getInflection();

        double getSlope();

        double getMax();

        double getMin();
    }

    public class FitFailedException extends Exception
    {
        public FitFailedException(String message)
        {
            super(message);
        }
    }

    public enum FitType
    {
        FOUR_PARAMETER("Four Parameter"),
        FIVE_PARAMETER("Five Parameter");

        private String _label;
        private FitType(String label)
        {
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }

        public static FitType fromLabel(String label)
        {
            for (FitType type : values())
            {
                if (type.getLabel().equals(label))
                    return type;
            }
            return null;
        }
    }

    DoublePoint[] getCurve() throws FitFailedException;

    Parameters getParameters() throws FitFailedException;

    double getFitError() throws FitFailedException;

    double getCutoffDilution(double percent) throws FitFailedException;

    double getInterpolatedCutoffDilution(double percent);

    double getMinDilution() throws FitFailedException;

    double getMaxDilution() throws FitFailedException;

    public static class DoublePoint extends Pair<Double, Double>
    {
        public DoublePoint(double x, double y)
        {
            super(x, y);
        }

        public double getX()
        {
            return getKey();
        }

        public double getY()
        {
            return getValue();
        }
    }

    public static interface PercentCalculator
    {
        double getPercent(WellGroup group, WellData data) throws FitFailedException;
    }
}
