/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

abstract public class FolderSchemaProvider extends DefaultSchema.SchemaProvider
{
    public FolderSchemaProvider()
    {
        super(null);
    }

    static public class FolderSchema extends UserSchema implements QuerySchema.ContainerSchema
    {
        QuerySchema _fallback;

        /** Cache created schemas to leverage their caching of queries and tables */
        private final Map<String, QuerySchema> _cache = new HashMap<>();

        /**
         * @param fallback schema to look in first, before checking for child folders.
         * This is to be able to disambiguate between a schema called "flow", and a folder called "flow".
         * That is, the folder "/home/myflowfolder/flow" can be found by doing "project.myflowfolder.folder.flow", and
         * the flow schema in "/home/myflowfolder" is "project.myflowfolder.flow".  
         */
        public FolderSchema(@NotNull String name, User user, Container container, QuerySchema fallback)
        {
            super(name, null, user, container, CoreSchema.getInstance().getSchema());
            _fallback = fallback;
        }

        @Override
        public boolean isHidden()
        {
            return true;
        }

        @Override
        public boolean isFolder()
        {
            return true;
        }

        @Override
        public DbSchema getDbSchema()
        {
            return CoreSchema.getInstance().getSchema();
        }

        @Override
        public Set<String> getTableNames()
        {
            return Collections.emptySet();
        }

        @Override
        public TableInfo createTable(String name, ContainerFilter cf)
        {
            return null;
        }

		@Override
        public TableInfo getTable(String name, @Nullable ContainerFilter cf, boolean includeExtraMetadata, boolean forWrite)
		{
			return null;
		}

        @Override
        public QuerySchema getSchema(String name)
        {
            return _cache.computeIfAbsent(name, _schemaCreator);
        }

        private final Function<String, QuerySchema> _schemaCreator = (name) ->
        {
            if (_restricted)
                return null;

            if (_fallback != null && !name.contains("/"))
            {
                QuerySchema ret = _fallback.getSchema(name);
                if (ret != null)
                {
                    return ret;
                }
            }

            Path parts = Path.parse(name).normalize();
            if (null == parts)
                return null;
			Container child = _container;
			for (String part : parts)
			{
				child = child.getChild(part);
				if (child == null)
					break;
			}
            if (child == null)
            {
                return DefaultSchema.get(_user, _container).getSchema(name);
            }

            QuerySchema fallback = null;

            if (_user != null && child.hasPermission(_user, ReadPermission.class))
            {
                fallback = DefaultSchema.get(_user, child);
            }

            return new FolderSchema(name, _user, child, fallback);
        };

        @Override
        public <R, P> R accept(SchemaTreeVisitor<R, P> visitor, SchemaTreeVisitor.Path path, P param)
        {
            // Skip visiting.  Consider adding SchemaTreeVisitor.visitFolderSchema() if we need it.
            return null;
        }
    }
}
