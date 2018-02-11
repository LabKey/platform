/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides a table info that will generate a cross-tab layout based upon
 * a source table info and a {@link CrosstabSettings} object. Use this class
 * with a query view derived from {@link org.labkey.api.query.CrosstabView}.
 *
 * User: Dave
 * Date: Jan 22, 2008
 * Time: 2:46:18 PM
 */
public class CrosstabTable extends VirtualTable implements CrosstabTableInfo
{
    public static final String COL_INSTANCE_COUNT = "InstanceCount";
    public static final String COL_SORT_PATTERN = "SortPattern";

    private CrosstabSettings _settings = null;
    private List<CrosstabMember> _colMembers = null;
    private Filter _aggFilter = null;
    private boolean _orAggFilters = false;
    private GroupTableInfo _groupTable = null;

    protected static final String AGG_ALIAS = "agg";
    protected static final String AGG_OR_ALIAS = "aggo";
    protected static final String AGG_FILTERED_ALIAS = "aggf";
    protected static final String PIVOT_ALIAS = "pvt";
    protected static final String ROWMEMS_ALIAS = "rmems";
    protected static final String ALIAS = "ctq";

    /**
     * Constructs a new CrosstabTableInfo object initialized by the supplied settings.
     * Note that this constructor should be used only when the table info is being
     * constructed for a non-display purpose, such as the Customize View dialog.
     *
     * @param settings Crosstab settings.
     */
    public CrosstabTable(CrosstabSettings settings)
    {
        this(settings, null);
    }

    /**
     * Constructs a new CrosstabTableInfo object for display. The settings object
     * specifies the layout of the crosstab, and the colMembers should be the
     * distinct set of column dimension members. For example, in a view that shows
     * peptides down the left and experiment runs across the top, the colMembers should
     * be the disinct set of experiment runs (runIds).
     *
     * @param settings The crosstab settings
     * @param colMembers The distinct set of column dimension members
     */
    public CrosstabTable(CrosstabSettings settings, List<CrosstabMember> colMembers)
    {
        super(settings.getSourceTable().getSchema(), ALIAS);

        assert null != settings.getRowAxis() && null != settings.getColumnAxis() && null != settings.getMeasures();
        assert !settings.getRowAxis().getDimensions().isEmpty() && ! settings.getColumnAxis().getDimensions().isEmpty();
        assert settings.getColumnAxis().getDimensions().size() == 1 : "CrosstabTableInfo currently supports only one column dimension!";

        _settings = settings;
        _colMembers = colMembers;

        //add a new column info for each row dimension
        for(CrosstabDimension dim : getSettings().getRowAxis().getDimensions())
            addColumn(createRowDimCol(dim));

        //if col members were passed, add the column infos for each member*measure combination
        //otherwise just add a column info for each measure (customize view case)
        if(null == colMembers)
            addMeasureCols();
        else
            addMemberMeasureCols();
        _groupTable = new GroupTableInfo(_settings.getSourceTable(), _settings.getSourceTableFilter(), _settings.getGroupColumns(),
                                            _settings.getMeasures());
    } //c-tor

    /**
     * Sets the aggregate filters. Aggregate filters are applied post-aggregation, but
     * pre-pivoting, so they can filter out rows with aggregates that do not match the filters.
     * This is typically called only by the {@link org.labkey.api.query.CrosstabView}
     * class to set the aggregate filters established by the user in the customize view dialog.
     *
     * You may pass null to clear the set of aggregate filters.
     *
     * @param filter The set of aggregate filters.
     */
    public void setAggregateFilter(Filter filter)
    {
        _aggFilter = filter;
    }

    /**
     * Returns the set of aggregate filters (may be null).
     *
     * @return The current set of aggregate filters
     */
    public Filter getAggregateFilter()
    {
        return _aggFilter;
    }

    public boolean getOrAggFilters()
    {
        return _orAggFilters;
    }

    public void setOrAggFitlers(boolean orAggFilters)
    {
        _orAggFilters = orAggFilters;
    }

