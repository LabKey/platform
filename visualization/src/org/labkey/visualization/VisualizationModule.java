/*
 * Copyright (c) 2011-2015 LabKey Corporation
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
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.reports.ReportService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.StartupListener;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.visualization.GenericChartReportDescriptor;
import org.labkey.api.visualization.TimeChartReportDescriptor;
import org.labkey.api.visualization.VisualizationService;
import org.labkey.visualization.report.GenericChartReportImpl;
import org.labkey.visualization.report.TimeChartReportImpl;
import org.labkey.visualization.report.VisualizationUIProvider;
import org.labkey.visualization.sql.VisualizationSQLGenerator;

import javax.servlet.ServletContext;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class VisualizationModule extends DefaultModule
{
    public static final String NAME = "Visualization";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 15.10;
    }

    @Override
    public boolean hasScripts()
    {
        return false;
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
        set.add(VisualizationSQLGenerator.TestCase.class);
        return set;
    }

    @Override
    protected void init()
    {
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
    }


    @Override
    public void afterUpdate(final ModuleContext moduleContext) {
        if (!moduleContext.isNewInstall() && moduleContext.getOriginalVersion() < 13.31)
        {
            ContextListener.addStartupListener(new StartupListener()
            {
                @Override
                public String getName()
                {
                    return "Visualization: upgrade saved json for Generic Charts";
                }

                @Override
                public void moduleStartupComplete(ServletContext servletContext)
                {
                    VisualizationUpgradeCode.upgradeGenericChartSaveConfig(moduleContext);
                }
            });
        }
    }
}