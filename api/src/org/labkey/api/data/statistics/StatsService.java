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

/**
 * Created by klum on 1/14/14.
 */
public interface StatsService
{
    enum CurveFitType
    {
        FOUR_PARAMETER("Four Parameter", "4pl"),
        FIVE_PARAMETER("Five Parameter", "5pl"),
        POLYNOMIAL("Polynomial", "poly"),
        NONE("None", "none");

        private String _label;
        private String _colSuffix;

        private CurveFitType(String label, String colSuffix)
        {
            _label = label;
            _colSuffix = colSuffix;
        }

        // Consider : moving the col suffix portion of this back into assays...

        public String getColSuffix()
        {
            return _colSuffix;
        }

        public String getLabel()
        {
            return _label;
        }

        public static CurveFitType fromLabel(String label)
        {
            for (CurveFitType type : values())
            {
                if (type.getLabel().equals(label))
                    return type;
            }
            return null;
        }

        public static CurveFitType fromColSuffix(String suffix)
        {
            for (CurveFitType type : values())
            {
                if (type.getColSuffix().equals(suffix))
                    return type;
            }
            return null;
        }
    }

    enum AUCType
    {
        NORMAL("Normal"),
        POSITIVE("Positive"),
        NEGATIVE("Negative");

        private String _label;

        private AUCType(String label)
        {
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }
    }

    /**
     * Factory to return a statistics instance for the specified data
     * @param data an array of data to compute statistics over
     * @return
     */
    MathStat getStats(double[] data);

    /**
     * Returns an instance of a curve fit for the specified curve fit type and initialized with
     * the data array.
     * @param type the type of curve fit to generate
     * @param data an array of {@code DoublePoint} instances to initialize the curve fit with.
     */
    CurveFit getCurveFit(CurveFitType type, DoublePoint[] data);
}
