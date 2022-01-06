/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Base class for both types of objects that can be the input and output from an experiment run - material and data.
 * User: jeckels
 * Date: Jul 28, 2008
 */
public abstract class AbstractRunItemImpl<Type extends RunItem> extends ExpIdentifiableBaseImpl<Type> implements ExpRunItem
{
    private ExpProtocolApplicationImpl _sourceApp;
    private List<ExpProtocolApplication> _successorAppList;
    private List<Integer> _successorRunIdList = null;

    // For serialization
    protected AbstractRunItemImpl() {}

    public AbstractRunItemImpl(Type object)
    {
        super(object);
    }

    @Nullable
    @Override
    public String getDescription()
    {
        return _object.getDescription();
    }

    public void setDescription(String description)
    {
        ensureUnlocked();
        _object.setDescription(description);
    }

    @Override
    public List<ExpProtocolApplication> getSuccessorApps()
    {
        if (null == _successorAppList)
            throw new IllegalStateException("successorAppList not populated");
        return _successorAppList;
    }

    @Override
    public List<ExpRun> getSuccessorRuns()
    {
        if (null == _successorRunIdList)
            throw new IllegalStateException("successorRunIdList not populated for '" + this.getName() + "'");
        List<ExpRun> result = new ArrayList<>();
        for (Integer integer : _successorRunIdList)
        {
            result.add(ExperimentService.get().getExpRun(integer.intValue()));
        }
        return result;
    }

    public void addSuccessorRunId(int runId)
    {
        if (_successorRunIdList == null)
        {
            _successorRunIdList = new ArrayList<>();
        }
        _successorRunIdList.add(runId);
    }

    public void setSuccessorAppList(ArrayList<ExpProtocolApplication> successorAppList)
    {
        ensureUnlocked();
        _successorAppList = successorAppList;
    }

    public void markAsPopulated(ExpProtocolApplicationImpl sourceApp)
    {
        _sourceApp = sourceApp;
        if (_successorAppList == null)
        {
            _successorRunIdList = new ArrayList<>();
        }
        markSuccessorAppsAsPopulated();
    }

    public void markSuccessorAppsAsPopulated()
    {
        if (_successorAppList == null)
        {
            _successorAppList = new ArrayList<>();
        }
    }

    @Override
    public ExpProtocol getSourceProtocol()
    {
        ExpProtocolApplication protApp = getSourceApplication();
        if (protApp != null)
        {
            return protApp.getProtocol();
        }
        return null;
    }

    @Override
    @Nullable
    public ExpProtocolApplication getSourceApplication()
    {
        if (null != _sourceApp)
        {
            return _sourceApp;
        }
        if (_object.getSourceApplicationId() == null)
        {
            return null;
        }
        _sourceApp = ExperimentServiceImpl.get().getExpProtocolApplication(_object.getSourceApplicationId().intValue());
        return _sourceApp;
    }

    @Override
    public int getRowId()
    {
        return _object.getRowId();
    }

    protected void setRowId(int rowId)
    {
        _object.setRowId(rowId);
    }

    @Override
    public User getCreatedBy()
    {
        return _object.getCreatedBy() == null ? null : UserManager.getUser(_object.getCreatedBy().intValue());
    }

    @Override
    public User getModifiedBy()
    {
        return _object.getModifiedBy() == null ? null : UserManager.getUser(_object.getModifiedBy().intValue());
    }

    @Override
    public Date getModified()
    {
        return _object.getModified();
    }

    @Override
    @Nullable
    public ExpRunImpl getRun()
    {
        if (_object.getRunId() == null)
        {
            return null;
        }
        return ExperimentServiceImpl.get().getExpRun(_object.getRunId().intValue());
    }


    @Override
    public Integer getRunId()
    {
        return _object.getRunId();
    }

