/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collections;
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
        _successorRunIdList.add(runId);
    }

    public void setSuccessorAppList(ArrayList<ExpProtocolApplication> successorAppList)
    {
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
        if (_object.getSourceProtocolLSID() == null)
        {
            return null;
        }
        return ExperimentService.get().getExpProtocol(_object.getSourceProtocolLSID());
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
        _sourceApp = ExperimentServiceImpl.get().getExpProtocolApplication(_object.getSourceApplicationId());
        return _sourceApp;
    }

    public int getRowId()
    {
        return _object.getRowId();
    }

    public User getCreatedBy()
    {
        ExpRunImpl run = getRun();
        return null == run ? null : run.getCreatedBy();
    }

    public User getModifiedBy()
    {
        ExpRunImpl run = getRun();
        return null == run ? null : run.getModifiedBy();
    }

    public Date getModified()
    {
        ExpRunImpl run = getRun();
        return null == run ? null : run.getModified();
    }

    public ExpRunImpl getRun()
    {
        if (_object.getRunId() == null)
        {
            return null;
        }
        return ExperimentServiceImpl.get().getExpRun(_object.getRunId());
    }

    public void setSourceApplication(ExpProtocolApplication app)
    {
        if (app != null && app.getRowId() == 0)
        {
            throw new IllegalArgumentException();
        }
        _object.setSourceApplicationId(app == null ? null : app.getRowId());
        _object.setSourceProtocolLSID(app == null ? null : app.getProtocol().getLSID());
        _object.setContainer(getContainer());
        _object.setRunId(app == null ? null : app.getRun().getRowId());
    }

    public void setSourceProtocol(ExpProtocol protocol)
    {
        if (protocol != null && protocol.getLSID() == null)
        {
            throw new IllegalArgumentException();
        }
        _object.setSourceProtocolLSID(protocol == null ? null : protocol.getLSID());
    }

    public void setRun(ExpRun run)
    {
        if (run != null && run.getRowId() == 0)
        {
            throw new IllegalArgumentException();
        }
        _object.setRunId(run == null ? null : run.getRowId());
    }

    public void setCpasType(String type)
    {
        _object.setCpasType(type);
    }

    public Container getContainer()
    {
        return _object.getContainer();
    }

    public void setContainer(Container container)
    {
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
            rs = Table.select(inputTable, Collections.singleton("TargetApplicationId"), filter, null);
            List<ExpProtocolApplication> ret = new ArrayList<ExpProtocolApplication>();
            while (rs.next())
            {
                ret.add(ExperimentService.get().getExpProtocolApplication(rs.getInt(1)));
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
        try
        {
            SQLFragment sql = new SQLFragment("SELECT r.* FROM " + ExperimentService.get().getTinfoExperimentRun() + " r " +
                    "\nWHERE r.RowId IN " +
                    "\n(SELECT pa.RunId" +
                    "\nFROM " + ExperimentServiceImpl.get().getTinfoProtocolApplication() + " pa " +
                    "\nINNER JOIN " + inputTable + " i ON pa.RowId = i.TargetApplicationId AND i." + rowIdColumnName + " = ?)");
            sql.add(getRowId());
            ExperimentRun[] runs = Table.executeQuery(ExperimentService.get().getSchema(), sql.getSQL(), sql.getParams().toArray(new Object[sql.getParams().size()]), ExperimentRun.class);
            return ExpRunImpl.fromRuns(runs);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }
}
