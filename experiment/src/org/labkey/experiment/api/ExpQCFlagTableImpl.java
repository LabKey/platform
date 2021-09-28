/*
 * Copyright (c) 2011-2019 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayFlagHandler;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.ExperimentAuditEvent;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpQCFlagTable;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.DataStateManager;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.experiment.ExperimentAuditProvider;

import java.sql.SQLException;
import java.util.Map;

public class ExpQCFlagTableImpl extends ExpTableImpl<ExpQCFlagTable.Column> implements ExpQCFlagTable
{
    private static final Logger LOG = LogManager.getLogger(ExpQCFlagTableImpl.class);

    private AssayProvider _provider;
    private ExpProtocol _assayProtocol;
    private Map<String, String> _columnMapping = new CaseInsensitiveHashMap<>();

    public ExpQCFlagTableImpl(String name, UserSchema schema, ContainerFilter cf)
    {
        super(name, ExperimentServiceImpl.get().getTinfoAssayQCFlag(), schema, cf);
    }

    @Override
    public MutableColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case RowId:
                var rowIdColumnInfo = wrapColumn(alias, _rootTable.getColumn("RowId"));
                rowIdColumnInfo.setFk(new RowIdForeignKey(rowIdColumnInfo));
                rowIdColumnInfo.setHidden(true);
                return rowIdColumnInfo;
            case Run:
                _columnMapping.put(column.name(), "RunId");
                var runColumnInfo = wrapColumn(alias, _rootTable.getColumn("RunId"));
                runColumnInfo.setFk(getExpSchema().getRunIdForeignKey(getContainerFilter()));
                return runColumnInfo;
            case FlagType:
                return wrapColumn(alias, _rootTable.getColumn("FlagType"));
            case Description:
                return wrapColumn(alias, _rootTable.getColumn("Description"));
            case Comment:
                return wrapColumn(alias, _rootTable.getColumn("Comment"));
            case Enabled:
                return wrapColumn(alias, _rootTable.getColumn("Enabled"));
            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            case CreatedBy:
                var createdByColumn = wrapColumn(alias, _rootTable.getColumn("CreatedBy"));
                createdByColumn.setFk(new UserIdQueryForeignKey(getUserSchema()));
                return createdByColumn;
            case Modified:
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            case ModifiedBy:
                var modifiedByColumn = wrapColumn(alias, _rootTable.getColumn("ModifiedBy"));
                modifiedByColumn.setFk(new UserIdQueryForeignKey(getUserSchema()));
                return modifiedByColumn;
            case IntKey1:
                _columnMapping.put(column.name(), alias);
                return wrapColumn(alias, _rootTable.getColumn("IntKey1"));
            case IntKey2:
                _columnMapping.put(column.name(), alias);
                return wrapColumn(alias, _rootTable.getColumn("IntKey2"));
            case Key1:
                _columnMapping.put(column.name(), alias);
                return wrapColumn(alias, _rootTable.getColumn("Key1"));
            case Key2:
                _columnMapping.put(column.name(), alias);
                return wrapColumn(alias, _rootTable.getColumn("Key2"));
        }
        throw new IllegalArgumentException("Unknown column " + column);
    }

    @Override
    protected void populateColumns()
    {
        addColumn(Column.RowId);
        addColumn(Column.Run);
        addColumn(Column.FlagType);
        addColumn(Column.Description);
        setTitleColumn(Column.Description.toString());
        addColumn(Column.Comment);
        addColumn(Column.Enabled);
        addColumn(Column.Created);
        addColumn(Column.CreatedBy);
        addColumn(Column.Modified);
        addColumn(Column.ModifiedBy);

        addColumn(Column.IntKey1).setHidden(true);
        addColumn(Column.IntKey2).setHidden(true);
        addColumn(Column.Key1).setHidden(true);
        addColumn(Column.Key2).setHidden(true);
    }

    @Override
    public void setAssayProtocol(AssayProvider provider, ExpProtocol protocol)
    {
        checkLocked();

        if (_provider != null && !_provider.equals(provider))
        {
            throw new IllegalStateException("Cannot change assay provider when it has already been set to " + provider.getName());
        }
        _provider = provider;

        if (_assayProtocol != null && !_assayProtocol.equals(protocol))
        {
            throw new IllegalStateException("Cannot change assay protocol when it has already been set to " + _assayProtocol.getLSID());
        }
        _assayProtocol = protocol;

        getMutableColumn(Column.Run).setFk(new LookupForeignKey("RowId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return null == _provider ? null :  _provider.createProtocolSchema(_userSchema.getUser(), _userSchema.getContainer(), _assayProtocol, null).createRunsTable(getContainerFilter());
            }
        });

        AssayFlagHandler handler = provider != null ? AssayFlagHandler.getHandler(provider) : null;
        if (handler != null)
        {
            handler.fixupQCFlagTable(this, _provider, _assayProtocol);
        }

        SQLFragment protocolSQL = new SQLFragment("RunId IN (SELECT er.RowId FROM ");
        protocolSQL.append(ExperimentService.get().getTinfoExperimentRun(), "er");
        protocolSQL.append(" WHERE ProtocolLSID = ?)");
        protocolSQL.add(_assayProtocol.getLSID());
        addCondition(protocolSQL, FieldKey.fromParts("ProtocolLSID"));
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        FieldKey containerFieldKey = FieldKey.fromParts("Container");
        clearConditions(containerFieldKey);
        SQLFragment sql = new SQLFragment("RunId IN (SELECT er.RowId FROM ");
        sql.append(ExperimentService.get().getTinfoExperimentRun(), "er");
        sql.append(" WHERE ");
        sql.append(filter.getSQLFragment(getSchema(), new SQLFragment("er.Container"), getContainer()));
        sql.append(")");

        addCondition(sql, containerFieldKey);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new UpdateService(this);
    }

    public class UpdateService extends DefaultQueryUpdateService
    {
        private static final int INSERT = 1;
        private static final int UPDATE = 2;
        private static final int DELETE = 3;

        public UpdateService(TableInfo queryTable)
        {
            super(queryTable, ExperimentService.get().getTinfoAssayQCFlag(), ExpQCFlagTableImpl.this._columnMapping);
        }

        @Override
        protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row) throws SQLException, ValidationException
        {
            Map<String, Object> newRow = super._insert(user, c, row);
            addAuditEvent(c, user, INSERT, row);
            return newRow;
        }

        @Override
        protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
        {
            Map<String, Object> newRow = super._update(user, c, row, oldRow, keys);
            addAuditEvent(c, user, UPDATE, row);
            return newRow;
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
        {
            Map<String, Object> newRow = super.deleteRow(user, container, oldRowMap);
            addAuditEvent(container, user, DELETE, oldRowMap);
            return newRow;
        }

        private void addAuditEvent(Container container, @Nullable User user, int action, Map<String, Object> row)
        {
            // for now we are only auditing QCState related flag events
            if (row.containsKey("IntKey1") && row.get("IntKey1") != null)
            {
                try
                {
                    Integer runId = row.containsKey("RunId") ? (Integer)row.get("RunId") : (row.containsKey("Run") ? (Integer)row.get("Run") : null);
                    Integer qcId = (Integer)row.get("IntKey1");
                    String comment = (String)row.get("Comment");

                    ExpRun run = runId != null ? ExperimentService.get().getExpRun(runId) : null;
                    if (run != null)
                    {
                        // check if there is a QC state associated with this flag
                        DataState state = qcId != null ? DataStateManager.getInstance().getStateForRowId(run.getProtocol().getContainer(), qcId) : null;
                        if (state != null)
                        {
                            ExperimentAuditEvent event = new ExperimentAuditEvent(container.getId(), comment);

                            event.setProtocolLsid(run.getProtocol().getLSID());
                            event.setRunLsid(run.getLSID());
                            event.setProtocolRun(ExperimentAuditProvider.getKey3(run.getProtocol(), run));

                            switch (action)
                            {
                                case INSERT:
                                    event.setMessage("QC State was set to: " + state.getLabel());
                                    break;
                                case UPDATE:
                                    event.setMessage("QC State was updated to: " + state.getLabel());
                                    break;
                                case DELETE:
                                    event.setMessage("QC State was removed: " + state.getLabel());
                                    break;
                            }
                            event.setQcState(state.getRowId());
                            AuditLogService.get().addEvent(user, event);
                        }
                    }
                }
                catch (ConversionException e)
                {
                    LOG.warn("Unable to log audit event for QC flag changes: " + e.getMessage());
                }
            }
        }
    }
}
