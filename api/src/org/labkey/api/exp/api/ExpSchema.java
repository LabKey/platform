/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.exp.api;

import org.labkey.api.query.*;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.security.User;

import java.util.*;

public class ExpSchema extends AbstractExpSchema
{
    public static final String EXPERIMENTS_NARROW_WEB_PART_TABLE_NAME = ExpSchema.TableType.Experiments + "NarrowWebPart";
    public static final String EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME = ExpSchema.TableType.Experiments + "MembershipForRun";
    public static final String RUN_GROUPS_TABLE_NAME = "RunGroups";

    public enum TableType
    {
        Runs
        {
            public TableInfo createTable(String alias, ExpSchema expSchema)
            {
                return expSchema.createRunsTable(alias);
            }
        },
        Datas
        {
            public TableInfo createTable(String alias, ExpSchema expSchema)
            {
                return expSchema.createDatasTable(alias);
            }
        },
        Materials
        {
            public TableInfo createTable(String alias, ExpSchema expSchema)
            {
                return expSchema.createMaterialsTable(alias);
            }
        },
        Protocols
        {
            public TableInfo createTable(String alias, ExpSchema expSchema)
            {
                return expSchema.createProtocolsTable(alias);
            }
        },
        SampleSets
        {
            public TableInfo createTable(String alias, ExpSchema expSchema)
            {
                return expSchema.createSampleSetTable(alias);
            }
        },
        Experiments
        {
            public TableInfo createTable(String alias, ExpSchema expSchema)
            {
                return expSchema.createExperimentsTable(Experiments.toString(), alias);
            }
        },
        ProtocolApplications
        {
            public TableInfo createTable(String alias, ExpSchema expSchema)
            {
                return ExperimentService.get().createProtocolApplicationTable(ProtocolApplications.toString(), alias, expSchema);
            }
        };

        public abstract TableInfo createTable(String alias, ExpSchema expSchema);
    }

    public ExpExperimentTable createExperimentsTable(String name, String alias)
    {
        ExpExperimentTable ret = ExperimentService.get().createExperimentTable(name, alias, this);
        return setupTable(ret);
    }
    
    public ExpExperimentTable createExperimentsTableWithRunMemberships(String alias, ExpRun run)
    {
        ExpExperimentTable ret = createExperimentsTable(EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME, alias);
        ret.setContainerFilter(_containerFilter);
        ret.getColumn(ExpExperimentTable.Column.RunCount).setIsHidden(true);

        ret.addExperimentMembershipColumn(run);
        List<FieldKey> defaultCols = new ArrayList<FieldKey>(ret.getDefaultVisibleColumns());
        defaultCols.add(0, FieldKey.fromParts("RunMembership"));
        defaultCols.remove(FieldKey.fromParts(ExpExperimentTable.Column.RunCount.name()));
        ret.setDefaultVisibleColumns(defaultCols);

        return ret;
    }

    static private Set<String> tableNames = new LinkedHashSet<String>();
    static
    {
        for (TableType type : TableType.values())
        {
            tableNames.add(type.toString());
        }
        tableNames.add(RUN_GROUPS_TABLE_NAME);
        tableNames = Collections.unmodifiableSet(tableNames);
    }


    public static final String SCHEMA_NAME = "exp";

    static public void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider() {
            public QuerySchema getSchema(DefaultSchema schema)
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
        super(SCHEMA_NAME, user, container, ExperimentService.get().getSchema());
    }

    public Set<String> getTableNames()
    {
        return tableNames;
    }

    public TableInfo createTable(String name, String alias)
    {
        try
        {
            return TableType.valueOf(name).createTable(alias, this);
        }
        catch (IllegalArgumentException e)
        {
            // ignore
        }

        // TODO - find a better way to do this. We want to have different sets of views for the experiments table,
        // so this is a hacky way to make sure that customizing one set of views doesn't affect the other.
        if (EXPERIMENTS_NARROW_WEB_PART_TABLE_NAME.equalsIgnoreCase(name) || RUN_GROUPS_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createExperimentsTable(name, alias);
        }
        if (EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createExperimentsTableWithRunMemberships(alias, null);
        }

        return null;
    }

    public ExpDataTable createDatasTable(String alias)
    {
        ExpDataTable ret = ExperimentService.get().createDataTable(TableType.Datas.toString(), alias, this);
        return setupTable(ret);
    }

    public ExpSampleSetTable createSampleSetTable(String alias)
    {
        ExpSampleSetTable ret = ExperimentService.get().createSampleSetTable(TableType.SampleSets.toString(), alias, this);
        return setupTable(ret);
    }

    public ExpMaterialTable createMaterialsTable(String alias)
    {
        return getSamplesSchema().getSampleTable(alias, null);
    }

    public ExpRunTable createRunsTable(String alias)
    {
        ExpRunTable ret = ExperimentService.get().createRunTable(TableType.Runs.toString(), alias, this);
        return setupTable(ret);
    }

    public ExpProtocolTable createProtocolsTable(String alias)
    {
        ExpProtocolTable ret = ExperimentService.get().createProtocolTable(TableType.Protocols.toString(), alias, this);
        return setupTable(ret);
    }

    public ForeignKey getProtocolLSIDForeignKey()
    {
        return new LookupForeignKey("LSID")
        {
            public TableInfo getLookupTableInfo()
            {
                ExpProtocolTable protocolTable = createProtocolsTable("Protocols");
                protocolTable.setContainerFilter(ContainerFilter.Filters.EVERYTHING);
                return protocolTable;
            }
        };
    }

    public ForeignKey getRunIdForeignKey()
    {
        return new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createRunsTable("Runs");
            }
        };
    }

    public ForeignKey getDataIdForeignKey()
    {
        return new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createDatasTable("Datas");
            }
        };
    }

    public ForeignKey getMaterialIdForeignKey()
    {
        return new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createMaterialsTable("Materials");
            }
        };
    }

    public ForeignKey getRunLSIDForeignKey()
    {
        return new LookupForeignKey("LSID")
        {
            public TableInfo getLookupTableInfo()
            {
                return createRunsTable("Runs");
            }
        };
    }
}
