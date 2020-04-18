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

import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExpQCFlag;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpQCFlagTable;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AssayDefaultFlagHandler implements AssayFlagHandler
{
    @Override
    public MutableColumnInfo createFlagColumn(ExpProtocol protocol, ExpRunTable runTable, String schemaName, boolean editable)
    {
        return new AssayQCFlagColumn(runTable, schemaName, editable);
    }

    @Override
    public MutableColumnInfo createQCEnabledColumn(ExpProtocol protocol, ExpRunTable parent, String schemaName)
    {
        ExprColumn qcEnabled = new ExprColumn(parent, "QCFlagsEnabled", AssayQCFlagColumn.createSQLFragment(parent.getSqlDialect(), "Enabled"), JdbcType.VARCHAR);
        qcEnabled.setLabel("QC Flags Enabled State");
        qcEnabled.setHidden(true);

        return qcEnabled;
    }

    @Override
    public void fixupQCFlagTable(ExpQCFlagTable table, AssayProvider provider, ExpProtocol assayProtocol)
    {
    }

    @Override
    public <FlagType extends ExpQCFlag> void saveFlag(Container container, User user, FlagType flag)
    {
        TableInfo tableInfo = getQCFlagTable(container, user);
        if (tableInfo != null)
        {
            try
            {
                QueryUpdateService qus = tableInfo.getUpdateService();
                ObjectFactory<FlagType> f = ObjectFactory.Registry.getFactory((Class<FlagType>)flag.getClass());

                Map<String, Object> row = f.toMap(flag, null);
                row.put("run", flag.getRunId());
                BatchValidationException errors = new BatchValidationException();

                if (flag.getRowId() != 0)
                    qus.updateRows(user, container, Collections.singletonList(row), Collections.singletonList(row), null, null);
                else
                    qus.insertRows(user, container, Collections.singletonList(row), errors, null, null);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public <FlagType extends ExpQCFlag> void deleteFlag(Container container, User user, FlagType flag)
    {
        TableInfo tableInfo = getQCFlagTable(container, user);
        if (flag != null && tableInfo != null)
        {
            try
            {
                QueryUpdateService qus = tableInfo.getUpdateService();
                qus.deleteRows(user, container, Collections.singletonList(Collections.singletonMap("RowId", flag.getRowId())), null, null);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public int deleteFlagsForRun(Container container, User user, int runId)
    {
        TableInfo tableInfo = getQCFlagTable(container, user);
        if (tableInfo != null)
        {
            try
            {
                List<ExpQCFlag> flags = new TableSelector(ExperimentService.get().getTinfoAssayQCFlag(), new SimpleFilter(FieldKey.fromParts("runId"), runId), null).getArrayList(ExpQCFlag.class);
                List<Map<String, Object>> rows = flags.stream().
                        map(f -> Collections.singletonMap("RowId", (Object)f.getRowId())).
                        collect(Collectors.toList());

                if (!rows.isEmpty())
                {
                    QueryUpdateService qus = tableInfo.getUpdateService();
                    return qus.deleteRows(user, container, rows, null, null).size();
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        return 0;
    }

    private TableInfo getQCFlagTable(Container container, User user)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, new SchemaKey(null, "exp"));
        return schema.getTable(ExpSchema.TableType.QCFlags.name());
    }

    @Override
    public <FlagType extends ExpQCFlag> List<FlagType> getFlags(int runId, Class<FlagType> cls)
    {
        return new TableSelector(ExperimentService.get().getTinfoAssayQCFlag(), new SimpleFilter(FieldKey.fromParts("runId"), runId), null).getArrayList(cls);
    }
}
