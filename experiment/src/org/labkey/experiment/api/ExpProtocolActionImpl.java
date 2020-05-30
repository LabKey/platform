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

package org.labkey.experiment.api;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpProtocolAction;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ExpProtocolActionImpl implements ExpProtocolAction
{
    private final ProtocolAction _action;

    static public ExpProtocolActionImpl fromRowId(int id)
    {
        ProtocolAction action = new TableSelector(ExperimentServiceImpl.get().getTinfoProtocolAction()).getObject(id, ProtocolAction.class);
        if (action == null)
            return null;
        return new ExpProtocolActionImpl(action);
    }

    public ExpProtocolActionImpl(ProtocolAction action)
    {
        _action = action;
    }

    @Override
    public ExpProtocolImpl getParentProtocol()
    {
        return ExperimentServiceImpl.get().getExpProtocol(_action.getParentProtocolId());
    }

    @Override
    public ExpProtocolImpl getChildProtocol()
    {
        return ExperimentServiceImpl.get().getExpProtocol(_action.getChildProtocolId());
    }

    @Override
    public int getRowId()
    {
        return _action.getRowId();
    }

    @Override
    public int getActionSequence()
    {
        return _action.getSequence();
    }

    @Override
    public List<ExpProtocolActionImpl> getPredecessors()
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("ActionId"), getRowId());
        TableInfo table = ExperimentServiceImpl.get().getTinfoProtocolActionPredecessor();
        return new TableSelector(table, table.getColumns("PredecessorId"), filter, null).stream(Integer.class).map(ExpProtocolActionImpl::fromRowId).collect(Collectors.toList());
    }

    @Override
    public List<ExpProtocolActionImpl> getSuccessors()
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("PredecessorId"), getRowId());
        TableInfo table = ExperimentServiceImpl.get().getTinfoProtocolActionPredecessor();
        return new TableSelector(table, table.getColumns("ActionId"), filter, null).stream(Integer.class).map(ExpProtocolActionImpl::fromRowId).collect(Collectors.toList());
    }

    @Override
    public void addSuccessor(User user, ExpProtocolAction successor)
    {
        ExperimentServiceImpl.get().insertProtocolPredecessor(user, successor.getRowId(), getRowId());
    }

    public boolean equals(Object obj)
    {
        if (obj == null || obj.getClass() != getClass())
            return false;
        return ((ExpProtocolAction) obj).getRowId() == getRowId();
    }

    public int hashCode()
    {
        return getRowId() ^ getClass().hashCode();
    }

    public static List<ExpProtocolActionImpl> fromProtocolActions(List<ProtocolAction> actions)
    {
        List<ExpProtocolActionImpl> result = new ArrayList<>(actions.size());
        for (ProtocolAction action : actions)
        {
            result.add(new ExpProtocolActionImpl(action));
        }
        return result;
    }
}
