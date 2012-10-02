/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.study.assay.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpExperimentTable;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.ShowSelectedRunsAction;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunType;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.assay.StudyContainerFilter;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.data.xml.TableType;
import org.labkey.study.assay.ModuleAssayLoader;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: brittp
 * Date: Jun 28, 2007
 * Time: 11:07:09 AM
 */
public class AssaySchemaImpl extends AssaySchema
{
    /** Legacy location for PropertyDescriptor columns is under a separate node. New location is as a top-level member of the table */
    private static final String RUN_PROPERTIES_COLUMN_NAME = "RunProperties";
    private static final String BATCH_PROPERTIES_COLUMN_NAME = "BatchProperties";

    private Map<ExpProtocol, AssayProvider> _protocols;
    /** Cache the "child" schemas so that we don't have to recreate them over and over within this schema's lifecycle */
    private Map<ExpProtocol, AssaySchema> _providerSchemas = new HashMap<ExpProtocol, AssaySchema>();

    static public class Provider extends DefaultSchema.SchemaProvider
    {
        public QuerySchema getSchema(DefaultSchema schema)
        {
            return new AssaySchemaImpl(schema.getUser(), schema.getContainer());
        }
    }

    public AssaySchemaImpl(User user, Container container)
    {
        super(NAME, user, container, ExperimentService.get().getSchema(), null);
    }

    /**
     * Use this constructor to use a prefetched map of assay protocols in this AssaySchema.
     */
    public AssaySchemaImpl(User user, Container container, @Nullable Map<ExpProtocol, AssayProvider> protocols)
    {
        this(user, container);
        _protocols = protocols;
    }

    // UNDONE: need to check permissions here 8449
    @Override
    protected boolean canReadSchema()
    {
        return true;
    }

    public Set<String> getTableNames()
    {
        Set<String> names = new TreeSet<String>(new Comparator<String>()
        {
            public int compare(String o1, String o2)
            {
                return o1.compareToIgnoreCase(o2);
            }
        });

        names.add(ASSAY_LIST_TABLE_NAME);
        for (Map.Entry<ExpProtocol, AssayProvider> entry : getProtocols().entrySet())
        {
            ExpProtocol protocol = entry.getKey();
            AssayProvider provider = entry.getValue();
            if (provider != null)
            {
                names.addAll(getTableNames(provider, protocol));
            }
        }
        return names;
    }

    public Set<String> getTableNames(AssayProvider provider, ExpProtocol protocol)
    {
        Set<String> names = new HashSet<String>();
        names.add(getBatchesTableName(protocol));
        names.add(getRunsTableName(protocol));
        names.add(getResultsTableName(protocol));
        names.add(getQCFlagTableName(protocol));
        AssaySchema providerSchema = provider.getProviderSchema(getUser(), getContainer(), protocol);
        if (providerSchema != null)
            names.addAll(providerSchema.getTableNames());

        return names;
    }

    private Map<ExpProtocol, AssayProvider> getProtocols()
    {
        if (_protocols == null)
        {
            _protocols = new HashMap<ExpProtocol, AssayProvider>();
            for (ExpProtocol protocol : AssayService.get().getAssayProtocols(getContainer()))
            {
                _protocols.put(protocol, AssayService.get().getProvider(protocol));
            }
        }
        return _protocols;
    }

