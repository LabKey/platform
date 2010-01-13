package org.labkey.core.workbook;

import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.settings.AppProps;
import org.labkey.core.query.CoreQuerySchema;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: labkey
 * Date: Jan 6, 2010
 * Time: 2:18:59 PM
 */
public class WorkbooksTableInfo extends FilteredTable
{
    private CoreQuerySchema _schema;

    public WorkbooksTableInfo(CoreQuerySchema coreSchema)
    {
        super(CoreSchema.getInstance().getTableInfoContainers(), coreSchema.getContainer());
        _schema = coreSchema;

        ColumnInfo col;
        ActionURL projBegin = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(_schema.getContainer());
        String wbURL = AppProps.getInstance().getContextPath() + "/" + projBegin.getPageFlow()
                + "/__r${ID}/" + projBegin.getAction() + ".view";
        StringExpression webURLExp = StringExpressionFactory.create(wbURL, true);

        col = this.wrapColumn("ID", getRealTable().getColumn("RowId"));
        col.setKeyField(true);
        col.setReadOnly(true);
        col.setURL(webURLExp);
        this.addColumn(col);

        col = this.wrapColumn(getRealTable().getColumn("Name"));
        col.setURL(webURLExp);
        this.addColumn(col);

        col = this.wrapColumn(getRealTable().getColumn("Title"));
        col.setURL(webURLExp);
        this.addColumn(col);

        this.addColumn(this.wrapColumn(getRealTable().getColumn("Description")));
        
        col = this.wrapColumn(getRealTable().getColumn("CreatedBy"));
        final boolean isSiteAdmin = _schema.getUser().isAdministrator();
        col.setFk(new LookupForeignKey("UserId", "DisplayName")
        {
            public TableInfo getLookupTableInfo()
            {
                return isSiteAdmin ? _schema.getSiteUsers() : _schema.getUsers();
            }
        });
        this.addColumn(col);

        this.addColumn(this.wrapColumn(getRealTable().getColumn("Created")));
        this.addColumn(this.wrapColumn(getRealTable().getColumn("Parent")));
        this.addColumn(this.wrapColumn(getRealTable().getColumn("EntityID")));

        List<FieldKey> defCols = new ArrayList<FieldKey>();
        defCols.add(FieldKey.fromParts("ID"));
        defCols.add(FieldKey.fromParts("Title"));
        defCols.add(FieldKey.fromParts("CreatedBy"));
        defCols.add(FieldKey.fromParts("Created"));
        this.setDefaultVisibleColumns(defCols);

        //workbook true
        this.addCondition(getRealTable().getColumn("Workbook"), "true");
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        clearConditions("Parent");
        
        //need to apply to the 'Parent' column
        Collection<String> containerIds = filter.getIds(getContainer());
        if (null != containerIds)
        {
            SimpleFilter.InClause containerClause = new SimpleFilter.InClause("Parent", containerIds);
            this.addCondition(new SimpleFilter(containerClause));
        }
    }

    @Override
    public boolean hasPermission(User user, int perm)
    {
        return _schema.getContainer().hasPermission(user, perm);
    }

    @Override
    public ActionURL delete(User user, ActionURL srcURL, QueryUpdateForm form) throws Exception
    {
        if (!_schema.getContainer().hasPermission(user, DeletePermission.class))
            throw new UnauthorizedException("You do not have permissions to delete workbooks!");

        Set<String> ids = DataRegionSelection.getSelected(form.getViewContext(), true);
        for (String id : ids)
        {
            Container workbook = ContainerManager.getForRowId(id);
            if (null == workbook || !workbook.isWorkbook())
                throw new NotFoundException("Could not find a workbook with id '" + id + "'");
            ContainerManager.delete(workbook, user);
        }
        return _schema.getContainer().getStartURL(form.getViewContext());
    }
}
