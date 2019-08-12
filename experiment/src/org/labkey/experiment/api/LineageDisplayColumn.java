package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FilteredTable;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;

import static org.labkey.api.util.PageFlowUtil.filter;

public class LineageDisplayColumn extends DataColumn
{
    final String displayField;
    final ExpLineageOptions options = new ExpLineageOptions();
    private TableInfo lookupTable;
    private ParameterMapStatement parameterMap;

    LineageDisplayColumn(ColumnInfo objectid, String displayField, boolean parents, Integer depth, String expType, String cpasType)
    {
        super(objectid, true);
        this.displayField = displayField;
        options.setParents(parents);
        options.setChildren(!parents);
        options.setDepth(depth==null?0:depth);
        options.setExpType(expType);
        options.setCpasType(cpasType);
        options.setForLookup(true);
        options.setUseObjectIds(true);
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        try
        {
            Integer objectid = (Integer) getValue(ctx);
            if (!"Material".equals(options.getExpType()) || null == options.getCpasType())
            {
                out.write(filter(getValue(ctx)));
                return;
            }
            var ss = SampleSetServiceImpl.get().getSampleSet(options.getCpasType());
            if (null == ss)
            {
                out.write(filter(getValue(ctx)));
                return;
            }

            if (null == lookupTable)
            {
                lookupTable = DefaultSchema.get(ctx.getViewContext().getUser(), ctx.getContainer(), "samples").getTable(ss.getName(), ContainerFilter.CURRENT);
                if (null == lookupTable)
                {
                    out.write(filter(getValue(ctx)));
                    return;
                }
            }

            // construct explicit sql for column value only
            // lame, but ParameterMap.selector() method is pretty cool
            if (true == false)
            {
                if (null == parameterMap)
                {
                    var display = lookupTable.getColumn(displayField);
                    SQLFragment sql = new SQLFragment("SELECT ");
                    sql.append("CAST(").append(display.getValueSql("_ss_")).append(" AS " + (lookupTable.getSqlDialect().getSqlTypeName(JdbcType.VARCHAR)) + "(4000)) AS ").append(display.getAlias()).append("\n");
                    sql.append("FROM ").append(lookupTable.getFromSQL("_ss_")).append("\n");
                    SQLFragment objectids = new SQLFragment("SELECT CAST(? AS INTEGER) AS objectid");
                    objectids.add(new Parameter("objectid", JdbcType.INTEGER));
                    SQLFragment lineage = ExperimentServiceImpl.get().generateExperimentTreeSQL(objectids, options);
                    sql.append("WHERE objectid IN (SELECT objectid FROM (").append(lineage).append(") _lineage_lookup_)");
                    parameterMap = new ParameterMapStatement(lookupTable.getSchema().getScope(), sql, null);
                }
                var result = StringUtils.join(
                        parameterMap.selector(Collections.singletonMap("objectid", objectid)).getArrayList(String.class),
                        ", ");
                out.write(filter(result));
            }
            // nested DataRegion
            else
            {
                // TODO make DataRegion parameterized and re-executable
                FilteredTable drTable = new FilteredTable<>(lookupTable, (SamplesSchema) lookupTable.getUserSchema(), ContainerFilter.EVERYTHING);
                drTable.wrapAllColumns(true);
                var display = drTable.getColumn(displayField);
                SQLFragment objectids = new SQLFragment("SELECT " + objectid + " AS objectid");
                SQLFragment lineage = ExperimentServiceImpl.get().generateExperimentTreeSQL(objectids, options);
                SQLFragment lineageFilter = new SQLFragment(" objectid IN (SELECT objectid FROM (").append(lineage).append(") _lineage_lookup_) ");
                drTable.addCondition(lineageFilter);
                DataRegion dr = new DataRegion();
                dr.setTable(drTable);
                var displayColumnRenderer = display.getDisplayColumnFactory().createRenderer(display);
                dr.addDisplayColumn(displayColumnRenderer);

                RenderContext innerCtx = new RenderContext(ctx.getViewContext(), ctx.getErrors());
                ResultSet rs = dr.getResultSet(innerCtx);
                ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

                String comma = "";
                while (rs.next())
                {
                    innerCtx.setRow(factory.getRowMap(rs));
                    out.append(comma);
                    comma = ", ";
                    displayColumnRenderer.renderGridCellContents(innerCtx, out);
                }
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
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
        return (options.isParents()?"Inputs":"Outputs") + "/" + (options.getDepth()) + "/" + options.getExpType() + "/" + options.getCpasType() + "/" + displayField;
    }
}
