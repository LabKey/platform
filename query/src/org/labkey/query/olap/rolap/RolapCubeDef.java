/*
 * Copyright (c) 2014-2016 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import static org.apache.commons.lang3.StringUtils.*;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Path;
import org.labkey.query.olap.metadata.CachedCube;
import org.olap4j.OlapException;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Member;
import org.olap4j.metadata.Property;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Created by matthew on 8/16/14.
 *
 * Loads a relational olap cube definition (ala Mondrian), for use by the CountDistinct api
 *     http://mondrian.pentaho.com/documentation/schema.php
 *
 *  These classes do two things.
 *
 *  1) represents the relevant data in the .xml file.
 *  2) SQL helpers to load data from the described schema.
 *
 * NOTE: RolapCubeDef is meant to be a shared, read-only object.  It should not
 * hold onto Schema or User or any such objects.
 *
 */

public class RolapCubeDef
{
    // Treat '' and NULL as the same value, mondrian behavior is FALSE
    final boolean emptyEqualsNull = false;

    // use LOJ for dimensions joins
    // using inner joins can cause problems when <join> specifications may filter where we do not want them to
    // however, depending on NULL semantics we may sometimes want JOIN semantics (see memberFilterUsersJoinKey)
    boolean useOuterJoin = true;


    // filter on PK IS NOT NULL if there is a member filter
    // if this is TRUE, then we'll eliminate values that are null only because the JOIN failed
    final boolean memberFilterUsesJoinKey = true;


    protected String name;
    protected JoinOrTable factTable;
    protected final ArrayList<DimensionDef> dimensions = new ArrayList<>();
    protected final ArrayList<MeasureDef> measures = new ArrayList<>();
    protected final Map<String,String> annotations = new TreeMap<>();
    protected final Map<String,Object> uniqueNameMap = new TreeMap<>();

    private final AliasManager columnAliases = new AliasManager(null);


    public String getName()
    {
        return name;
    }

    public String getSchemaName() { return factTable.schemaName; }

    public Map<String,String> getAnnotations()
    {
        return annotations;
    }



    public String getMembersSQL(HierarchyDef hdef)
    {
        LevelDef lowest = hdef.levels.get(hdef.levels.size()-1);
        String selectColumns = lowest.getAllColumnsSQL(null);
        String joins;

        if (null == hdef.join)
            joins = _getFromClause(factTable);
        else
            joins = _getFromClause(hdef.join);

        String orderBy = lowest.getOrderByAliases();

        return
                "SELECT * FROM (\n" +
                "  SELECT DISTINCT " + selectColumns + "\n" +
                "  FROM " + joins + ") $$\n" +
                "ORDER BY " + orderBy;
    }


    public String getFromSQLWithFactTable(LevelDef... levels)
    {
        LinkedHashSet<Join> joins = new LinkedHashSet<>();
        for (LevelDef l : levels)
        {
            if (null != l)
                l.addJoins(null, joins);
        }
        return _getFromSQL(factTable, null, joins);
    }


    /**
     * For performance, we sometimes want to push down the outer "DISTINCT" to the inner SELECT on the fact table.
     * To do that we need to know the columns required by the outer query.  Those columns are listed in the
     * factTableColumns map.
     *
     * Note: that the any code that generates any usages of fact table columns must cooperate in this scheme.
     * They need to correctly use the columns expression or the column alias and add factTableColumns entries.
     *
     * @param factTableColumns
     * @param levels
     * @return
     */
    public String getFromSQLWithFactTableDistinct(Map<String,String> factTableColumns, LevelDef... levels)
    {
        LinkedHashSet<Join> joins = new LinkedHashSet<>();
        for (LevelDef l : levels)
        {
            if (null != l)
                l.addJoins(factTableColumns, joins);
        }
        return _getFromSQL(factTable, factTableColumns, joins);
    }


    public String getFromSQL(Collection<HierarchyDef> hierarchyDefs)
    {
        LinkedHashSet<Join> joins = new LinkedHashSet<>();
        for (HierarchyDef h : hierarchyDefs)
        {
            h.addJoins(null, joins);
        }
        return _getFromSQL(factTable, null, joins);
    }


