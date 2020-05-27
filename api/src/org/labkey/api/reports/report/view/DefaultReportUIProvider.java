/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
package org.labkey.api.reports.report.view;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.settings.ResourceURL;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.List;

/*
 * User: Karl Lum
 * Date: May 16, 2008
 * Time: 4:11:33 PM
 */
public class DefaultReportUIProvider implements ReportService.UIProvider
{
    @Override
    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context)
    {
        return Collections.emptyList();
    }

    @Override
    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context, QuerySettings settings)
    {
        return Collections.emptyList();
    }

    @Override
    public @Nullable String getIconPath(Report report)
    {
        return null;
    }

    @Override
    public @Nullable String getIconCls(Report report)
    {
        return null;
    }

    @Override
    public List<Pair<NavTree, String>> getAdditionalChartingMenuItems(ViewContext context, QuerySettings settings)
    {
        return Collections.emptyList();
    }

    protected ActionURL addForwardParams(ActionURL url, ViewContext context, String[] params)
    {
        for (String name : params)
        {
            String value = context.getActionURL().getParameter(name);
            if (null != value)
                url.replaceParameter(name, value);
        }
        return url;
    }

    protected void addDesignerURL(ViewContext context, QuerySettings settings, List<ReportService.DesignerInfo> designers, String type, String[] params)
    {
        ScriptReportBean bean = new ScriptReportBean(settings);
        bean.setReportType(type);
        bean.setRedirectUrl(context.getActionURL().toString());

        ActionURL designerURL = ReportUtil.getRReportDesignerURL(context, bean);
        designerURL = addForwardParams(designerURL, context, params);

        Report report = ReportService.get().createReportInstance(type);
        if (report != null)
        {
            designers.add(new DesignerInfoImpl(type, report.getTypeDescription(), designerURL,
                    ReportService.get().getIconPath(report)));
        }
    }

    public static class DesignerInfoImpl implements ReportService.DesignerInfo
    {
        private final String _reportType;
        private final ReportService.DesignerType _type;
        private final URLHelper _iconURL;
        private final String _iconCls;

        private String _label;
        private ActionURL _designerURL;
        private String _description;
        private boolean _disabled;
        private String _id;

        public DesignerInfoImpl(String reportType, String label, ActionURL designerURL, String iconPath)
        {
            this(reportType, label, null, designerURL, iconPath, ReportService.DesignerType.DEFAULT);
        }

        public DesignerInfoImpl(String reportType, String label, String description, ActionURL designerURL, String iconPath)
        {
            this(reportType, label, description, designerURL, iconPath, ReportService.DesignerType.DEFAULT);
        }

        public DesignerInfoImpl(String reportType, String label, String description, ActionURL designerURL, String iconPath, ReportService.DesignerType type)
        {

            this(reportType, label, description, designerURL, iconPath, type, null);
        }

        public DesignerInfoImpl(String reportType, String label, String description, ActionURL designerURL, String iconPath, ReportService.DesignerType type, String iconCls)
        {
            if (reportType == null)
                throw new IllegalArgumentException("The reportType param is required");

            _reportType = reportType;
            _label = label;
            _description = description;
            _designerURL = designerURL;
            _iconURL = null != iconPath ? new ResourceURL(iconPath) : null;
            _type = type;
            _iconCls = iconCls;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public void setDesignerURL(ActionURL designerURL)
        {
            _designerURL = designerURL;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public void setDisabled(boolean disabled)
        {
            _disabled = disabled;
        }

        public void setId(String id)
        {
            _id = id;
        }

        @Override
        public String getReportType()
        {
            return _reportType;
        }

        @Override
        public String getLabel()
        {
            return _label;
        }

        @Override
        public ActionURL getDesignerURL()
        {
            return _designerURL;
        }

        @Override
        public String getDescription()
        {
            return _description;
        }

        @Override
        public boolean isDisabled()
        {
            return _disabled;
        }

        @Override
        public String getId()
        {
            return _id;
        }

        @Override
        public @Nullable URLHelper getIconURL()
        {
            return _iconURL;
        }

        @Override
        public @Nullable String getIconCls()
        {
            return _iconCls;
        }

        @Override
        public ReportService.DesignerType getType()
        {
            return _type;
        }
    }
}