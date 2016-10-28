/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

package org.labkey.api.reports.report;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.JavaScriptExportScriptModel;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.*;
import org.labkey.api.view.template.ClientDependency;

import java.util.LinkedHashSet;

/*
* User: adam
* Date: Dec 14, 2010
* Time: 5:38:37 PM
*/
public class JavaScriptReport extends ScriptReport
{
    public static final String TYPE = "ReportService.JavaScriptReport";

    @Override
    public String getDescriptorType()
    {
        return JavaScriptReportDescriptor.TYPE;
    }

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public String getTypeDescription()
    {
        return "JavaScript Report";
    }

    @Override
    public String getEditAreaSyntax()
    {
        return "text/javascript";
    }

    @Override
    public String getDefaultScript()
    {
        try
        {
            return (new JspTemplate("/org/labkey/api/reports/report/view/javaScriptReportExample.jsp")).render();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasClientDependencies()
    {
        return false;
    }

    @Override
    public @NotNull String getDesignerHelpHtml()
    {
        try
        {
            return new JspTemplate("/org/labkey/api/reports/report/view/javaScriptReportHelp.jsp").render();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean supportsPipeline()
    {
        return false;
    }

    @Override
    public String getDownloadDataHelpMessage()
    {
        return "LabKey Server calls your render() function with a query config that your code can use to retrieve the data. You can download the data via this link to help with the development of your script.";
    }

    @Override
    public HttpView renderReport(ViewContext context) throws Exception
    {
        JavaScriptReportBean model = new JavaScriptReportBean(context);
        model.setClientDependencies(getDescriptor().getClientDependencies());

        JspView<JavaScriptReportBean> view = new JspView<>("/org/labkey/api/reports/report/view/javaScriptReport.jsp", model);
        view.setFrame(WebPartView.FrameType.NONE);
        view.setClientDependencies(getDescriptor().getClientDependencies());
        return view;
    }

    public class JavaScriptReportBean
    {
        public final JavaScriptExportScriptModel model;
        public final String script;
        public final boolean useGetDataApi;
        private LinkedHashSet<ClientDependency> _dependencies;

        private JavaScriptReportBean(ViewContext context) throws Exception
        {
            QueryView qv = createQueryView(context, getDescriptor());
            model = new JavaScriptExportScriptModel(qv);
            script = getDescriptor().getProperty(ScriptReportDescriptor.Prop.script);
            if (getDescriptor().getProperty(ScriptReportDescriptor.Prop.useGetDataApi) != null)
            {
                useGetDataApi = getDescriptor().getProperty(ScriptReportDescriptor.Prop.useGetDataApi).equals("true");
            }
            else
            {
                useGetDataApi = false;
            }
        }

        public void setClientDependencies(LinkedHashSet<ClientDependency> dependencies)
        {
            _dependencies = dependencies;
        }

        public LinkedHashSet<ClientDependency> getClientDependencies()
        {
            return _dependencies;
        }
    }
}
