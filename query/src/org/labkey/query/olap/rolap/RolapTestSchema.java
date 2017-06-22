/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.query.olap.rolap;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.util.Set;

/**
 * Created by matthew on 9/19/14.
 */
public class RolapTestSchema extends UserSchema
{
    public RolapTestSchema(User user, Container c)
    {
        super("rolap_test", "unit test schema", user, c, CoreSchema.getInstance().getSchema());
    }


    @Nullable
    @Override
    public TableInfo createTable(String name)
    {
        switch (name.toLowerCase())
        {
            case "fact": return createFact();
            case "study": return createStudy();
            case "participant": return createParticipant();
            case "visit": return createVisit();
            case "assay": return createAssay();
        }
        return null;
    }


    @Override
    public Set<String> getTableNames()
    {
        return PageFlowUtil.set("Fact", "Study", "Participant", "Visit", "Assay");
    }

    class TestTableInfo extends AbstractTableInfo
    {
        String sql;

        TestTableInfo(String name, ColumnInfo[] columns, Object[][] data)
        {
            super(getDbSchema(), name);
            for (ColumnInfo c : columns)
            {
                c.setParentTable(this);
                this.addColumn(c);
            }
            SqlDialect d = getDbSchema().getSqlDialect();
            String union = "";
            StringBuilder sql = new StringBuilder();
            for (Object[] row : data)
            {
                sql.append(union);
                union = "\nUNION\n";
                sql.append("SELECT ");
                String comma = "";
                for (int i=0 ; i<columns.length ; i++)
                {
                    ColumnInfo c = columns[i];
                    JdbcType t = c.getJdbcType();
                    Object v = t.convert(row[i]);
                    sql.append(comma);
                    comma = ", ";
                    if (null == v)
                        sql.append("CAST(NULL AS " + d.sqlTypeNameFromJdbcType(t) + (t.isText() ? "(100)" : "") + ")");
                    else
                        sql.append(RolapCubeDef.toSqlLiteral(t,v));
                    sql.append(" AS " + c.getSelectName());
                }
            }
            sql.append("\n");
            this.sql = sql.toString();
        }


        @Override
        protected SQLFragment getFromSQL()
        {
            return new SQLFragment(sql);
        }


        @Nullable
        @Override
        public UserSchema getUserSchema()
        {
            return RolapTestSchema.this;
        }
    }


    static final Object[] row(Object... values)
    {
        return values;
    }


