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
package org.labkey.api.assay.matrix;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.AbstractExperimentDataHandler;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

public abstract class AbstractMatrixDataHandler extends AbstractExperimentDataHandler
{
    protected final String _idColumnName;
    protected final String _dbSchemaName;
    protected final String _dataTableName;

    private static final Logger LOG = LogManager.getLogger(AbstractMatrixDataHandler.class);
    private static boolean autoCreateSamples = true;     // CONSIDER: move this flag to the assay design

    public AbstractMatrixDataHandler(String idColName, String dbSchemaName, String dataTableName)
    {
        _idColumnName = idColName;
        _dbSchemaName = dbSchemaName;
        _dataTableName = dataTableName;
    }

    public abstract DbSchema getDbSchema();
    public abstract void insertMatrixData(Container c, User user, Map<String, ExpMaterial> samplesMap, DataLoader loader, Map<String, String> runProps, Integer dataRowId) throws ExperimentException;

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
        return createLoader(file, idColumnName, Collections.emptySet());
    }

    public static DataLoader createLoader(File file, String idColumnName, Set<String> aliases) throws IOException, ExperimentException
    {
        DataLoaderFactory factory = DataLoader.get().findFactory(file, null);
        DataLoader loader = factory.createLoader(file, true);
        ensureColumns(idColumnName, aliases, loader.getColumns());
        return loader;
    }

    public static TabLoader createTabLoader(File file, String idColumnName, Set<String> aliases) throws IOException, ExperimentException
    {
        TabLoader loader = new TabLoader(file, true);
        ensureColumns(idColumnName, aliases, loader.getColumns());
        return loader;
    }

    private static void ensureColumns(String idColumnName, Set<String> aliases, ColumnDescriptor[] cols) throws ExperimentException
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

        if (!found)
        {
            for (ColumnDescriptor col : cols)
            {
                if (aliases.contains(col.name))
                {
                    col.name = idColumnName;
                    found = true;
                    break;
                }
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

    public static Map<String, ExpMaterial> ensureSamples(Container container, User user, Collection<String> columnNames, String columnName, @NotNull RemapCache cache, @NotNull Map<Integer, ExpMaterial> materialCache) throws ExperimentException
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

        Set<String> unresolved = new HashSet<>();
        Map<String, ExpMaterial> sampleMap = new HashMap<>(sampleNames.size());
        for (String sampleName : sampleNames)
        {
            ExpMaterial m;
            try
            {
                m = ExperimentService.get().findExpMaterial(container, user, null, null, sampleName, cache, materialCache, true);
            }
            catch (ValidationException e)
            {
                throw new ExperimentException(e);
            }

            if (m != null)
            {
                sampleMap.put(m.getName(), m);
            }
            else
            {
                unresolved.add(sampleName);
            }
        }

        if (!unresolved.isEmpty())
        {
            if (!autoCreateSamples)
                throw new ExperimentException("No samples found for: " + StringUtils.join(unresolved, ", "));

            List<? extends ExpMaterial> created = createExpMaterials(container, user, unresolved);
            for (ExpMaterial m : created)
            {
                sampleMap.put(m.getName(), m);
            }
        }

        return sampleMap;
    }

    private static List<? extends ExpMaterial> createExpMaterials(Container c, User user, Set<String> sampleNames) throws ExperimentException
    {
        ExpSampleType sampleType = ensureSampleType(c, user);

        UserSchema schema = QueryService.get().getUserSchema(user, c, SamplesSchema.SCHEMA_NAME);
        TableInfo table = schema.getTable(sampleType.getName());

        List<Integer> rowIds;
        try (DbScope.Transaction tx = schema.getDbSchema().getScope().ensureTransaction())
        {
            List<Map<String, Object>> rows = new ArrayList<>(sampleNames.size());
            for (String sampleName : sampleNames)
                rows.add(CaseInsensitiveHashMap.of("Name", sampleName));

            BatchValidationException errors = new BatchValidationException();
            List<Map<String, Object>> inserted = table.getUpdateService().insertRows(user, c, rows, errors, null, null);
            if (errors.hasErrors())
                throw new ExperimentException(errors.getMessage(), errors);

            if (inserted.size() != sampleNames.size())
                throw new ExperimentException("Expected to insert " + sampleNames.size() + " samples; only inserted " + inserted.size());

            rowIds = inserted.stream().map(m -> (Integer)m.get("rowId")).collect(Collectors.toList());
            tx.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (DuplicateKeyException | QueryUpdateServiceException | BatchValidationException e)
        {
            throw new ExperimentException(e.getMessage(), e);
        }

        if (rowIds.isEmpty())
            return Collections.emptyList();

        return ExperimentService.get().getExpMaterials(rowIds);
    }

    // Get a SampleType in the current, project, or shared container.
    // If more than one SampleType exists, an exception is thrown.
    // If none exist, a new SampleType named "Samples" is created.
    private static @NotNull ExpSampleType ensureSampleType(Container c, User user) throws ExperimentException
    {
        List<? extends ExpSampleType> sampleTypes = SampleTypeService.get().getSampleTypes(c, user, true);
        if (sampleTypes.isEmpty())
        {
            return createSampleType(c, user);
        }
        else if (sampleTypes.size() == 1)
        {
            return sampleTypes.get(0);
        }
        else
        {
            throw new ExperimentException("More than one SampleType in scope: " + sampleTypes.stream().map(ExpSampleType::getName).collect(Collectors.joining(", ")));
        }
    }

    private static @NotNull ExpSampleType createSampleType(Container c, User user) throws ExperimentException
    {
        // Create a new SampleSet in the current container
        List<GWTPropertyDescriptor> properties = new ArrayList<>();
        properties.add(new GWTPropertyDescriptor("Name", "http://www.w3.org/2001/XMLSchema#string"));
        try
        {
            ExpSampleType sampleType = SampleTypeService.get().createSampleType(c, user, "Samples", null, properties, emptyList(), -1, -1, -1, -1, "${Name}");
            LOG.info("Created new SampleType in " + c.getName() + ": " + sampleType.getLSID());
            return sampleType;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
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