    /**
     * Creates a row dimension column info. Override to create a different sub-class.
     *
     * @param dimension The dimension object from the settings.
     * @return A new column info for the row dimension.
     */
    protected ColumnInfo createRowDimCol(CrosstabDimension dimension)
    {
        return new DimensionColumnInfo(this, dimension);
    }

    /**
     * Adds just the measure columns (without reference to column members),
     * suitable for a customize view scenario.
     */
    protected void addMeasureCols()
    {
        addCountAndPatternCols();
        for(CrosstabMeasure measure : getSettings().getMeasures())
            addColumn(createMemberMeasureCol(null, measure));
    }

    /**
     * Adds the measure columns, repeated for each column member, suitable for display.
     */
    protected void addMemberMeasureCols()
    {
        addCountAndPatternCols();

        //add a column for each column axis dimension member * measure combination
        //note that we currently support only one column dimension.
        for(CrosstabMember member : getColMembers())
        {
            if(null == member || null == member.getValue())
                continue;

            for(CrosstabMeasure measure : getSettings().getMeasures())
                addColumn(createMemberMeasureCol(member, measure));
        } //for each column member
    }

    /**
     * Creates a member * measure column info for dislay. Override to create a different sub-class
     * @param member The column member (pivot value)
     * @param measure The measure (aggregate)
     * @return A new column info for display
     */
    protected ColumnInfo createMemberMeasureCol(@Nullable CrosstabMember member, CrosstabMeasure measure)
    {
        AggregateColumnInfo result = new AggregateColumnInfo(this, member, measure);
        result.setMeasure(true);
        result.setFacetingBehaviorType(FacetingBehaviorType.ALWAYS_OFF);
        return result;
    }

    /**
     * Adds the {@link CrosstabSettings#setInstanceCountCaption instance count} and sort pattern columns.
     */
    protected void addCountAndPatternCols()
    {
        //add the instance count/sort pattern columns
        ColumnInfo instanceCountCol = addColumn(new ExprColumn(this, COL_INSTANCE_COUNT,
                new SQLFragment(COL_INSTANCE_COUNT), JdbcType.INTEGER));
        instanceCountCol.setLabel(getSettings().getInstanceCountCaption());
        instanceCountCol.setFacetingBehaviorType(FacetingBehaviorType.ALWAYS_OFF);

        ColumnInfo sortPatternCol = addColumn(new ExprColumn(this, COL_SORT_PATTERN,
                new SQLFragment(COL_SORT_PATTERN), JdbcType.INTEGER));
        sortPatternCol.setHidden(true);
        sortPatternCol.setFacetingBehaviorType(FacetingBehaviorType.ALWAYS_OFF);
    }

