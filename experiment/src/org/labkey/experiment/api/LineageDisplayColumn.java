package org.labkey.experiment.api;

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
import org.labkey.api.data.IMultiValuedDisplayColumn;
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
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static org.labkey.api.util.PageFlowUtil.filter;

// NOTE DataColumn perhaps does more than we need, consider extending DisplayColumn instead?

public class LineageDisplayColumn extends DataColumn implements IMultiValuedDisplayColumn
{
    private final FieldKey boundFieldKey;

    private ReexecutableDataregion innerDataRegion;
    private ReexecutableRenderContext innerCtx;
    private DisplayColumn innerDisplayColumn;

    public static DisplayColumn create(QuerySchema schema, ColumnInfo objectid, FieldKey boundFieldKey)
    {
        return new LineageDisplayColumn(schema, objectid, boundFieldKey);
    }

    // TODO what to do with Level columns (like All, First, etc)
    private LineageDisplayColumn(QuerySchema schema, ColumnInfo objectId, FieldKey boundFieldKey)
    {
        super(objectId, false);
        this.boundFieldKey = boundFieldKey;

        /* SET UP DataRegion */

        // TODO ContainerFilter
        TableInfo seedTable = new SeedTable((UserSchema) schema);
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


    private int innerCtxObjectId = -1;

    /*
     * NOTE DisplayColumn is very stateless, we don't know when we advance a row, or when we are at end-of-resultset
     * so we have to infer this ourselves by watching when objectid changes
     */
    private void updateInnerContext(RenderContext outerCtx)
    {
        if (null == innerCtx)
            innerCtx = new ReexecutableRenderContext(outerCtx);
        int currentObjectId = requireNonNullElse((Integer) getValue(outerCtx), -1);
        if (innerCtxObjectId == currentObjectId)
            return;

        innerCtxObjectId = currentObjectId;
        innerDataRegion.reset(innerCtx, Collections.singletonMap(SeedTable.OBJECTID_PARAMETER, currentObjectId));
        if (-1 != currentObjectId)
        {
            try (ResultSet rs = requireNonNull(innerDataRegion.getResultSet(innerCtx)))
            {
                ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);
                if (rs.next())
                {
                    innerCtx.setRow(factory.getRowMap(rs));
                    boolean hasNext = rs.next();
                    assert !hasNext;
                }
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
            catch (IOException x)
            {
                throw new RuntimeException(x);
            }
        }
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        if (null == innerDisplayColumn)
        {
            Object v = getValue(ctx);
            if (null != v)
                out.write("&lt;" + filter(v) + "&gt;");
            return;
        }
        updateInnerContext(ctx);
        innerDisplayColumn.renderGridCellContents(innerCtx, out);
    }

    @Override
    public List<String> renderURLs(RenderContext ctx)
    {
        if (null == innerDisplayColumn)
            return Collections.emptyList();
        updateInnerContext(ctx);
        return ((IMultiValuedDisplayColumn)innerDisplayColumn).renderURLs(innerCtx);
    }

    @Override
    public List<Object> getDisplayValues(RenderContext ctx)
    {
        if (null == innerDisplayColumn)
            return Collections.emptyList();
        updateInnerContext(ctx);
        return ((IMultiValuedDisplayColumn)innerDisplayColumn).getDisplayValues(innerCtx);
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        // see MultiValuedDisplayColumn.getDisplayValue()
        return getDisplayValues(ctx).stream().map(o -> o == null ? " " : o.toString()).collect(Collectors.joining(", "));
    }

    @Override
    public List<String> getTsvFormattedValues(RenderContext ctx)
    {
        if (null == innerDisplayColumn)
            return Collections.emptyList();
        updateInnerContext(ctx);
        return ((IMultiValuedDisplayColumn)innerDisplayColumn).getTsvFormattedValues(innerCtx);
    }

    @Override
    public List<String> getFormattedTexts(RenderContext ctx)
    {
        if (null == innerDisplayColumn)
            return Collections.emptyList();
        updateInnerContext(ctx);
        return ((IMultiValuedDisplayColumn)innerDisplayColumn).getFormattedTexts(innerCtx);
    }

    @Override
    public List<Object> getJsonValues(RenderContext ctx)
    {
        if (null == innerDisplayColumn)
            return Collections.emptyList();
        updateInnerContext(ctx);
        return ((IMultiValuedDisplayColumn)innerDisplayColumn).getJsonValues(innerCtx);
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
    public void renderInputHtml(RenderContext ctx, Writer out, Object value)
    {
    }

    @Override
    public Class getValueClass()
    {
        if (null == innerDisplayColumn)
            return String.class;
        return innerDisplayColumn.getValueClass();
    }

    @Override
    public String toString()
    {
        return this.getClass().getName() + ":" + boundFieldKey.toDisplayString();
    }


    static class SeedTable extends AbstractTableInfo
    {
        static final String OBJECTID_PARAMETER = "_$_OBJECTID_$_";
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
        void reset(ReexecutableRenderContext ctx, Map<String,Object> currentParameters)
        {
            ctx.setRow(Collections.emptyMap());
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
