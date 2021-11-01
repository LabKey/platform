package org.labkey.api.query;

import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.ExtendedApiQueryResponse;
import org.labkey.api.action.ExtendedApiQueryResponse.ColMap;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NestedPropertyDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.Pair;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.toList;

public class PropertiesDisplayColumn extends DataColumn implements NestedPropertyDisplayColumn
{
    public static final String CONCEPT_URI = "http://www.labkey.org/types#properties";
    public static final String EXTRA_PROPERTIES = PropertiesDisplayColumn.class.getName();

    private final UserSchema schema;

    private final PropsTable propTable;
    private final ReexecutableDataregion innerDataRegion;

    // shared across all of the PropertiesDisplayColumn in this RenderContext
    private Map<String, Pair<PropertyColumn, DisplayColumn>> propCols;

    // nested render state that changes for each row
    private ReexecutableRenderContext innerCtx;
    private String innerCtxLsid = null;
    private List<Pair<PropertyColumn, DisplayColumn>> innerCtxCols;

    public PropertiesDisplayColumn(UserSchema schema, ColumnInfo lsidCol)
    {
        super(lsidCol, false);
        this.schema = schema;

        propTable = new PropsTable(schema);

        innerDataRegion = new ReexecutableDataregion();
        innerDataRegion.setTable(propTable);
        innerDataRegion.setShowPagination(false);
        innerDataRegion.setShowPaginationCount(false);
        innerDataRegion.setMaxRows(1);
        innerDataRegion.setSortable(false);
        innerDataRegion.setShowFilters(false);
    }

    UserSchema getUserSchema()
    {
        return schema;
    }

