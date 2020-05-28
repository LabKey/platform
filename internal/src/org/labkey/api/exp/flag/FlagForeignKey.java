/*
 * Copyright (c) 2012-2019 LabKey Corporation
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

package org.labkey.api.exp.flag;

import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.StringExpression;

public class FlagForeignKey extends AbstractForeignKey
{
    public static final String DISPLAYFIELD_NAME = "Comment";
    private final String _urlFlagged;
    private final String _urlUnflagged;

    public FlagForeignKey(UserSchema schema, String urlFlagged, String urlUnflagged)
    {
        super(schema, null);
        _urlFlagged = urlFlagged;
        _urlUnflagged = urlUnflagged;
    }

    @Override
    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        if (displayField == null)
        {
            displayField = DISPLAYFIELD_NAME;
        }
        if (!displayField.equalsIgnoreCase(DISPLAYFIELD_NAME))
            return null;
        return new FlagColumn(parent, _urlFlagged, _urlUnflagged, _sourceSchema.getContainer(), _sourceSchema.getUser(), displayField);
    }

    @Override
    public TableInfo getLookupTableInfo()
    {
        VirtualTable ret = new VirtualTable<>(ExperimentService.get().getSchema(), "FlagComment", (UserSchema)_sourceSchema);
        var colComment = new BaseColumnInfo("Comment", ret, JdbcType.VARCHAR);
        ret.addColumn(colComment);
        return ret;
    }

    @Override
    public StringExpression getURL(ColumnInfo parent)
    {
        return null;
    }
}
