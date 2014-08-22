package org.labkey.query.olap.rolap;

import org.apache.commons.lang3.StringUtils;
import static org.apache.commons.lang3.StringUtils.*;

import org.labkey.api.query.AliasManager;
import org.labkey.api.query.DefaultSchema;
import org.olap4j.OlapException;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Member;
import org.olap4j.metadata.Property;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by matthew on 8/16/14.
 */
public class RolapCubeDef
{
    protected String name;
    protected JoinOrTable factTable;
    protected final ArrayList<DimensionDef> dimensions = new ArrayList<>();
    protected final ArrayList<MeasureDef> measures = new ArrayList<>();
    protected final Map<String,String> annotations = new TreeMap<>();
    protected final Map<String,Object> uniqueNameMap = new TreeMap<>();
    protected final AliasManager aliases = new AliasManager(null);


    public String getName()
    {
        return name;
    }

    public String getFromSQL()
    {
        return factTable.getFromEntry();
    }

    public LevelDef getRolapDef(Level l)
    {
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
    }


    static public class JoinOrTable
    {
        protected RolapCubeDef cube;

        // table
        protected String tableName;
        protected String schemaName;

        // join
        protected String leftKey;
        protected String leftAlias;
        protected String rightKey;
        protected String rightAlias;
        protected JoinOrTable left;
        protected JoinOrTable right;


        private void validate()
        {
            if (null != left)
                left.validate();
            if (null != right)
                right.validate();

            if (null == schemaName && null != cube.factTable)
                schemaName = cube.factTable.schemaName;
            if (null == leftAlias && null != left)
                leftAlias = StringUtils.defaultString(left.tableName, left.rightAlias);
            if (null != right && null == rightAlias)
                rightAlias = StringUtils.defaultString(right.tableName, right.leftAlias);

            if (null != left && null == leftAlias)
                throw new IllegalArgumentException("Could not infer leftAlias for join");
            if (null != right && null == rightAlias)
                throw new IllegalArgumentException("Could not infer rightAlias for join");
        }

        public String getFromEntry()
        {
            if (null != tableName)
            {
                return id_quote(schemaName, tableName);
            }
            else
            {
                String lhs = left.getFromEntry();
                String rhs = right.getFromEntry();
                StringBuilder sb = new StringBuilder();
                sb.append("(");
                sb.append(lhs).append(" INNER JOIN ").append(rhs).append(" ON ");
                sb.append(id_quote(leftAlias, leftKey));
                sb.append("=");
                sb.append(id_quote(rightAlias, rightKey));
                sb.append(")");
                return sb.toString();
            }
        }
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
        }


        public String getMemberFilter(Member m)
        {
            LevelDef l = (LevelDef)cube.uniqueNameMap.get(m.getLevel().getUniqueName());
            if (null == l || l.hierarchy != this)
                throw new IllegalStateException();
            return l.getMemberFilter(m);
        }


        public String getFullJoin()
        {
            JoinOrTable fact = cube.factTable;
            String factTable = fact.getFromEntry();

            if (null == join)
            {
                if (factTable.startsWith("(") && factTable.endsWith(")"))
                    return factTable.substring(1, factTable.length() - 2);
                return factTable;
            }

            String joinClause = getFromJoin();
            return fact + joinClause;
        }


