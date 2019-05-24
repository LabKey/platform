/*
 * Copyright (c) 2006-2018 LabKey Corporation
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

package org.labkey.api.exp.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class ExpSchema extends AbstractExpSchema
{
    public static final String EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME = "ExperimentsMembershipForRun";


    public enum NestedSchemas
    {
        data,
        materials
    }

    public enum TableType
    {
        Runs
        {
            @Override
            public TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf)
            {
                ExpRunTable ret = ExperimentService.get().createRunTable(Runs.toString(), expSchema, cf);
                return expSchema.setupTable(ret);
            }
        },
        Data
        {
            @Override
            public TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf)
            {
                ExpDataTable ret = ExperimentService.get().createDataTable(Data.toString(), expSchema, cf);
                return expSchema.setupTable(ret);
            }
        },
        DataInputs
        {
            @Override
            public TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf)
            {
                ExpDataInputTable ret = ExperimentService.get().createDataInputTable(DataInputs.toString(), expSchema, cf);
                return expSchema.setupTable(ret);
            }
        },
        DataProtocolInputs
        {
            @Override
            public TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf)
            {
                ExpDataProtocolInputTable ret = ExperimentService.get().createDataProtocolInputTable(DataProtocolInputs.toString(), expSchema, cf);
                return expSchema.setupTable(ret);
            }
        },
        Materials
        {
            @Override
            public TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf)
            {
                SamplesSchema schema = new SamplesSchema(expSchema.getPath(), expSchema.getUser(), expSchema.getContainer());
                schema.setContainerFilter(expSchema._containerFilter);
                ExpMaterialTable result = schema.getSampleTable(null, cf);
                return result;
            }
        },
        MaterialInputs
        {
            @Override
            public TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf)
            {
                ExpMaterialInputTable ret = ExperimentService.get().createMaterialInputTable(MaterialInputs.toString(), expSchema, cf);
                return expSchema.setupTable(ret);
            }
        },
        MaterialProtocolInputs
        {
            @Override
            public TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf)
            {
                ExpMaterialProtocolInputTable ret = ExperimentService.get().createMaterialProtocolInputTable(MaterialProtocolInputs.toString(), expSchema, cf);
                return expSchema.setupTable(ret);
            }
        },
        Protocols
        {
            @Override
            public TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf)
            {
                ExpProtocolTable ret = ExperimentService.get().createProtocolTable(Protocols.toString(), expSchema, cf);
                return expSchema.setupTable(ret);
            }
        },
        SampleSets
        {
            @Override
            public TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf)
            {
                ExpSampleSetTable ret = ExperimentService.get().createSampleSetTable(SampleSets.toString(), expSchema, cf);
                return expSchema.setupTable(ret);
            }
        },
        DataClasses
        {
            @Override
            public TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf)
            {
                ExpDataClassTable ret = ExperimentService.get().createDataClassTable(DataClasses.toString(), expSchema, cf);
                return expSchema.setupTable(ret);
            }
        },
        RunGroups
        {
            @Override
            public TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf)
            {
                ExpExperimentTable ret = ExperimentService.get().createExperimentTable(RunGroups.toString(), expSchema, cf);
                return expSchema.setupTable(ret);
            }
        },
        RunGroupMap
        {
            @Override
            public TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf)
            {
                ExpRunGroupMapTable ret = ExperimentService.get().createRunGroupMapTable(RunGroupMap.toString(), expSchema, cf);
                return expSchema.setupTable(ret);
            }
        },
        ProtocolApplications
        {
            @Override
            public TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf)
            {
                ExpProtocolApplicationTable result = ExperimentService.get().createProtocolApplicationTable(ProtocolApplications.toString(), expSchema, cf);
                return expSchema.setupTable(result);
            }
        },
        QCFlags
        {
            @Override
            public TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf)
            {
                ExpQCFlagTable result = ExperimentService.get().createQCFlagsTable(QCFlags.toString(), expSchema, cf);
                return expSchema.setupTable(result);
            }
        },
        Files
        {
            @Override
            public TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf)
            {
                ExpDataTable result = ExperimentService.get().createFilesTable(Files.toString(), expSchema);
                return expSchema.setupTable(result);
            }
        };
        public abstract TableInfo createTable(ExpSchema expSchema, String queryName, ContainerFilter cf);
    }

    public ExpTable getTable(TableType tableType)
    {
        return (ExpTable)getTable(tableType.toString());
    }

    public ExpTable getTable(TableType tableType, ContainerFilter cf)
    {
        return (ExpTable)getTable(tableType.toString(), cf);
    }

    public ExpExperimentTable createExperimentsTableWithRunMemberships(ExpRun run, ContainerFilter cf)
    {
        ExpExperimentTable ret = ExperimentService.get().createExperimentTable(EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME, this, cf);
        setupTable(ret);
        // Don't include exp.experiment rows that are assay batches
        ret.setBatchProtocol(null);
        ret.getMutableColumn(ExpExperimentTable.Column.RunCount).setHidden(true);

        ret.addExperimentMembershipColumn(run);
        List<FieldKey> defaultCols = new ArrayList<>(ret.getDefaultVisibleColumns());
        defaultCols.add(0, FieldKey.fromParts("RunMembership"));
        defaultCols.remove(FieldKey.fromParts(ExpExperimentTable.Column.RunCount.name()));
        ret.setDefaultVisibleColumns(defaultCols);

        return ret;
    }

    static private Set<String> tableNames = new LinkedHashSet<>();

    static
    {
        for (TableType type : TableType.values())
        {
            tableNames.add(type.toString());
        }
        tableNames = Collections.unmodifiableSet(tableNames);
    }

    public static final String SCHEMA_NAME = "exp";
    public static final String SCHEMA_DESCR = "Contains data about experiment runs, data files, materials, sample sets, etc.";

    static public void register(final Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                return true;
            }

            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new ExpSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public SamplesSchema getSamplesSchema()
    {
        SamplesSchema schema = new SamplesSchema(getUser(), getContainer());
        schema.setContainerFilter(_containerFilter);
        return schema;
    }

    public ExpSchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCR, user, container, ExperimentService.get().getSchema());
    }

    public Set<String> getTableNames()
    {
        return tableNames;
    }

    public TableInfo createTable(String name, ContainerFilter cf)
    {
        for (TableType tableType : TableType.values())
        {
            if (tableType.name().equalsIgnoreCase(name))
            {
                return tableType.createTable(this, tableType.name(), cf);
            }
        }

        if ("Experiments".equalsIgnoreCase(name))
        {
            // Support "Experiments" as a legacy name for the RunGroups table
            return TableType.RunGroups.createTable(this, name, cf);
        }
        if ("Datas".equalsIgnoreCase(name))
        {
            /// Support "Datas" as a legacy name for the Data table
            return TableType.Data.createTable(this, name, cf);
        }
        if (EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createExperimentsTableWithRunMemberships(null, cf);
        }

        return null;
    }

    @Override
    public Set<String> getSchemaNames()
    {
        if (_restricted)
            return Collections.emptySet();

        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(super.getSchemaNames());
        names.add(NestedSchemas.materials.name());
        names.add(NestedSchemas.data.name());
        return names;
    }

    public QuerySchema getSchema(NestedSchemas schema)
    {
        return getSchema(schema.name());
    }

    public QuerySchema getSchema(String name)
    {
        if (_restricted)
            return null;

        // CONSIDER: also support hidden "samples" schema ?
        if (name.equals(NestedSchemas.materials.name()))
            return new SamplesSchema(SchemaKey.fromParts(getName(), NestedSchemas.materials.name()), getUser(), getContainer());

        if (name.equals(NestedSchemas.data.name()))
            return new DataClassUserSchema(getContainer(), getUser());

        return super.getSchema(name);
    }

    public ExpDataTable getDatasTable()
    {
        return (ExpDataTable)getTable(TableType.Data);
    }

    public ExpDataTable getDatasTable(boolean forWrite)
    {
        return (ExpDataTable)getTable(TableType.Data.toString(), null, true, forWrite);
    }

    public ExpRunTable getRunsTable()
    {
        return (ExpRunTable)getTable(TableType.Runs.toString(), null, true, false);
    }

    public ExpRunTable getRunsTable(boolean forWrite)
    {
        return (ExpRunTable)getTable(TableType.Runs.toString(), null, true, forWrite);
    }


    public ForeignKey getProtocolApplicationForeignKey()
    {
        return new ExperimentLookupForeignKey(null, null, ExpSchema.SCHEMA_NAME, TableType.ProtocolApplications.name(), "RowId", null)
        {
            public TableInfo getLookupTableInfo()
            {
                return getTable(TableType.ProtocolApplications);
            }
        };
    }

    public ForeignKey getProtocolForeignKey(ContainerFilter cf, String targetColumnName)
    {
        return new LookupForeignKey(targetColumnName)
        {
            public TableInfo getLookupTableInfo()
            {
                ExpProtocolTable protocolTable = (ExpProtocolTable)TableType.Protocols.createTable(ExpSchema.this, TableType.Protocols.toString(), cf);
                protocolTable.setContainerFilter(ContainerFilter.EVERYTHING);
                return protocolTable;
            }
        };
    }

    public ForeignKey getMaterialProtocolInputForeignKey()
    {
        return new ExperimentLookupForeignKey(null, null, ExpSchema.SCHEMA_NAME, TableType.MaterialProtocolInputs.name(), "RowId", null)
        {
            public TableInfo getLookupTableInfo()
            {
                return getTable(TableType.MaterialProtocolInputs);
            }
            public TableInfo getLookupTableInfo(ContainerFilter cf)
            {
                return getTable(TableType.MaterialProtocolInputs, cf);
            }
        };
    }

    public ForeignKey getDataProtocolInputForeignKey()
    {
        return new ExperimentLookupForeignKey(null, null, ExpSchema.SCHEMA_NAME, TableType.DataProtocolInputs.name(), "RowId", null)
        {
            public TableInfo getLookupTableInfo()
            {
                return getTable(TableType.DataProtocolInputs);
            }
            public TableInfo getLookupTableInfo(ContainerFilter cf)
            {
                return getTable(TableType.DataProtocolInputs, cf);
            }
        };
    }

    public ForeignKey getJobForeignKey()
    {
        return new LookupForeignKey("RowId", "RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return PipelineService.get().getJobsTable(getUser(), getContainer());
            }

            public StringExpression getURL(ColumnInfo parent)
            {
                return getURL(parent, true);
            }
        };
    }

    public ForeignKey getRunIdForeignKey()
    {
        return new ExperimentLookupForeignKey(null, null, ExpSchema.SCHEMA_NAME, TableType.Runs.name(), "RowId", null)
        {
            public TableInfo getLookupTableInfo()
            {
                return getTable(TableType.Runs);
            }
            public TableInfo getLookupTableInfo(ContainerFilter cf)
            {
                return getTable(TableType.Runs, cf);
            }
        };
    }

    /** @param includeBatches if false, then filter out run groups of type batch when doing the join */
    public ForeignKey getRunGroupIdForeignKey(final boolean includeBatches)
    {
        return new ExperimentLookupForeignKey(null, null, ExpSchema.SCHEMA_NAME, TableType.RunGroups.name(), "RowId", null)
        {
            public TableInfo getLookupTableInfo()
            {
                return getLookupTableInfo(null);
            }
            public TableInfo getLookupTableInfo(ContainerFilter cf)
            {
                ExpExperimentTable result = (ExpExperimentTable)getTable(TableType.RunGroups.name(),
                        Objects.requireNonNullElse(cf, new ContainerFilter.CurrentPlusProjectAndShared(getUser())), true, true);
                if (!includeBatches)
                {
                    result.setBatchProtocol(null);
                }
                result.setLocked(true);
                return result;
            }
        };
    }

    public ForeignKey getDataIdForeignKey()
    {
        return new ExperimentLookupForeignKey(null, null, ExpSchema.SCHEMA_NAME, TableType.Data.name(), "RowId", null)
        {
            public TableInfo getLookupTableInfo()
            {
                return getTable(TableType.Data);
            }
        };
    }

    /**
     * @param domainProperty the property on which the lookup is configured
     */
    @NotNull
    public ForeignKey getMaterialIdForeignKey(@Nullable ExpSampleSet targetSampleSet, @Nullable DomainProperty domainProperty)
    {
        if (targetSampleSet == null)
        {
            return new ExperimentLookupForeignKey(null, null, ExpSchema.SCHEMA_NAME, TableType.Materials.name(), "RowId", null)
            {
                public TableInfo getLookupTableInfo()
                {
                    // UNDONE ContainerFilter
//                    ContainerFilterable t = (ContainerFilterable)table;
//                    // Merge the special container filter set above with whatever else might have been requested in the parent table's
//                    t.setContainerFilter(new UnionContainerFilter(t.getContainerFilter(), parent.getParentTable().getContainerFilter()));

                    ContainerFilter cf = new ContainerFilter.SimpleContainerFilter(getSearchContainers(getContainer(), targetSampleSet, domainProperty, getUser()));
                    ExpTable result = getTable(TableType.Materials, cf);
                    return result;
                }
            };
        }
        return getSamplesSchema().materialIdForeignKey(targetSampleSet, domainProperty);
    }

    @NotNull
    public static Set<Container> getSearchContainers(Container currentContainer, @Nullable ExpSampleSet ss, @Nullable DomainProperty dp, User user)
    {
        Set<Container> searchContainers = new LinkedHashSet<>();
        if (dp != null)
        {
            Lookup lookup = dp.getLookup();
            if (lookup != null && lookup.getContainer() != null)
            {
                Container lookupContainer = lookup.getContainer();
                if (lookupContainer.hasPermission(user, ReadPermission.class))
                {
                    // The property is specifically targeting a container, so look there and only there
                    searchContainers.add(lookup.getContainer());
                }
            }
        }

        if (searchContainers.isEmpty())
        {
            // Default to looking in the current container
            searchContainers.add(currentContainer);
            if (ss == null || (ss.getContainer().isProject() && !currentContainer.isProject()))
            {
                Container c = currentContainer.getParent();
                // Recurse up the chain to the project
                while (c != null && !c.isRoot())
                {
                    if (c.hasPermission(user, ReadPermission.class))
                    {
                        searchContainers.add(c);
                    }
                    c = c.getParent();
                }
            }
            Container sharedContainer = ContainerManager.getSharedContainer();
            if (ss == null || ss.getContainer().equals(sharedContainer))
            {
                if (sharedContainer.hasPermission(user, ReadPermission.class))
                {
                    searchContainers.add(ContainerManager.getSharedContainer());
                }
            }
        }
        return searchContainers;
    }

    public abstract static class ExperimentLookupForeignKey extends LookupForeignKey
    {
        public ExperimentLookupForeignKey(String pkColumnName)
        {
            super(pkColumnName);
        }

        public ExperimentLookupForeignKey(ActionURL baseURL, String paramName, String schemaName, String tableName, String pkColumnName, String titleColumn)
        {
            super(baseURL, paramName, schemaName, tableName, pkColumnName, titleColumn);
        }

        @Override
        public StringExpression getURL(ColumnInfo parent)
        {
            return getURL(parent, true);
        }
    }

    @Override
    public QueryView createView(ViewContext context, @NotNull QuerySettings settings, BindException errors)
    {
        if (TableType.DataClasses.name().equalsIgnoreCase(settings.getQueryName()))
        {
            return new QueryView(this, settings, errors)
            {
                @Override
                protected boolean canInsert()
                {
                    TableInfo table = getTable();
                    return table != null && table.hasPermission(getUser(), InsertPermission.class);
                }

                @Override
                public boolean showImportDataButton()
                {
                    return false;
                }
            };
        }

        if (TableType.Materials.name().equalsIgnoreCase(settings.getQueryName()) ||
            TableType.Data.name().equalsIgnoreCase(settings.getQueryName()))
        {
            return new QueryView(this, settings, errors)
            {
                @Override
                public ActionButton createDeleteButton()
                {
                    // Use default delete button, but without showing the confirmation text
                    ActionButton button = super.createDeleteButton();
                    if (button != null)
                    {
                        button.setRequiresSelection(true);
                    }
                    return button;
                }
            };
        }

        return super.createView(context, settings, errors);
    }
}
