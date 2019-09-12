package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleSetService;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.labkey.api.util.PageFlowUtil.filter;

// NOTE DataColumn perhaps does more than we need, consider extending DisplayColumn instead?

public class LineageDisplayColumn extends DataColumn
{
    final QuerySchema sourceSchema;
    final FieldKey boundFieldKey;
    final ExpLineageOptions options;

    ReexecutableDataregion innerDataRegion;
    RenderContext innerCtx;
    DisplayColumn innerDisplayColumn;


    public static DisplayColumn create(QuerySchema schema, ColumnInfo objectid, boolean parents, Integer depth, String expType, String cpasType)
    {
        var options = new ExpLineageOptions();
        options.setParents(parents);
        options.setChildren(!parents);
        options.setDepth(depth==null?0:depth);
        options.setExpType(expType);
        options.setCpasType(cpasType);
        options.setForLookup(true);
        options.setUseObjectIds(true);

        // TODO: have LineageForeignKey pass in this key instead of trying to reproduce the logic in LFK
        FieldKey boundFieldKey = new FieldKey(null, parents ? "Inputs" : "Outputs");

        switch (StringUtils.trimToEmpty(options.getExpType()))
        {
            case "Material":
            {
                boundFieldKey = new FieldKey(boundFieldKey, "Materials");
                if (options.getDepth() != 0)
                    boundFieldKey = new FieldKey(boundFieldKey, "First");
                else
                {
                    var type = "All";
                    if (null != options.getCpasType())
                    {
                        var ss = SampleSetService.get().getSampleSet(options.getCpasType());
                        if (null != ss)
                            type = ss.getName();
                    }
                    boundFieldKey = new FieldKey(boundFieldKey, type);
                }
                break;
            }
            case "Data":
            {
                boundFieldKey = new FieldKey(boundFieldKey, "Data");
                if (options.getDepth() != 0)
                    boundFieldKey = new FieldKey(boundFieldKey, "First");
                else
                {
                    var type = "All";
                    if (null != options.getCpasType())
                    {
                        var dc = ExperimentServiceImpl.get().getDataClass(options.getCpasType());
                        if (null != dc)
                            type = dc.getName();
                    }
                    boundFieldKey = new FieldKey(boundFieldKey, type);
                }
                break;
            }
            case "ExperimentRun":
            {
                boundFieldKey = new FieldKey(boundFieldKey, "Runs");
                if (options.getDepth() != 0)
                    boundFieldKey = new FieldKey(boundFieldKey, "First");
                else
                {
                    var type = "All";
                    if (null != options.getExpType())
                    {
                        var protocol = ExperimentService.get().getExpProtocol(cpasType);
                        if (protocol != null)
                            type = protocol.getName();
                    }
                    boundFieldKey = new FieldKey(boundFieldKey, type);
                }
                break;
            }
            default:
            {
                boundFieldKey = new FieldKey(boundFieldKey, "All");
                break;
            }
        }
        // TODO see above, this is ugly
        if (!equalsIgnoreCase(boundFieldKey.getName(),objectid.getFieldKey().getName()))
            boundFieldKey = new FieldKey(boundFieldKey, objectid.getFieldKey().getName());

        return new LineageDisplayColumn(schema, objectid, boundFieldKey, options);
    }

    // TODO what to do with Level columns (like All, First, etc)
    protected LineageDisplayColumn(QuerySchema schema, ColumnInfo objectid, FieldKey boundFieldKey, ExpLineageOptions options)
    {
        super(objectid, false);
        this.sourceSchema = schema;
        this.boundFieldKey = boundFieldKey;
        this.options = options;

        /* SET UP DataRegion */

        // TODO ContainerFilter
        TableInfo seedTable = new SeedTable((UserSchema)sourceSchema);
        ColumnInfo bound = null;
        for (String part : boundFieldKey.getParts())
        {
            if (null == bound)
                bound = seedTable.getColumn(boundFieldKey.getRootName());
            else
                bound = null == bound.getFk() ? null : bound.getFk().createLookupColumn(bound, part);
            if (null == bound)
                break;
        }
        // This is an error, probably in recreating the fieldkey correctly
        if (null == bound)
            return;

        innerDataRegion = new ReexecutableDataregion();
        innerDataRegion.setTable(seedTable);
        innerDataRegion.addColumn(bound);
        innerDisplayColumn = innerDataRegion.getDisplayColumn(0);
    }

