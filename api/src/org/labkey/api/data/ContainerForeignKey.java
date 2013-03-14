/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Nov 2, 2007
 */
public class ContainerForeignKey extends QueryForeignKey
{
    static public ColumnInfo initColumn(ColumnInfo column, UserSchema schema)
    {
        return initColumn(column, schema, null);
    }

    static public ColumnInfo initColumn(ColumnInfo column, UserSchema schema, final ActionURL url)
    {
        column.setFk(new ContainerForeignKey(schema));
        column.setUserEditable(false);
        column.setShownInInsertView(false);
        column.setShownInUpdateView(false);
        column.setReadOnly(true);
        column.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new ContainerDisplayColumn(colInfo, false, true);
            }
        });
        if (url != null)
            column.setURL(new DetailsURL(url));
        return column;
    }

    public ContainerForeignKey(UserSchema schema)
    {
        super("core", schema.getContainer(), schema.getUser(), "Containers", "EntityId", "DisplayName");
    }

}