    private String _getFromSQL(JoinOrTable innerMost, @Nullable Map<String,String> distinctInnerColumns, Collection<Join> joinsIn)
    {
        StringBuilder sb = new StringBuilder();

        if (null == distinctInnerColumns)
        {
            sb.append(id_quote(innerMost.schemaName, innerMost.tableName));
        }
        else
        {
            StringBuilder innerSql = new StringBuilder("(SELECT DISTINCT ");
            String comma = "";
            for (Map.Entry<String,String> e : distinctInnerColumns.entrySet())
            {
                innerSql.append(comma).append(e.getValue()).append(" AS ").append(e.getKey());
                comma = ", ";
            }
            innerSql.append(" FROM ");
            innerSql.append(id_quote(innerMost.schemaName, innerMost.tableName));
            innerSql.append(") ").append(id_quote(factTable.tableName));
            sb.append(innerSql);
        }

        if (joinsIn.size() == 0)
            return sb.toString();

        LinkedList<Join> list = new LinkedList(joinsIn);

        Set<String> includedTables = new CaseInsensitiveTreeSet();
        includedTables.add(innerMost.tableName);

        for (int i=0 ; i<100 && !list.isEmpty(); i++)
        {
            Join j = list.removeFirst();
            Path a = j.left;
            Path b = j.right;

            // is left table included already
            if (!includedTables.contains(a.getParent().get(1)))
            {
                if (includedTables.contains(b.getParent().get(1)))
                {
                    a = j.right;
                    b = j.left;
                }
                else
                {
                    // throw it back
                    list.addLast(j);
                    continue;
                }
            }

            if (useOuterJoin)
                sb.append(" LEFT OUTER JOIN ");
            else
                sb.append(" INNER JOIN ");
            sb.append(id_quote(b.get(0), b.get(1)));
            sb.append(" ON " );
            sb.append(id_quote(a.get(1), a.get(2)));
            sb.append("=");
            sb.append(id_quote(b.get(1), b.get(2)));

            // add new table name
            includedTables.add(b.get(1));
        }

        if (!list.isEmpty())
            throw new IllegalStateException("Couldn't write join expression");

        return sb.toString();
    }


    private String _getFromClause(JoinOrTable jt)
    {
        if (null != jt.tableName)
        {
            return id_quote(jt.schemaName, jt.tableName);
        }
        else
        {
            StringBuilder sb = new StringBuilder();

            if (null == jt.left.tableName)
                sb.append("(");
            String lhs = _getFromClause(jt.left);
            sb.append(lhs);
            if (null == jt.left.tableName)
                sb.append(")");

            sb.append(" INNER JOIN ");

            if (null == jt.right.tableName)
                sb.append("(");
            String rhs = _getFromClause(jt.right);
            sb.append(rhs);
            if (null == jt.right.tableName)
                sb.append(")");

            sb.append(" ON ");
            sb.append(id_quote(jt.leftAlias, jt.leftKey));
            sb.append("=");
            sb.append(id_quote(jt.rightAlias, jt.rightKey));
            return sb.toString();
        }
    }


    public LevelDef getRolapDef(Level l)
    {
        if (null == l)
            return null;
        return (LevelDef)uniqueNameMap.get(l.getUniqueName());
    }

    public HierarchyDef getRolapDef(Hierarchy h)
    {
        return (HierarchyDef)uniqueNameMap.get(h.getUniqueName());
    }


    public List<DimensionDef> getDimensions()
    {
        return Collections.unmodifiableList(dimensions);
    }

    public DimensionDef getDimension(String name)
    {
        for (DimensionDef d : dimensions)
            if (d.getName().equalsIgnoreCase(name))
                return d;
        return null;
    }


    public List<MeasureDef> getMeasures()
    {
        return Collections.unmodifiableList(measures);
    }


    public List<HierarchyDef> getHierarchies()
    {
        ArrayList<HierarchyDef> ret = new ArrayList<>();
        for (DimensionDef d : dimensions)
        {
            ret.addAll(d.hierarchies);
        }
        return ret;
    }


