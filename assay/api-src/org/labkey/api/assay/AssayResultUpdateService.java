/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.assay;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.sample.AssaySampleLookupContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ProvenanceService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.UnauthorizedException;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.labkey.api.dataiterator.DetailedAuditLogDataIterator.AuditConfigs.AuditUserComment;

public class AssayResultUpdateService extends DefaultQueryUpdateService
{
    private final AssaySampleLookupContext _assaySampleLookupContext;

    public AssayResultUpdateService(AssayProtocolSchema schema, FilteredTable table)
    {
        super(table, table.getRealTable(), createMVMapping(schema.getProvider().getResultsDomain(schema.getProtocol())));
        if (!(table instanceof AssayResultTable))
            throw new IllegalArgumentException("Expected AssayResultTable");

        _assaySampleLookupContext = new AssaySampleLookupContext(getQueryTable(), FieldKey.fromParts("Run", "RowId"));
    }

    @Override
    public List<Map<String, Object>> updateRows(
        User user,
        Container container,
        List<Map<String, Object>> rows,
        List<Map<String, Object>> oldKeys,
        BatchValidationException errors,
        @Nullable Map<Enum, Object> configParameters,
        Map<String, Object> extraScriptContext
    ) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        var result = super.updateRows(user, container, rows, oldKeys, errors, configParameters, extraScriptContext);

        _assaySampleLookupContext.syncLineage(container, user, errors);

        if (errors.hasErrors())
            throw errors;

        return result;
    }

    @Override
    protected Map<String, Object> updateRow(
        User user,
        Container container,
        Map<String, Object> row,
        @NotNull Map<String, Object> oldRow,
        @Nullable Map<Enum, Object> configParameters
    ) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        Map<String, Object> originalRow = getRow(user, container, oldRow);
        if (originalRow == null)
            throw new InvalidKeyException("Could not find row");

        ExpRun run = getRun(originalRow, user, UpdatePermission.class);

        if (!run.getContainer().equals(container))
            throw new UnauthorizedException("Assay results being updated are from a different container.");

        // Assay results use FILE_LINK not FILE_ATTACHMENT, use convertTypes() to handle directing the posted files to the run specific directory
        Path assayResultsRunDir = AssayResultsFileWriter.getAssayFilesDirectoryPath(run);
        convertTypes(user, container, row, getDbTable(), assayResultsRunDir);

        Map<String, Object> result = super.updateRow(user, container, row, oldRow, configParameters);
        Map<String, Object> updatedValues = getRow(user, container, oldRow);

        StringBuilder sb = new StringBuilder("Data row, id " + oldRow.get("RowId") + ", edited in " + run.getProtocol().getName() + ".");
        for (Map.Entry<String, Object> entry : updatedValues.entrySet())
        {
            // Also check for properties
            TableInfo table = getQueryTable();
            ColumnInfo col = table.getColumn(entry.getKey());

            if (col != null)
            {
                Object oldValue = originalRow.get(entry.getKey());
                Object newValue = entry.getValue();
                boolean hasValueChanged = !Objects.equals(oldValue, newValue);

                if (hasValueChanged)
                    _assaySampleLookupContext.markLookup(container, user, col, run);

                TableInfo fkTableInfo = col.getFkTableInfo();
                // Don't follow the lookup for specimen IDs, since their FK is very special and based on target study, etc
                if (!hasValueChanged && fkTableInfo != null && !AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equalsIgnoreCase(entry.getKey()))
                {
                    // Do type conversion in case there's a mismatch in the lookup source and target columns
                    ColumnInfo fkTablePkCol = fkTableInfo.getPkColumns().get(0);
                    newValue = lookupDisplayValue(newValue, fkTableInfo, fkTablePkCol);
                    oldValue = lookupDisplayValue(oldValue, fkTableInfo, fkTablePkCol);
                }
                appendPropertyIfChanged(sb, col.getLabel(), oldValue, newValue);
            }
        }

        String userComment = configParameters == null ? null : (String) configParameters.get(AuditUserComment);
        ExperimentService.get().auditRunEvent(user, run.getProtocol(), run, null, sb.toString(), userComment);

        return result;
    }

    private Object lookupDisplayValue(Object o, @NotNull TableInfo fkTableInfo, ColumnInfo fkTablePkCol)
    {
        if (o == null)
            return null;

        if (fkTablePkCol == null)
            return o;

        if (!fkTablePkCol.getJavaClass().isAssignableFrom(o.getClass()))
        {
            try
            {
                o = ConvertUtils.convert(o.toString(), fkTablePkCol.getJavaClass());
                Map<String, Object> newLookupTarget = new TableSelector(fkTableInfo).getMap(o);
                if (newLookupTarget != null)
                {
                    o = newLookupTarget.get(fkTableInfo.getTitleColumn());
                }
            }
            catch (ConversionException ex)
            {
                // ok - just use the value as is
            }
        }

        return o;
    }

    private ExpRun getRun(Map<String, Object> row, User user, Class<? extends Permission> perm) throws InvalidKeyException
    {
        int dataId = ((Number) row.get("DataId")).intValue();
        ExpData data = ExperimentService.get().getExpData(dataId);
        if (data == null)
        {
            throw new InvalidKeyException("Could not find data object");
        }
        ExpRun run = data.getRun();
        if (run == null)
        {
            throw new InvalidKeyException("Could not find run object");
        }
        if (!run.getContainer().hasPermission(user, perm))
        {
            throw new UnauthorizedException("User does not have " + perm.getSimpleName() + " result in " + run.getContainer());
        }
        return run;
    }

    @Override
    protected Map<String, Object> deleteRow(
        User user,
        Container container,
        Map<String, Object> oldRowMap,
        @Nullable Map<Enum, Object> configParameters,
        @Nullable Map<String, Object> extraScriptContext
    ) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        ExpRun run = getRun(oldRowMap, user, DeletePermission.class);

        AssayService.get().onBeforeAssayResultDelete(container, user, run, oldRowMap);

        TableInfo datatableInfo = this.getQueryTable();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("run"), run.getRowId());
        filter.addCondition(FieldKey.fromParts("rowId"), oldRowMap.get("rowId"));

        Map<String, Object> dataObjectMap = new TableSelector(datatableInfo, filter, null).getMap();

        Map<String, Object> result = super.deleteRow(user, container, oldRowMap);

        String userComment = configParameters == null ? null : (String) configParameters.get(AuditUserComment);
        ExperimentService.get().auditRunEvent(user, run.getProtocol(), run, null, "Deleted data row, id " + oldRowMap.get("RowId"), userComment);

        if (null != dataObjectMap)
        {
            String objectLsid = dataObjectMap.get("LSID").toString();
            OntologyObject objectToDelete = OntologyManager.getOntologyObject(container, objectLsid);

            if (null != objectToDelete)
            {
                ProvenanceService.get().deleteObjectProvenance(objectToDelete.getObjectId());
                OntologyManager.deleteOntologyObject(objectLsid, container, false);
            }
        }

        return result;
    }

    private void appendPropertyIfChanged(StringBuilder sb, String label, Object oldValue, Object newValue)
    {
        if (Objects.equals(oldValue, newValue))
            return;

        sb.append(" ");
        sb.append(label);
        sb.append(" changed from ");
        sb.append(oldValue == null ? "blank" : "'" + oldValue + "'");
        sb.append(" to ");
        sb.append(newValue == null ? "blank" : "'" + newValue + "'");
        sb.append(".");
    }
}
