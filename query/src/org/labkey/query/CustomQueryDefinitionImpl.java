/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.query;

import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.query.persist.QueryDef;
import org.labkey.api.data.Container;

/**
 * User: jeckels
 * Date: Nov 12, 2008
 */
public class CustomQueryDefinitionImpl extends QueryDefinitionImpl
{
    public CustomQueryDefinitionImpl(User user, Container container, QueryDef queryDef)
    {
        super(user, container, queryDef);
    }

    public CustomQueryDefinitionImpl(User user, Container container, UserSchema schema, String name)
    {
        super(user, container, schema, name);
    }

    public CustomQueryDefinitionImpl(User user, Container container, SchemaKey schema, String name)
    {
        super(user, container, schema, name);
    }

    public void setSql(String sql)
    {
        edit().setSql(sql);
        // CONSIDER: Add sql QueryPropertyChange to _changes
    }

    public boolean isMetadataEditable()
    {
        return true;
    }

    public String getSql()
    {
        return getQueryDef().getSql();
    }
}
