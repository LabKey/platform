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

package org.labkey.query.persist;

import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;
import org.labkey.query.view.DbUserSchema;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;


public class QueryManager
{
    static final private Logger _log = Logger.getLogger(QueryManager.class);
    static private QueryManager instance;
    static final private String SCHEMA_NAME = "query";
    final static public int FLAG_INHERITABLE = 0x01;
    final static public int FLAG_HIDDEN = 0x02;
    final static public int FLAG_SNAPSHOT = 0x04;

    synchronized static public QueryManager get()
    {
        if (instance == null)
            instance = new QueryManager();
        return instance;
    }

    public QueryDef getQueryDef(int id)
    {
        QueryDef def = Table.selectObject(getTableInfoQueryDef(), id, QueryDef.class);
        _log.debug(def.toString());
        return def;
    }

    /**
     * @param customQuery whether to look for custom queries or modified metadata on built-in tables
     */
    public QueryDef getQueryDef(Container container, String schema, String name, boolean customQuery)
    {
        // Metadata for built-in tables is stored with a NULL value for the SQL
        QueryDef.Key key = new QueryDef.Key(container, customQuery);
        key.setSchema(schema);
        key.setQueryName(name);
        try
        {
            return key.selectObject();
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    /**
     * @param customQuery whether to look for custom queries or modified metadata on built-in tables
     */
    public QueryDef[] getQueryDefs(Container container, String schema, boolean inheritableOnly, boolean includeSnapshots, boolean customQuery)
    {
        // Metadata for built-in tables is stored with a NULL value for the SQL
        try
        {
            QueryDef.Key key = new QueryDef.Key(container, customQuery);
            if (schema != null)
            {
                key.setSchema(schema);
            }

            int mask = 0;
            int value = 0;

            if (inheritableOnly)
            {
                mask |= FLAG_INHERITABLE;
                value |= FLAG_INHERITABLE;
            }

            if (!includeSnapshots)
                mask |= FLAG_SNAPSHOT;

            if (mask != 0 || value != 0)
                key.setFlagMask(mask, value);

            return key.select();
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public QuerySnapshotDef[] getQuerySnapshots(Container container, String schema)
    {
        try
        {
            QuerySnapshotDef.Key key = new QuerySnapshotDef.Key(container);
            if (schema != null)
            {
                key.setSchema(schema);
            }
            return key.select();
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public QueryDef insert(User user, QueryDef queryDef) throws SQLException
    {
        return Table.insert(user, getTableInfoQueryDef(), queryDef);
    }

    public QueryDef update(User user, QueryDef queryDef) throws SQLException
    {
        return Table.update(user, getTableInfoQueryDef(), queryDef, queryDef.getQueryDefId(), null);
    }

    public void delete(User user, QueryDef queryDef) throws SQLException
    {
        Table.delete(getTableInfoQueryDef(), queryDef.getQueryDefId(), null);
    }

    public void delete(User user, QuerySnapshotDef querySnapshotDef) throws SQLException
    {
        Table.delete(getTableInfoQuerySnapshotDef(), querySnapshotDef.getRowId(), null);
        if (querySnapshotDef.getQueryDefId() != null)
            Table.delete(getTableInfoQueryDef(), querySnapshotDef.getQueryDefId(), null);
    }

    public QuerySnapshotDef insert(User user, QueryDef queryDef, QuerySnapshotDef snapshotDef) throws SQLException
    {
        if (queryDef != null && snapshotDef.getQueryTableName() == null)
        {
            QueryDef def = insert(user, queryDef);
            snapshotDef.setQueryDefId(def.getQueryDefId());
        }
        return Table.insert(user, getTableInfoQuerySnapshotDef(), snapshotDef);
    }

    public QuerySnapshotDef update(User user, QueryDef queryDef, QuerySnapshotDef snapshotDef) throws SQLException
    {
        if (queryDef != null && snapshotDef.getQueryTableName() == null)
            update(user, queryDef);
        return Table.update(user, getTableInfoQuerySnapshotDef(), snapshotDef, snapshotDef.getRowId(), null);
    }

    public CstmView getCustomView(int id) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(getTableInfoCustomView().getColumn("CustomViewId"), id);
        CstmView view = Table.selectObject(getTableInfoCustomView(), id, CstmView.class);
        _log.debug(view);
        return view;
    }

    public CstmView[] getColumnLists(Container container, String schemaName, String queryName, String viewName, User user, boolean inheritableOnly) throws SQLException
    {
        CstmView.Key key = new CstmView.Key(container);
        if (schemaName != null)
            key.setSchema(schemaName);
        if (queryName != null)
            key.setQueryName(queryName);
        if (viewName != null)
            key.setName(viewName);
        if (inheritableOnly)
        {
            key.setFlagMask(FLAG_INHERITABLE, FLAG_INHERITABLE);
        }

        key.setUser(user);
        return key.select();
    }

    public CstmView update(User user, CstmView view) throws SQLException
    {
        return Table.update(user, getTableInfoCustomView(), view, view.getCustomViewId(), null);
    }

    public CstmView insert(User user, CstmView view) throws SQLException
    {
        return Table.insert(user, getTableInfoCustomView(), view);
    }

    public void delete(User user, CstmView view) throws SQLException
    {
        Table.delete(getTableInfoCustomView(), view.getCustomViewId(), null);
    }

    public boolean canInherit(int flag)
    {
        return (flag & FLAG_INHERITABLE) != 0;
    }

    public DbUserSchemaDef getDbUserSchemaDef(int id)
    {
        return Table.selectObject(getTableInfoDbUserSchema(), id, DbUserSchemaDef.class);
    }

    public DbUserSchemaDef[] getDbUserSchemaDefs(Container container)
    {
        try
        {
            DbUserSchemaDef.Key key = new DbUserSchemaDef.Key(container);
            return key.select();
        }
        catch (SQLException e)
        {
            _log.error("Error", e);
        }
        return new DbUserSchemaDef[0];
    }

    public DbUserSchemaDef getDbUserSchemaDef(Container container, String userSchemaName)
    {
        if (userSchemaName == null)
            return null;
        try
        {
            DbUserSchemaDef.Key key = new DbUserSchemaDef.Key(container);
            key.setUserSchemaName(userSchemaName);
            return key.selectObject();
        }
        catch (SQLException e)
        {
            _log.error("Error", e);
            return null;
        }
    }

    public DbUserSchemaDef insert(User user, DbUserSchemaDef def) throws Exception
    {
        return Table.insert(user, getTableInfoDbUserSchema(), def);
    }

    public DbUserSchemaDef update(User user, DbUserSchemaDef def) throws Exception
    {
        DbUserSchemaDef ret = Table.update(user, getTableInfoDbUserSchema(), def, def.getDbUserSchemaId(), null);
        return ret;
    }

    public void delete(User user, DbUserSchemaDef def) throws Exception
    {
        Table.delete(getTableInfoDbUserSchema(), def.getDbUserSchemaId(), null);
    }


    public void reloadDbUserSchema(DbUserSchemaDef def)
    {
        DbUserSchema.uncache(def);
    }


    public int setCanInherit(int flag, boolean f)
    {
        if (f)
        {
            return flag | FLAG_INHERITABLE;
        }
        else
        {
            return flag & ~FLAG_INHERITABLE;
        }
    }

    public boolean isHidden(int flag)
    {
        return (flag & FLAG_HIDDEN) != 0;
    }

    public int setIsHidden(int flag, boolean f)
    {
        if (f)
        {
            return flag | FLAG_HIDDEN;
        }
        else
        {
            return flag & ~FLAG_HIDDEN;
        }
    }

    public boolean isSnapshot(int flag)
    {
        return (flag & FLAG_SNAPSHOT) != 0;
    }

    public int setIsSnapshot(int flag, boolean f)
    {
        if (f)
        {
            return flag | FLAG_SNAPSHOT;
        }
        else
        {
            return flag & ~FLAG_SNAPSHOT;
        }
    }
    
    public String getDbSchemaName()
    {
        return SCHEMA_NAME;
    }

    public DbSchema getDbSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public TableInfo getTableInfoQueryDef()
    {
        return getDbSchema().getTable("QueryDef");
    }

    public TableInfo getTableInfoSupplementalMetadata()
    {
        return getDbSchema().getTable("SupplementalMetadata");
    }

    public TableInfo getTableInfoQuerySnapshotDef()
    {
        return getDbSchema().getTable("QuerySnapshotDef");
    }

    public TableInfo getTableInfoCustomView()
    {
        return getDbSchema().getTable("CustomView");
    }

    public TableInfo getTableInfoDbUserSchema()
    {
        return getDbSchema().getTable("DbUserSchema");
    }

    public void containerDeleted(Container c) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Container", c.getId());
        Table.delete(getTableInfoQuerySnapshotDef(), filter);
        Table.delete(getTableInfoCustomView(), filter);
        Table.delete(getTableInfoQueryDef(), filter);
        Table.delete(getTableInfoDbUserSchema(), filter);
    }

    static public final ContainerManager.ContainerListener CONTAINER_LISTENER = new ContainerManager.ContainerListener()
    {
        public void containerCreated(Container c)
        {

        }

        public void containerDeleted(Container c, User user)
        {
            try
            {
                QueryManager.get().containerDeleted(c);
            }
            catch (SQLException e)
            {
                _log.error("Error", e);
            }
        }

        public void propertyChange(PropertyChangeEvent evt)
        {
        }

    };
}
