/*
 * Copyright (c) 2014-2026 LabKey Corporation
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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
        // TODO see updated labels for these equations in genericChartHelper.js TRENDLINE_OPTIONS
        // we should update the labels here as well at some point (but this would like need to include an upgrade script for saved chart configs)
        THREE_PARAMETER("Three Parameter", "3pl", Arrays.asList("min", "max", "inflection")),
        FOUR_PARAMETER("Four Parameter", "4pl", Arrays.asList("min", "max", "slope", "inflection")),
        FIVE_PARAMETER("Five Parameter", "5pl", Arrays.asList("min", "max", "slope", "inflection", "asymmetry")),
        THREE_PARAMETER_ALT("3 Parameter", "3param", Arrays.asList("min", "max", "inflection")),
        FOUR_PARAMETER_SIMPLEX("4 Parameter", "4param", Arrays.asList("min", "max", "slope", "inflection")),
        FIVE_PARAMETER_ALT("5 Parameter", "5param", Arrays.asList("min", "max", "slope", "inflection", "asymmetry")),
        POLYNOMIAL("Polynomial", "poly", Arrays.asList("coefficients")),
        LINEAR("Linear", "linear", Arrays.asList("slope", "intercept")),
        NONE("None", "none", Arrays.asList());

        private final String _label;
        private final String _colSuffix;
        private final List<String> _parameterNames = new ArrayList<>();

        CurveFitType(String label, String colSuffix, List<String> parameterNames)
        {
            _label = label;
            _colSuffix = colSuffix;
            _parameterNames.addAll(parameterNames);
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

        public List<String> getParameterNames()
        {
            return _parameterNames;
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

        private final String _label;

        AUCType(String label)
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
    CurveFit getCurveFit(CurveFitType type, DoublePoint[] data, @Nullable Double asymptoteMin, @Nullable Double asymptoteMax);
}
