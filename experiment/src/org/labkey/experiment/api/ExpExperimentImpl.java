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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.query.ExpExperimentTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExpExperimentImpl extends ExpIdentifiableEntityImpl<Experiment> implements ExpExperiment
{
    // For serialization
    protected ExpExperimentImpl() {}

    public ExpExperimentImpl(Experiment experiment)
    {
        super(experiment);
    }

    @Override
    public Container getContainer()
    {
        return _object.getContainer();
    }

    @Override
    public ActionURL detailsURL()
    {
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getExperimentDetailsURL(getContainer(), this);
    }

    @Override
    public @Nullable QueryRowReference getQueryRowReference()
    {
        return getQueryRowReference(null);
    }

    @Override
    public @Nullable QueryRowReference getQueryRowReference(@Nullable User user)
    {
        // If an ExpExperiment represents an Assay Batch, then resolve the QueryRowReference more specifically
        // to the Assay Batch.
        // NK: This could be done via a Handler pattern (similar to ExperimentProtocolHandler) but given
        // the current narrow use case for Assay Batches I've elected to just resolve them directly here.
        ExpProtocol protocol = getBatchProtocol();
        if (protocol != null)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (provider != null)
            {
                AssayProtocolSchema schema = provider.createProtocolSchema(user, protocol.getContainer(), protocol, null);
                return new QueryRowReference(protocol.getContainer(), schema.getSchemaPath(), AssayProtocolSchema.BATCHES_TABLE_NAME, FieldKey.fromParts(ExpExperimentTable.Column.RowId.name()), getRowId());
            }
        }

        return new QueryRowReference(getContainer(), ExpSchema.SCHEMA_EXP, ExpSchema.TableType.RunGroups.name(), FieldKey.fromParts(ExpExperimentTable.Column.RowId.name()), getRowId());
    }

    @Override
    public int getRowId()
    {
        return _object.getRowId();
    }

    @Override
    public List<ExpRunImpl> getRuns()
    {
        String sql = "SELECT ER.* FROM " + ExperimentServiceImpl.get().getTinfoExperiment() + " E "
                + " INNER JOIN " + ExperimentServiceImpl.get().getTinfoRunList()  + " RL ON (E.RowId = RL.ExperimentId) "
                + " INNER JOIN " + ExperimentServiceImpl.get().getTinfoExperimentRun()  + " ER ON (ER.RowId = RL.ExperimentRunId) "
                + " WHERE E.LSID = ? ORDER BY ER.RowId" ;

        return ExpRunImpl.fromRuns(new SqlSelector(ExperimentServiceImpl.get().getExpSchema(), sql, getLSID()).getArrayList(ExperimentRun.class));
    }

    @Override
    public List<ExpRunImpl> getRuns(@Nullable ExpProtocol parentProtocol, ExpProtocol childProtocol)
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
        return ExpRunImpl.fromRuns(new SqlSelector(ExperimentService.get().getSchema(), sql).getArrayList(ExperimentRun.class));
    }

    @Override
    public @Nullable ExpProtocol getBatchProtocol()
    {
        if (_object.getBatchProtocolId() == null)
        {
            return null;
        }
        return ExperimentService.get().getExpProtocol(_object.getBatchProtocolId().intValue());
    }

    @Override
    public void setBatchProtocol(ExpProtocol protocol)
    {
        ensureUnlocked();
        _object.setBatchProtocolId(protocol == null ? null : protocol.getRowId());
    }

    @Override
    public List<ExpProtocolImpl> getAllProtocols()
    {
        String sql = "SELECT p.* FROM " + ExperimentServiceImpl.get().getTinfoProtocol() + " p, " + ExperimentServiceImpl.get().getTinfoExperimentRun() + " r WHERE p.LSID = r.ProtocolLSID AND r.RowId IN (SELECT ExperimentRunId FROM " + ExperimentServiceImpl.get().getTinfoRunList() + " WHERE ExperimentId = ?)";
        return ExpProtocolImpl.fromProtocols(new SqlSelector(ExperimentServiceImpl.get().getSchema(), sql, getRowId()).getArrayList(Protocol.class));
    }

    @Override
    public void removeRun(User user, ExpRun run)
    {
        SQLFragment sql = new SQLFragment("DELETE FROM " + ExperimentServiceImpl.get().getTinfoRunList() +
                " WHERE ExperimentId = ? AND ExperimentRunId = ?", getRowId(), run.getRowId());

        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            new SqlExecutor(ExperimentServiceImpl.get().getExpSchema()).execute(sql);

            // Clear out the experimentrun.batchId column if it was set to this ExpExperiment.
            if (this.equals(run.getBatch()))
            {
                ((ExpRunImpl) run).setBatchId(null);
                try
                {
                    run.save(user);
                }
                catch (BatchValidationException e)
                {
                    throw new UnexpectedException(e);
                }
            }

            ExperimentServiceImpl.get().auditRunEvent(user, run.getProtocol(), run, this, "The run '" + run.getName() + "' was removed from the run group '" + getName() + "'");
            transaction.commit();
        }
    }

    @Override
    public void addRuns(User user, ExpRun... newRuns)
    {
        try (DbScope.Transaction transaction = ExperimentServiceImpl.get().ensureTransaction())
        {
            List<ExpRunImpl> existingRuns = getRuns();
            Set<Integer> existingRunIds = new HashSet<>();
            for (ExpRun run : newRuns)
            {
                if (_object.getBatchProtocolId() != null && run.getProtocol().getRowId() != _object.getBatchProtocolId().intValue())
                {
                    throw new IllegalArgumentException("Attempting to add a run of a different protocol (LSID: " + run.getProtocol().getLSID() + ") to a batch (LSID: " + getBatchProtocol().getLSID() + ")");
                }
            }

            for (ExpRun er : existingRuns)
            {
                existingRunIds.add(er.getRowId());
            }

            Integer batchId = _object.getBatchProtocolId() != null ? getRowId() : null;

            String sql = "INSERT INTO " + ExperimentServiceImpl.get().getTinfoRunList() + " ( ExperimentId, ExperimentRunId, Created" + (user == null ? " " : ", CreatedBy ") + ") VALUES ( ?, ?, ?" + (user == null ? " " : ", ? ") + ")";
            for (ExpRun run : newRuns)
            {
                if (!existingRunIds.contains(run.getRowId()))
                {
                    SQLFragment fragment = new SQLFragment(sql, getRowId(), run.getRowId(), new Date());
                    if (user != null)
                    {
                        fragment.add(user.getUserId());
                    }
                    new SqlExecutor(ExperimentServiceImpl.get().getExpSchema()).execute(fragment);

                    // Set the experimentrun.batchId column to this ExpExperiment if it is a batch.
                    // If this experiment is not a batch, don't clear the run's current batchId.
                    if (batchId != null)
                    {
                        ((ExpRunImpl) run).setBatchId(batchId);
                        try
                        {
                            run.save(user);
                        }
                        catch (BatchValidationException e)
                        {
                            throw new UnexpectedException(e);
                        }
                    }

                    ExperimentServiceImpl.get().auditRunEvent(user, run.getProtocol(), run, this, "The run '" + run.getName() + "' was added to the run group '" + getName() + "'");
                }
            }

            transaction.commit();
        }
    }

    @Override
    public void save(User user)
    {
        try (DbScope.Transaction tx = ExperimentServiceImpl.get().ensureTransaction())
        {
            save(user, ExperimentServiceImpl.get().getTinfoExperiment(), false);

            tx.addCommitTask(() -> ExperimentServiceImpl.get().onAfterExperimentSaved(this, getContainer(), user), DbScope.CommitTaskOption.POSTCOMMIT);

            tx.commit();
        }
    }

    @Override
    public void delete(User user)
    {
        ExperimentServiceImpl.get().deleteExpExperimentByRowId(getContainer(), user, getRowId());
    }

    public void setHidden(boolean hidden)
    {
        ensureUnlocked();
        _object.setHidden(hidden);
    }
    
    @Override
    public boolean isHidden()
    {
        return _object.isHidden();
    }

    @Override
    public void setContainer(Container container)
    {
        ensureUnlocked();
        _object.setContainer(container);
    }

    @Override
    public String getComments()
    {
        return _object.getComments();
    }

    @Override
    public void setComments(String comments)
    {
        ensureUnlocked();
        _object.setComments(comments);
    }

    public static List<ExpExperimentImpl> fromExperiments(Experiment[] experiments)
    {
        List<ExpExperimentImpl> result = new ArrayList<>(experiments.length);
        for (Experiment experiment : experiments)
        {
            result.add(new ExpExperimentImpl(experiment));
        }
        return Collections.unmodifiableList(result);
    }
}
