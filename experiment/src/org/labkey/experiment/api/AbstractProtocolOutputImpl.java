/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.User;

import java.util.List;
import java.util.ArrayList;

/*
* User: jeckels
* Date: Jul 28, 2008
*/
public abstract class AbstractProtocolOutputImpl<Type extends ProtocolOutput> extends ExpIdentifiableBaseImpl<Type>
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
        _object.setContainer(getContainer().getId());
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

    public String getContainerId()
    {
        return _object.getContainer();
    }

    public void setContainerId(String containerId)
    {
        _object.setContainer(containerId);
    }
}