    protected void addCrosstabQuery(SQLFragment sql)
    {
        sql.append("SELECT ");

        SQLFragment instanceCountExpr = new SQLFragment("(");
        SQLFragment sortPatternExpr = new SQLFragment("(");
        String plus = "";

        String sep = "";
        for(CrosstabDimension rowDim : getSettings().getRowAxis().getDimensions())
        {
            sql.append(sep);
            sql.append(PIVOT_ALIAS + "." + rowDim.getSourceColumn().getAlias());
            sep = ", ";
        }
        sep = ",\n";

        for(CrosstabMember member : getColMembers())
        {
            if(null == member || null == member.getValue())
                continue;

            //append to the instance count and sort pattern expressions
            instanceCountExpr.append(plus);
            instanceCountExpr.append("MAX(");
            instanceCountExpr.append(PIVOT_ALIAS);
            instanceCountExpr.append(".");
            instanceCountExpr.append(getMemberInstanceCountAlias(member));
            instanceCountExpr.append(")");

            sortPatternExpr.append(plus);
            sortPatternExpr.append("MAX(");
            sortPatternExpr.append(PIVOT_ALIAS);
            sortPatternExpr.append(".");
            sortPatternExpr.append(getMemberSortPatternAlias(member));
            sortPatternExpr.append(")");

            plus = " + ";

            for(CrosstabMeasure measure : getSettings().getMeasures())
            {
                //wrap the pivoted aggregate value in a max since we'll be grouping by the row dimensions
                sql.append(sep);
                sql.append("MAX(");
                sql.append(PIVOT_ALIAS);
                sql.append(".");
                sql.append(AggregateColumnInfo.getColumnName(member, measure));
                sql.append(") AS ");
                sql.append(AggregateColumnInfo.getColumnName(member, measure));
            } //for each measure
        } //for each member

        if (getColMembers().isEmpty())
        {
            instanceCountExpr.append("0");
            sortPatternExpr.append("0");

            for(CrosstabMeasure measure : getSettings().getMeasures())
            {
                //wrap the pivoted aggregate value in a max since we'll be grouping by the row dimensions
                sql.append(sep);
                sql.append("MAX(");
                sql.append(PIVOT_ALIAS);
                sql.append(".");
                sql.append(AggregateColumnInfo.getColumnName(null, measure));
                sql.append(") AS ");
                sql.append(AggregateColumnInfo.getColumnName(null, measure));
            } //for each measure
        }
        //end the instance count/sort pattern expressions
        instanceCountExpr.append(")");
        sortPatternExpr.append(")");

        //add the instance count and sort expressions to the select list
        sql.append(sep);
        sql.append(instanceCountExpr);
        sql.append(" AS ");
        sql.append(COL_INSTANCE_COUNT);

        sql.append(sep);
        sql.append(sortPatternExpr);
        sql.append(" AS ");
        sql.append(COL_SORT_PATTERN);

        sql.append("\nFROM (\n");

        addPivotQuery(sql);

        //alias the pivot query
        sql.append("\n) AS ");
        sql.append(PIVOT_ALIAS);

        //finally group by the row dimensions
        sql.append("\nGROUP BY ");
        sep = "";
        for(CrosstabDimension rowDim : getSettings().getRowAxis().getDimensions())
        {
            sql.append(sep);
            sql.append(PIVOT_ALIAS + "." + rowDim.getSourceColumn().getAlias());
            sep = ", ";
        }

    } //addCrosstabQuery()

    public Map<String, String> getMeasureNameToColumnNameMap()
    {
        Map<String, String> measureNameToColumnName = new HashMap<>();
        for (Map.Entry<String, ColumnInfo> entry : _columnMap.entrySet())
        {
            String colName = entry.getKey();
            ColumnInfo col = entry.getValue();
            String measureName;
            if (col instanceof AggregateColumnInfo)
                measureName = String.valueOf(((AggregateColumnInfo) col).getMember().getValue());
            else if (col instanceof DimensionColumnInfo)
                measureName = ((DimensionColumnInfo) col).getSourceFieldKey().toString();
            else
                measureName = col.getName();
            measureNameToColumnName.put(measureName, colName);
        }
        return measureNameToColumnName;
    }

