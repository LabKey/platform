package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.ILineageDisplayColumn;
import org.labkey.api.data.IMultiValuedDisplayColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.ReexecutableDataregion;
import org.labkey.api.query.ReexecutableRenderContext;
import org.labkey.api.query.UserSchema;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static org.labkey.api.util.PageFlowUtil.filter;

// NOTE DataColumn perhaps does more than we need, consider extending DisplayColumn instead?

public class LineageDisplayColumn extends DataColumn implements IMultiValuedDisplayColumn, ILineageDisplayColumn
{
    private final FieldKey boundFieldKey;

    private ReexecutableDataregion innerDataRegion;
    private ReexecutableRenderContext innerCtx;
    private ColumnInfo innerBoundColumn;
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

        innerBoundColumn = bound;
        innerDataRegion = new ReexecutableDataregion();
        innerDataRegion.setTable(seedTable);
        innerDataRegion.addColumn(bound);
        innerDisplayColumn = innerDataRegion.getDisplayColumn(0);
        // apply date and number formats
        innerDataRegion.prepareDisplayColumns(schema.getContainer());
    }

    @Override
    public ColumnInfo getDisplayColumn()
    {
        return null;
    }

    @Override
    public DisplayColumn getInnerDisplayColumn()
    {
        return innerDisplayColumn;
    }

    @Override
    public ColumnInfo getInnerBoundColumn()
    {
        return innerBoundColumn;
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
            try (Results results = requireNonNull(innerDataRegion.getResults(innerCtx)))
            {
                ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(results);
                if (results.next())
                {
                    innerCtx.setRow(factory.getRowMap(results));
                    boolean hasNext = results.next();
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
    public Object getJsonValue(RenderContext ctx)
    {
        // issue: 44337. Doesn't seem to be a reason to return the object ID, even in the extended API response
        return new JSONArray(getJsonValues(ctx).stream().map(o -> o == null ? " " : o.toString()).collect(Collectors.toList()));
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
    public Class getDisplayValueClass()
    {
        if (null == innerDisplayColumn)
            return String.class;
        return innerDisplayColumn.getDisplayValueClass();
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

}