        public String getFromJoin()
        {
            if (null == join)
                return "";

            String levelTable = join.getFromEntry();

            StringBuilder sb = new StringBuilder(" INNER JOIN ");
            sb.append(levelTable);
            sb.append(" ON " );
            sb.append(id_quote(cube.factTable.tableName,dimension.foreignKey));
            sb.append("=");
            sb.append(id_quote(primaryKeyTable, primaryKey));
            return sb.toString();
        }
    }


    static public class LevelDef
    {
        protected RolapCubeDef cube;
        protected HierarchyDef hierarchy;
        protected LevelDef parent;

        protected String uniqueName;
        protected String name;
        protected String table;

        protected String keyColumn;
        protected String nameColumn;
        protected String ordinalColumn;

        protected String keyExpression;
        protected String nameExpression;
        protected String ordinalExpression;

        protected String keyAlias;
        protected String nameAlias;
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

        public String getAllColumnsSQL()
        {
            String comma = "";
            StringBuilder sb = new StringBuilder();

            if (null != parent)
            {
                sb.append(parent.getAllColumnsSQL());
                comma = ", ";
            }
            if (null != ordinalExpression)
            {
                sb.append(comma).append(ordinalExpression);
                sb.append(" AS ").append(ordinalAlias);
                comma = ", ";
            }
            if (null != keyExpression)
            {
                sb.append(comma).append(keyExpression);
                sb.append(" AS ").append(keyAlias);
                comma = ", ";
            }
            if (null != nameExpression)
            {
                sb.append(comma).append(nameExpression);
                sb.append(" AS ").append(nameAlias);
            }
            return sb.toString();
        }


        public String getMemberUniqueNameFromResult(ResultSet rs)
        {
            StringBuilder sb = new StringBuilder();
            if (null == parent)
                sb.append(hierarchy.uniqueName);
            else
                sb.append(parent.getMemberUniqueNameFromResult(rs));
            try
            {
                String s = rs.getString(nameAlias);
                // TODO null member name
                if (StringUtils.isEmpty(s))
                    s = "#null";
                sb.append(".[").append(s).append("]");
                return sb.toString();
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
        }


        public String getFromJoin()
        {
            return hierarchy.getFromJoin();
        }


        public String getMemberFilter(Member m)
        {
            try
            {
                // TODO parent keys
                // TODO nameType, keyType, ordinalType
                if (null != keyExpression)
                {
                    Property keyProperty = m.getLevel().getProperties().get("KEY");
                    Object key = m.getPropertyValue(keyProperty);
                    if (null == key)
                        return "(" + keyExpression + " IS NULL OR " + keyExpression + "='')";
                    else if (key instanceof String)
                        return keyExpression + "=" + string_quote((String) key);
                    else
                        return keyExpression + "=" + String.valueOf(key);
                }
                else
                {
                    Property captionProperty = m.getLevel().getProperties().get("CAPTION");
                    Object name = m.getPropertyValue(captionProperty);
                    // TODO null member name
                    if (name.equals("#null"))
                        return "(" + nameExpression + " IS NULL OR " + name + "='')";
                    else if (name instanceof String)
                        return nameExpression + "=" + string_quote((String) name);
                    else
                        return nameExpression + "=" + String.valueOf(name);
                }
            }
            catch (OlapException x)
            {
                throw new RuntimeException(x);
            }
        }


        public String getOrderBy()
        {
            // TODO parent levels
            String expression = defaultString(ordinalExpression, defaultString(keyExpression, nameExpression));
            return "CASE WHEN " + expression + " IS NULL THEN 0 ELSE 1 END ASC, " + expression + " ASC";
        }


        private void validate()
        {
            // compute uniqueName according to Mondrian rules
            uniqueName = hierarchy.uniqueName + ".[" + getName() + "]";
            cube.uniqueNameMap.put(uniqueName,this);

            if (null == table)
            {
                if (null != hierarchy.join)
                    table = hierarchy.join.tableName;
                else if (null != cube.factTable)
                    table = cube.factTable.tableName;
            }

            // key
            if (null == keyExpression && null != keyColumn)
            {
                keyExpression = id_quote(table, keyColumn);
            }
            if (null == keyAlias)
            {
                keyAlias = cube.aliases.decideAlias((hierarchy.getName() + "_" + getName() + "_key").toLowerCase());
            }

            // name
            if (null == nameExpression && null != nameColumn)
            {
                nameExpression = id_quote(table, nameColumn);
            }
            if (null == nameAlias)
            {
                if (null != nameExpression)
                    nameAlias = cube.aliases.decideAlias((hierarchy.getName() + "_" + getName() + "_name").toLowerCase());
                else
                    nameAlias = keyAlias;
            }

            // ordinal
            if (null == ordinalExpression && null != ordinalColumn)
            {
                ordinalExpression = id_quote(table, ordinalColumn);
            }
            if (null == ordinalAlias)
            {
                ordinalAlias = cube.aliases.decideAlias((hierarchy.getName() + "_" + getName() + "_ord").toLowerCase());
            }
        }
    }


    static public class MeasureDef
    {
        String name;
        String columnExpression;
        String aggregator = "count";
    }


    static public class PropertyDef
    {
        String name;
        String columnExpression;
        String type;
        boolean dependsOnLevelValue;
    }
}
