/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.settings.AppProps;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.regex.Pattern;

/**
 * User: dax
 * Date: May 7, 2008
 *
 * Post processes Knitr-generated HTML to fixup hrefs.  It also
 * does not output extra HTML tags (as HtmlOutput does)
  */
public class KnitrOutput extends HtmlOutput
{
    public static final String ID = "knitrout:";

    public KnitrOutput()
    {
        super(ID);
    }

    @Override
    public ScriptOutput renderAsScriptOutput(File file) throws Exception
    {
        KnitrOutputView view = new KnitrOutputView(this, getLabel());
        String html = view.renderInternalAsString(file);

        if (null != html)
            return new ScriptOutput(ScriptOutput.ScriptOutputType.html, getName(), html);

        return null;
    }

    public HttpView render(ViewContext context)
    {
        return new KnitrOutputView(this, getLabel());
    }

    @Override
    public @Nullable Thumbnail renderThumbnail(ViewContext context) throws IOException
    {
        for (File file : getFiles())
        {
            KnitrOutputView view = new KnitrOutputView(this, getLabel());
            Thumbnail thumb = null;

            try
            {
                String html = view.renderInternalAsString(file);
                URI baseURI = new URI(AppProps.getInstance().getBaseServerUrl());
                if (html != null && baseURI != null)
                    thumb = ImageUtil.webThumbnail(context, html, baseURI);
            }
            catch(Exception ignore){}// if we can't get a thumbnail then that is okay; LabKey should use a default

            return thumb;
        }
        return null;
    }

    public static class KnitrOutputView extends HtmlOutputView
    {
        final RReport _report;

        public KnitrOutputView(KnitrOutput param, String label)
        {
            super(param, label);
            _report = (RReport) param.getReport();
        }

        @Override
        protected String renderInternalAsString(File file) throws Exception
        {
            String htmlIn = super.renderInternalAsString(file);

            // do post processing on this html to fixup any hrefs
            if (null != htmlIn)
            {
                // replace all ${hrefout:<filename>} with the appropriate url
                File reportDir = _report.getReportDir(this.getViewContext().getContainer().getId());
                String htmlOut = ParamReplacementSvc.get().processHrefParamReplacement(_report, htmlIn, reportDir);
                htmlOut = ParamReplacementSvc.get().processRelativeHrefReplacement(
                        _report,
                        htmlOut,
                        reportDir);
                return "<div class=\"labkey-knitr\">" + htmlOut + "</div>";
            }

            return null;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            String delim = "";
            for (File file : getFiles())
            {
                String html = renderInternalAsString(file);
                if (null != html)
                {
                    out.write(delim);
                    out.write(html);

                    delim = "<br>";
                }
            }
        }
    }
}
