package org.labkey.visualization.report;

import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SimpleAction;
import org.labkey.api.view.HBox;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.visualization.TimeChartReport;
import org.labkey.visualization.VisualizationController;
import org.labkey.visualization.VisualizationModule;

/**
 * Copyright (c) 2011 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * <p/>
 * User: brittp
 * Date: Feb 7, 2011 11:23:05 AM
 */
public class TimeChartReportImpl extends TimeChartReport
{
    @Override
    public HttpView renderReport(ViewContext context) throws Exception
    {
        Module vizModule = ModuleLoader.getInstance().getModule(VisualizationModule.class);
        WebPartView timeChartWizard = SimpleAction.getModuleHtmlView(vizModule, VisualizationController.VisualizationUrlsImpl.TIME_CHART_VIEW_NAME);
        timeChartWizard.setFrame(WebPartView.FrameType.NONE);
        return new HBox(timeChartWizard);
    }
}
