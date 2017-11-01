/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.visualization;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

/**
 * User: klum
 * Date: May 9, 2012
 */

/**
 * Generic javascript report which uses the client side apis developed over d3/raphael to
 * create box, scatter, bar charts.
 */
public abstract class GenericChartReport extends AbstractReport
{
    public static final String TYPE = "ReportService.GenericChartReport";

    public enum RenderType
    {
        BAR_PLOT()
        {
            @Override
            public String getId()
            {
                return "bar_chart";
            }
            @Override
            public String getName()
            {
                return "Bar Chart";
            }
            @Override
            public String getDescription()
            {
                return "Bar Chart";
            }
            @Override
            public String getIconPath()
            {
                return "/visualization/report/box_plot.gif";
            }
            @Override
            public String getThumbnailName()
            {
                return "barchart.png";
            }
            @Override
            public String getIconCls()
            {
                return "fa fa-bar-chart";
            }
            @Override
            public boolean isActive()
            {
                return true;
            }
        },
        BOX_PLOT()
        {
            @Override
            public String getId()
            {
                return "box_plot";
            }
            @Override
            public String getName()
            {
                return "Box Plot";
            }
            @Override
            public String getDescription()
            {
                return "Box and Whisker Plot";
            }
            @Override
            public String getIconPath()
            {
                return "/visualization/report/box_plot.gif";
            }
            @Override
            public String getThumbnailName()
            {
                return "boxplot.png";
            }
            @Override
            public String getIconCls()
            {
                return "fa fa-sliders fa-rotate-90";
            }
            @Override
            public boolean isActive()
            {
                return true;
            }
        },
        LINE_PLOT()
        {
            @Override
            public String getId()
            {
                return "line_plot";
            }
            @Override
            public String getName()
            {
                return "Line Plot";
            }
            @Override
            public String getDescription()
            {
                return "XY Series Line Plot";
            }
            @Override
            public String getIconPath()
            {
                return "/visualization/report/timechart.gif";
            }
            @Override
            public String getThumbnailName()
            {
                return "timechart.png";
            }
            @Override
            public String getIconCls()
            {
                return "fa fa-line-chart";
            }
            @Override
            public boolean isActive()
            {
                return true;
            }
        },
        PIE_CHART()
        {
            @Override
            public String getId()
            {
                return "pie_chart";
            }
            @Override
            public String getName()
            {
                return "Pie Chart";
            }
            @Override
            public String getDescription()
            {
                return "Pie Chart";
            }
            @Override
            public String getIconPath()
            {
                return "/visualization/report/box_plot.gif";
            }
            @Override
            public String getThumbnailName()
            {
                return "piechart.png";
            }
            @Override
            public String getIconCls()
            {
                return "fa fa-pie-chart";
            }
            @Override
            public boolean isActive()
            {
                return true;
            }
        },
        SCATTER_PLOT()
        {
            @Override
            public String getId()
            {
                return "scatter_plot";
            }
            @Override
            public String getName()
            {
                return "Scatter Plot";
            }
            @Override
            public String getDescription()
            {
                return "XY Scatter Plot";
            }
            @Override
            public String getIconPath()
            {
                return "/visualization/report/scatter_plot.gif";
            }
            @Override
            public String getThumbnailName()
            {
                return "scatterplot.png";
            }
            @Override
            public String getIconCls()
            {
                return "fa fa-area-chart";
            }
            @Override
            public boolean isActive()
            {
                return true;
            }
        },
        AUTO_PLOT() // deprecated - but need to keep for any saved reports that have this type in their saved config
        {
            @Override
            public String getId()
            {
                return "auto_plot";
            }
            @Override
            public String getName()
            {
                return "Auto Plot";
            }
            @Override
            public String getDescription()
            {
                return "Automatic Plot";
            }
            @Override
            public String getIconPath()
            {
                return "/visualization/report/box_plot.gif";
            }
            @Override
            public String getThumbnailName()
            {
                return "boxplot.png";
            }
            @Override
            public String getIconCls()
            {
                return "fa fa-sliders fa-rotate-90";
            }
            @Override
            public boolean isActive()
            {
                return false;
            }
        };
        public abstract String getId();
        public abstract String getName();
        public abstract String getDescription();
        public abstract String getIconPath(); // TODO I believe this is deprecated, can it be removed all together?
        public abstract String getIconCls();
        public abstract String getThumbnailName();
        public abstract boolean isActive();
    }

    public static RenderType getRenderType(String typeId)
    {
        for (RenderType type : RenderType.values())
        {
            if (type.getId().equals(typeId))
                return type;
        }
        return null;
    }

    public @Nullable RenderType getRenderType()
    {
        ReportDescriptor descriptor = getDescriptor();

        if (descriptor != null)
        {
            String id = descriptor.getProperty(GenericChartReportDescriptor.Prop.renderType);
            return getRenderType(id);
        }

        return null;
    }

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public String getTypeDescription()
    {
        RenderType type = getRenderType();

        return null != type ? type.getDescription() : "Generic Chart Report";
    }

    @Override
    public String getDescriptorType()
    {
        return GenericChartReportDescriptor.TYPE;
    }

    @Override
    public ActionURL getEditReportURL(ViewContext context)
    {
        ActionURL url = super.getRunReportURL(context);
        url.addParameter("edit", true);
        return url;
    }

    @Override
    public void afterImport(Container container, User user)
    {
        GenericChartReportDescriptor descriptor = (GenericChartReportDescriptor) getDescriptor();

        if (descriptor.getJSON() != null)
        {
            descriptor.updateSaveConfig();
        }
    }
}
