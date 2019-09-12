package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleSetService;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    }

    @Override
    public ColumnInfo getDisplayColumn()
    {
        return null;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        try
        {
            Integer objectid = (Integer) getValue(ctx);
            if (null == objectid)
            {
                return;
            }

            // TODO ContainerFilter
            TableInfo seedTable = new SeedTable((UserSchema)sourceSchema, objectid);
            ColumnInfo bound = null;
//            ColumnInfo display = null;
            for (String part : boundFieldKey.getParts())
            {
                if (null == bound)
                    bound = seedTable.getColumn(boundFieldKey.getRootName());
                else
                    bound = null == bound.getFk() ? null : bound.getFk().createLookupColumn(bound, part);
                if (null == bound)
                    break;
            }
            if (null == bound)
            {
                // This is an error, probably in recreating the fieldkey correctly
                out.write("&lt;" + filter(this.toString()) + " " + objectid + "&gt;");
                return;
            }

            DataRegion dr = new DataRegion();
            dr.setTable(seedTable);
            dr.addColumn(bound);
            var displayColumn = dr.getDisplayColumn(0);

            RenderContext innerCtx = new RenderContext(ctx.getViewContext(), ctx.getErrors());
            try (ResultSet rs = requireNonNull(dr.getResultSet(innerCtx)))
            {
                ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

                if (rs.next())
                {
                    innerCtx.setRow(factory.getRowMap(rs));
                    displayColumn.renderGridCellContents(innerCtx, out);
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
        final UserSchema schema;
        final SQLFragment sqlf;

        SeedTable(UserSchema schema, Integer objectid)
        {
            super(schema.getDbSchema(), "seed");
            SqlDialect d = schema.getDbSchema().getScope().getSqlDialect();
            this.schema = schema;
            this.sqlf = new SQLFragment("SELECT CAST(" + (null==objectid ? "NULL" : String.valueOf(objectid)) + " AS " + d.getSqlCastTypeName(JdbcType.INTEGER)+ ") AS objectid");
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
