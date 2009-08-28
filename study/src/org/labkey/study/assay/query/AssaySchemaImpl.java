/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpExperimentTable;
import org.labkey.api.exp.api.*;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.*;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.study.actions.ShowSelectedRunsAction;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.model.StudyManager;
import org.labkey.study.controllers.assay.AssayController;

import javax.servlet.ServletException;
import java.sql.Types;
import java.util.*;

/**
 * User: brittp
 * Date: Jun 28, 2007
 * Time: 11:07:09 AM
 */
public class AssaySchemaImpl extends AssaySchema
{
    private List<ExpProtocol> _protocols;

    static public class Provider extends DefaultSchema.SchemaProvider
    {
        public QuerySchema getSchema(DefaultSchema schema)
        {
            return new AssaySchemaImpl(schema.getUser(), schema.getContainer());
        }
    }

    public AssaySchemaImpl(User user, Container container)
    {
        super(NAME, user, container, ExperimentService.get().getSchema());
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
        for (ExpProtocol protocol : getProtocols())
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (provider != null)
            {
                names.add(getBatchesTableName(protocol));
                names.add(getRunsTableName(protocol));
                names.add(getResultsTableName(protocol));
            }
        }
        return names;
    }

    public static String getBatchesTableName(ExpProtocol protocol)
    {
        return protocol.getName() + " Batches";
    }

    public static String getRunsTableName(ExpProtocol protocol)
    {
        return protocol.getName() + " Runs";
    }

    public static String getResultsTableName(ExpProtocol protocol)
    {
        return protocol.getName() + " Data";
    }

    private List<ExpProtocol> getProtocols()
    {
        if (_protocols == null)
        {
            _protocols = AssayService.get().getAssayProtocols(getContainer());
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
            for (ExpProtocol protocol : getProtocols())
            {
                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (provider != null)
                {
                    if (name.equalsIgnoreCase(getBatchesTableName(protocol)))
                    {
                        return createBatchesTable(protocol, provider, null);
                    }
                    if (name.equalsIgnoreCase(getRunsTableName(protocol)))
                    {
                        return createRunTable(protocol, provider);
                    }
                    if (name.equalsIgnoreCase(getResultsTableName(protocol)) || name.equalsIgnoreCase(protocol.getName() + " Data"))
                    {
                        return provider.createDataTable(this, protocol);
                    }
                }
            }
        }
        return null;
    }

    public ExpExperimentTable createBatchesTable(ExpProtocol protocol, AssayProvider provider, final ContainerFilter containerFilter)
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

        result.getColumn(ExpExperimentTable.Column.Name).setURL(runsURL.toString() + "&" + paramName + "=${RowId}");
        result.setBatchProtocol(protocol);
        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts(ExpExperimentTable.Column.Name));
        defaultCols.add(FieldKey.fromParts(ExpExperimentTable.Column.CreatedBy));
        defaultCols.add(FieldKey.fromParts(ExpExperimentTable.Column.RunCount));
        List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();
        Set<String> hiddenCols = new HashSet<String>();
        hiddenCols.add(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME);
        for (DomainProperty prop : provider.getBatchDomain(protocol).getProperties())
        {
            pds.add(prop.getPropertyDescriptor());
            if (!prop.isHidden() && !hiddenCols.contains(prop.getName()))
                defaultCols.add(FieldKey.fromParts(AssayService.BATCH_PROPERTIES_COLUMN_NAME, prop.getName()));
        }

        PropertyDescriptor[] pdsArray = pds.toArray(new PropertyDescriptor[pds.size()]);
        ColumnInfo propsCol = result.addPropertyColumns(AssayService.BATCH_PROPERTIES_COLUMN_NAME, pdsArray, this);
        propsCol.setFk(new AssayPropertyForeignKey(pdsArray));

        result.setDefaultVisibleColumns(defaultCols);
        return result;
    }

    public ExpRunTable createRunTable(final ExpProtocol protocol, final AssayProvider provider)
    {
        final ExpRunTable runTable = provider.createRunTable(this, protocol);
        runTable.setProtocolPatterns(protocol.getLSID());

        List<PropertyDescriptor> runColumns = provider.getRunTableColumns(protocol);
        PropertyDescriptor[] pds = runColumns.toArray(new PropertyDescriptor[runColumns.size()]);

        ColumnInfo propsCol = runTable.addPropertyColumns(AssayService.RUN_PROPERTIES_COLUMN_NAME, pds, this);
        propsCol.setFk(new AssayPropertyForeignKey(pds));

        SQLFragment batchSQL = new SQLFragment("(SELECT MIN(ExperimentId) FROM ");
        batchSQL.append(ExperimentService.get().getTinfoRunList(), "rl");
        batchSQL.append(", ");
        batchSQL.append(ExperimentService.get().getTinfoExperiment(), "e");
        batchSQL.append(" WHERE e.RowId = rl.ExperimentId AND rl.ExperimentRunId = ");
        batchSQL.append(ExprColumn.STR_TABLE_ALIAS);
        batchSQL.append(".RowId AND e.BatchProtocolId = ");
        batchSQL.append(protocol.getRowId());
        batchSQL.append(")");
        ExprColumn batchColumn = new ExprColumn(runTable, AssayService.BATCH_COLUMN_NAME, batchSQL, Types.INTEGER, runTable.getColumn(ExpRunTable.Column.RowId));
        batchColumn.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createBatchesTable(protocol, provider, runTable.getContainerFilter());
            }
        });
        runTable.addColumn(batchColumn);

        List<FieldKey> visibleColumns = new ArrayList<FieldKey>(runTable.getDefaultVisibleColumns());
        visibleColumns.add(FieldKey.fromParts(batchColumn.getName()));
        Set<String> hiddenCols = new HashSet<String>();
        hiddenCols.add(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME);
        FieldKey runKey = FieldKey.fromString(AssayService.RUN_PROPERTIES_COLUMN_NAME);
        for (PropertyDescriptor runColumn : runColumns)
        {
            if (!runColumn.isHidden() && !hiddenCols.contains(runColumn.getName()))
                visibleColumns.add(new FieldKey(runKey, runColumn.getName()));
        }
        FieldKey batchPropsKey = FieldKey.fromParts(batchColumn.getName(), AssayService.BATCH_PROPERTIES_COLUMN_NAME);
        for (DomainProperty col : provider.getBatchDomain(protocol).getProperties())
        {
            if (!col.isHidden() && !hiddenCols.contains(col.getName()))
                visibleColumns.add(new FieldKey(batchPropsKey, col.getName()));
        }

        runTable.setDefaultVisibleColumns(visibleColumns);

        return runTable;
    }


    public QueryView createView(ViewContext context, QuerySettings settings) throws ServletException
    {
        String name = settings.getQueryName();
        for (ExpProtocol protocol : getProtocols())
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (provider != null)
            {
                if (name != null && name.equals(AssayService.get().getResultsTableName(protocol)))
                {
                    return new ResultsQueryView(protocol, context, settings);
                }
            }
        }

        return super.createView(context, settings);
    }

    private class AssayPropertyForeignKey extends PropertyForeignKey
    {
        public AssayPropertyForeignKey(PropertyDescriptor[] pds)
        {
            super(pds, AssaySchemaImpl.this);
        }

        protected ColumnInfo constructColumnInfo(ColumnInfo parent, String name, final PropertyDescriptor pd)
        {
            ColumnInfo result = super.constructColumnInfo(parent, name, pd);
            if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(pd.getName()))
            {
                result.setFk(new LookupForeignKey("Folder", "Label")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        FilteredTable table = new FilteredTable(StudyManager.getSchema().getTable("Study"));
                        table.setContainerFilter(new StudyContainerFilter(AssaySchemaImpl.this));
                        ExprColumn col = new ExprColumn(table, "Folder", new SQLFragment("CAST (" + ExprColumn.STR_TABLE_ALIAS + ".Container AS VARCHAR(200))"), Types.VARCHAR);
                        col.setFk(new ContainerForeignKey());
                        table.addColumn(col);
                        table.addWrapColumn(table.getRealTable().getColumn("Label"));
                        return table;
                    }
                });
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new DataColumn(colInfo)
                        {
                            public String getFormattedValue(RenderContext ctx)
                            {
                                Object value = getDisplayColumn().getValue(ctx);
                                if (value == null)
                                {
                                    return "[None]";
                                }
                                return super.getFormattedValue(ctx);
                            }
                        };
                    }
                });
            }
            if (pd.getPropertyType() == PropertyType.FILE_LINK)
            {
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new FileLinkDisplayColumn(colInfo, pd, new ActionURL(AssayController.DownloadFileAction.class, _container));
                    }
                });
            }
            return result;
        }
    }
}
