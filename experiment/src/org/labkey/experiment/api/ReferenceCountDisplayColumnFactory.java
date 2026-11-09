/*
 * Copyright (c) 2026 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.writer.HtmlWriter;
import org.labkey.experiment.FileLinkFileListener;

public class ReferenceCountDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new ExpDataFileColumn(colInfo)
        {
            private Long getCount(ExpData data)
            {

                if (data == null || StringUtils.isEmpty(data.getDataFileUrl()) || data.getFile() == null)
                    return null;
                else
                {
                    FileLinkFileListener fileListener = new FileLinkFileListener();
                    SQLFragment unionSql = fileListener.listFilesQuery(true, data.getFile().getAbsolutePath());

                    return new SqlSelector(CoreSchema.getInstance().getSchema(), unionSql).getRowCount();
                }
            }

            @Override
            protected void renderData(HtmlWriter out, ExpData data)
            {
                Long val = getCount(data);
                if (val == null)
                    out.write("");
                else
                    out.write(val);
            }

            @Override
            protected Object getJsonValue(ExpData data)
            {
                return getCount(data);
            }
        };
    }
}
