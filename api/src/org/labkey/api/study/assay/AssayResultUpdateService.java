/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.UnauthorizedException;

import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

/**
 * User: jeckels
 * Date: 7/1/11
 */
public class AssayResultUpdateService extends DefaultQueryUpdateService
{
    private final AssayProtocolSchema _schema;

    public AssayResultUpdateService(AssayProtocolSchema schema, FilteredTable table)
    {
        super(table, table.getRealTable(), createMVMapping(schema.getProvider().getResultsDomain(schema.getProtocol())));
        if (!(table instanceof AssayResultTable))
            throw new IllegalArgumentException("Expected AssayResultTable");
        _schema = schema;
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        _schema.getProtocol();

        Map<String, Object> originalRow = getRow(user, container, oldRow);
        if (originalRow == null)
        {
            throw new InvalidKeyException("Could not find row");
        }

        ExpRun run = getRun(originalRow, user, UpdatePermission.class);

        Map<String, Object> result = super.updateRow(user, container, row, oldRow);

        Map<String, Object> updatedValues = getRow(user, container, oldRow);

        StringBuilder sb = new StringBuilder("Data row, id " + oldRow.get("RowId") + ", edited in " + run.getProtocol().getName() + ".");
        for (Map.Entry<String, Object> entry : updatedValues.entrySet())
        {
            // Also check for properties
            ColumnInfo col = getQueryTable().getColumn(entry.getKey());
            if (col != null)
            {
                Object oldValue = originalRow.get(entry.getKey());
                Object newValue = entry.getValue();

                TableInfo fkTableInfo = col.getFkTableInfo();
                // Don't follow the lookup for specimen IDs, since their FK is very special and based on target study, etc
                if (!Objects.equals(oldValue, newValue) && fkTableInfo != null && !AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equalsIgnoreCase(entry.getKey()))
                {
                    // Do type conversion in case there's a mismatch in the lookup source and target columns
                    ColumnInfo fkTablePkCol = fkTableInfo.getPkColumns().get(0);
                    newValue = lookupDisplayValue(newValue, fkTableInfo, fkTablePkCol);
                    oldValue = lookupDisplayValue(oldValue, fkTableInfo, fkTablePkCol);
                }
                appendPropertyIfChanged(sb, col.getLabel(), oldValue, newValue);
            }
        }
        ExperimentService.get().auditRunEvent(user, run.getProtocol(), run, null, sb.toString());

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
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        ExpRun run = getRun(oldRowMap, user, DeletePermission.class);
        Map<String, Object> result = super.deleteRow(user, container, oldRowMap);

        ExperimentService.get().auditRunEvent(user, run.getProtocol(), run, null, "Deleted data row.");

        return result;
    }

    private StringBuilder appendPropertyIfChanged(StringBuilder sb, String label, Object oldValue, Object newValue)
    {
        if (!Objects.equals(oldValue, newValue))
        {
            sb.append(" ");
            sb.append(label);
            sb.append(" changed from ");
            sb.append(oldValue == null ? "blank" : "'" + oldValue + "'");
            sb.append(" to ");
            sb.append(newValue == null ? "blank" : "'" + newValue + "'");
            sb.append(".");
        }
        return sb;
    }
}
