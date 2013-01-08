/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

package org.labkey.visualization;

import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.reports.ReportService;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.visualization.GenericChartReportDescriptor;
import org.labkey.api.visualization.TimeChartReportDescriptor;
import org.labkey.visualization.report.GenericChartReportImpl;
import org.labkey.visualization.report.TimeChartReportImpl;
import org.labkey.visualization.report.VisualizationUIProvider;

import java.util.Collection;
import java.util.Collections;

public class VisualizationModule extends DefaultModule
{
    public static final String NAME = "Visualization";
    public static final String EXPERIMENTAL_BOXPLOT = "experimental-boxplot";

    public String getName()
    {
        return NAME;
    }

    public double getVersion()
    {
        return 12.30;
    }

    public boolean hasScripts()
    {
        return false;
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    protected void init()
    {
        addController(VisualizationController.NAME, VisualizationController.class);
        ReportService.get().registerDescriptor(new TimeChartReportDescriptor());
        ReportService.get().registerDescriptor(new GenericChartReportDescriptor());

        ReportService.get().registerReport(new TimeChartReportImpl());
        ReportService.get().registerReport(new GenericChartReportImpl());

        ReportService.get().addUIProvider(new VisualizationUIProvider());
    }

    public void doStartup(ModuleContext moduleContext)
    {
    }
}