    protected void addPivotQuery(SQLFragment sql)
    {
        sql.append("SELECT ");

        //row dimensions
        String sep = "";
        for(CrosstabDimension rowDim : getSettings().getRowAxis().getDimensions())
        {
            sql.append(sep);
            sql.append(AGG_FILTERED_ALIAS + "." + rowDim.getSourceColumn().getAlias());
            sep = ", ";
        }

        //instance count and sort pattern
        //member * measure buckets
        int memberIndex = 0;
        for(CrosstabMember member : getColMembers())
        {
            //instance count and sort pattern
            sql.append(",\n");
            addMemberCase(sql, member, AGG_FILTERED_ALIAS, "1", "0", getMemberInstanceCountAlias(member));
            sql.append(",\n");
            addMemberCase(sql, member, AGG_FILTERED_ALIAS,
                    (getColMembers().size() - memberIndex - 1) > 31 ? "0" : String.valueOf(1 << (getColMembers().size() - memberIndex - 1)),
                    "0", getMemberSortPatternAlias(member));

            for(CrosstabMeasure measure : getSettings().getMeasures())
            {
                sql.append(",\n");
                addMemberCase(sql, member, AGG_FILTERED_ALIAS, AGG_FILTERED_ALIAS + "." + AggregateColumnInfo.getColumnName(null, measure),
                        "NULL", AggregateColumnInfo.getColumnName(member, measure));
           } //for each measure

            ++memberIndex;
        } //for each member

        if (getColMembers().isEmpty())
        {
            for(CrosstabMeasure measure : getSettings().getMeasures())
            {
                sql.append(",\n");
                sql.append(AggregateColumnInfo.getColumnName(null, measure));
           } //for each measure
        }

        //add the aggregation query
        sql.append("\nFROM (\n");
        Map<FieldKey,ColumnInfo> filterColMap = addAggQuery(sql);
        sql.append("\n) AS ");
        sql.append(AGG_FILTERED_ALIAS);

        if(null != getAggregateFilter())
        {
            sql.append("\n");
            if(getOrAggFilters())
                addOrAggFilterJoin(sql, filterColMap, AGG_FILTERED_ALIAS);
            else
                sql.append(getAggregateFilter().getSQLFragment(getSqlDialect(), filterColMap));
        }

    } //addPivotQuery()

    protected Map<FieldKey,ColumnInfo> addAggQuery(SQLFragment sql)
    {
        //this is pretty nasty, but it seems to be the only way to
        //ensure that filters on fk table values can be applied
        GroupTableInfo groupTable = getGroupTable();
        Filter aggFilter = getAggregateFilter();

        Collection<ColumnInfo> reqCols = new ArrayList<>(groupTable.getColumns());    // Make a copy
        reqCols = QueryService.get().ensureRequiredColumns(groupTable, reqCols, aggFilter, null, null);

        sql.append("SELECT * FROM (\n");
        sql.append(Table.getSelectSQL(groupTable, reqCols, null, null));
        sql.append("\n) AS ");
        sql.append(AGG_ALIAS);

        return getAggregateFilterColMap(reqCols);
    } //addAggQuery()

    protected String getMemberInstanceCountAlias(CrosstabMember member)
    {
        return getSettings().getColumnAxis().getDimensions().get(0).getSourceColumn().getAlias()
                    + member.getValueSQLAlias(getSqlDialect()) + COL_INSTANCE_COUNT;
    }

    protected String getMemberSortPatternAlias(CrosstabMember member)
    {
        return getSettings().getColumnAxis().getDimensions().get(0).getSourceColumn().getAlias()
                    + member.getValueSQLAlias(getSqlDialect()) + COL_SORT_PATTERN;
    }

    protected void addMemberCase(SQLFragment sql, CrosstabMember member, String queryAlias, String ifSql, String elseSql, String alias)
    {
        CrosstabDimension colDimension = getSettings().getColumnAxis().getDimensions().get(0);

        sql.append("(CASE WHEN ");
        sql.append(queryAlias + "." + colDimension.getSourceColumn().getAlias());
        sql.append("=");

        // TODO: Remove this assert once we've tested the JdbcType-based method
        String oldSqlValue = getSQLValue(colDimension.getSourceColumn().getSqlTypeInt(), member.getValue());
        String newSqlValue = getSQLValue(colDimension.getSourceColumn().getJdbcType(), member.getValue());

        assert oldSqlValue.equals(newSqlValue);

        sql.append(newSqlValue);
        sql.append(" THEN ");
        sql.append(ifSql);
        sql.append(" ELSE ");
        sql.append(elseSql);
        sql.append(" END) AS ");
        sql.append(alias);
    }

    protected void addSourceQuery(SQLFragment sql)
    {
        sql.append(Table.getSelectSQL(getSettings().getSourceTable(), getSettings().getDistinctColumns(), null, null));
    } //addSourceQuery()