    protected void validate()
    {
        if (StringUtils.isEmpty(name))
            throw new IllegalArgumentException("Cube name attribute not found");
        if (measures.isEmpty())
            throw new IllegalArgumentException("No measures defined in cube: " + name);
        if (dimensions.isEmpty())
            throw new IllegalArgumentException("No dimensions defined in cube: " + name);
        factTable.validate();

        for (DimensionDef d : dimensions)
        {
            d.validate();
        }
    }


    public void validateSchema(DefaultSchema s, List<String> errors)
    {
        try
        {
            validate();
        }
        catch (Exception x)
        {
            errors.add(x.getMessage());
            return;
        }
        // TODO put useful validation here
    }



    static public class DimensionDef
    {
        protected RolapCubeDef cube;

        protected String name;
        protected String foreignKey;
        protected final ArrayList<HierarchyDef> hierarchies = new ArrayList<>();
        protected final Map<String,String> annotations = new TreeMap<>();

        public String getName()
        {
            return name;
        }

        private void validate()
        {
            if (StringUtils.isEmpty(name))
                throw new IllegalArgumentException("Dimension name attribute not found");
            if (null == foreignKey)
                foreignKey = name;
            for (HierarchyDef h : hierarchies)
                h.validate();
        }

        public List<HierarchyDef> getHierarchies()
        {
            return Collections.unmodifiableList(hierarchies);
        }

        public HierarchyDef getHierarchy(String name)
        {
            for (HierarchyDef h : hierarchies)
                if (h.getName().equalsIgnoreCase(name))
                    return h;
            return null;
        }
    }


    static public class JoinOrTable
    {
        protected RolapCubeDef cube;
        protected Map<String,JoinOrTable> tables;

        // table
        protected String tableName;
//        protected String tableAlias;
        protected String schemaName;

        // join
        protected JoinOrTable left;
        protected JoinOrTable right;

        protected String leftKey;
        protected String leftAlias;         // declared alias
        protected String rightKey;
        protected String rightAlias;        // declared alias


        private void validate()
        {
            Map<String,JoinOrTable> tables = new CaseInsensitiveHashMap<>();
            _validate(tables);
        }


        private void _validate(Map<String,JoinOrTable> tables)
        {
            this.tables = tables;

            if (null != left)
                left._validate(tables);
            if (null != right)
                right._validate(tables);

            if (null == schemaName && null != cube.factTable)
                schemaName = cube.factTable.schemaName;

            if (null != tableName)
            {
                this.tables.put(tableName,this);
            }

            if (null == leftAlias && null != left)
                leftAlias = StringUtils.defaultString(left.tableName, left.rightAlias);
            if (null != right && null == rightAlias)
                rightAlias = StringUtils.defaultString(right.tableName, right.leftAlias);

            if (null != left && null == leftAlias)
                throw new IllegalArgumentException("Could not infer leftAlias for join");
            if (null != right && null == rightAlias)
                throw new IllegalArgumentException("Could not infer rightAlias for join");
        }


        public void addJoins(Set<Join> set)
        {
            if (null != tableName)
            {
                /* */
            }
            else
            {   /* we purposely ignore nesting, these are all INNER JOIN */
                left.addJoins(set);
                right.addJoins(set);

                JoinOrTable leftTable = tables.get(leftAlias);
                assert null != leftTable.tableName;
                JoinOrTable rightTable = tables.get(rightAlias);
                assert null != rightTable.tableName;

                Join j = new Join(
                        new Path(leftTable.schemaName, leftTable.tableName, this.leftKey),
                        new Path(rightTable.schemaName, rightTable.tableName, this.rightKey));
                set.add(j);
            }
        }
    }


    /* these are unique instances of joins shared across the cube, used to collapse duplicate joins during
     * sql generation
     */
    public static class Join
    {
        final Path left;  // schema/table/column
        final Path right; // schema/table/column
//        Join requires = null;

        Join(@NotNull Path left, @NotNull Path right)
        {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof Join))
                throw new IllegalStateException();
            Join a = this;
            Join b = (Join)obj;
            return a.left.equals(b.left) && a.right.equals(b.right);
        }

        @Override
        public int hashCode()
        {
            return left.hashCode() * 31 + right.hashCode();
        }

        //        public void addRequiredJoins(Set<Join> set)
