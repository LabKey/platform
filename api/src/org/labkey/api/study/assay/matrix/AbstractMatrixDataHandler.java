/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.study.assay.matrix;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.AbstractExperimentDataHandler;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractMatrixDataHandler extends AbstractExperimentDataHandler
{
    protected final String _idColumnName;
    protected final String _dbSchemaName;
    protected final String _dataTableName;

    private static final Logger LOG = Logger.getLogger(AbstractMatrixDataHandler.class);
    private static boolean autoCreateSamples = true;     // CONSIDER: move this flag to the assay design

    public AbstractMatrixDataHandler(String idColName, String dbSchemaName, String dataTableName)
    {
        _idColumnName = idColName;
        _dbSchemaName = dbSchemaName;
        _dataTableName = dataTableName;
    }

    public abstract DbSchema getDbSchema();
    public abstract void insertMatrixData(Container c, User user, Map<String, Integer> samplesMap, DataLoader loader, Map<String, String> runProps, Integer dataRowId) throws ExperimentException;

    public String getIdColumnName()
    {
        return _idColumnName;
    }

    public String getDbSchemaName()
    {
        return _dbSchemaName;
    }

    public String getDataTableName()
    {
        return _dataTableName;
    }

    @Override
    public DataType getDataType()
    {
        return null;
    }

    @Override
    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException
    {
    }

    public static DataLoader createLoader(File file, String idColumnName) throws IOException, ExperimentException
    {
        DataLoaderFactory factory = DataLoader.get().findFactory(file, null);
        DataLoader loader = factory.createLoader(file, true);
        ensureColumns(idColumnName, loader.getColumns());
        return loader;
    }

    public static TabLoader createTabLoader(File file, String idColumnName) throws IOException, ExperimentException
    {
        TabLoader loader = new TabLoader(file, true);
        ensureColumns(idColumnName, loader.getColumns());
        return loader;
    }

    private static void ensureColumns(String idColumnName, ColumnDescriptor[] cols) throws ExperimentException
    {
        boolean found = false;

        for (ColumnDescriptor col : cols)
        {
            if (col.name.equals(idColumnName))
            {
                found = true;
                break;
            }
        }

        // If the 0th column is missing a name, consider it the column name of the corresponding expression matrix
        if (!found)
        {
            if (cols[0].name.equals("column0"))
            {
                cols[0].name = idColumnName;
                found = true;
            }
        }

        if (!found)
            throw new ExperimentException(idColumnName + " column header must be present and cannot be blank");
    }

    public static Map<String, Integer> ensureSamples(Container container, User user, Collection<String> columnNames, String columnName) throws ExperimentException
    {
        Set<String> sampleNames = new HashSet<>(columnNames.size());
        for (String name : columnNames)
        {
            if (!name.equals(columnName))
            {
                sampleNames.add(name);
            }
        }
        LOG.debug("All samples in matrix: " + StringUtils.join(sampleNames, ", "));

        List<? extends ExpMaterial> samples = ExperimentService.get().getExpMaterials(container, user, sampleNames, null, !autoCreateSamples, autoCreateSamples);
        Map<String, Integer> sampleMap = new HashMap<>(samples.size());
        for (ExpMaterial sample : samples)
        {
            sampleMap.put(sample.getName(), sample.getRowId());
        }

        return sampleMap;
    }

    public  Map<String, String> getRunPropertyValues(ExpRun run, Domain domain)
    {
        Map<String, String> runPropValues = new HashMap<>();
        for (DomainProperty runProp : domain.getProperties())
        {
            Object value = run.getProperty(runProp);
            if (value != null)
                runPropValues.put(runProp.getName(), value.toString());
        }
        return runPropValues;
    }

    @Override
    public ActionURL getContentURL(ExpData data)
    {
        return null;
    }

    @Override
    public void deleteData(ExpData data, Container container, User user)
    {
        SqlExecutor executor = new SqlExecutor(DbSchema.get(_dbSchemaName));
        SQLFragment deleteDataSql = new SQLFragment("DELETE FROM " + _dbSchemaName);
        deleteDataSql.append("." + _dataTableName);
        deleteDataSql.append(" WHERE DataId = ?").add(data.getRowId());
        executor.execute(deleteDataSql);
    }
}
