/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.visualization.test;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.visualization.IVisualizationSourceQuery;
import org.labkey.api.visualization.VisualizationProvider;
import org.labkey.api.visualization.VisualizationSourceColumn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Created by matthew on 6/8/15.
 */
public class VisTestSchema extends UserSchema
{
    public VisTestSchema(User user, Container c)
    {
        super("vis_junit", "unit test schema", user, c, CoreSchema.getInstance().getSchema());
    }


    @Nullable
    @Override
    public TableInfo createTable(String name)
    {
        switch (name.toLowerCase())
        {
            case "demographics":
                return createDemographics();
            case "study":
                return createStudy();
            case "visit":
                return createVisit();
            case "flow":
                return createAssay("Flow", "cellcount", 10000, "lin");
            case "ics":
                return createAssay("ICS", "MFI", 1000, "log");
        }
        return null;
    }


    @Override
    public Set<String> getTableNames()
    {
        return new CaseInsensitiveHashSet(Arrays.asList("Demographics", "Study", "Visit", "Flow", "ICS"));
    }


    class TestTableInfo extends AbstractTableInfo
    {
        final String sql;
        final String[] keys;

        TestTableInfo(String name, ColumnInfo[] columns, String[] keys, Object[][] data)
        {
            super(getDbSchema(), name);
            this.keys = keys;
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
                for (int i = 0; i < columns.length; i++)
                {
                    ColumnInfo c = columns[i];
                    JdbcType t = c.getJdbcType();
                    Object v = t.convert(row[i]);
                    sql.append(comma);
                    comma = ", ";
                    if (null == v)
                        sql.append("CAST(NULL AS ").append(d.sqlTypeNameFromJdbcType(t)).append(t.isText() ? "(100)" : "").append(")");
                    else
                        sql.append(toSqlLiteral(t, v));
                    sql.append(" AS ").append(c.getSelectName());
                }
            }
            sql.append("\n");
            this.sql = sql.toString();
        }


        @NotNull
        @Override
        public List<ColumnInfo> getPkColumns()
        {
            List<ColumnInfo> ret = new ArrayList<>(keys.length);
            for (String key : keys)
                ret.add(getColumn(key));
            return ret;
        }


        @NotNull
        @Override
        public SQLFragment getFromSQL(String alias)
        {
            SQLFragment cte = new SQLFragment(this.sql);
            SQLFragment ret = new SQLFragment();
            String token = ret.addCommonTableExpression(this.getName(), this.getName(), cte);
            ret.append(token).append(" ").append(alias);
            return ret;
        }


        @Override
        protected SQLFragment getFromSQL()
        {
            return getFromSQL(getName());
        }