    // This function may be called more than once for the same row during rendering
    private void updateInnerContext(RenderContext outerCtx)
    {
        if (null == innerCtx)
        {
            innerCtx = new ReexecutableRenderContext(outerCtx);
            innerCtx.setCurrentRegion(innerDataRegion);

            // NOTE: We are sharing this same instance across all PropertiesDisplayColumn in the RenderContext
            propCols = (Map<String, Pair<PropertyColumn, DisplayColumn>>)outerCtx.computeIfAbsent(EXTRA_PROPERTIES, k -> new LinkedHashMap<>());
        }

        String currentLsid = requireNonNullElse((String)getValue(outerCtx), null);
        if (Objects.equals(innerCtxLsid, currentLsid))
            return;

        innerCtxLsid = currentLsid;
        innerDataRegion.reset(innerCtx, Collections.singletonMap(PropsTable.OBJECTURI_PARAMETER, currentLsid));
        innerCtxCols = new ArrayList<>(propCols.size());

        if (null != currentLsid)
        {
            // get the ordered list of properties used by the object
            List<String> propertyURIs = OntologyManager.getObjectPropertyOrder(this.schema.getContainer(), currentLsid);

            // create property descriptors and columns
            for (String propertyURI : propertyURIs)
            {
                var pair = propCols.computeIfAbsent(propertyURI, (s) -> {
                    PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(propertyURI, this.schema.getContainer());
                    // limit to only vocabulary properties -- assay Run still uses OntologyManager and we don't want to show those properties
                    if (pd != null && pd.isVocabulary())
                    {
                        PropertyColumn pc = new PropertyColumn(pd, propTable, "objectUri", this.schema.getContainer(), this.schema.getUser(), false);
                        // use the property URI as the column's FieldKey name
                        String label = pc.getLabel();
                        pc.setFieldKey(FieldKey.fromParts(pd.getPropertyURI()));
                        pc.setLabel(label);

                        DisplayColumn dc = pc.getDisplayColumnFactory().createRenderer(pc);
                        // apply date and number formats
                        dc.prepare(this.schema.getContainer());
                        return Pair.of(pc, dc);
                    }
                    return null;
                });
                if (pair != null)
                    innerCtxCols.add(pair);
            }

            // now add the properties as columns
            innerDataRegion.clearColumns();
            for (Pair<PropertyColumn, DisplayColumn> pair : innerCtxCols)
            {
                innerDataRegion.addDisplayColumn(pair.second);
            }

            // finally, execute the query
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
    public @NotNull List<Pair<RenderContext, DisplayColumn>> getNestedDisplayColumns(RenderContext ctx)
    {
        updateInnerContext(ctx);
        if (innerCtxLsid != null && !innerCtxCols.isEmpty())
        {
            return innerCtxCols.stream().map(p -> Pair.<RenderContext,DisplayColumn>of(innerCtx, p.second)).collect(toList());
        }

        return Collections.emptyList();
    }

    @Override
    public @NotNull String getNestedColumnKey(@NotNull DisplayColumn nestedCol)
    {
        return (nestedCol.getColumnInfo() != null ? nestedCol.getColumnInfo().getFieldKey() : nestedCol.getName()).toString();
    }

    @Override
    public Object getJsonValue(RenderContext ctx)
    {
        updateInnerContext(ctx);
        if (innerCtxLsid != null && !innerCtxCols.isEmpty())
        {
            // !! Don't format the json object here, just return the wrapped
            // column value to indicate that this row has properties.
            // ExtendedApiQueryResponse will ask for the NestedPropertyDisplayColumn.getNestedColumns() to render themselves
            return super.getJsonValue(ctx);
        }

        return null;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        updateInnerContext(ctx);

        if (innerCtxLsid != null && !innerCtxCols.isEmpty())
        {
            out.write("<table>");
            out.write("<table class='table-condensed labkey-data-region table-bordered'>");
            out.write("<thead>");
            out.write("<tr>");
            for (var pair : innerCtxCols)
            {
                pair.second.renderGridHeaderCell(innerCtx, out);
            }
            out.write("</tr>");
            out.write("</thead>");

            out.write("<tbody>");
            out.write("<tr>");
            for (var pair : innerCtxCols)
            {
                pair.second.renderGridDataCell(innerCtx, out);
            }
            out.write("</tr>");
            out.write("</tbody>");
            out.write("</table>");
        }
    }

    @Override
    public Object getExcelCompatibleValue(RenderContext ctx)
    {
        updateInnerContext(ctx);
        return toJSONString(ctx);
    }

    @Override
    public String getTsvFormattedValue(RenderContext ctx)
    {
        updateInnerContext(ctx);
        return toJSONString(ctx);
    }

    public List<ColMap> toColMaps(RenderContext ctx)
    {
        if (innerCtxLsid != null && !innerCtxCols.isEmpty())
        {
            var nested = ExtendedApiQueryResponse.getNestedPropertiesArray(ctx, this, true, true, false);
            return nested;
        }

        return null;
    }

    // return json object in same style as select rows response
    public JsonNode toJSONNode(RenderContext ctx)
    {
        if (innerCtxLsid != null && !innerCtxCols.isEmpty())
        {
            var o = toColMaps(ctx);
            return JsonUtil.DEFAULT_MAPPER.valueToTree(o);
        }

        return null;
    }

    private String toJSONString(RenderContext ctx)
    {
        JsonNode node = toJSONNode(ctx);
        if (node == null)
            return null;

        return node.toPrettyString();
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
        // no-op
    }


    private class PropsTable extends VirtualTable
    {
        static final String OBJECTURI_PARAMETER = "__OBJECTURI__";

        final UserSchema schema;
        final Map<String, Pair<PropertyColumn, DisplayColumn>> propCols;

        final SQLFragment sqlf;
        final List<QueryService.ParameterDecl> parameters = Collections.singletonList(new QueryService.ParameterDeclaration(OBJECTURI_PARAMETER, JdbcType.VARCHAR));

        public PropsTable(UserSchema schema)
        {
            super(schema.getDbSchema(), "props", schema);
            this.schema = schema;
            SqlDialect d = schema.getDbSchema().getSqlDialect();

            this.propCols = new HashMap<>();

            this.sqlf = new SQLFragment("SELECT CAST(? AS " + d.getSqlCastTypeName(JdbcType.VARCHAR)+ ") AS objectUri");
            this.sqlf.addAll(parameters);

            var objectUriCol = new BaseColumnInfo("objectUri", this, JdbcType.INTEGER);
            addColumn(objectUriCol);
        }

        @Override
        public @NotNull Collection<QueryService.ParameterDecl> getNamedParameters()
        {
            return parameters;
        }

        @Override
        public @NotNull SQLFragment getFromSQL()
        {
            return sqlf;
        }

        @Override
        public UserSchema getUserSchema()
        {
            return this.schema;
        }
    }
}
