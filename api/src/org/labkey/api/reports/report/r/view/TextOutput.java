/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

    @Override
    protected File getSubstitution(File directory) throws Exception
    {
        File file;
        if (directory != null)
            file = File.createTempFile(RReport.FILE_PREFIX, "Result.txt", directory);
        else
            file = File.createTempFile(RReport.FILE_PREFIX, "Result.txt");

        addFile(file);
        return file;
    }

    public HttpView render(ViewContext context)
    {
        return new TextOutputView(this);
    }

    @Override
    public ScriptOutput renderAsScriptOutput(File file) throws Exception
    {
        TextOutputView view = new TextOutputView(this);
        String text = view.renderInternalAsString(file);

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
        protected String renderInternalAsString(File file) throws Exception
        {
            if (exists(file))
                return PageFlowUtil.getFileContentsAsString(file);

            return null;
        }

        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            for (File file : getFiles())
            {
                String rawValue = renderInternalAsString(file);

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
}
