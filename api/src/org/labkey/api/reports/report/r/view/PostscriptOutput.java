/*
 * Copyright (c) 2008-2018 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.vfs.FileLike;

import java.io.IOException;

/**
 * User: Karl Lum
 * Date: May 7, 2008
 */
public class PostscriptOutput extends DownloadParamReplacement
{
    public static final String ID = "psout:";

    public PostscriptOutput()
    {
        super(ID);
    }

    @Override
    protected @Nullable FileLike getSubstitution(FileLike directory)
    {
        return getSubstitution(directory, ".ps");
    }

    @Override
    public ScriptOutput renderAsScriptOutput(FileLike file) throws IOException
    {
        if (getReport() != null)
            return renderAsScriptOutput(file, new PostscriptReportView(this),
                    ScriptOutput.ScriptOutputType.postscript);
        else
            return renderAsScriptOutputError();
    }

    @Override
    public HttpView<?> getView(ViewContext context)
    {
        if (getReport() != null)
            return new PostscriptReportView(this);
        else
            return HtmlView.of(DownloadParamReplacement.UNABLE_TO_RENDER);
    }

    public static class PostscriptReportView extends DownloadOutputView
    {
        PostscriptReportView(ParamReplacement param)
        {
            super(param, "Postscript");
        }
    }
}
