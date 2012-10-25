package org.labkey.query.audit;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DetailsColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.query.controllers.QueryController;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Event field documentation:
 *
 * key1 - row PK of the source query
 * key2 - schemaName
 * key3 - queryName
 *
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 10/24/12
 */
public class QueryUpdateAuditViewFactory extends SimpleAuditViewFactory
{
    public static final String QUERY_UPDATE_AUDIT_EVENT = "QueryUpdateAuditEvent";

    private static final QueryUpdateAuditViewFactory INSTANCE = new QueryUpdateAuditViewFactory();

    private QueryUpdateAuditViewFactory() {}

    public static QueryUpdateAuditViewFactory getInstance()
    {
        return INSTANCE;
    }

    @Override
    public String getName()
    {
        return "Query update events";
    }

    @Override
    public String getEventType()
    {
        return QUERY_UPDATE_AUDIT_EVENT;
    }

    @Override
    public QueryView createDefaultQueryView(ViewContext context)
    {
        AuditLogQueryView view = AuditLogService.get().createQueryView(context, null, getEventType());
        view.setSort(new Sort("-Date"));
        addDetailsColumn(view);
        return view;
    }

    public QueryView createHistoryQueryView(ViewContext context, QueryForm form)
    {
        AuditLogQueryView view = AuditLogService.get().createQueryView(context, null, getEventType());
        view.setSort(new Sort("-Date"));

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Key2"), form.getSchemaName());
        filter.addCondition(FieldKey.fromParts("Key3"), form.getQueryName());

        view.setFilter(filter);
        addDetailsColumn(view);
        return view;
    }

    public QueryView createDetailsQueryView(ViewContext context, QueryController.QueryDetailsForm form)
    {
        return createDetailsQueryView(context, form.getSchemaName(), form.getQueryName(), form.getKeyValue());
    }

    public QueryView createDetailsQueryView(ViewContext context, String schemaName, String queryName, String keyValue)
    {
        AuditLogQueryView view = AuditLogService.get().createQueryView(context, null, getEventType());
        view.setSort(new Sort("-Date"));

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Key1"), keyValue);
        filter.addCondition(FieldKey.fromParts("Key2"), schemaName);
        filter.addCondition(FieldKey.fromParts("Key3"), queryName);

        view.setFilter(filter);
        addDetailsColumn(view);
        return view;
    }

    private void addDetailsColumn(AuditLogQueryView view)
    {
        ColumnInfo containerId = view.getTable().getColumn("ContainerId");

        ColumnInfo schemaCol = view.getTable().getColumn("Key2");
        ColumnInfo queryCol = view.getTable().getColumn("Key3");
        ColumnInfo lsidCol = view.getTable().getColumn("Lsid");
        ColumnInfo rowCol = view.getTable().getColumn("RowId");

        view.addDisplayColumn(0, new QueryDetailsColumn(containerId, schemaCol, queryCol, lsidCol, rowCol));
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<FieldKey>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ImpersonatedBy"));
        columns.add(FieldKey.fromParts("ProjectId"));
        columns.add(FieldKey.fromParts("ContainerId"));
        columns.add(FieldKey.fromParts("Key2"));
        columns.add(FieldKey.fromParts("Key3"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    @Override
    public void setupTable(FilteredTable table, UserSchema schema)
    {
        super.setupTable(table, schema);

        ColumnInfo schemaCol = table.getColumn("Key2");
        schemaCol.setLabel("SchemaName");

        ColumnInfo queryCol = table.getColumn("Key3");
        queryCol.setLabel("QueryName");

        table.getColumn("Key1").setHidden(true);
        table.getColumn("IntKey1").setHidden(true);
        table.getColumn("IntKey2").setHidden(true);
        table.getColumn("IntKey3").setHidden(true);
    }

    @Override
    public void initialize(ContainerUser context) throws Exception
    {
        Container c = ContainerManager.getSharedContainer();
        String domainURI = AuditLogService.get().getDomainURI(QUERY_UPDATE_AUDIT_EVENT);

        Domain domain = PropertyService.get().getDomain(c, domainURI);
        if (domain == null)
        {
            domain = PropertyService.get().createDomain(c, domainURI, "QueryUpdateAuditEventDomain");
            domain.save(context.getUser());
            domain = PropertyService.get().getDomain(c, domainURI);
        }

        if (domain != null)
        {
            ensureProperties(context.getUser(), domain, new PropertyInfo[]{
                    new PropertyInfo(OLD_RECORD_PROP_NAME, OLD_RECORD_PROP_CAPTION, PropertyType.STRING),
                    new PropertyInfo(NEW_RECORD_PROP_NAME, NEW_RECORD_PROP_CAPTION, PropertyType.STRING)

            });
        }
    }

    private static class QueryDetailsColumn extends DetailsColumn
    {
        ColumnInfo _containerCol, _schemaCol, _queryCol;
        ColumnInfo _lsidCol, _rowCol;

        public QueryDetailsColumn(ColumnInfo containerCol, ColumnInfo schemaCol, ColumnInfo queryCol, ColumnInfo lsidCol, ColumnInfo rowCol)
        {
            super(null, null);
            _containerCol = containerCol;
            _schemaCol = schemaCol;
            _queryCol = queryCol;
            _lsidCol = lsidCol;
            _rowCol = rowCol;
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            super.addQueryFieldKeys(keys);
            keys.add(_containerCol.getFieldKey());
            keys.add(_schemaCol.getFieldKey());
            keys.add(_queryCol.getFieldKey());
            keys.add(_lsidCol.getFieldKey());
            keys.add(_rowCol.getFieldKey());
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String lsid = ctx.get(_lsidCol.getFieldKey(), String.class);
            if (lsid != null)
                super.renderGridCellContents(ctx, out);
            else
                out.write("&nbsp;");
        }

        @Override
        public String renderURL(RenderContext ctx)
        {
            String containerId = (String)ctx.get("ContainerId");
            Container c = ContainerManager.getForId(containerId);
            if (c == null)
                return null;

            Integer rowId = ctx.get(_rowCol.getFieldKey(), Integer.class);

            ActionURL url = new ActionURL(QueryController.QueryAuditChangesAction.class, c).
                    addParameter("auditRowId", rowId);

            return url.toString();
        }

        @Override
        public boolean isVisible(RenderContext ctx)
        {
            return true;
        }
    }
}
