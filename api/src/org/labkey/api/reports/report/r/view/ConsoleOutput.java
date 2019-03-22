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
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.Report;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.io.File;

/**
 * User: Karl Lum
 * Date: May 7, 2008
 */
public class ConsoleOutput extends AbstractParamReplacement
{
    public static final String ID = "consoleout:";

    public ConsoleOutput()
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
        ROutputView view = new TextOutput.TextOutputView(this);
        view.setLabel("Console output");
        if (HttpView.currentContext().get(Report.renderParam.reportWebPart.name()) != null)
            view.setCollapse(true);

        return view;
    }

    @Override
    public ScriptOutput renderAsScriptOutput(File file) throws Exception
    {
        ROutputView view = new TextOutput.TextOutputView(this);
        String console = view.renderInternalAsString(file);

        if (null != console)
            return new ScriptOutput(ScriptOutput.ScriptOutputType.console, "console", console);

        return null;
    }
}
