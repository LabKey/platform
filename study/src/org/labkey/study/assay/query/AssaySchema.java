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
import org.labkey.api.exp.query.ExpSchema;
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
public class AssaySchema extends UserSchema
{
    static public class Provider extends DefaultSchema.SchemaProvider
    {
        public QuerySchema getSchema(DefaultSchema schema)
        {
            return new AssaySchema(schema.getUser(), schema.getContainer());
        }
    }

    public AssaySchema(User user, Container container)
    {
        super(AssayService.ASSAY_SCHEMA_NAME, user, container, ExperimentService.get().getSchema());
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

        names.add("AssayList");
        for (ExpProtocol protocol : AssayService.get().getAssayProtocols(getContainer()))
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

    @Override
    public TableInfo createTable(String name, String alias)
    {
        if (name.equals("AssayList"))
            return new AssayListTable(this, alias);
        else
        {
            for (ExpProtocol protocol : AssayService.get().getAssayProtocols(getContainer()))
            {
                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (provider != null)
                {
                    if (name.equalsIgnoreCase(getBatchesTableName(protocol)))
                    {
                        return createBatchesTable(alias, protocol, provider, null);
                    }
                    if (name.equalsIgnoreCase(getRunsTableName(protocol)))
                    {
                        return createRunTable(alias, protocol, provider);
                    }
                    if (name.equalsIgnoreCase(getResultsTableName(protocol)) || name.equalsIgnoreCase(protocol.getName() + " Data"))
                    {
                        return provider.createDataTable(this, alias, protocol);
                    }
                }
            }
        }
        return null;
    }

    public ExpExperimentTable createBatchesTable(String alias, ExpProtocol protocol, AssayProvider provider, final ContainerFilter containerFilter)
    {
        final ExpExperimentTable result = new ExpSchema(getUser(), getContainer()).createExperimentsTable(getBatchesTableName(protocol), alias);
        if (containerFilter != null)
        {
            result.setContainerFilter(containerFilter, getUser());
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
        for (DomainProperty prop : provider.getBatchDomain(protocol).getProperties())
        {
            pds.add(prop.getPropertyDescriptor());
            if (!prop.isHidden())
                defaultCols.add(FieldKey.fromParts("Batch Properties", prop.getName()));
        }

        result.addPropertyColumns("Batch Properties", pds.toArray(new PropertyDescriptor[pds.size()]), this);
        result.setDefaultVisibleColumns(defaultCols);
        return result;
    }

    public ExpRunTable createRunTable(String alias, final ExpProtocol protocol, final AssayProvider provider)
    {
        final ExpRunTable runTable = provider.createRunTable(this, alias, protocol);
        runTable.setProtocolPatterns(protocol.getLSID());

        List<PropertyDescriptor> runColumns = provider.getRunTableColumns(protocol);
        PropertyDescriptor[] pds = runColumns.toArray(new PropertyDescriptor[runColumns.size()]);

        ColumnInfo propsCol = runTable.addPropertyColumns("Run Properties", pds, this);
        propsCol.setFk(new AssayPropertyForeignKey(pds));

        SQLFragment batchSQL = new SQLFragment("(SELECT MIN(ExperimentId) FROM ");
        batchSQL.append(ExperimentService.get().getTinfoRunList());
        batchSQL.append(" rl, ");
        batchSQL.append(ExperimentService.get().getTinfoExperiment());
        batchSQL.append(" e WHERE e.RowId = rl.ExperimentId AND rl.ExperimentRunId = ");
        batchSQL.append(ExprColumn.STR_TABLE_ALIAS);
        batchSQL.append(".RowId AND e.BatchProtocolId = ");
        batchSQL.append(protocol.getRowId());
        batchSQL.append(")");
        ExprColumn batchColumn = new ExprColumn(runTable, "Batch", batchSQL, Types.INTEGER, runTable.getColumn(ExpRunTable.Column.RowId));
        batchColumn.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createBatchesTable(null, protocol, provider, runTable.getContainerFilter());
            }
        });
        runTable.addColumn(batchColumn);

        List<FieldKey> visibleColumns = new ArrayList<FieldKey>(runTable.getDefaultVisibleColumns());
        FieldKey runKey = FieldKey.fromString("Run Properties");
        for (PropertyDescriptor runColumn : runColumns)
        {
            if (!runColumn.isHidden())
                visibleColumns.add(new FieldKey(runKey, runColumn.getName()));
        }

        runTable.setTitleColumn("");
        runTable.setDefaultVisibleColumns(visibleColumns);

        return runTable;
    }


    public QueryView createView(ViewContext context, QuerySettings settings) throws ServletException
    {
        String name = settings.getQueryName();
        for (ExpProtocol protocol : AssayService.get().getAssayProtocols(context.getContainer()))
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
            super(pds, AssaySchema.this);
        }

        protected ColumnInfo constructColumnInfo(ColumnInfo parent, String name, final PropertyDescriptor pd)
        {
            ColumnInfo result = super.constructColumnInfo(parent, name, pd);
            if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(pd.getName()))
            {
                result.setFk(new LookupForeignKey("Container", "Label")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        FilteredTable table = new FilteredTable(StudyManager.getSchema().getTable("Study"));
                        ExprColumn col = new ExprColumn(table, "Container", new SQLFragment("CAST (" + ExprColumn.STR_TABLE_ALIAS + ".Container AS VARCHAR(200))"), Types.VARCHAR);
                        table.addColumn(col);
                        table.addColumn(table.wrapColumn(table.getRealTable().getColumn("Label")));
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
