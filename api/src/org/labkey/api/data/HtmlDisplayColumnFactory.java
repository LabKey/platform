/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.PageFlowUtil;

import java.util.ArrayList;

/**
 * Created by matthew on 12/5/13.
 */
public class HtmlDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo col)
    {
        DataColumn dc = new HtmlDataColumn(col);
        dc.setRequiresHtmlFiltering(false);
        return dc;
    }

    static class HtmlDataColumn extends DataColumn
    {
        HtmlDataColumn(ColumnInfo col)
        {
            super(col,false);
            setRequiresHtmlFiltering(false);
        }

        @NotNull
        @Override
        public String getFormattedValue(RenderContext ctx)
        {
            Object value = ctx.get(getBoundColumn().getFieldKey());
            if (null == value)
                return "";
            String rawHtml = String.valueOf(value);
            ArrayList<String> errors = new ArrayList<>();
            String tidyHtml = PageFlowUtil.validateHtml(rawHtml, errors, false);
            if (errors.isEmpty())
                return tidyHtml;
            else
                return PageFlowUtil.filter(errors.get(0));
        }
    }
}
