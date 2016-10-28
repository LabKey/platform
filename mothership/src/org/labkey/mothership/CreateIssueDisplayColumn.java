/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
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
        _saveButton.render(ctx, out);
        out.write("\t" + PageFlowUtil.button("Create Issue").href("javascript:document.forms.CreateIssue.elements['assignedTo'].value = document.forms[" + PageFlowUtil.jsString(ctx.getCurrentRegion().getFormId()) + "].elements['assignedTo'].value; document.forms.CreateIssue.submit();"));
    }
}
