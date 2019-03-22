/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

public class FlagColumn extends PropertyColumn
{
    String _urlFlagged;
    String _urlUnflagged;

    public FlagColumn(ColumnInfo parent, String urlFlagged, String urlUnflagged, Container container, User user, String name)
    {
        super(ExperimentProperty.COMMENT.getPropertyDescriptor(), parent, container, user, false);
        setFieldKey(new FieldKey(parent.getFieldKey(),name));
        setAlias(parent.getAlias() + "$");
        _urlFlagged = urlFlagged;
        _urlUnflagged = urlUnflagged;
    }

    public String urlFlag(boolean flagged)
    {
        return flagged ? _urlFlagged : _urlUnflagged;
    }
}
