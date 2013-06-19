/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpProtocolAction;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.security.User;
import org.labkey.api.collections.CsvSet;
import org.apache.log4j.Logger;

import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.*;

public class ExpProtocolActionImpl implements ExpProtocolAction
{
    static final private Logger _log = Logger.getLogger(ExpProtocolActionImpl.class);
    ProtocolAction _action;
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

    public ExpProtocolAction[] getPredecessors()
    {
        ResultSet rs = null;
        try
        {
            List<ExpProtocolAction> ret = new ArrayList<>();
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("ActionId", getRowId());
            rs = Table.select(ExperimentServiceImpl.get().getTinfoProtocolActionPredecessor(), new CsvSet("PredecessorId,ActionId"), filter, null);
            while (rs.next())
            {
                ret.add(fromRowId(rs.getInt("PredecessorId")));
            }
            return ret.toArray(new ExpProtocolAction[ret.size()]);
        }
        catch (SQLException e)
        {
            _log.error("Error", e);
            return new ExpProtocolAction[0];
        }
        finally
        {
            if (rs != null) { try { rs.close(); } catch (SQLException e) {} }
        }

    }

    public ExpProtocolAction[] getSuccessors()
    {
        ResultSet rs = null;
        try
        {
            List<ExpProtocolAction> ret = new ArrayList<>();
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("PredecessorId", getRowId());
            rs = Table.select(ExperimentServiceImpl.get().getTinfoProtocolActionPredecessor(), new CsvSet("ActionId,PredecessorId"), filter, null);
            while (rs.next())
            {
                ret.add(fromRowId(rs.getInt("ActionId")));
            }
            return ret.toArray(new ExpProtocolAction[ret.size()]);
        }
        catch (SQLException e)
        {
            _log.error("Error", e);
            return new ExpProtocolAction[0];
        }
        finally
        {
            if (rs != null) { try { rs.close(); } catch (SQLException e) {} }
        }
    }

    public void addSuccessor(User user, ExpProtocolAction successor) throws Exception
    {
        Map<String, Integer> map = new HashMap<>();
        map.put("PredecessorId", getRowId());
        map.put("ActionId", successor.getRowId());
        Table.insert(user, ExperimentServiceImpl.get().getTinfoProtocolActionPredecessor(), map);
    }

    public static ExpProtocolActionImpl[] fromProtocolActions(ProtocolAction[] actions)
    {
        ExpProtocolActionImpl[] result = new ExpProtocolActionImpl[actions.length];
        for (int i = 0; i < actions.length; i++)
        {
            result[i] = new ExpProtocolActionImpl(actions[i]);
        }
        return result;
    }
}
