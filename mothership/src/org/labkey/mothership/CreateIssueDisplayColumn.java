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

package org.labkey.mothership;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.writer.HtmlWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class CreateIssueDisplayColumn extends DataColumn
{
    private final ActionButton _saveButton;

    public CreateIssueDisplayColumn(ColumnInfo column, ActionButton saveButton)
    {
        super(column);
        _saveButton = saveButton;
        setCaption("");
        setEditable(false);
    }

    @Override
    public void renderDetailsCellContents(RenderContext ctx, HtmlWriter out)
    {
        _saveButton.render(ctx, out);

        String repo = MothershipManager.get().getGitHubRepo();
        if (!StringUtils.isEmpty(repo))
        {
            out.write("\t");

            StringBuilder body = new StringBuilder();

            body.append("Created from crash report: ");
            body.append(HttpView.currentContext().getRequest().getAttribute(ViewServlet.ORIGINAL_URL_STRING));
            body.append("\n\n");

            StringBuilder title = new StringBuilder();
            try
            {
                String stackTraceString = ctx.get(getBoundColumn().getFieldKey(), String.class);
                BufferedReader reader = new BufferedReader(new StringReader(stackTraceString));
                String firstLine = reader.readLine();
                // Grab the exception class
                String className = firstLine.split(":")[0];
                if (className.lastIndexOf('.') != -1)
                {
                    // Strip off the package name to make the title a little shorter
                    className = className.substring(className.lastIndexOf('.') + 1);
                }
                title.append(className);
                body.append(firstLine);
                String nextLine;
                String separator = " in ";
                String suffix = "";
                String bestLocation = null;
                String firstLocation = null;
                while ((nextLine = reader.readLine()) != null)
                {
                    if (firstLocation == null)
                    {
                        firstLocation = nextLine;
                    }
                    if (bestLocation == null &&
                            ((nextLine.contains("org.labkey") && !nextLine.contains(ConnectionWrapper.class.getPackage().getName())) ||
                                    nextLine.contains("org.fhcrc")))
                    {
                        bestLocation = nextLine;
                        separator = " from ";
                    }

                    if (body.length() + nextLine.length() < 6000)  // Don't exceed GitHub's GET URL limit
                    {
                        body.append(nextLine);
                        body.append("\n");
                    }
                    else
                    {
                        suffix = "...\n";
                    }
                }
                body.append(suffix);

                if (bestLocation == null)
                {
                    bestLocation = firstLocation;
                }
                if (bestLocation != null)
                {
                    bestLocation = bestLocation.trim();
                    if (bestLocation.startsWith("at "))
                    {
                        bestLocation = bestLocation.substring("at ".length());
                    }
                    title.append(separator);
                    title.append(bestLocation.split("\\(")[0]);
                    title.append("()");
                }
            }
            catch (IOException e)
            {
                throw UnexpectedException.wrap(e);
            }

            String url = "https://github.com/LabKey/" + repo + "/issues/new?title=" +
                    URLEncoder.encode(title.toString(), StandardCharsets.UTF_8) +
                    "&body=" + URLEncoder.encode(body.toString(), StandardCharsets.UTF_8);

            PageFlowUtil.button("Create Issue").target("_blank").href(url).appendTo(out);
        }
    }
}