//        {
//            if (null != requires)
//                requires.addRequiredJoins(set);
//            set.add(this);
//        }
    }


    static private String string_quote(String s)
    {
        if (s.contains("'"))
            s = s.replace("'","''");
        return "'" + s + "'";
    }

    static private String id_quote(String s)
    {
        return "\"" + s + "\"";
    }

    static private String id_quote(String a, String b)
    {
        if (null != a)
            return  id_quote(a) + "." + id_quote(b);
        else
            return id_quote(b);
    }


    static String toSqlLiteral(JdbcType type, Object value)
    {
        value = type.convert(value);

        if (value instanceof String)
            return string_quote((String)value);

        if (value instanceof Date)
        {
            if (type == JdbcType.DATE)
                return "{d '" + DateUtil.toISO((Date)value).substring(0, 10) + "'}";
            else
                return "{ts '" + DateUtil.toISO((Date)value) + "'}";
        }

        return String.valueOf(value);
    }



    static public class HierarchyDef
    {
        protected RolapCubeDef cube;
        protected DimensionDef dimension;

        protected String uniqueName;
        protected String name;
        protected String primaryKey;
        protected String primaryKeyTable;
        protected JoinOrTable join;
        protected boolean hasAll;
        protected ArrayList<LevelDef> levels = new ArrayList<>();

        public String getName()
        {
            return name;
        }

        public DimensionDef getDimension()
        {
            return dimension;
        }

        public List<LevelDef> getLevels()
        {
            return Collections.unmodifiableList(levels);
        }

        public LevelDef getLevel(String name)
        {
            for (LevelDef l : levels)
                if (l.getName().equalsIgnoreCase(name))
                    return l;
            return null;
        }

        public boolean hasDimensionTable()
        {
            return null != join;
        }

        private void validate()
        {
            // compute uniqueName according to Mondrian rules
            if (getName().equals(dimension.getName()))
                uniqueName = "[" + getName() + "]";
            else
                uniqueName = "[" + dimension.getName() + "." + getName() + "]";
            cube.uniqueNameMap.put(uniqueName,this);

            if (null != join)
                join.validate();
            LevelDef prev = null;
            for (LevelDef l : levels)
            {
                l.parent = prev;
                l.validate();
                prev = l;
            }
            if (null == primaryKeyTable && null != join)
                primaryKeyTable = join.tableName;

            if (null != join)
            {
                if (null==join.tables.get(primaryKeyTable))
                    throw new IllegalStateException("Could not find primaryKeyTable: " + primaryKeyTable + " in hierarchy definition: " + name);
            }
        }


        public String getMemberFilter(Member m, @Nullable SqlDialect d)
        {
            LevelDef l = (LevelDef)cube.uniqueNameMap.get(m.getLevel().getUniqueName());
            if (null == l || l.hierarchy != this)
                throw new IllegalStateException();
            return l.getMemberFilter(m, d);
        }


        public void addJoins(@Nullable Map<String,String> factTableAliases, Set<Join> joins)
        {
            cube.factTable.addJoins(joins);
            if (null == join)
                return;
            this.join.addJoins(joins);
            JoinOrTable pkTable = join.tables.get(primaryKeyTable);
            assert null != pkTable;
            Join j = new Join(
                    new Path(cube.factTable.schemaName, cube.factTable.tableName, dimension.foreignKey),
                    new Path(pkTable.schemaName, pkTable.tableName, primaryKey));
            if (null != factTableAliases)
            {
                factTableAliases.put(dimension.foreignKey,dimension.foreignKey);
            }
            joins.add(j);
        }
    }


    static public class LevelDef
    {
        protected RolapCubeDef cube;
        protected HierarchyDef hierarchy;
        protected LevelDef parent;
        protected boolean isLeaf = false;

        protected String uniqueName;
        protected String name;
        protected String table;

        protected String keyColumn;
        protected String keyExpression;
        protected String keyAlias;
        protected String keyType="String";
        protected JdbcType jdbcType;

        protected String nameColumn;
        protected String nameExpression;
        protected String nameAlias;

        protected String ordinalColumn;
        protected String ordinalExpression;
        protected String ordinalAlias;

        protected boolean uniqueMembers = false;
        protected ArrayList<PropertyDef> properties = new ArrayList<>();


        public String getName()
        {
            return name;
        }

        public String getUniqueName()
        {
            return uniqueName;
        }


        public String getSchemaName()
        {
            String schemaName = null;
            if (null != hierarchy.join)
                schemaName = findSchemaNameForTable(table, hierarchy.join);
            if (null != schemaName)
                return schemaName;
            schemaName = findSchemaNameForTable(table, cube.factTable);
            if (null != schemaName)
                return schemaName;
            return cube.factTable.schemaName;
        }

        @Nullable
        private String findSchemaNameForTable(String table, JoinOrTable jt)
        {
            String schemaName = null;
            if (null != jt)
            {
                if (StringUtils.equalsIgnoreCase(jt.tableName,table))
                    schemaName = jt.schemaName;
                else
                {
                    if (null != jt.left)
                        schemaName = findSchemaNameForTable(table, jt.left);
                    if (null == schemaName && null != jt.right)
                        schemaName = findSchemaNameForTable(table, jt.right);
                }
            }
            return schemaName;
        }


        public String getTableName()
        {
            return table;
        }

        public String getKeyExpression()
        {
            return keyExpression;
        }

        public JdbcType getJdbcType()
        {
            return jdbcType;
        }


        public String getAllColumnsSQL()
        {
            return getAllColumnsSQL(null);
        }


        /*
         * factTableAliases is minor hack.  If factTableAliases!=null, this indicates that the fact table expressions
         * will be nested in the FROM clause so only the aliases should be injected into the outer SELECT
         */
        public String getAllColumnsSQL(@Nullable Map<String,String> factTableAliases)
        {
            String comma = "";
            StringBuilder sb = new StringBuilder();

            if (null != parent)
            {
                sb.append(parent.getAllColumnsSQL(factTableAliases));
                comma = ", ";
            }

            boolean isFactTable = null == hierarchy.join;
            boolean onlySelectAliases = isFactTable && null != factTableAliases;

            if (null != ordinalExpression)
            {
                sb.append(comma);
                if (onlySelectAliases)
                {
                    sb.append(ordinalAlias);
                    factTableAliases.put(ordinalAlias, ordinalExpression);
                }
                else
                {
                    sb.append(ordinalExpression);
                    sb.append(" AS ").append(ordinalAlias);
                }
                comma = ", ";
            }
            if (null != keyExpression)
            {
                sb.append(comma);
                if (onlySelectAliases)
                {
                    sb.append(keyAlias);
                    factTableAliases.put(keyAlias,keyExpression);
                }
                else
                {
                    sb.append(keyExpression);
                    sb.append(" AS ").append(keyAlias);
                }
                comma = ", ";
            }
            if (null != nameExpression)
            {
                sb.append(comma);
                if (onlySelectAliases)
                {
                    sb.append(nameAlias);
                    factTableAliases.put(nameAlias,nameExpression);
                }
                else
                {
                    sb.append(nameExpression);
                    sb.append(" AS ").append(nameAlias);
                }
            }
            return sb.toString();
        }


        NumberFormat df = new DecimalFormat("0.#");

        public String getMemberUniqueNameFromResult(ResultSet rs)
        {
            StringBuilder sb = new StringBuilder();
            if (null == parent)
                sb.append(hierarchy.uniqueName);
            else
                sb.append(parent.getMemberUniqueNameFromResult(rs));
            try
            {
                String s;
                Object o = rs.getObject(nameAlias);
                if (null == o)
                    s = "#null";
                else if (o instanceof Number)
                    s = df.format(o);
                else if (cube.emptyEqualsNull && o instanceof String && StringUtils.isEmpty((String)o))
                    s = "#null";
                else
                    s = String.valueOf(o);
                sb.append(".[").append(s).append("]");
                return sb.toString();
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
        }


        public String getMembeNameFromResult(ResultSet rs)
        {
            try
            {
                String s;
                Object o = rs.getObject(nameAlias);
                if (null == o)
                    s = "#null";
                else if (o instanceof Number)
                    s = df.format(o);
                else if (cube.emptyEqualsNull && o instanceof String && StringUtils.isEmpty((String)o))
                    s = "#null";
                else
                    s = String.valueOf(o);
                return s;
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
        }


        public JdbcType computeKeyType(ResultSet rs) throws SQLException
        {
            int index = rs.findColumn(keyAlias);
            int columnType = rs.getMetaData().getColumnType(index);
            JdbcType j = JdbcType.valueOf(columnType);
            if (j.isText() != jdbcType.isText() || j.isDateOrTime() != jdbcType.isDateOrTime())
            {
                Logger.getLogger(RolapCubeDef.class).info("jdbc types do not match, expected: " + jdbcType + " found: " + j + ". Alias: \"" + keyAlias + "\"");
            }
            return jdbcType;
        }


        public boolean isLeaf()
        {
            return isLeaf;
        }

        public Object getKeyValue(ResultSet rs) throws SQLException
        {
            if (null == keyAlias)
                return null;
            return rs.getObject(keyAlias);
        }


        public Object getOrindalValue(ResultSet rs) throws SQLException
        {
            if (null == ordinalAlias)
                return null;
            return rs.getObject(ordinalAlias);
        }


        public void addJoins(@Nullable Map<String,String> factTableAliases, Set<Join> joins)
        {
            hierarchy.addJoins(factTableAliases, joins);
        }


        public String getMemberFilter(Member m, @Nullable SqlDialect d)
        {
            StringBuilder sb = new StringBuilder();
            makeMemberFilter(m,d,sb);
            return sb.toString();
        }


        private void makeMemberFilter(Member m, @Nullable SqlDialect d, StringBuilder sb)
        {
            boolean toUpper = null==d || d.isCaseSensitive();

            try
            {
                if (null == parent && cube.memberFilterUsesJoinKey)
                {
                    if (null != hierarchy.primaryKey)
                    {
                        sb.append( "(" + id_quote(hierarchy.primaryKeyTable, hierarchy.primaryKey) + " IS NOT NULL) AND ");
                    }
                }
                if (null != parent && !uniqueMembers)
                {
                    parent.makeMemberFilter(m.getParentMember(), d, sb);
                    sb.append(" AND ");
                }

                // TODO parent keys
                // TODO nameType, keyType, ordinalType
                if (null != keyExpression)
                {
                    //Property keyProperty = m.getLevel().getProperties().get("KEY");
                    Object value = ((CachedCube._Member)m).getKeyValue(); // m.getPropertyValue(keyProperty);

                    if (m.isCalculated())
                    {
                        if (!(m instanceof CachedCube._NotNullMember))
                            throw new IllegalStateException("only not null suppported");
                        if (cube.emptyEqualsNull && jdbcType.isText())
                            sb.append("((" + keyExpression + ") IS NOT NULL AND (" + keyExpression + ")<>''))");
                        else
                            sb.append("((" + keyExpression + ") IS NOT NULL)");
                    }
                    else if (null == value || (value instanceof String) && "#null".equals(value.toString()))
                    {
                        if (cube.emptyEqualsNull && jdbcType.isText())
                            sb.append("((" + keyExpression + ") IS NULL OR (" + keyExpression + ")='')");
                        else
                            sb.append("((" + keyExpression + ") IS NULL)");
                    }
                    else
                    {
                        String literal = toSqlLiteral(jdbcType, value);
                        if (jdbcType.isText() && toUpper)
                            sb.append("(UPPER(" + keyExpression + ")=UPPER(" + literal + "))");
                        else
                            sb.append("((" + keyExpression + ")=" + literal + ")");
                    }
                }
                else
                {
                    Property captionProperty = m.getLevel().getProperties().get("CAPTION");
                    Object name = m.getPropertyValue(captionProperty);
                    // TODO null member name
                    if ("#null".equals(name))
                    {
                        if (cube.emptyEqualsNull)
                            sb.append("((" + nameExpression + ") IS NULL OR (" + nameExpression + ")='')");
                        else
                            sb.append("((" + nameExpression + ") IS NULL)");
                    }
                    else if (name instanceof String)
                        sb.append("((" + nameExpression + ")=" + string_quote((String) name) + ")");
                    else
                        sb.append("((" + nameExpression + ")=" + String.valueOf(name) + ")");
                }
            }
            catch (OlapException x)
            {
                throw new RuntimeException(x);
            }
        }


        public String getOrderBy()
        {
            String orderBy = "";

            if (null != parent)
            {
                orderBy = parent.getOrderBy() + ", ";
            }

            String expression = defaultString(ordinalExpression, defaultString(keyExpression, nameExpression));
            // we don't need to force NULLs first for our loading code
            //return orderBy + "CASE WHEN " + expression + " IS NULL THEN 0 ELSE 1 END ASC, " + expression + " ASC";
            return orderBy + expression + " ASC";
        }


        public String getOrderByAliases()
        {
            String orderBy = "";

            if (null != parent)
            {
                orderBy = parent.getOrderByAliases() + ", ";
            }

            String alias;
            if (null != ordinalExpression)
                alias = ordinalAlias;
            else if (null != keyExpression)
                alias = keyAlias;
            else
                alias = nameAlias;


            // we don't need to force NULLs first for our loading code
            //return orderBy + "CASE WHEN " + expression + " IS NULL THEN 0 ELSE 1 END ASC, " + expression + " ASC";
            return orderBy + alias + " ASC";
        }


        private void validate()
        {
            // compute uniqueName according to Mondrian rules
            uniqueName = hierarchy.uniqueName + ".[" + getName() + "]";
            cube.uniqueNameMap.put(uniqueName,this);

            String tableAlias = null;

            if (null != hierarchy.join)
            {
                if (null == table)
                    table = hierarchy.join.tableName;
                if (null != table)
                    tableAlias = table; //hierarchy.join.tableAliases.get(table);
            }
            else if (null != cube.factTable)
            {
                if (null == table)
                    table = cube.factTable.tableName;
                if (null != table)
                    tableAlias = table; // cube.factTable.tableAliases.get(table);
            }

            // key
            if (null == keyExpression && null != keyColumn)
            {
                keyExpression = id_quote(tableAlias, keyColumn);
            }
            if (null == keyAlias)
            {
                keyAlias = cube.columnAliases.decideAlias((hierarchy.getName() + "$" + getName() + "_key").toLowerCase());
            }

            // name
            if (null == nameExpression && null != nameColumn)
            {
                nameExpression = id_quote(tableAlias, nameColumn);
            }
            if (null == nameAlias)
            {
                if (null != nameExpression)
                    nameAlias = cube.columnAliases.decideAlias((hierarchy.getName() + "$" + getName() + "_name").toLowerCase());
                else
                    nameAlias = keyAlias;
            }

            // ordinal
            if (null == ordinalExpression && null != ordinalColumn)
            {
                ordinalExpression = id_quote(tableAlias, ordinalColumn);
            }
            if (null == ordinalAlias && null != ordinalExpression)
            {
                ordinalAlias = cube.columnAliases.decideAlias((hierarchy.getName() + "$" + getName() + "_ord").toLowerCase());
            }

            if (null == keyType)
                keyType = "String";
            switch (keyType)
            {
                case "String": jdbcType = JdbcType.VARCHAR; break;
                case "Numeric": jdbcType = JdbcType.DOUBLE; break;
                case "Integer": jdbcType = JdbcType.INTEGER; break;
                case "Boolean": jdbcType = JdbcType.BOOLEAN; break;
                case "Date": jdbcType = JdbcType.DATE; break;
                case "Timestamp": jdbcType = JdbcType.TIMESTAMP; break;

                case "Time":
                default:
                    throw new UnsupportedOperationException("type attribute is not supported: " + keyType);
            }
        }
    }


    static public class MeasureDef
    {
        String name;
        String columnExpression;
        String aggregator = "count";
        Map<String,String> annotations = new TreeMap<>();

        public MeasureDef()
        {
        }
        public String getName()
        {
            return name;
        }
    }


    static public class PropertyDef
    {
        String name;
        String columnExpression;
        String type;
        boolean dependsOnLevelValue;
    }
}
