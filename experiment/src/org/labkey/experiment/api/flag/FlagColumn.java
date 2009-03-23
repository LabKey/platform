/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.experiment.api.flag;

import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.PropertyDescriptor;

import java.sql.Types;

public class FlagColumn extends ExprColumn
{
    String _urlFlagged;
    String _urlUnflagged;

    public FlagColumn(ColumnInfo parent, String urlFlagged, String urlUnflagged)
    {
        super(parent.getParentTable(), parent.getName() + "$", null, Types.VARCHAR, parent);
        setAlias(parent.getAlias() + "$");
        PropertyDescriptor pd = ExperimentProperty.COMMENT.getPropertyDescriptor();
        SQLFragment sql = PropertyForeignKey.getValueSql(parent,
                PropertyForeignKey.getValueSql(pd.getPropertyType()),
                pd.getPropertyId(), true);
        setValueSQL(sql);
        _urlFlagged = urlFlagged;
        _urlUnflagged = urlUnflagged;
    }

    public String urlFlag(boolean flagged)
    {
        return flagged ? _urlFlagged : _urlUnflagged;
    }
}