    /**
     * Helper to add an inner-join that will apply the agg filters in an OR manner
     * rather than an AND.
     * @param sql the sql fragment to append to
     * @param filterColMap the filter column map to use
     * @param joinAlias the alias of the table to which the SQL should join
     */
    protected void addOrAggFilterJoin(SQLFragment sql, Map<FieldKey, ColumnInfo> filterColMap, String joinAlias)
    {
        //The theory here is that we will join the result of the aggregations
        //to another inner-select that gets the row dimension members that
        //satisfy the aggregate filters, without respect to the column dimension.
        //That should select any row dimension member combination that satisfies
        //the filters for at least one of the column dimension members. For example,
        //in a cross-tab showing peptides down the left and runs across the top,
        //a filter of NumFeatures > 1 should display all features where at least
        //one run had more than one feature for the given peptide
        String sep = "";
        sql.append("\nINNER JOIN (SELECT ");
        for(CrosstabDimension dim : getSettings().getRowAxis().getDimensions())
        {
            sql.append(sep);
            sql.append(AGG_OR_ALIAS);
            sql.append(".");
            sql.append(dim.getSourceColumn().getAlias());
            sep = ",";
        }

        //add an aggregation inner-select
        sql.append("\nFROM (");
        addAggQuery(sql);

        sql.append("\n) AS ");
        sql.append(AGG_OR_ALIAS);

        //now add the agg filters
        sql.append("\n");
        sql.append(getAggregateFilter().getSQLFragment(getSqlDialect(), filterColMap));

        //end the inner-join table
        //aliased as ROWMEMS_ALIAS
        sql.append(") AS ");
        sql.append(ROWMEMS_ALIAS);
        sql.append(" ON (");

        //joined on the row dimensions
        sep = "";
        for(CrosstabDimension dim : getSettings().getRowAxis().getDimensions())
        {
            sql.append(sep);
            sql.append(ROWMEMS_ALIAS);
            sql.append(".");
            sql.append(dim.getSourceColumn().getAlias());
            sql.append("=");
            sql.append(joinAlias);
            sql.append(".");
            sql.append(dim.getSourceColumn().getAlias());
            sep = " AND ";
        }
        
        //end the ON clause
        sql.append(")");
    }

    /**
     * Returns the appropriate SQL FROM clause for this table info.
     * Note that Query actually expects a full select statement here
     * wrapped in parens and aliased using the alias parameter
     *
     * @return The FROM clause
     */
    @Override @NotNull
    public SQLFragment getFromSQL()
    {
        assert null != getColMembers();

        SQLFragment sql = new SQLFragment();
        addCrosstabQuery(sql);
        return sql;
    }
    

    /**
     * Returns a column map suitable for passing to SimpleFilter's getSQLFragment() method.
     * The map contains the same columns that are added when the table info is constructed
     * without any column members (e.g., for customize view).
     *
     * @return A column map
     */
    protected Map<String, ColumnInfo> getAggregateFilterColMap()
    {
        return new CrosstabTable(getSettings())._columnMap;
    }

    protected Map<FieldKey, ColumnInfo> getAggregateFilterColMap(Collection<ColumnInfo> cols)
    {
        Map<FieldKey,ColumnInfo> map = new HashMap<>(cols.size());
        for(ColumnInfo col : cols)
        {
            map.put(col.getFieldKey(), col);
        }
        return map;
    }

