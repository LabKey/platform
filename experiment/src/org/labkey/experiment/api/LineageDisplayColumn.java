package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;

import static org.labkey.api.util.PageFlowUtil.filter;

public class LineageDisplayColumn extends DisplayColumn
{
    final ColumnInfo lsidCol;
    final String displayField;
    final ExpLineageOptions options = new ExpLineageOptions();
    private TableInfo lookupTable;

    LineageDisplayColumn(ColumnInfo lsid, String displayField, boolean parents, Integer depth, String expType, String cpasType)
    {
        this.lsidCol = lsid;
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
        out.write(filter(getValue(ctx)));
    }

    @Override
    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        out.write(filter(getValue(ctx)));
    }

    @Override
    public void renderTitle(RenderContext ctx, Writer out) throws IOException
    {
        out.write(PageFlowUtil.filter(displayField));
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
    public ColumnInfo getColumnInfo()
    {
        // TODO return display column info
        return lsidCol;
    }

    @Override
    public Object getValue(RenderContext ctx)
    {
        Integer objectid = (Integer)this.lsidCol.getValue(ctx);
        if (!"Material".equals(options.getExpType()) || null==options.getCpasType())
            return "<" + objectid + ">" + this.toString();
        var ss = SampleSetServiceImpl.get().getSampleSet(options.getCpasType());
        if (null == ss)
            return "<" + objectid + ">" + this.toString();

        if (null == lookupTable)
        {
            lookupTable = DefaultSchema.get(ctx.getViewContext().getUser(), ctx.getContainer(), "samples").getTable(ss.getName(), ContainerFilter.CURRENT);
            if (null == lookupTable)
                return "<" + objectid + ">" + this.toString();
        }
        var display = lookupTable.getColumn(displayField);

        // NOTE: can not use ParameterMap (for PreparedStatementFunctionality) because we don't support CTE with parameter markers yet
        // TODO optimize later
        SQLFragment objectids = new SQLFragment("SELECT " + objectid + " AS objectid");
        SQLFragment lineage = ExperimentServiceImpl.get().generateExperimentTreeSQL(objectids, options);
        SQLFragment sql = new SQLFragment("SELECT ");
        sql.append("CAST(").append(display.getValueSql("_ss_")).append(" AS " + (lookupTable.getSqlDialect().getSqlTypeName(JdbcType.VARCHAR)) + "(4000)) AS " ).append(display.getAlias()).append("\n");
        sql.append("FROM ").append(lookupTable.getFromSQL("_ss_")).append("\n");
        sql.append("WHERE objectid IN (SELECT objectid FROM (").append(lineage).append(") _lineage_lookup_)");

        var result = StringUtils.join(new SqlSelector(lookupTable.getSchema(), sql).getArrayList(String.class), ", ");
        return result;
    }

    @Override
    public Class getValueClass()
    {
        return String.class;
    }

    @Override
    public void render(RenderContext ctx, Writer out) throws IOException
    {
        out.write(filter(getValue(ctx)));
    }

    @Override
    public String toString()
    {
        return (options.isParents()?"Inputs":"Outputs") + "/" + (options.getDepth()) + "/" + options.getExpType() + "/" + options.getCpasType() + "/" + displayField;
    }
}
