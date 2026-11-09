/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

package org.labkey.api.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.actions.AssayRunUploadForm;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.writer.HtmlWriter;

public class AssayDataCollectorDisplayColumn extends SimpleDisplayColumn
{
    private final AssayRunUploadForm _form;
    private final ColumnInfo _col;

    public AssayDataCollectorDisplayColumn(TableInfo table, AssayRunUploadForm form)
    {
        _form = form;
        setCaption("Run Data");
        var col = new BaseColumnInfo(FieldKey.fromParts("Run Data"), table);
        col.setInputType("file");
        _col = col;
    }

    @Override
    public @NotNull HtmlString getTitle(RenderContext ctx)
    {
        return HtmlStringBuilder.of(super.getTitle(ctx)).append(" *").getHtmlString();
    }

    @Override
    public boolean isEditable()
    {
        return true;
    }
    
    @Override
    public ColumnInfo getColumnInfo()
    {
        return _col;
    }

    @Override
    public void renderInputHtml(RenderContext ctx, HtmlWriter out, Object value)
    {
        HttpView descriptionView = _form.getProvider().getDataDescriptionView(_form);
        JspView view = new JspView<>("/org/labkey/assay/view/dataUpload.jsp", _form);
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
            throw new RuntimeException(e);
        }
    }
}
