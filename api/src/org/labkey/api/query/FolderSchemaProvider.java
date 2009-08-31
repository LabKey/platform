/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.security.ACL;
import org.labkey.api.util.FileUtil;

import java.util.*;

abstract public class FolderSchemaProvider extends DefaultSchema.SchemaProvider
{
    static public class FolderSchema extends UserSchema
    {
        QuerySchema _fallback;

        /**
         * @param fallback schema to look in first, before checking for child folders.
         * This is to be able to disambiguate between a schema called "flow", and a folder called "flow".
         * That is, the folder "/home/myflowfolder/flow" can be found by doing "project.myflowfolder.folder.flow", and
         * the flow schema in "/home/myflowfolder" is "project.myflowfolder.flow".  
         */
        public FolderSchema(User user, Container container, QuerySchema fallback)
        {
            super(null, null, user, container, CoreSchema.getInstance().getSchema());
            _user = user;
            _container = container;
            _fallback = fallback;
        }

        public DbSchema getDbSchema()
        {
            return CoreSchema.getInstance().getSchema();
        }

        public Set<String> getTableNames()
        {
            return Collections.EMPTY_SET;
        }

        protected TableInfo createTable(String name)
        {
            return null;
        }

		@Override
		public TableInfo getTable(String name, boolean includeExtraMetadata)
		{
			return null;
		}

		public QuerySchema getSchema(String name)
        {
            if (_fallback != null && !name.contains("/"))
            {
                QuerySchema ret = _fallback.getSchema(name);
                if (ret != null)
                {
                    return ret;
                }
            }

			ArrayList<String> parts = FileUtil.normalizeSplit(name);
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

            if (_user != null && child.hasPermission(_user, ACL.PERM_READ))
            {
                fallback = DefaultSchema.get(_user, child);
            }

            return new FolderSchema(_user, child, fallback);
        }

        protected Map<String, Container> getChildContainers()
        {
            Map<String, Container> ret = new TreeMap();
            Container[] children = ContainerManager.getAllChildren(_container);
            for (Container child : children)
            {
                ret.put(child.getName(), child);
            }
            return ret;
        }

        public boolean canEdit(String name)
        {
            return false;
        }
    }
}