        @Nullable
        @Override
        public UserSchema getUserSchema()
        {
            return VisTestSchema.this;
        }
    }


    static Object[] row(Object... values)
    {
        return values;
    }


    public static final List<String> humans = Arrays.asList(
            "P001001","P001002","P001003","P001004","P001005","P001006","P001007","P001008",
            "P002001","P002002","P002003","P002004","P002005","P002006","P002007","P002008"
    );
    public static final List<String> males = Arrays.asList(
            "P001001","P001003","P001005","P001007",
            "P002001","P002003","P002005","P002007",
            "P003001","P003003","P003005","P003007",
            "P004001","P004003","P004005","P004007",
            "P005001","P005003","P005005","P005007"
    );

    TableInfo createDemographics()
    {
        ColumnInfo[] cols = new ColumnInfo[]
        {
                new ColumnInfo("participantid", JdbcType.VARCHAR),
                new ColumnInfo("study", JdbcType.VARCHAR),
                new ColumnInfo("age", JdbcType.INTEGER),
                new ColumnInfo("gender", JdbcType.VARCHAR),
                new ColumnInfo("species", JdbcType.VARCHAR)
        };
        Object[][] data = new Object[][]
        {
                row("P001001", "S001", 21, "Male", "Human"),
                row("P001002", "S001", 22, "Female", "Human"),
                row("P001003", "S001", 23, "Male", "Human"),
                row("P001004", "S001", 24, "Female", "Human"),
                row("P001005", "S001", 25, "Male", "Human"),
                row("P001006", "S001", 26, "Female", "Human"),
                row("P001007", "S001", 27, "Male", "Human"),
                row("P001008", "S001", 28, "Female", "Human"),

                row("P002001", "S002", 21, "Male", "Human"),
                row("P002002", "S002", 22, "Female", "Human"),
                row("P002003", "S002", 23, "Male", "Human"),
                row("P002004", "S002", 24, "Female", "Human"),
                row("P002005", "S002", 25, "Male", "Human"),
                row("P002006", "S002", 26, "Female", "Human"),
                row("P002007", "S002", 27, "Male", "Human"),
                row("P002008", "S002", 28, null, "Human"),

                row("P003001", "S003", 31, "Male", "Monkey"),
                row("P003002", "S003", 32, "Female", "Monkey"),
                row("P003003", "S003", 33, "Male", "Monkey"),
                row("P003004", "S003", 34, "Female", "Monkey"),
                row("P003005", "S003", 35, "Male", "Monkey"),
                row("P003006", "S003", 36, "Female", "Monkey"),
                row("P003007", "S003", 37, "Male", "Monkey"),
                row("P003008", "S003", 38, null, "Monkey"),

                row("P004001", "S004", 40, "Male", "Mouse"),
                row("P004002", "S004", 40, "Female", "Mouse"),
                row("P004003", "S004", 40, "Male", "Mouse"),
                row("P004004", "S004", 40, "Female", "Mouse"),
                row("P004005", "S004", 40, "male", "mouse"),
                row("P004006", "S004", 40, "female", "mouse"),
                row("P004007", "S004", 40, "male", "mouse"),
                row("P004008", "S004", 40, null, "Mouse"),

                row("P005001", "S005", 50, "Male", null),
                row("P005002", "S005", 50, "Female", null),
                row("P005003", "S005", 50, "Male", null),
                row("P005004", "S005", 50, "Female", null),
                row("P005005", "S005", 50, "Male", null),
                row("P005006", "S005", 50, "Female", null),
                row("P005007", "S005", 50, "Male", null),
                row("P005008", "S005", 50, null, null),

                row("P006001", "S006", 60, null, null),
                row("P006002", "S006", 60, null, null),
                row("P006003", "S006", 60, null, null),
                row("P006004", "S006", 60, null, null),
                row("P006005", "S006", 60, null, null),
                row("P006006", "S006", 60, null, null),
                row("P006007", "S006", 60, null, null),
                row("P006008", "S006", 60, null, null)
        };

        return new TestTableInfo("Demographics", cols, new String[]{"participantid"}, data);
    }

    TableInfo createStudy()
    {
        ColumnInfo[] cols = new ColumnInfo[]
        {
                new ColumnInfo("study", JdbcType.VARCHAR),
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
        return new TestTableInfo("Study", cols, new String[]{"study"}, data);
    }

    TableInfo createVisit()
    {
        ColumnInfo[] cols = new ColumnInfo[]
        {
                new ColumnInfo("sequencenum", JdbcType.INTEGER),
                new ColumnInfo("visit", JdbcType.VARCHAR),
                new ColumnInfo("label", JdbcType.VARCHAR)
        };
        Object[][] data = new Object[][]
        {
                row(0, "V0", "Day 0"),
                row(1, "V1", "Day 1"),
                row(7, "V7", "Day 7"),
                row(28, "V28", "Follow-up")
        };
        return new TestTableInfo("Visit", cols, new String[]{"sequencenum"}, data);
    }


    TableInfo createAssay(String assayName, String measureName, double range, String scale)
    {
        ColumnInfo[] cols = new ColumnInfo[]
        {
                new ColumnInfo("container", JdbcType.VARCHAR),
                new ColumnInfo("participantid", JdbcType.VARCHAR),
                new ColumnInfo("sequencenum", JdbcType.INTEGER),
                new ColumnInfo("antigen", JdbcType.VARCHAR),
                new ColumnInfo("population", JdbcType.VARCHAR),
                new ColumnInfo(measureName,JdbcType.DOUBLE)
        };
        String[] keys = new String[] {"container", "participantid","sequencenum","antigen","population"};
        ArrayList<Object[]> data = new ArrayList<>();
        Random r = new Random();
        for (String participantid : Arrays.asList(
                "P001001","P001002","P001003","P001004","P001005","P001006","P001007","P001008",
                "P002001","P002002","P002003","P002004","P002005","P002006","P002007","P002008"
                ))
        {
            for (Integer visit : Arrays.asList(0,1,7))
            {
                for (String antigen : Arrays.asList("A1","A2"))
                {
                    if (scale.equals("log"))
                    {
                        for (String pop : Arrays.asList("CD4", "CD8"))
                            data.add(new Object[]{"TestContainer1", participantid, visit, antigen, pop, Math.exp(r.nextDouble()*Math.log(range))});
                    }
                    else
                    {
                        for (String pop : Arrays.asList("CD4","CD8"))
                            data.add(new Object[]{"TestContainer1", participantid,visit,antigen,pop, Math.round(r.nextDouble()*range)});

                    }
                }
            }
        }

        return new TestTableInfo(assayName, cols, keys, data.toArray(new Object[data.size()][]));
    }

    static String toSqlLiteral(JdbcType type, Object value)
    {
        value = type.convert(value);

        if (value instanceof String)
            return string_quote((String)value);

        if (value instanceof Date)
        {
            if (type == JdbcType.DATE)
                return "{d '" + DateUtil.toISO((Date) value).substring(0, 10) + "'}";
            else
                return "{ts '" + DateUtil.toISO((Date)value) + "'}";
        }

        return String.valueOf(value);
    }

    static private String string_quote(String s)
    {
        if (s.contains("'"))
            s = s.replace("'","''");
        return "'" + s + "'";
    }


    private VisTestSchema _schema;

    boolean columnExists(String query, String name)
    {
        if (null == _schema)
            _schema = new VisTestSchema(getUser(), getContainer());
        return null != _schema.getTable(query).getColumn(name);
    }

    @Nullable
    @Override
    public VisualizationProvider createVisualizationProvider()
    {
        return new _VisualizationProvider();
    }

    class _VisualizationProvider extends VisualizationProvider<VisTestSchema>
    {
        _VisualizationProvider()
        {
            super(VisTestSchema.this);
        }

        @Override
        public List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> getJoinColumns(VisualizationSourceColumn.Factory factory, IVisualizationSourceQuery first, IVisualizationSourceQuery second, boolean isGroupByQuery)
        {
            List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> list = new ArrayList<>();

            for (String name : Arrays.asList("participantid","study","sequencenum"))
            {
                if (columnExists(first.getQueryName(), name) && columnExists(second.getQueryName(), name))
                {
                    VisualizationSourceColumn f = factory.create(first.getSchema(),first.getQueryName(),name,true);
                    VisualizationSourceColumn s = factory.create(second.getSchema(),second.getQueryName(),name,true);
                    list.add(new Pair<>(f, s));
                }
            }
            return list;
        }

        @Override
        public void addExtraSelectColumns(VisualizationSourceColumn.Factory factory, IVisualizationSourceQuery query)
        {
            for (String name : Arrays.asList("participantid","study","sequencenum"))
            {
                if (columnExists(query.getQueryName(), name))
                {
                    query.addSelect(factory.create(query.getSchema(),query.getQueryName(),name,true),false);
                }
            }
        }

        @Override
        public void appendAggregates(StringBuilder sql, Map columnAliases, Map intervals, String queryAlias, IVisualizationSourceQuery joinQuery)
        {

        }

        @Override
        public boolean isJoinColumn(VisualizationSourceColumn column, Container container)
        {
            return false;
        }

        @Override
        public void addExtraResponseProperties(Map extraProperties)
        {

        }

        @Override
        public void addExtraColumnProperties(ColumnInfo column, TableInfo table, Map props)
        {

        }

        @Override
        public String getSourceCountSql(@NotNull JSONArray sources, JSONArray members, String colName)
        {
            return null;
        }
    }
}
