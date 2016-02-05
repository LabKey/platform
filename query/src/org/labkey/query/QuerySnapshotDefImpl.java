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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
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
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryDefCache;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.persist.QuerySnapshotDef;

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
    final static private Logger LOG = Logger.getLogger(QuerySnapshotDefImpl.class);

    // data models
    private QueryDef _queryDef;
    private QuerySnapshotDef _snapshotDef;
    private boolean _dirty;


    public QuerySnapshotDefImpl(QueryDefinition queryDef, Container container, String name)
    {
        _snapshotDef = new QuerySnapshotDef();

        _snapshotDef.setName(name);
        _snapshotDef.setSchema(queryDef.getSchemaName());
        _snapshotDef.setContainer(container.getId());

        // if this is a table based query view, we just need to save the table name, else create a copy of the query
        // definition for the snapshot to refer back to on updates.
        if (queryDef.isTableQueryDefinition())
        {
            _snapshotDef.setQueryTableName(queryDef.getName());
            _snapshotDef.setQueryTableContainer(queryDef.getContainer().getId());
        }
        else
        {
            QueryDefinitionImpl qd = new CustomQueryDefinitionImpl(queryDef.getUser(), queryDef.getContainer(), queryDef.getSchemaPath(), queryDef.getName() + "_" + name);

            qd.setMetadataXml(queryDef.getMetadataXml());
            qd.setSql(queryDef.getSql());
            qd.setDescription(queryDef.getDescription());
            qd.setIsHidden(true);
            qd.setIsSnapshot(true);
            
            _queryDef = qd.getQueryDef();
        }
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

    public String getQueryTableName()
    {
        return _snapshotDef.getQueryTableName();
    }

    @Override
    public String getQueryTableContainerId()
    {
        return _snapshotDef.getQueryTableContainer();
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
                _queryDef = QueryDefCache.getQueryDefById(getContainer(), _snapshotDef.getQueryDefId());
            }
            if (_queryDef == null)
            {
                LOG.warn("Could not find query with queryDefId " + _snapshotDef.getQueryDefId() + " for query snapshot " + _snapshotDef.getName() + " in " + _snapshotDef.lookupContainer());
            }
            return _queryDef == null ? null : new CustomQueryDefinitionImpl(user, getContainer(), _queryDef);
        }
        else if (_snapshotDef.getQueryTableName() != null)
        {
            Container queryTableContainer = ContainerManager.getForId(_snapshotDef.getQueryTableContainer());
            if (queryTableContainer != null)
            {
                UserSchema schema = QueryService.get().getUserSchema(user, queryTableContainer, _snapshotDef.getSchema());
                if (schema == null)
                {
                    LOG.warn("Could not find query with schema " + _snapshotDef.getSchema() + " for query snapshot " + _snapshotDef.getName() + " in " + _snapshotDef.lookupContainer());
                }
                else
                {
                    QueryDefinition result = schema.getQueryDefForTable(_snapshotDef.getQueryTableName());
                    if (result == null)
                    {
                        LOG.warn("Could not find query with queryTableName " + _snapshotDef.getQueryTableName() + " for query snapshot " + _snapshotDef.getName() + " in " + _snapshotDef.lookupContainer());
                    }
                    return result;
                }
            }
            else
            {
                LOG.warn("Could not find query with queryTableContainer " + _snapshotDef.getQueryTableContainer() + " for query snapshot " + _snapshotDef.getName() + " in " + _snapshotDef.lookupContainer());
            }
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
        QueryManager.get().delete(_snapshotDef);
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
        List<FieldKey> ret = new ArrayList<>();
        if (values != null)
        {
            for (String entry : values)
                ret.add(FieldKey.fromString(entry));
        }
        return Collections.unmodifiableList(ret);
    }

    public void setColumns(List<FieldKey> columns)
    {
        edit().setColumns(StringUtils.join(columns.iterator(), "&"));
    }

    @Override
    public List<Integer> getParticipantGroups()
    {
        String[] values = StringUtils.split(_snapshotDef.getParticipantGroups(), ",");
        List<Integer> ret = new ArrayList<>();
        if (values != null)
            for (String entry : values)
            {
                Integer group = NumberUtils.createInteger(entry);
                if (group != null)
                    ret.add(group);
            }
        return Collections.unmodifiableList(ret);
    }

    @Override
    public void setParticipantGroups(List<Integer> groups)
    {
        edit().setParticipantGroups(StringUtils.join(groups.iterator(), ","));
    }

    @Override
    public void setQueryTableName(String queryTableName)
    {
        edit().setQueryTableName(queryTableName);
    }

    @Override
    public void setName(String name)
    {
        edit().setName(name);
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

    @Override
    public void setOptionsId(Integer optionsId)
    {
        _snapshotDef.setOptionsId(optionsId);
    }

    @Override
    public Integer getOptionsId()
    {
        return _snapshotDef.getOptionsId();
    }

    public void save(User user) throws Exception
    {
        if (!_dirty)
            return;
        if (isNew())
        {
            _snapshotDef = QueryManager.get().insert(user, _queryDef, _snapshotDef);
        }
        else
        {
            if (QueryManager.get().getQuerySnapshotDef(_snapshotDef.getRowId()) != null)
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