    TableInfo createFact()
    {
        ColumnInfo[] cols = new ColumnInfo[]
        {
            new ColumnInfo("ptid", JdbcType.VARCHAR),
            new ColumnInfo("studyid", JdbcType.VARCHAR),
            new ColumnInfo("visitid", JdbcType.VARCHAR),
            new ColumnInfo("assay", JdbcType.VARCHAR),
            new ColumnInfo("positivity", JdbcType.INTEGER)
        };
        cols[0].setFk(new LookupForeignKey("ptid")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return RolapTestSchema.this.createParticipant();
            }
        });
        Object[][] data = new Object[][]
        {
            row("P001001", "S001", "V0", "mRNA", 0),
            row("P001001", "S001", "V0", "FCS", 0),
            row("P001001", "S001", "V7", "FCS", 0),
            row("P001002", "S001", "V0", "mRNA", 0),
            row("P001002", "S001", "V0", "FCS", 0),
            row("P001002", "S001", "V7", "FCS", 0),
            row("P001003", "S001", "V0", "mRNA", 0),
            row("P001003", "S001", "V0", "FCS", 0),
            row("P001003", "S001", "V7", "FCS", 0),
            row("P001004", "S001", "V0", "mRNA", 0),
            row("P001004", "S001", "V0", "FCS", 0),
            row("P001004", "S001", "V7", "FCS", 0),
            row("P001005", "S001", "V0", "mRNA", 0),
            row("P001005", "S001", "V0", "FCS", 0),
            row("P001005", "S001", "V7", "FCS", 0),
            row("P001006", "S001", "V0", "mRNA", 0),
            row("P001006", "S001", "V0", "FCS", 0),
            row("P001006", "S001", "V7", "FCS", 1),
            row("P001007", "S001", "V0", "mRNA", 0),
            row("P001007", "S001", "V0", "FCS", 0),
            row("P001007", "S001", "V7", "FCS", 1),
            row("P001008", "S001", "V0", "mRNA", 0),
            row("P001008", "S001", "V0", "FCS", 0),
            row("P001008", "S001", "V7", "FCS", 1),

            row("P002001", "S002", "V0", "PCR", 0),
            row("P002001", "S002", "V28", "NAB", 0),
            row("P002002", "S002", "V0", "PCR", 0),
            row("P002002", "S002", "V28", "NAB", 0),
            row("P002003", "S002", "V0", "PCR", 0),
            row("P002003", "S002", "V28", "NAB", 0),
            row("P002004", "S002", "V0", "PCR", 0),
            row("P002004", "S002", "V28", "NAB", 0),
            row("P002005", "S002", "V0", "PCR", 0),
            row("P002005", "S002", "V28", "NAB", 0),
            row("P002006", "S002", "V0", "PCR", 0),
            row("P002006", "S002", "V28", "NAB", 0),
            row("P002007", "S002", "V0", "PCR", 0),
            row("P002007", "S002", "V28", "NAB", 0),
            row("P002008", "S002", "V0", "PCR", 0),
            row("P002008", "S002", "V28", "NAB", 0),

            row("P003001", "S003", "V1", "FCS", 0),
            row("P003002", "S003", "V1", "FCS", 0),
            row("P003003", "S003", "V1", "FCS", 0),
            row("P003004", "S003", "V1", "FCS", 0),
            row("P003005", "S003", "V1", "FCS", 0),
            row("P003006", "S003", "V1", "FCS", 0),
            row("P003007", "S003", "V1", "FCS", 0),
            row("P003008", "S003", "V1", "FCS", 0),

            row("P004001", "S004", "V1", "FCS", 0),
            row("P004002", "S004", "V1", "FCS", 0),
            row("P004003", "S004", "V1", "FCS", 0),
            row("P004004", "S004", "V1", "FCS", 0),
            row("P004005", "S004", "V1", "FCS", 0),
            row("P004006", "S004", "V1", "FCS", 0),
            row("P004007", "S004", "V1", "FCS", 0),
            row("P004008", "S004", "V1", "FCS", 0),

            row("P005001", "S005", "V1", "FCS", 0),
            row("P005002", "S005", "V1", "FCS", 0),
            row("P005003", "S005", "V1", "FCS", 0),
            row("P005004", "S005", "V1", "FCS", 0),
            row("P005005", "S005", "V1", "FCS", 0),
            row("P005006", "S005", "V1", "FCS", 0),
            row("P005007", "S005", "V1", "FCS", 0),
            row("P005008", "S005", "V1", "FCS", 0),

            row("P006001", "S006", "V1", "FCS", 0),
            row("P006002", "S006", "V1", "FCS", 0),
            row("P006003", "S006", "V1", "FCS", 0),
            row("P006004", "S006", "V1", "FCS", 0),
            row("P006005", "S006", "V1", "FCS", 0),
            row("P006006", "S006", "V1", "FCS", 0),
            row("P006007", "S006", "V1", "FCS", 0),
            row("P006008", "S006", "V1", "FCS", 0)
        };

        return new TestTableInfo("Fact", cols, data);
    }

    TableInfo createStudy()
    {
        ColumnInfo[] cols = new ColumnInfo[]
        {
            new ColumnInfo("studyid", JdbcType.VARCHAR),
            new ColumnInfo("type", JdbcType.VARCHAR),
            new ColumnInfo("condition", JdbcType.VARCHAR)
        };
        Object[][] data = new Object[][]
        {
           row("S001", "Interventional", "Influenza"),
           row("S002", "Observational", "Hepatitis C"),
           row("S003", "Longitudinal", "Influenza"),
           row("S004", "Interventional", "Influenza"),
           row("S005", "Observational", "Smallpox"),
           row("S006", "Longitudinal", "Influenza")
        };
        return new TestTableInfo("Study", cols, data);
    }

    TableInfo createParticipant()
    {
        ColumnInfo[] cols = new ColumnInfo[]
        {
            new ColumnInfo("ptid", JdbcType.VARCHAR),
            new ColumnInfo("gender", JdbcType.VARCHAR),
            new ColumnInfo("species", JdbcType.VARCHAR)
        };
        Object[][] data = new Object[][]
        {
            row("P001001", "Male", "Human"),
            row("P001002", "Female", "Human"),
            row("P001003", "Male", "Human"),
            row("P001004", "Female", "Human"),
            row("P001005", "Male", "Human"),
            row("P001006", "Female", "Human"),
            row("P001007", "Male", "Human"),
            row("P001008", "Female", "Human"),

            row("P002001", "Male", "Human"),
            row("P002002", "Female", "Human"),
            row("P002003", "Male", "Human"),
            row("P002004", "Female", "Human"),
            row("P002005", "Male", "Human"),
            row("P002006", "Female", "Human"),
            row("P002007", "Male", "Human"),
            row("P002008", null, "Human"),

            row("P003001", "Male", "Monkey"),
            row("P003002", "Female", "Monkey"),
            row("P003003", "Male", "Monkey"),
            row("P003004", "Female", "Monkey"),
            row("P003005", "Male", "Monkey"),
            row("P003006", "Female", "Monkey"),
            row("P003007", "Male", "Monkey"),
            row("P003008", null, "Monkey"),

            row("P004001", "Male", "Mouse"),
            row("P004002", "Female", "Mouse"),
            row("P004003", "Male", "Mouse"),
            row("P004004", "Female", "Mouse"),
            row("P004005", "male", "mouse"),
            row("P004006", "female", "mouse"),
            row("P004007", "male", "mouse"),
            row("P004008", null, "Mouse"),

            row("P005001", "Male", null),
            row("P005002", "Female", null),
            row("P005003", "Male", null),
            row("P005004", "Female", null),
            row("P005005", "Male", null),
            row("P005006", "Female", null),
            row("P005007", "Male", null),
            row("P005008", null, null),

            row("P006001", null, null),
            row("P006002", null, null),
            row("P006003", null, null),
            row("P006004", null, null),
            row("P006005", null, null),
            row("P006006", null, null),
            row("P006007", null, null),
            row("P006008", null, null)
        };

        return new TestTableInfo("Participant", cols, data);
    }


    TableInfo createVisit()
    {
        ColumnInfo[] cols = new ColumnInfo[]
        {
            new ColumnInfo("visitid", JdbcType.VARCHAR),
            new ColumnInfo("label", JdbcType.VARCHAR)
        };
        Object[][] data = new Object[][]
        {
           row("V0", "Day 0"),
           row("V1", "Day 1"),
           row("V7", "Day 7"),
           row("V28", "Follow-up")
        };
        return new TestTableInfo("Visit", cols, data);
    }


    TableInfo createAssay()
    {
        ColumnInfo[] cols = new ColumnInfo[]
        {
                new ColumnInfo("name", JdbcType.VARCHAR),
                new ColumnInfo("label", JdbcType.VARCHAR)
        };
        Object[][] data = new Object[][]
        {
            row("FCS", "Flow Cytometry"),
            row("mRNA", "Gene Expression"),
            row("PCR", "Polymerase Chain Reaction"),
            row("NAB", "Neutralizing Antibody"),
            row("XYZ", "PDQ")
        };
        return new TestTableInfo("Visit", cols, data);
    }
}
