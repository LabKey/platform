/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.persist.QuerySnapshotDef;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
/*
 * User: Karl Lum
 * Date: Jul 14, 2008
 * Time: 1:19:42 PM
 */

public class QuerySnapshotDefImpl implements QuerySnapshotDefinition
{
    final static private Logger log = Logger.getLogger(QuerySnapshotDefImpl.class);
    final static private QueryManager mgr = QueryManager.get();

    // data models
    private QueryDef _queryDef;
    private QuerySnapshotDef _snapshotDef;
    private boolean _dirty;


    public QuerySnapshotDefImpl(QueryDef queryDef, String name)
    {
        this(queryDef.getContainerId(), queryDef.getSchema(), null, name);
        _queryDef = queryDef;
    }

    public QuerySnapshotDefImpl(String containerId, String schema, String queryTableName, String name)
    {
        _snapshotDef = new QuerySnapshotDef();
        _snapshotDef.setContainer(containerId);
        _snapshotDef.setSchema(schema);
        _snapshotDef.setName(name);
        _snapshotDef.setQueryTableName(queryTableName);
        _dirty = true;
    }

    public QuerySnapshotDefImpl(QuerySnapshotDef snapshotDef)
    {
        _snapshotDef = snapshotDef;
    }

    public String getName()
    {
        return _snapshotDef.getName();
    }

    public int getId()
    {
        return _snapshotDef.getRowId();
    }

    public User getCreatedBy()
    {
        return UserManager.getUser(_snapshotDef.getCreatedBy());
    }

    public User getModifiedBy()
    {
        return UserManager.getUser(_snapshotDef.getModifiedBy());
    }

    public Container getContainer()
    {
        return ContainerManager.getForId(_snapshotDef.getContainerId());
    }

    public QueryDefinition getQueryDefinition(User user)
    {
        if (_snapshotDef.getQueryDefId() != null)
        {
            if (_queryDef == null)
            {
                try {
                    QueryDef.Key key = new QueryDef.Key(getContainer(), true);
                    key.setQueryDefId(_snapshotDef.getQueryDefId());
                    _queryDef = key.selectObject();
                }
                catch (SQLException e)
                {
                    throw new RuntimeException(e);
                }
            }
            return _queryDef == null ? null : new CustomQueryDefinitionImpl(user, _queryDef);
        }
        else if (_snapshotDef.getQueryTableName() != null)
        {
            UserSchema schema = QueryService.get().getUserSchema(user, getContainer(), _snapshotDef.getSchema());
            return schema.getQueryDefForTable(_snapshotDef.getQueryTableName());
        }
        return null;
    }

    public boolean canEdit(User user)
    {
        return getContainer().hasPermission(user, AdminPermission.class);
    }

    public void delete(User user) throws Exception
    {
        if (!canEdit(user))
        {
            throw new IllegalAccessException("Access denied");
        }
        QueryManager.get().delete(user, _snapshotDef);
        _snapshotDef = null;
        _queryDef = null;
    }

    protected boolean isNew()
    {
        return _snapshotDef.getRowId() == 0;
    }

    public List<FieldKey> getColumns()
    {
        String[] values = StringUtils.split(_snapshotDef.getColumns(), "&");
        List<FieldKey> ret = new ArrayList<FieldKey>();
        for (String entry : values)
        {
            ret.add(FieldKey.fromString(PageFlowUtil.decode(entry)));
        }
        return Collections.unmodifiableList(ret);
    }

    public void setColumns(List<FieldKey> columns)
    {
        edit().setColumns(StringUtils.join(columns.iterator(), "&"));
    }

    public Date getCreated()
    {
        return _snapshotDef.getCreated();
    }

    public Date getLastUpdated()
    {
        return _snapshotDef.getLastUpdated();
    }

    public void setLastUpdated(Date date)
    {
        edit().setLastUpdated(date);
    }

    public Date getNextUpdate()
    {
        return _snapshotDef.getNextUpdate();
    }

    public void setNextUpdate(Date date)
    {
        edit().setNextUpdate(date);
    }

    public int getUpdateDelay()
    {
        return _snapshotDef.getUpdateDelay();
    }

    public void setUpdateDelay(int delayInSeconds)
    {
        edit().setUpdateDelay(delayInSeconds);
    }

    public void setFilter(String filter)
    {
        edit().setFilter(filter);
    }

    public String getFilter()
    {
        return _snapshotDef.getFilter();
    }

    public void save(User user, Container container) throws Exception
    {
        //setContainer(container);
        if (!_dirty)
            return;
        if (isNew())
        {
            _snapshotDef = QueryManager.get().insert(user, _queryDef, _snapshotDef);
        }
        else
        {
            _snapshotDef = QueryManager.get().update(user, _queryDef, _snapshotDef);
        }
        _dirty = false;
    }

    protected QuerySnapshotDef edit()
    {
        if (_dirty)
            return _snapshotDef;
        _snapshotDef = _snapshotDef.clone();
        _dirty = true;
        return _snapshotDef;
    }
}