    @Override
    public ColumnInfo getDisplayColumn()
    {
        return null;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        if (null == innerCtx)
            innerCtx = new ReexecutableRenderContext(ctx);

        try
        {
            if (null == innerDataRegion)
            {
                out.write("&lt;" + filter(this.toString()) + "&gt;");
                return;
            }

            innerDataRegion.reset(innerCtx, Collections.singletonMap(SeedTable.OBJECTID_PARAMETER, getValue(ctx)));
            try (ResultSet rs = requireNonNull(innerDataRegion.getResultSet(innerCtx)))
            {
                ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

                if (rs.next())
                {
                    innerCtx.setRow(factory.getRowMap(rs));
                    innerDisplayColumn.renderGridCellContents(innerCtx, out);
                    boolean hasNext = rs.next();
                    assert !hasNext;
                }
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    @Override
    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
    }

    @Override
    public void renderTitle(RenderContext ctx, Writer out) throws IOException
    {
        super.renderTitle(ctx, out);
    }

    @Override
    public boolean isSortable()
    {
        return false;
    }

    @Override
    public boolean isFilterable()
    {
        return false;
    }

    @Override
    public boolean isEditable()
    {
        return false;
    }

    @Override
    public void renderFilterOnClick(RenderContext ctx, Writer out)
    {
    }

    @Override
    public void renderInputHtml(RenderContext ctx, Writer out, Object value)
    {
    }

    @Override
    public boolean isQueryColumn()
    {
        return false;
    }

    @Override
    public Object getValue(RenderContext ctx)
    {
        return super.getValue(ctx);
    }

    @Override
    public Class getValueClass()
    {
        return String.class;
    }

    @Override
    public String toString()
    {
        return this.getClass().getName() + ":" + boundFieldKey.toDisplayString();
    }


    static class SeedTable extends AbstractTableInfo
    {
        public static final String OBJECTID_PARAMETER = "_$_OBJECTID_$_";
        final UserSchema schema;
        final SQLFragment sqlf;
        final List<QueryService.ParameterDecl> parameters = Collections.singletonList(new QueryService.ParameterDeclaration(OBJECTID_PARAMETER, JdbcType.INTEGER));

        SeedTable(UserSchema schema)
        {
            super(schema.getDbSchema(), "seed");
            SqlDialect d = schema.getDbSchema().getScope().getSqlDialect();
            this.schema = schema;
            this.sqlf = new SQLFragment("SELECT CAST( ? AS " + d.getSqlCastTypeName(JdbcType.INTEGER)+ ") AS objectid");
            this.sqlf.addAll(parameters);
            var objectidCol = new BaseColumnInfo("objectid", this, JdbcType.INTEGER);
            addColumn(objectidCol);
            var inputs = new AliasedColumn(this, "Inputs", objectidCol);
            inputs.setFk(LineageForeignKey.createWithMultiValuedColumn(schema, sqlf, true));
            addColumn(inputs);
            var outputs = new AliasedColumn(this, "Outputs", objectidCol);
            outputs.setFk(LineageForeignKey.createWithMultiValuedColumn(schema, sqlf, false));
            addColumn(outputs);
        }

        @Override
        public @NotNull Collection<QueryService.ParameterDecl> getNamedParameters()
        {
            return parameters;
        }

        @Override
        protected SQLFragment getFromSQL()
        {
            return sqlf;
        }

        @Override
        public @Nullable UserSchema getUserSchema()
        {
            return schema;
        }
    }

    /*
     * This is an attemp at making a DataRegion that can be reused/reexecuted where ONLY the query parameters change
     */
    public static class ReexecutableDataregion extends DataRegion
    {
        Map<String,Object> parameters = new CaseInsensitiveHashMap<>();

        // close current result set and update query parameters
        // usually followed immediately by call to getResultSet()
        void reset(RenderContext ctx, Map<String,Object> currentParameters)
        {
            ResultSet rs = ctx.getResults();
            if (null != rs)
            {
                try {if (!rs.isClosed()) rs.close();}catch(SQLException x){/*pass*/}
                ctx.setResults(null);
            }
            parameters.clear();
            parameters.putAll(super.getQueryParameters());
            parameters.putAll(currentParameters);
        }

        @Override
        public @NotNull Map<String, Object> getQueryParameters()
        {
            return parameters;
        }

        @Override
        protected Results getResultSet(RenderContext ctx, boolean async) throws SQLException, IOException
        {
            return super.getResultSet(ctx, async);
        }
    }
    public static class ReexecutableRenderContext extends RenderContext
    {
        SQLFragment sqlf  = null;
        ArrayList<ColumnInfo> selectedColumns;

        ReexecutableRenderContext(RenderContext ctx)
        {
            super(ctx.getViewContext(), ctx.getErrors());
        }

        @Override
        protected Results selectForDisplay(TableInfo table, Collection<ColumnInfo> columns, Map<String, Object> parameters, SimpleFilter filter, Sort sort, int maxRows, long offset, boolean async)
        {
            if (null == sqlf)
            {
                TableSelector selector = new TableSelector(table, columns, filter, sort)
                        .setNamedParameters(null)       // leave named parameters in SQLFragment
                        .setMaxRows(maxRows)
                        .setOffset(offset)
                        .setForDisplay(true);
                var sqlfWithCTE = selector.getSql();
                // flatten out CTEs
                sqlf = new SQLFragment(sqlfWithCTE.getSQL(), sqlfWithCTE.getParams());
                selectedColumns = new ArrayList<>(selector.getSelectedColumns());
            }

            SQLFragment copy = new SQLFragment(sqlf, true);
            QueryService.get().bindNamedParameters(copy, parameters);
            QueryService.get().validateNamedParameters(copy);
            return new ResultsImpl(new SqlSelector(table.getSchema(), copy).getResultSet(), selectedColumns);
        }
    }
}
