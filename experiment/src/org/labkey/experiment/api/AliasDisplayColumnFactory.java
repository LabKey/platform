/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.MultiValuedDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.PageFlowUtil;

import java.util.List;

class AliasDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        DataColumn dataColumn = new DataColumn(colInfo, false);
        dataColumn.setInputType("text");

        return new MultiValuedDisplayColumn(dataColumn)
        {
            @Override
            public Object getInputValue(RenderContext ctx)
            {
                Object value = super.getInputValue(ctx);
                if (value instanceof List)
                    return PageFlowUtil.joinValuesToStringForExport((List<String>) value);
                return "";
            }
        };
    }
}
