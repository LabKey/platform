/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolAction;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public ExpProtocol getParentProtocol()
    {
        return ExperimentService.get().getExpProtocol(_action.getParentProtocolId());
    }

    public ExpProtocol getChildProtocol()
    {
        return ExperimentService.get().getExpProtocol(_action.getChildProtocolId());
    }

    public int getRowId()
    {
        return _action.getRowId();
    }

    public int getActionSequence()
    {
        return _action.getSequence();
    }

    public List<ExpProtocolActionImpl> getPredecessors()
    {
        final List<ExpProtocolActionImpl> ret = new ArrayList<>();
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("ActionId"), getRowId());

        new TableSelector(ExperimentServiceImpl.get().getTinfoProtocolActionPredecessor(), new CsvSet("PredecessorId,ActionId"), filter, null).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                ret.add(fromRowId(rs.getInt("PredecessorId")));
            }
        });

        return ret;
    }

    public List<ExpProtocolActionImpl> getSuccessors()
    {
        final List<ExpProtocolActionImpl> ret = new ArrayList<>();
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("PredecessorId"), getRowId());

        new TableSelector(ExperimentServiceImpl.get().getTinfoProtocolActionPredecessor(), new CsvSet("ActionId,PredecessorId"), filter, null).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                ret.add(fromRowId(rs.getInt("ActionId")));
            }
        });

        return ret;
    }

    public void addSuccessor(User user, ExpProtocolAction successor) throws Exception
    {
        Map<String, Integer> map = new HashMap<>();
        map.put("PredecessorId", getRowId());
        map.put("ActionId", successor.getRowId());
        Table.insert(user, ExperimentServiceImpl.get().getTinfoProtocolActionPredecessor(), map);
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
