/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.view.JspView;
import org.labkey.api.view.HttpView;
import org.labkey.api.study.actions.AssayRunUploadForm;

import java.io.Writer;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Aug 3, 2007
 */
public class AssayDataCollectorDisplayColumn extends SimpleDisplayColumn
{
    private final AssayRunUploadForm _form;
    private ColumnInfo _col;

    public AssayDataCollectorDisplayColumn(AssayRunUploadForm form)
    {
        _form = form;
        setCaption("Run Data");
        _col = new ColumnInfo("Run Data");
        _col.setInputType("file");
    }

    @Override
    public void renderTitle(RenderContext ctx, Writer out) throws IOException
    {
        super.renderTitle(ctx, out);
        out.write(" *");
    }

    public boolean isEditable()
    {
        return true;
    }
    
    public ColumnInfo getColumnInfo()
    {
        return _col;
    }

    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        HttpView descriptionView = _form.getProvider().getDataDescriptionView(_form);
        JspView view = new JspView<>("/org/labkey/study/assay/view/dataUpload.jsp", _form);
        try
        {
            if (descriptionView != null)
            {
                descriptionView.render(ctx.getRequest(), ctx.getViewContext().getResponse());
            }
            view.render(ctx.getRequest(), ctx.getViewContext().getResponse());
        }
        catch (Exception e)
        {
            throw (IOException)new IOException().initCause(e);
        }
    }
}