    @Override
    public void setSourceApplication(ExpProtocolApplication app)
    {
        ensureUnlocked();
        if (app != null && app.getRowId() == 0)
        {
            throw new IllegalArgumentException();
        }
        _object.setSourceApplicationId(app == null ? null : app.getRowId());
        _object.setContainer(getContainer());
        _object.setRunId(app == null ? null : app.getRun().getRowId());
        _sourceApp = null;
        _successorAppList = null;
        _successorRunIdList = null;
    }

    @Override
    public void setRun(ExpRun run)
    {
        ensureUnlocked();
        if (run != null && run.getRowId() == 0)
        {
            throw new IllegalArgumentException();
        }
        _object.setRunId(run == null ? null : run.getRowId());
    }

    @Override
    public void setCpasType(String type)
    {
        ensureUnlocked();
        _object.setCpasType(type);
    }

    @Override
    public Container getContainer()
    {
        return _object.getContainer();
    }

    @Override
    public void setContainer(Container container)
    {
        ensureUnlocked();
        _object.setContainer(container);
    }

    @Override
    public Date getCreated()
    {
        return _object.getCreated();
    }

    protected List<ExpProtocolApplicationImpl> getTargetApplications(SimpleFilter filter, TableInfo inputTable)
    {
        List<ExpProtocolApplicationImpl> ret = new ArrayList<>();
        for (Integer id : new TableSelector(inputTable, Collections.singleton("TargetApplicationId"), filter, null).getArrayList(Integer.class))
        {
            ret.add(ExperimentServiceImpl.get().getExpProtocolApplication(id.intValue()));
        }
        return ret;
    }

    protected List<ExpRunImpl> getTargetRuns(TableInfo inputTable, String rowIdColumnName)
    {
        SQLFragment sql = new SQLFragment("SELECT r.* FROM ");
        sql.append(ExperimentService.get().getTinfoExperimentRun(), "r");
        sql.append("\nWHERE r.RowId IN (SELECT pa.RunId \nFROM ");
        sql.append(ExperimentServiceImpl.get().getTinfoProtocolApplication(), "pa");
        sql.append("\nINNER JOIN ");
        sql.append(inputTable, "i");
        sql.append(" ON pa.RowId = i.TargetApplicationId AND i.");
        sql.append(rowIdColumnName);
        sql.append(" = ?)");
        sql.add(getRowId());
        return ExpRunImpl.fromRuns(new SqlSelector(ExperimentService.get().getSchema(), sql).getArrayList(ExperimentRun.class));
    }

    protected HashMap<String,ObjectProperty> getObjectProperties(TableInfo ti)
    {
        HashMap<String,ObjectProperty> ret = new HashMap<>();
        if (null != ti)
        {
            new SqlSelector(ti.getSchema(),"SELECT * FROM " + ti + " WHERE lsid=?",  getLSID()).forEach(rs ->
            {
                for (ColumnInfo c : ti.getColumns())
                {
                    if (c.getPropertyURI() == null || StringUtils.equalsIgnoreCase("lsid", c.getName()) || StringUtils.equalsIgnoreCase("genId", c.getName()))
                        continue;
                    if (c.isMvIndicatorColumn())
                        continue;
                    Object value = c.getValue(rs);
                    String mvIndicator = null;
                    if (null != c.getMvColumnName())
                    {
                        ColumnInfo mv = ti.getColumn(c.getMvColumnName());
                        mvIndicator = null==mv ? null : (String)mv.getValue(rs);
                    }
                    if (null == value && null == mvIndicator)
                        continue;
                    if (null != mvIndicator)
                        value = null;
                    var prop = new ObjectProperty(getLSID(), getContainer(), c.getPropertyURI(), value, null==c.getPropertyType()? PropertyType.getFromJdbcType(c.getJdbcType()) : c.getPropertyType(), c.getName());
                    if (null != mvIndicator)
                        prop.setMvIndicator(mvIndicator);
                    ret.put(c.getPropertyURI(), prop);
                }
            });
        }
        return ret;
    }
}
