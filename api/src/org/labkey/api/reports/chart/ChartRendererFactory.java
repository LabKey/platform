/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.api.reports.chart;

import java.util.HashMap;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: Apr 19, 2007
 */
public class ChartRendererFactory
{
    private static final ChartRendererFactory instance = new ChartRendererFactory();
    private static Map<String, ChartRenderer> _chartRenderers = new HashMap<>();


    public static ChartRendererFactory get()
    {
        return instance;
    }

    private ChartRendererFactory(){}

    public synchronized void addChartRenderer(ChartRenderer renderer)
    {
        _chartRenderers.put(renderer.getType(), renderer);
    }

    public synchronized ChartRenderer getChartRenderer(String type)
    {
        return _chartRenderers.get(type);
    }

    public synchronized ChartRenderer[] getChartRenderers()
    {
        return _chartRenderers.values().toArray(new ChartRenderer[0]);
    }
}
