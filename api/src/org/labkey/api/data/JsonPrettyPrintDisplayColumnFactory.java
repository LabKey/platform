/*
 * Copyright (c) 2014-2017 LabKey Corporation
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

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * User: tgaluhn
 * Date: 10/17/2014
 *
 */
public class JsonPrettyPrintDisplayColumnFactory extends ExpandableTextDisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new JsonPrettyPrintDataColumn(colInfo);
    }

    static class JsonPrettyPrintDataColumn extends ExpandableTextDataColumn
    {
        JsonPrettyPrintDataColumn(ColumnInfo col)
        {
            super(col);
        }

        @NotNull
        @Override
        public String getFormattedValue(RenderContext ctx)
        {
            Object value = getValueFromCtx(ctx);
            if (null == value)
                return "";

            ObjectMapper mapper = new ObjectMapper();
            DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
            pp.indentArraysWith(new DefaultIndenter());

            try
            {
                Object json = mapper.readValue(value.toString(), Object.class);
                return getFormattedOutputText(mapper.writer(pp).writeValueAsString(json), 10, null);
            }
            catch (IOException e)
            {
                return "Bad JSON object";
            }
        }
    }
}
