/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

import org.labkey.api.query.CreateJavaScriptModel;
import org.labkey.api.query.QueryView;
import org.labkey.api.reports.report.view.RunScriptReportView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;

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
    public String getExecutionLocation()
    {
        return "by the client browser";
    }

    @Override
    public boolean supportsPipeline()
    {
        return false;
    }

    @Override
    public String getDownloadDataHelpMessage()
    {
        return "LabKey Server provides your JavaScript render() function with a query config that your code can use to retrieve the data. You can download the data via this link to help with the development of your script.";
    }

    @Override
    public HttpView renderReport(ViewContext context) throws Exception
    {
        return new JspView<JavaScriptReportBean>("/org/labkey/api/reports/report/view/javaScriptReport.jsp", new JavaScriptReportBean(context));
    }

    @Override
    public HttpView getRunReportView(ViewContext context) throws Exception
    {
        return new RunScriptReportView(this);
    }

    public class JavaScriptReportBean
    {
        public final CreateJavaScriptModel model;
        public final String script;

        private JavaScriptReportBean(ViewContext context) throws Exception
        {
            QueryView qv = createQueryView(context, getDescriptor());
            model = new CreateJavaScriptModel(qv);
            script = getDescriptor().getProperty(ScriptReportDescriptor.Prop.script);
        }
    }
}
