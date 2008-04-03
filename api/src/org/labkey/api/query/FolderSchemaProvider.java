package org.labkey.api.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.ACL;

import java.util.Set;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;

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
            super(null, user, container, CoreSchema.getInstance().getSchema());
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

        public QuerySchema getSchema(String name)
        {
            if (_fallback != null)
            {
                QuerySchema ret = _fallback.getSchema(name);
                if (ret != null)
                {
                    return ret;
                }
            }
            Container child = getChildContainers().get(name);
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
