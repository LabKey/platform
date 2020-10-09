/*
 * Copyright (c) 2014-2018 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;

import java.awt.geom.Arc2D;
import java.util.Arrays;
import java.util.Collection;

/**
 * Created by klum on 1/14/14.
 */
public interface StatsService
{
    static StatsService get()
    {
        return ServiceRegistry.get().getService(StatsService.class);
    }

    static void setInstance(StatsService impl)
    {
        ServiceRegistry.get().registerService(StatsService.class, impl);
    }

    enum CurveFitType
    {
        FOUR_PARAMETER("Four Parameter", "4pl"),
        FIVE_PARAMETER("Five Parameter", "5pl"),
        FOUR_PARAMETER_SIMPLEX("4 Parameter", "4param"),
        POLYNOMIAL("Polynomial", "poly"),
        LINEAR("Linear", "linear"),
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

        @Override
        public String toString()
        {
            return _label;
        }

        @Nullable
        public static CurveFitType fromLabel(String label)
        {
            return Arrays.stream(values()).filter(type -> type.getLabel().equals(label)).findFirst().orElse(null);
        }

        @Nullable
        public static CurveFitType fromColSuffix(String suffix)
        {
            return Arrays.stream(values()).filter(type -> type.getColSuffix().equals(suffix)).findFirst().orElse(null);
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
    MathStat getStats(Collection<Double> data);

    /**
     * Returns an instance of a curve fit for the specified curve fit type and initialized with
     * the data array.
     * @param type the type of curve fit to generate
     * @param data an array of {@code DoublePoint} instances to initialize the curve fit with.
     */
    CurveFit getCurveFit(CurveFitType type, DoublePoint[] data);
}
