/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.api.study;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Sort;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * User: brittp
 * Date: Nov 2, 2006
 * Time: 4:55:09 PM
 */
public abstract class PlateQueryView extends QueryView
{
    protected PlateQueryView(UserSchema schema, QuerySettings settings)
    {
        super(schema, settings);
    }

    public abstract void setSort(Sort sort);

    public abstract void setButtons(List<ActionButton> buttons);

    public abstract boolean hasRecords() throws SQLException, IOException;

    public abstract void addHiddenFormField(String key, String value);
}
