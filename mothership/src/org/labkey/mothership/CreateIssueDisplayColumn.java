/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewServlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;

/**
 * User: jeckels
 * Date: Jun 29, 2006
 */
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

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String originalURL = (String)ctx.getRequest().getAttribute(ViewServlet.ORIGINAL_URL_STRING);
        StringBuilder body = new StringBuilder();
        body.append("Created from crash report: ");
        body.append(originalURL);
        body.append("\n\n");
        String stackTrace = PageFlowUtil.filter(ctx.getRow().get("StackTrace"));
        body.append(stackTrace);

        StringBuilder title = new StringBuilder();
        BufferedReader reader = new BufferedReader(new StringReader(stackTrace));
        // Grab the exception class
        String className = reader.readLine().split("\\:")[0];
        if (className.lastIndexOf('.') != -1)
        {
            // Strip off the package name to make the title a little shorter
            className = className.substring(className.lastIndexOf('.') + 1);
        }
        title.append(className);
        String firstLocation = reader.readLine();
        String location = firstLocation;
        String separator = " in ";
        while (location != null &&
                (!location.contains("org.labkey") || location.contains(ConnectionWrapper.class.getPackage().getName())) &&
                !location.contains("org.fhcrc"))
        {
            location = reader.readLine();
            separator = " from ";
        }

        if (location == null)
        {
            location = firstLocation;
        }
        if (location != null)
        {
            location = location.trim();
            if (location.startsWith("at "))
            {
                location = location.substring("at ".length());
            }
            title.append(separator);
            title.append(location.split("\\(")[0]);
            title.append("()");
        }

        String createIssueURL = MothershipManager.get().getCreateIssueURL(ctx.getContainer());
        ActionURL callbackURL = ctx.getViewContext().getActionURL().clone();
        callbackURL.setAction("createIssueFinished.view");

        out.write("\t<input type=\"hidden\" name=\"callbackURL\" value=\"" + callbackURL.toString() + "\"/>\n");
        out.write("\t<input type=\"hidden\" name=\"body\" value=\"" + body.toString() + "\"/>\n");
        out.write("\t<input type=\"hidden\" name=\"title\" value=\"" + title.toString() + "\"/>\n");
        out.write("\t<input type=\"hidden\" name=\"skipPost\" value=\"true\"/>\n");

        _saveButton.render(ctx, out);
        out.write("\t" + PageFlowUtil.generateButton("Create Issue", "javascript:document.forms.ExceptionStackTrace.action = '" + createIssueURL + "'; document.forms.ExceptionStackTrace.submit()"));
    }
}
