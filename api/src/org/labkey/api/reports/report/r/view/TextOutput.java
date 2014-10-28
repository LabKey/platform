/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

package org.labkey.api.reports.report.r.view;

import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.reports.report.r.AbstractParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.io.PrintWriter;

/**
 * User: Karl Lum
 * Date: May 5, 2008
 */
public class TextOutput extends AbstractParamReplacement
{
    public static final String ID = "txtout:";

    public TextOutput()
    {
        super(ID);
    }

    public File convertSubstitution(File directory) throws Exception
    {
        if (directory != null)
            _file = File.createTempFile(RReport.FILE_PREFIX, "Result.txt", directory);
        else
            _file = File.createTempFile(RReport.FILE_PREFIX, "Result.txt");

        return _file;
    }

    public HttpView render(ViewContext context)
    {
        return new TextOutputView(this);
    }

    public ScriptOutput renderAsScriptOutput() throws Exception
    {
        TextOutputView view = new TextOutputView(this);
        String text = view.renderInternalAsString();

        if (null != text)
            return new ScriptOutput(ScriptOutput.ScriptOutputType.text, getName(), text);

        return null;
    }

    public static class TextOutputView extends ROutputView
    {
        public TextOutputView(ParamReplacement param)
        {
            super(param);
            setLabel("Text output");
        }

        @Override
        protected String renderInternalAsString() throws Exception
        {
            if (exists())
                return PageFlowUtil.getFileContentsAsString(getFile());

            return null;
        }

        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            String rawValue = renderInternalAsString();

            if (null != rawValue)
            {
                out.write("<table class=\"labkey-output\">");
                renderTitle(model, out);
                if (isCollapse())
                    out.write("<tr style=\"display:none\"><td><pre>");
                else
                    out.write("<tr><td><pre>");
                out.write(PageFlowUtil.filter(rawValue, false, true));
                out.write("</pre></td></tr>");
                out.write("</table>");
            }
        }
    }
}
