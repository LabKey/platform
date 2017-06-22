/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.stats.AnalyticsProviderRegistry;
import org.labkey.api.module.CodeOnlyModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.reports.ReportService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.visualization.GenericChartReportDescriptor;
import org.labkey.api.visualization.TimeChartReportDescriptor;
import org.labkey.api.visualization.VisualizationService;
import org.labkey.visualization.report.DimensionBarChartAnalyticsProvider;
import org.labkey.visualization.report.DimensionPieChartAnalyticsProvider;
import org.labkey.visualization.report.GenericChartReportImpl;
import org.labkey.visualization.report.MeasureBoxPlotAnalyticsProvider;
import org.labkey.visualization.report.QuickChartAnalyticsProvider;
import org.labkey.visualization.report.TimeChartReportImpl;
import org.labkey.visualization.report.VisualizationUIProvider;
import org.labkey.visualization.sql.VisualizationCDSGenerator;
import org.labkey.visualization.sql.VisualizationSQLGenerator;
import org.labkey.visualization.test.VisTestSchema;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class VisualizationModule extends CodeOnlyModule
{
    public static final String NAME = "Visualization";

    @Override
    public String getName()
    {
        return NAME;
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }


    @NotNull
    @Override
    public Set<Class> getIntegrationTests()
    {
        Set<Class> set = new LinkedHashSet<>();
        set.add(VisualizationController.TestCase.class);
        set.add(VisualizationSQLGenerator.GetDataTestCase.class);
        set.add(VisualizationCDSGenerator.TestCase.class);
        return set;
    }

    @Override
    protected void init()
    {
        DefaultSchema.registerProvider("vis_junit", new DefaultSchema.SchemaProvider(this)
        {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                return schema.getContainer().getParsedPath().equals(JunitUtil.getTestContainerPath());
            }

            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new VisTestSchema(schema.getUser(), schema.getContainer());
            }
        });


        addController(VisualizationController.NAME, VisualizationController.class);
        ReportService.get().registerDescriptor(new TimeChartReportDescriptor());
        ReportService.get().registerDescriptor(new GenericChartReportDescriptor());

        ReportService.get().registerReport(new TimeChartReportImpl());
        ReportService.get().registerReport(new GenericChartReportImpl());

        ReportService.get().addUIProvider(new VisualizationUIProvider());

        ServiceRegistry.get().registerService(VisualizationService.class, new VisualizationServiceImpl());
    }


    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        AnalyticsProviderRegistry analyticsProviderRegistry = ServiceRegistry.get().getService(AnalyticsProviderRegistry.class);
        if (null != analyticsProviderRegistry)
        {
            analyticsProviderRegistry.registerProvider(new MeasureBoxPlotAnalyticsProvider());
            analyticsProviderRegistry.registerProvider(new DimensionPieChartAnalyticsProvider());
            analyticsProviderRegistry.registerProvider(new DimensionBarChartAnalyticsProvider());
            analyticsProviderRegistry.registerProvider(new QuickChartAnalyticsProvider());
        }
    }
}