    /**
     * Helper function to return the member value suitable for appending into a 
     * SQL statement.
     *
     * @param sqlType The JDBC SQL type
     * @param memberValue The member value
     * @return A string representation of the member value suitable for appending into a SQL string as a literal value.
     */
    @Deprecated // Remove this after testing
    private String getSQLValue(int sqlType, Object memberValue)
    {
        //CONSIDER: there *must* be something in query or utils that
        //already does this. Maybe use param substitution instead?
        switch(sqlType)
        {
            case Types.INTEGER:
            case Types.BIGINT:
            case Types.DECIMAL:
            case Types.NUMERIC:
            case Types.FLOAT:
            case Types.SMALLINT:
            case Types.TINYINT:
                return memberValue.toString();
            case Types.CHAR:
            case Types.CLOB:
            case Types.LONGVARCHAR:
            case Types.VARCHAR:
                return "'" + memberValue.toString().replace("'", "''") + "'";
            case Types.BOOLEAN:
            case Types.BIT:
                return "CAST('" + Boolean.valueOf(memberValue.toString()).toString() +"' AS " + getSqlDialect().getBooleanDataType() + ")";

            default:
                //if you get this, add support for the type you want.
                throw new IllegalArgumentException("Crosstab table info supports numeric and character types for the column dimension.");
        }
    } //getSQLValue()

    /**
     * Helper function to return the member value suitable for appending into a SQL statement.
     *
     * @param jdbcType The JdbcType
     * @param memberValue The member value
     * @return A string representation of the member value suitable for appending into a SQL string as a literal value.
     */
    private String getSQLValue(JdbcType jdbcType, Object memberValue)
    {
        //CONSIDER: there *must* be something in query or utils that
        //already does this. Maybe use param substitution instead?
        if (jdbcType.isNumeric())
            return memberValue.toString();
        else if (jdbcType.isText())
            return "'" + memberValue.toString().replace("'", "''") + "'";
        else if (jdbcType == JdbcType.BOOLEAN)
            return "CAST('" + Boolean.valueOf(memberValue.toString()).toString() +"' AS " + getSqlDialect().getBooleanDataType() + ")";

        //if you get this, add support for the type you want.
        throw new IllegalArgumentException("Crosstab table info supports numeric and character types for the column dimension.");
    } //getSQLValue()

    @Override
    public CrosstabSettings getSettings()
    {
        return _settings;
    }

    /**
     * Returns a reference to the GroupTableInfo used for grouping and aggregation
     *
     * @return The GroupTableInfo object
     */
    protected GroupTableInfo getGroupTable()
    {
        return _groupTable;
    }

    @Override
    public List<CrosstabMember> getColMembers()
    {
        return _colMembers == null ? Collections.emptyList() : _colMembers;
    }

    @Override
    public CrosstabMeasure getMeasureFromKey(String fieldKey)
    {
        for(CrosstabMeasure measure : getSettings().getMeasures())
        {
            if(AggregateColumnInfo.getColumnName(null, measure).equals(fieldKey))
                return measure;
        }
        return null;
    }

    /**
     * Returns the default sort string a view should use as its base sort. The default
     * sort is on instance count descending, sort pattern descending. Views will typically
     * wish to add at least one of their row dimensions to the end of this string.
     *
     * @return The default sort string
     */
    public static String getDefaultSortString()
    {
        return "-" + COL_INSTANCE_COUNT + ",-" + COL_SORT_PATTERN;
    }

    /**
     * Returns a new Sort object initialized with the string returned from
     * {@link #getDefaultSortString}.
     *
     * @return A default Sort object
     */
    @Override
    public Sort getDefaultSort()
    {
        return new Sort(getDefaultSortString());
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        //if they've already been set, just return the super's implementation
        //(which returns an unmodifiable version)
        if (null != _defaultVisibleColumns)
            return super.getDefaultVisibleColumns();

        //need to return a list of field keys similar to what query will
        //get in customize view. That way the client can adjust the
        //default set in an abstract kind of way, while still providing
        //the column members for display.
        ArrayList<FieldKey> defaultCols = new ArrayList<>();
        for(CrosstabDimension dim : getSettings().getRowAxis().getDimensions())
            defaultCols.add(dim.getFieldKey());

        defaultCols.add(FieldKey.fromParts(COL_INSTANCE_COUNT));

        for(CrosstabMeasure measure : getSettings().getMeasures())
            defaultCols.add(measure.getFieldKey());
        
        return Collections.unmodifiableList(defaultCols);
    }
} //CrosstabTableInfo