    @Override
    public TableInfo createTable(String name)
    {
        if (name.equalsIgnoreCase(ASSAY_LIST_TABLE_NAME))
            return new AssayListTable(this);
        else
        {
            for (Map.Entry<ExpProtocol, AssayProvider> entry : getProtocols().entrySet())
            {
                ExpProtocol protocol = entry.getKey();
                AssayProvider provider = entry.getValue();
                TableInfo table = null;
                if (provider != null)
                {
                    if (name.equalsIgnoreCase(getBatchesTableName(protocol)))
                    {
                        table = createBatchesTable(protocol, provider, null);
                    }
                    else if (name.equalsIgnoreCase(getRunsTableName(protocol)))
                    {
                        table = createRunTable(protocol, provider);
                    }
                    else if (name.equalsIgnoreCase(getResultsTableName(protocol)) || name.equalsIgnoreCase(protocol.getName() + " Data"))
                    {
                        table = provider.createDataTable(this, protocol, true);
                        if (table != null && null != table.getColumn("Properties"))
                            fixupPropertyURLs(table.getColumn("Properties"));
                    }
                    else if (name.equalsIgnoreCase(getQCFlagTableName(protocol)) || name.equalsIgnoreCase(protocol.getName() + " QCFlags"))
                    {
                        table = provider.createQCFlagTable(this, protocol);
                    }
                    else
                    {
                        AssaySchema providerSchema = _providerSchemas.get(protocol);
                        if (providerSchema == null)
                        {
                            providerSchema = provider.getProviderSchema(getUser(), getContainer(), protocol);
                            _providerSchemas.put(protocol, providerSchema);
                        }
                        if (providerSchema != null && name.toLowerCase().startsWith(protocol.getName().toLowerCase() + " "))
                        {
                            table = providerSchema.createTable(name);
                        }
                    }

                    if (table != null)
                    {
                        overlayMetadata(provider, protocol, table, name);
                        return table;
                    }
                }
            }
        }
        return null;
    }

    // NOTE: This should be transitioned to partly happen in the TableInfo.overlayMetadata() for the various tables
    // associated with the assay design. They should call into here with the unprefixed name to be added
    // from the assay provider's metadata files.
    protected void overlayMetadata(AssayProvider provider, ExpProtocol protocol, TableInfo table, String name)
    {
        fixupRenderers(table);

        // Look for metadata using the table's raw name (ie. not prefixed with the provider's name)
        String prefix = protocol.getName() + " ";
        if (name.startsWith(prefix))
        {
            String unprefixedTableName = name.substring(prefix.length());

            ArrayList<QueryException> errors = new ArrayList<QueryException>();
            Path dir = new Path(ModuleAssayLoader.ASSAY_DIR_NAME, provider.getResourceName(), QueryService.MODULE_QUERIES_DIRECTORY);
            TableType metadata = QueryService.get().findMetadataOverride(this, unprefixedTableName, false, true, errors, dir);
            if (errors.isEmpty())
                table.overlayMetadata(metadata, this, errors);
        }
    }


