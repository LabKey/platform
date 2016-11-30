/*
 * Copyright (c) 2014 LabKey Corporation
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

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.ClientDependency;

import java.io.IOException;
import java.util.Set;

/**
 * User: tgaluhn
 * Date: 10/17/2014
 *
 */
public class JsonPrettyPrintDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new JsonPrettyPrintDataColumn(colInfo);
    }

    static class JsonPrettyPrintDataColumn extends DataColumn
    {
        JsonPrettyPrintDataColumn(ColumnInfo col)
        {
            super(col,false);
        }

        @NotNull
        @Override
        public String getFormattedValue(RenderContext ctx)
        {
            Object value = ctx.get(getBoundColumn().getFieldKey());
            if (value == null)
            {
                // If we couldn't find it by FieldKey, check by alias as well
                value = getValue(ctx);
            }
            if (null == value)
                return "";

            ObjectMapper mapper = new ObjectMapper();
            DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
            pp.indentArraysWith(new DefaultPrettyPrinter.Lf2SpacesIndenter());

            try
            {
                Object json = mapper.readValue(value.toString(), Object.class);
                String output = PageFlowUtil.filter(mapper.writer(pp).writeValueAsString(json));
                // Too bad there's no way to configure EOL characters for the Jackson pretty printer.
                // It seems to use system defaults.
                String[] outputLines = output.replace("  ", "&nbsp;&nbsp;").split("\r\n|\r|\n");
                String outputTxt = "<div class='json-container'>" + StringUtils.join(outputLines, "<br/>") + "</div>";

                if (outputLines.length > 10)
                {
                    outputTxt = "<div class='json-collapsed'>" + outputTxt
                        + "<div class='json-overflow'></div>"
                        + "<div class='json-showmore'><div class='labkey-wp-text-buttons'><a href='#more' onclick=\"return LABKEY.JSONDisplayColumn.showMore(this);\">Show More&#9660;</a></div></div>"
                        + "<div class='json-showless'><div class='labkey-wp-text-buttons'><a href='#less' onclick=\"return LABKEY.JSONDisplayColumn.showLess(this);\">Show Less&#9650;</a></div></div>"
                        + "</div>";
                }

                return outputTxt;
            }
            catch (IOException e)
            {
                return "Bad JSON object";
            }
        }

        @NotNull
        @Override
        public Set<ClientDependency> getClientDependencies()
        {
            return PageFlowUtil.set(
                ClientDependency.fromPath("core/JSONDisplayColumn.js"),
                ClientDependency.fromPath("core/JSONDisplayColumn.css")
            );
        }
    }
}
