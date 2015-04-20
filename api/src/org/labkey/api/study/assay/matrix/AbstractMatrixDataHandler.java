/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.AbstractExperimentDataHandler;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

    public static TabLoader createTabLoader(File file, String idColumnName) throws IOException, ExperimentException
    {
        TabLoader loader = new TabLoader(file, true);
        ColumnDescriptor[] cols = loader.getColumns();

        boolean found = false;

        // Find the ID_REF column
        for (ColumnDescriptor col : cols)
        {
            if (col.name.equals(idColumnName))
            {
                found = true;
                break;
            }
        }

        // If the 0th column is missing a name, consider it the ID_REF column
        if (!found)
        {
            if (cols[0].name.equals("column0"))
            {
                cols[0].name = idColumnName;
                found = true;
            }
        }

        // CONSIDER: If there is no ID_REF column, assume the first column is the ID_REF column
        if (!found)
            throw new ExperimentException("Feature ID_REF column header must be present and cannot be blank");

        return loader;
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

        SimpleFilter sampleSetFilter = new SimpleFilter();
        sampleSetFilter.addInClause(FieldKey.fromParts("Name"), sampleNames);

        // SampleSet may live in different container
        ContainerFilter.CurrentPlusProjectAndShared containerFilter = new ContainerFilter.CurrentPlusProjectAndShared(user);
        SimpleFilter.FilterClause clause = containerFilter.createFilterClause(ExperimentService.get().getSchema(), FieldKey.fromParts("Container"), container);
        sampleSetFilter.addClause(clause);

        Set<String> selectNames = new LinkedHashSet<>();
        selectNames.add("Name");
        selectNames.add("RowId");
        TableSelector sampleTableSelector = new TableSelector(ExperimentService.get().getTinfoMaterial(), selectNames, sampleSetFilter, null);
        if (!autoCreateSamples)
        {
            Map<String, Object>[] sampleSetResults = sampleTableSelector.getMapArray();
            if (sampleSetResults.length < 1)
                throw new ExperimentException("No matching samples found");
        }

        Map<String, Integer> sampleMap = sampleTableSelector.getValueMap();
        if (sampleMap.size() > 0)
            LOG.debug("Existing samples used in matrix: " + StringUtils.join(sampleMap.keySet(), ", "));
        else
            LOG.debug("No existing samples used in matrix");

        if (sampleMap.size() < sampleNames.size())
        {
            Set<String> missingSamples = new HashSet<>(sampleNames);
            missingSamples.removeAll(sampleMap.keySet());
            if (!autoCreateSamples)
                throw new ExperimentException("No samples found for: " + StringUtils.join(missingSamples, ", "));

            // Create missing samples in the active SampleSet
            LOG.info("Samples to be created for matrix: " + StringUtils.join(missingSamples, ", "));
            Map<String, Integer> createdSamples = createSamples(container, user, missingSamples);
            sampleMap.putAll(createdSamples);
        }

        return sampleMap;
    }

    private static Map<String, Integer> createSamples(Container c, User user, Set<String> missingSamples) throws ExperimentException
    {
        DbScope scope = ExperimentService.get().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            ExpSampleSet sampleSet = ensureSampleSet(c, user);

            Map<String, Integer> createdSamples = new HashMap<>();

            // Create materials directly using Name.
            // XXX: Doesn't handle idColumn concat magic.
            for (String name : missingSamples)
            {
                List<? extends ExpMaterial> materials = ExperimentService.get().getExpMaterialsByName(name, c, user);
                if (materials.size() > 0)
                {
                    LOG.warn("Found samples for '" + name + "' that should have been found in the query:");
                    for (ExpMaterial m : materials)
                    {
                        LOG.warn("  " + m.getName() + ", container=" + m.getContainer() + ", sampleset=" + m.getSampleSet().getName());
                    }
                    ExpMaterial material = materials.get(0);
                    createdSamples.put(name, material.getRowId());
                }
                else
                {
                    Lsid lsid = new Lsid(sampleSet.getMaterialLSIDPrefix() + "test");
                    lsid.setObjectId(name);
                    String materialLsid = lsid.toString();

                    ExpMaterial material = ExperimentService.get().createExpMaterial(c, materialLsid, name);
                    material.setCpasType(sampleSet.getLSID());
                    material.save(user);

                    createdSamples.put(name, material.getRowId());
                }
            }

            transaction.commit();
            return createdSamples;
        }
    }

    private static ExpSampleSet ensureSampleSet(Container c, User user) throws ExperimentException
    {
        ExpSampleSet sampleSet = ExperimentService.get().ensureActiveSampleSet(c);
        if (sampleSet.getName().equals("Unspecified") && ContainerManager.getSharedContainer().equals(sampleSet.getContainer()))
        {
            // Create a new SampleSet in the current container
            List<GWTPropertyDescriptor> properties = new ArrayList<>();
            properties.add(new GWTPropertyDescriptor("Name", "http://www.w3.org/2001/XMLSchema#string"));
            try
            {
                sampleSet = ExperimentService.get().createSampleSet(c, user, "Samples", null, properties, 0, -1, -1, -1);
                LOG.info("Created new SampleSet in " + c.getName() + ": " + sampleSet.getLSID());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        return sampleSet;
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

    @Override
    public void runMoved(ExpData newData, Container container, Container targetContainer, String oldRunLSID, String newRunLSID, User user, int oldDataRowID) throws ExperimentException
    {
        throw new UnsupportedOperationException();
    }
}
