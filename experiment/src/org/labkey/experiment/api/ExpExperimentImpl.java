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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.util.URLHelper;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExpExperimentImpl extends ExpIdentifiableEntityImpl<Experiment> implements ExpExperiment
{
    public ExpExperimentImpl(Experiment experiment)
    {
        super(experiment);
    }

    public Container getContainer()
    {
        return _object.getContainer();
    }

    public URLHelper detailsURL()
    {
        return null;
    }

    public int getRowId()
    {
        return _object.getRowId();
    }

    public ExpRunImpl[] getRuns()
    {
        String sql = "SELECT ER.* FROM " + ExperimentServiceImpl.get().getTinfoExperiment() + " E "
                + " INNER JOIN " + ExperimentServiceImpl.get().getTinfoRunList()  + " RL ON (E.RowId = RL.ExperimentId) "
                + " INNER JOIN " + ExperimentServiceImpl.get().getTinfoExperimentRun()  + " ER ON (ER.RowId = RL.ExperimentRunId) "
                + " WHERE E.LSID = ? ORDER BY ER.RowId" ;

        return ExpRunImpl.fromRuns(new SqlSelector(ExperimentServiceImpl.get().getExpSchema(), sql, getLSID()).getArray(ExperimentRun.class));
    }

    public ExpRun[] getRuns(@Nullable ExpProtocol parentProtocol, ExpProtocol childProtocol)
    {
        SQLFragment sql = new SQLFragment(" SELECT ER.* "
                    + " FROM exp.ExperimentRun ER "
                    + " INNER JOIN exp.RunList RL ON ( ER.RowId = RL.ExperimentRunId ) "
                    + " WHERE RL.ExperimentId = ? ");
        sql.add(getRowId());
        if (parentProtocol != null)
        {
            sql.append("\nAND ER.ProtocolLSID = ?");
            sql.add(parentProtocol.getLSID());
        }
        if (childProtocol != null)
        {
            sql.append("\nAND ER.RowId IN (SELECT PA.RunId "
                + " FROM exp.ProtocolApplication PA "
                + " WHERE PA.ProtocolLSID = ? ) ");
            sql.add(childProtocol.getLSID());
        }
        ExperimentRun[] runs = new SqlSelector(ExperimentService.get().getSchema(), sql).getArray(ExperimentRun.class);
        return ExpRunImpl.fromRuns(runs);
    }

    public ExpProtocol getBatchProtocol()
    {
        if (_object.getBatchProtocolId() == null)
        {
            return null;
        }
        return ExperimentService.get().getExpProtocol(_object.getBatchProtocolId().intValue());
    }

    public void setBatchProtocol(ExpProtocol protocol)
    {
        ensureUnlocked();
        _object.setBatchProtocolId(protocol == null ? null : protocol.getRowId());
    }

    public List<ExpProtocol> getAllProtocols()
    {
        String sql = "SELECT p.* FROM " + ExperimentServiceImpl.get().getTinfoProtocol() + " p, " + ExperimentServiceImpl.get().getTinfoExperimentRun() + " r WHERE p.LSID = r.ProtocolLSID AND r.RowId IN (SELECT ExperimentRunId FROM " + ExperimentServiceImpl.get().getTinfoRunList() + " WHERE ExperimentId = ?)";
        return Arrays.<ExpProtocol>asList(ExpProtocolImpl.fromProtocols(new SqlSelector(ExperimentServiceImpl.get().getSchema(), sql, getRowId()).getArray(Protocol.class)));
    }

    public void removeRun(User user, ExpRun run)
    {
        SQLFragment sql = new SQLFragment("DELETE FROM " + ExperimentServiceImpl.get().getTinfoRunList() +
                " WHERE ExperimentId = ? AND ExperimentRunId = ?", getRowId(), run.getRowId());

        try
        {
            ExperimentServiceImpl.get().ensureTransaction();

            new SqlExecutor(ExperimentServiceImpl.get().getExpSchema()).execute(sql);

            ExperimentServiceImpl.get().auditRunEvent(user, run.getProtocol(), run, this, "The run '" + run.getName() + "' was removed from the run group '" + getName() + "'");

            ExperimentServiceImpl.get().commitTransaction();
        }
        finally
        {
            ExperimentServiceImpl.get().closeTransaction();
        }
    }

    public void addRuns(User user, ExpRun... newRuns)
    {
        try (DbScope.Transaction transaction = ExperimentServiceImpl.get().getExpSchema().getScope().ensureTransaction())
        {
            ExpRun[] existingRuns = getRuns();
            Set<Integer> existingRunIds = new HashSet<>();
            for (ExpRun run : newRuns)
            {
                if (_object.getBatchProtocolId() != null && run.getProtocol().getRowId() != _object.getBatchProtocolId().intValue())
                {
                    throw new IllegalArgumentException("Attempting to add a run of a different protocol to a batch.");
                }
            }

            for (ExpRun er : existingRuns)
            {
                existingRunIds.add(er.getRowId());
            }

            String sql = "INSERT INTO " + ExperimentServiceImpl.get().getTinfoRunList() + " ( ExperimentId, ExperimentRunId, Created, CreatedBy )  VALUES ( ? , ?, ? , ? )";
            for (ExpRun run : newRuns)
            {
                if (!existingRunIds.contains(run.getRowId()))
                {
                    SQLFragment fragment = new SQLFragment(sql, getRowId(), run.getRowId(), new Date(), user == null ? null : user.getUserId());
                    new SqlExecutor(ExperimentServiceImpl.get().getExpSchema()).execute(fragment);

                    ExperimentServiceImpl.get().auditRunEvent(user, run.getProtocol(), run, this, "The run '" + run.getName() + "' was added to the run group '" + getName() + "'");
                }
            }

            transaction.commit();
        }
    }

    public void save(User user)
    {
        save(user, ExperimentServiceImpl.get().getTinfoExperiment());
    }

    public void delete(User user)
    {
        if (!getContainer().hasPermission(user, DeletePermission.class))
        {
            throw new IllegalStateException("Not permitted");
        }

        try (DbScope.Transaction t = ExperimentServiceImpl.get().getExpSchema().getScope().ensureTransaction())
        {
            // If we're a batch, delete all the runs too
            if (_object.getBatchProtocolId() != null)
            {
                for (ExpRunImpl expRun : getRuns())
                {
                    expRun.delete(user);
                }
            }

            SqlExecutor executor = new SqlExecutor(ExperimentServiceImpl.get().getExpSchema());

            SQLFragment sql = new SQLFragment("DELETE FROM " + ExperimentServiceImpl.get().getTinfoRunList()
                    + " WHERE ExperimentId IN ("
                    + " SELECT E.RowId FROM " + ExperimentServiceImpl.get().getTinfoExperiment() + " E "
                    + " WHERE E.RowId = " + getRowId()
                    + " AND E.Container = ? )", getContainer());
            executor.execute(sql);

            OntologyManager.deleteOntologyObjects(getContainer(), getLSID());

            sql = new SQLFragment("DELETE FROM " + ExperimentServiceImpl.get().getTinfoExperiment()
                    + " WHERE RowId = " + getRowId()
                    + " AND Container = ?", getContainer());
            executor.execute(sql);

            t.commit();
        }
    }

    public void setHidden(boolean hidden)
    {
        ensureUnlocked();
        _object.setHidden(hidden);
    }
    
    public boolean isHidden()
    {
        return _object.isHidden();
    }

    public void setContainer(Container container)
    {
        ensureUnlocked();
        _object.setContainer(container);
    }

    public String getComments()
    {
        return _object.getComments();
    }

    public void setComments(String comments)
    {
        ensureUnlocked();
        _object.setComments(comments);
    }

    public static ExpExperimentImpl[] fromExperiments(Experiment[] experiments)
    {
        ExpExperimentImpl[] result = new ExpExperimentImpl[experiments.length];
        for (int i = 0; i < experiments.length; i++)
        {
            result[i] = new ExpExperimentImpl(experiments[i]);
        }
        return result;
    }
}
