/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.query.*;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.security.User;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpRun;

import java.util.*;

public class ExpSchema extends AbstractExpSchema
{
    public static final String EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME = "ExperimentsMembershipForRun";

    public enum TableType
    {
        Runs
        {
            public TableInfo createTable(ExpSchema expSchema)
            {
                return expSchema.createRunsTable();
            }
        },
        Datas
        {
            public TableInfo createTable(ExpSchema expSchema)
            {
                return expSchema.createDatasTable();
            }
        },
        Materials
        {
            public TableInfo createTable(ExpSchema expSchema)
            {
                return expSchema.createMaterialsTable();
            }
        },
        Protocols
        {
            public TableInfo createTable(ExpSchema expSchema)
            {
                return expSchema.createProtocolsTable();
            }
        },
        SampleSets
        {
            public TableInfo createTable(ExpSchema expSchema)
            {
                return expSchema.createSampleSetTable();
            }
        },
        RunGroups
        {
            public TableInfo createTable(ExpSchema expSchema)
            {
                return expSchema.createExperimentsTable(RunGroups.toString());
            }
        },
        ProtocolApplications
        {
            public TableInfo createTable(ExpSchema expSchema)
            {
                return ExperimentService.get().createProtocolApplicationTable(ProtocolApplications.toString(), expSchema);
            }
        };

        public abstract TableInfo createTable(ExpSchema expSchema);
    }

    public ExpExperimentTable createExperimentsTable(String name)
    {
        ExpExperimentTable ret = ExperimentService.get().createExperimentTable(name, this);
        return setupTable(ret);
    }
    
    public ExpExperimentTable createExperimentsTableWithRunMemberships(ExpRun run)
    {
        ExpExperimentTable ret = createExperimentsTable(EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME);
        if (_containerFilter != null)
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
        tableNames = Collections.unmodifiableSet(tableNames);
    }


    public static final String SCHEMA_NAME = "exp";

    static public void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider()
        {
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

    public TableInfo createTable(String name)
    {
        for (TableType tableType : TableType.values())
        {
            if (tableType.name().equalsIgnoreCase(name))
            {
                return tableType.createTable(this);
            }
        }

        // Support "Experiments" as a legacy name for the RunGroups table
        if ("Experiments".equalsIgnoreCase(name))
        {
            return createExperimentsTable(name);
        }
        if (EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createExperimentsTableWithRunMemberships(null);
        }

        return null;
    }

    public ExpDataTable createDatasTable()
    {
        ExpDataTable ret = ExperimentService.get().createDataTable(TableType.Datas.toString(), this);
        return setupTable(ret);
    }

    public ExpSampleSetTable createSampleSetTable()
    {
        ExpSampleSetTable ret = ExperimentService.get().createSampleSetTable(TableType.SampleSets.toString(), this);
        return setupTable(ret);
    }

    public ExpMaterialTable createMaterialsTable()
    {
        return getSamplesSchema().getSampleTable(null);
    }

    public ExpRunTable createRunsTable()
    {
        ExpRunTable ret = ExperimentService.get().createRunTable(TableType.Runs.toString(), this);
        return setupTable(ret);
    }

    public ExpProtocolTable createProtocolsTable()
    {
        ExpProtocolTable ret = ExperimentService.get().createProtocolTable(TableType.Protocols.toString(), this);
        return setupTable(ret);
    }

    public ForeignKey getProtocolForeignKey(String targetColumnName)
    {
        return new LookupForeignKey(targetColumnName)
        {
            public TableInfo getLookupTableInfo()
            {
                ExpProtocolTable protocolTable = createProtocolsTable();
                protocolTable.setContainerFilter(ContainerFilter.EVERYTHING);
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
                return createRunsTable();
            }
        };
    }

    public ForeignKey getDataIdForeignKey()
    {
        return new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createDatasTable();
            }
        };
    }

    public ForeignKey getMaterialIdForeignKey()
    {
        return new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createMaterialsTable();
            }
        };
    }

    public ForeignKey getRunLSIDForeignKey()
    {
        return new LookupForeignKey("LSID")
        {
            public TableInfo getLookupTableInfo()
            {
                return createRunsTable();
            }
        };
    }
}
