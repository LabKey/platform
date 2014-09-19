/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.settings.ResourceURL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import java.util.List;

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

    public static enum RenderType
    {
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
                return "box_plot.png";
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
                return "scatter_plot.png";
            }
        },
        AUTO_PLOT()
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
                return "box_plot.png";
            }
        };
        public abstract String getId();
        public abstract String getName();
        public abstract String getDescription();
        public abstract String getIconPath();
        public abstract String getThumbnailName();
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

    /**
     * Create a menu item for the auto chart option based on the specified column type
     * @return
     */
    public static NavTree getQuickChartItem(String rgnName, ViewContext context, List<DisplayColumn> columns, ColumnInfo col, QuerySettings settings)
    {
        VisualizationUrls urlProvider = PageFlowUtil.urlProvider(VisualizationUrls.class);
        if (urlProvider != null && settings != null && settings.getSchemaName() != null && settings.getQueryName() != null)
        {
            boolean quickChartDisabled = BooleanUtils.toBoolean(context.getActionURL().getParameter(rgnName + ".quickChartDisabled"));

            if (!quickChartDisabled)
            {
                if (col.getFk() == null)
                {
                    Class cls = col.getJavaObjectClass();

                    if (Number.class.isAssignableFrom(cls))
                    {
                        RenderType type = RenderType.AUTO_PLOT;

                        ActionURL plotURL = urlProvider.getGenericChartDesignerURL(context.getContainer(), context.getUser(), settings, type).addParameter("autoColumnYName", col.getName());

                        NavTree navItem = new NavTree("Quick Chart");

                        navItem.setImageSrc(new ResourceURL(type.getIconPath()));
                        navItem.setHref(plotURL.getLocalURIString());

                        return navItem;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public ActionURL getRunReportURL(ViewContext context)
    {
        ActionURL url =  super.getRunReportURL(context);
        url.addParameter("edit", false);
        return url;
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
