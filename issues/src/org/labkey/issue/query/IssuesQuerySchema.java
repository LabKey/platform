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

package org.labkey.issue.query;

import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;

public class IssuesQuerySchema extends UserSchema 
{
    public static final String SCHEMA_NAME = "issues";
    public static final String SCHEMA_DESCR = "Contains one data table containing all the issues.";

    public enum TableType
    {
        Issues
        {
            @Override
            public TableInfo createTable(IssuesQuerySchema schema)
            {
                return new IssuesTable(schema);
            }
        };

        public abstract TableInfo createTable(IssuesQuerySchema schema);
    }
    static private Set<String> tableNames = new LinkedHashSet<>();
    static
    {
        for (TableType type : TableType.values())
        {
            tableNames.add(type.toString());
        }
        tableNames = Collections.unmodifiableSet(tableNames);
    }

    static public void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider() {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new IssuesQuerySchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public IssuesQuerySchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCR, user, container, IssuesSchema.getInstance().getSchema());
    }

    public Set<String> getTableNames()
    {
        return tableNames;
    }

    public TableInfo createTable(String name)
    {
        if (name != null)
        {
            TableType tableType = null;
            for (TableType t : TableType.values())
            {
                // Make the enum name lookup case insensitive
                if (t.name().equalsIgnoreCase(name.toLowerCase()))
                {
                    tableType = t;
                    break;
                }
            }
            if (tableType != null)
            {
                return tableType.createTable(this);
            }
        }
        return null;
    }

    public enum QueryType
    {
        Issues
        {
            @Override
            public QueryView createView(ViewContext context, IssuesQuerySchema schema, QuerySettings settings, BindException errors)
            {
                return new IssuesQueryView(context, schema, settings, errors);
            }
        };

        public abstract QueryView createView(ViewContext context, IssuesQuerySchema schema, QuerySettings settings, BindException errors);
    }

    @Override
    public QueryView createView(ViewContext context, QuerySettings settings, BindException errors)
    {
        String queryName = settings.getQueryName();
        if (queryName != null)
        {
            QueryType queryType = null;
            for (QueryType qt : QueryType.values())
            {
                if (qt.name().equalsIgnoreCase(queryName))
                {
                    queryType = qt;
                    break;
                }
            }
            if (queryType != null)
                return queryType.createView(context, this, settings, errors);
        }

        return super.createView(context, settings, errors);
    }
}
