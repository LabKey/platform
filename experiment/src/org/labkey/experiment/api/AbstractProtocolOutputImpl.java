/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.labkey.api.exp.api.*;
import org.labkey.api.security.User;
import org.labkey.api.data.*;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.security.UserManager;

import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.sql.ResultSet;
import java.sql.SQLException;

/*
* User: jeckels
* Date: Jul 28, 2008
*/
public abstract class AbstractProtocolOutputImpl<Type extends ProtocolOutput> extends ExpIdentifiableBaseImpl<Type> implements ExpProtocolOutput
{
    private ExpProtocolApplicationImpl _sourceApp;
    private List<ExpProtocolApplication> _successorAppList;
    private List<Integer> _successorRunIdList;

    public AbstractProtocolOutputImpl(Type object)
    {
        super(object);
    }

    public List<ExpProtocolApplication> getSuccessorApps()
    {
        if (null == _successorAppList)
            throw new IllegalStateException("successorAppList not populated");
        return _successorAppList;
    }

    public List<ExpRun> getSuccessorRuns()
    {
        if (null == _successorRunIdList)
            throw new IllegalStateException("successorRunIdList not populated");
        List<ExpRun> result = new ArrayList<ExpRun>();
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
            _successorRunIdList = new ArrayList<Integer>();
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
            _successorRunIdList = new ArrayList<Integer>();
        }
        markSuccessorAppsAsPopulated();
    }

    public void markSuccessorAppsAsPopulated()
    {
        if (_successorAppList == null)
        {
            _successorAppList = new ArrayList<ExpProtocolApplication>();
        }
    }

    public ExpProtocol getSourceProtocol()
    {
        ExpProtocolApplication protApp = getSourceApplication();
        if (protApp != null)
        {
            return protApp.getProtocol();
        }
        return null;
    }

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

    public int getRowId()
    {
        return _object.getRowId();
    }

    public User getCreatedBy()
    {
        return _object.getCreatedBy() == null ? null : UserManager.getUser(_object.getCreatedBy().intValue());
    }

    public User getModifiedBy()
    {
        return _object.getModifiedBy() == null ? null : UserManager.getUser(_object.getModifiedBy().intValue());
    }

    public Date getModified()
    {
        return _object.getModified();
    }

    public ExpRunImpl getRun()
    {
        if (_object.getRunId() == null)
        {
            return null;
        }
        return ExperimentServiceImpl.get().getExpRun(_object.getRunId().intValue());
    }


    public Integer getRunId()
    {
        return _object.getRunId();
    }

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

    public void setRun(ExpRun run)
    {
        ensureUnlocked();
        if (run != null && run.getRowId() == 0)
        {
            throw new IllegalArgumentException();
        }
        _object.setRunId(run == null ? null : run.getRowId());
    }

    public void setCpasType(String type)
    {
        ensureUnlocked();
        _object.setCpasType(type);
    }

    public Container getContainer()
    {
        return _object.getContainer();
    }

    public void setContainer(Container container)
    {
        ensureUnlocked();
        _object.setContainer(container);
    }

    public Date getCreated()
    {
        return _object.getCreated();
    }

    protected ExpProtocolApplication[] getTargetApplications(SimpleFilter filter, TableInfo inputTable)
    {
        ResultSet rs = null;
        try
        {
            rs = Table.select(inputTable, new CsvSet("TargetApplicationId,DataId"), filter, null);
            List<ExpProtocolApplication> ret = new ArrayList<ExpProtocolApplication>();
			int targetCol = rs.findColumn("TargetApplicationId");
            while (rs.next())
            {
                ret.add(ExperimentService.get().getExpProtocolApplication(rs.getInt(targetCol)));
            }
            return ret.toArray(new ExpProtocolApplication[ret.size()]);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (rs != null) { try { rs.close(); } catch (SQLException e) {} }
        }
    }

    protected ExpRun[] getTargetRuns(TableInfo inputTable, String rowIdColumnName)
    {
        SQLFragment sql = new SQLFragment("SELECT r.* FROM ");
        sql.append(ExperimentService.get().getTinfoExperimentRun(), "r");
        sql.append("\nWHERE r.RowId IN (SELECT pa.RunId \nFROM ");
        sql.append(ExperimentServiceImpl.get().getTinfoProtocolApplication(), "pa");
        sql.append("\nINNER JOIN ");
        sql.append(inputTable, "i");
        sql.append(" ON pa.RowId = i.TargetApplicationId AND i." + rowIdColumnName + " = ?)");
        sql.add(getRowId());
        ExperimentRun[] runs = new SqlSelector(ExperimentService.get().getSchema(), sql).getArray(ExperimentRun.class);
        return ExpRunImpl.fromRuns(runs);
    }
}