    private ExpExperimentTable createBatchesTable(ExpProtocol protocol, AssayProvider provider, @Nullable final ContainerFilter containerFilter)
    {
        final ExpExperimentTable result = ExperimentService.get().createExperimentTable(getBatchesTableName(protocol), this);
        result.populate();
        if (containerFilter != null)
        {
            result.setContainerFilter(containerFilter);
        }
        ActionURL runsURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), protocol, result.getContainerFilter());

        // Unfortunately this seems to be the best way to figure out the name of the URL parameter to filter by batch id
        ActionURL fakeURL = new ActionURL(ShowSelectedRunsAction.class, getContainer());
        fakeURL.addFilter(AssayService.get().getRunsTableName(protocol),
                AbstractAssayProvider.BATCH_ROWID_FROM_RUN, CompareType.EQUAL, "${RowId}");
        String paramName = fakeURL.getParameters()[0].getKey();

        Map<String, String> urlParams = new HashMap<String, String>();
        urlParams.put(paramName, "RowId");
        result.setDetailsURL(new DetailsURL(runsURL, urlParams));

        runsURL.addParameter(paramName, "${RowId}");
        result.getColumn(ExpExperimentTable.Column.Name).setURL(StringExpressionFactory.createURL(runsURL));
        result.setBatchProtocol(protocol);
        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts(ExpExperimentTable.Column.Name));
        defaultCols.add(FieldKey.fromParts(ExpExperimentTable.Column.CreatedBy));
        defaultCols.add(FieldKey.fromParts(ExpExperimentTable.Column.RunCount));
        result.setDefaultVisibleColumns(defaultCols);

        Domain batchDomain = provider.getBatchDomain(protocol);
        if (batchDomain != null)
        {
            ColumnInfo propsCol = result.addColumns(batchDomain, BATCH_PROPERTIES_COLUMN_NAME);
            if (propsCol != null)
            {
                // Will be null if the domain doesn't have any properties
                propsCol.setFk(new AssayPropertyForeignKey(batchDomain));
                propsCol.setUserEditable(false);
                propsCol.setShownInInsertView(false);
                propsCol.setShownInUpdateView(false);
            }
            result.setDomain(batchDomain);
        }

        for (ColumnInfo col : result.getColumns())
        {
            fixupRenderers(col, col);
        }

        result.setDescription("Contains a row per " + protocol.getName() + " batch, a group of runs that were loaded at the same time.");

        return result;
    }

    private ExpRunTable createRunTable(final ExpProtocol protocol, final AssayProvider provider)
    {
        final ExpRunTable runTable = provider.createRunTable(this, protocol);
        runTable.setProtocolPatterns(protocol.getLSID());

        Domain runDomain = provider.getRunDomain(protocol);
        if (runDomain != null)
        {
            ColumnInfo propsCol = runTable.addColumns(runDomain, RUN_PROPERTIES_COLUMN_NAME);
            if (propsCol != null)
            {
                // Will be null if the domain doesn't have any properties
                propsCol.setFk(new AssayPropertyForeignKey(runDomain));
                propsCol.setUserEditable(false);
                propsCol.setShownInInsertView(false);
                propsCol.setShownInUpdateView(false);
            }
            runTable.setDomain(runDomain);
        }

        List<FieldKey> visibleColumns = new ArrayList<FieldKey>(runTable.getDefaultVisibleColumns());
        visibleColumns.remove(FieldKey.fromParts(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME));
    
        SQLFragment batchSQL = new SQLFragment("(SELECT MIN(ExperimentId) FROM ");
        batchSQL.append(ExperimentService.get().getTinfoRunList(), "rl");
        batchSQL.append(", ");
        batchSQL.append(ExperimentService.get().getTinfoExperiment(), "e");
        batchSQL.append(" WHERE e.RowId = rl.ExperimentId AND rl.ExperimentRunId = ");
        batchSQL.append(ExprColumn.STR_TABLE_ALIAS);
        batchSQL.append(".RowId AND e.BatchProtocolId = ");
        batchSQL.append(protocol.getRowId());
        batchSQL.append(")");
        ExprColumn batchColumn = new ExprColumn(runTable, AssayService.BATCH_COLUMN_NAME, batchSQL, JdbcType.INTEGER, runTable.getColumn(ExpRunTable.Column.RowId));
        batchColumn.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                ExpExperimentTable batchesTable = createBatchesTable(protocol, provider, runTable.getContainerFilter());
                fixupRenderers(batchesTable);
                return batchesTable;
            }
        });
        runTable.addColumn(batchColumn);

        visibleColumns.add(FieldKey.fromParts(batchColumn.getName()));
        FieldKey batchPropsKey = FieldKey.fromParts(batchColumn.getName());
        Domain batchDomain = provider.getBatchDomain(protocol);
        if (batchDomain != null)
        {
            for (DomainProperty col : batchDomain.getProperties())
            {
                if (!col.isHidden() && !AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equalsIgnoreCase(col.getName()))
                    visibleColumns.add(new FieldKey(batchPropsKey, col.getName()));
            }
        }
        runTable.setDefaultVisibleColumns(visibleColumns);

        runTable.setDescription("Contains a row per " + protocol.getName() + " run.");

        return runTable;
    }

    @Override
    public QueryView createView(ViewContext context, QuerySettings settings, org.springframework.validation.BindException errors)
    {
        String name = settings.getQueryName();
        for (Map.Entry<ExpProtocol, AssayProvider> entry : getProtocols().entrySet())
        {
            ExpProtocol protocol = entry.getKey();
            AssayProvider provider = entry.getValue();
            if (provider != null)
            {
                if (name != null && name.equals(AssayService.get().getResultsTableName(protocol)))
                {
                    return new ResultsQueryView(protocol, context, settings);
                }
                if (name != null && name.equals(AssayService.get().getRunsTableName(protocol)))
                {
                    return new RunListQueryView(protocol, this, settings, new AssayRunType(protocol, getContainer()));
                }
            }
        }

        return super.createView(context, settings, errors);
    }


    /**
     * in order to allow using short name for properties in assay table we need
     * to patch up the keys
     *
     * for instance ${myProp} instead of ${RunProperties/myProp}
     *
     * @param fk properties column (e.g. RunProperties)
     */
    private static void fixupPropertyURL(ColumnInfo fk, ColumnInfo col)
    {
        if (null == fk || !(col.getURL() instanceof StringExpressionFactory.FieldKeyStringExpression))
            return;

        TableInfo table = fk.getParentTable();
        StringExpressionFactory.FieldKeyStringExpression fkse = (StringExpressionFactory.FieldKeyStringExpression)col.getURL();
        // quick check
        Set<FieldKey> keys = fkse.getFieldKeys();
        Map<FieldKey,FieldKey> map = new HashMap<FieldKey, FieldKey>();
        for (FieldKey key : keys)
        {
            if (null == key.getParent() && null == table.getColumn(key.getName()))
                map.put(key, new FieldKey(fk.getFieldKey(), key.getName()));
        }
        if (map.isEmpty())
            return;
        col.setURL(fkse.remapFieldKeys(null, map));
    }

    @Override
    public List<CustomView> getModuleCustomViews(Container container, QueryDefinition qd)
    {
        List<CustomView> result = new ArrayList<CustomView>();

        // Look for <MODULE>/assay/<ASSAY_TYPE>/queries/<TABLE_TYPE>/*.qview.xml files
        // where TABLE_TYPE is Runs, Batches, Data, etc
        for (Map.Entry<ExpProtocol, AssayProvider> entry : getProtocols().entrySet())
        {
            String providerTableType = getProviderTableType(entry.getKey(), qd.getName());
            if (providerTableType != null)
            {
                Path path = new Path(ModuleAssayLoader.ASSAY_DIR_NAME, entry.getValue().getResourceName(), QueryService.MODULE_QUERIES_DIRECTORY, FileUtil.makeLegalName(providerTableType));
                result.addAll(QueryService.get().getModuleCustomViews(container, qd, path));
                break;
            }
        }

        // Look in the standard location (based on the assay design name) for additional custom views
        result.addAll(super.getModuleCustomViews(container, qd));
        return result;
    }

    private static void fixupPropertyURLs(ColumnInfo fk)
    {
        for (ColumnInfo c : fk.getParentTable().getColumns())
            fixupPropertyURL(fk, c);
    }

    public void fixupRenderers(TableInfo table)
    {
        for (ColumnInfo col : table.getColumns())
        {
            fixupRenderers(col, col);
        }
    }

    public void fixupRenderers(final ColumnRenderProperties col, ColumnInfo columnInfo)
    {
        if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(col.getName()))
        {
            columnInfo.setFk(new LookupForeignKey("Folder", "Label")
            {
                public TableInfo getLookupTableInfo()
                {
                    FilteredTable table = new FilteredTable(StudyManager.getSchema().getTable("Study"));
                    table.setContainerFilter(new StudyContainerFilter(AssaySchemaImpl.this));
                    ExprColumn col = new ExprColumn(table, "Folder", new SQLFragment("CAST (" + ExprColumn.STR_TABLE_ALIAS + ".Container AS VARCHAR(200))"), JdbcType.VARCHAR);
                    col.setFk(new ContainerForeignKey(AssaySchemaImpl.this));
                    table.addColumn(col);
                    table.addWrapColumn(table.getRealTable().getColumn("Label"));
                    table.setPublic(false);
                    return table;
                }
            });
        }
    }


    private class AssayPropertyForeignKey extends PropertyForeignKey
    {
        public AssayPropertyForeignKey(Domain domain)
        {
            super(domain, AssaySchemaImpl.this);
        }

        @Override
        protected ColumnInfo constructColumnInfo(ColumnInfo parent, FieldKey name, final PropertyDescriptor pd)
        {
            ColumnInfo result = super.constructColumnInfo(parent, name, pd);
            fixupRenderers(pd, result);
            return result;
        }
    }
}
