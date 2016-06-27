package org.labkey.issue.query;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;
import org.labkey.issue.IssuesController;
import org.labkey.issue.actions.InsertIssueDefAction;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

/**
 * Created by klum on 4/10/16.
 */
public class IssuesListDefTable extends FilteredTable<IssuesQuerySchema>
{
    private static final Logger LOG = Logger.getLogger(IssuesListDefTable.class);

    public IssuesListDefTable(IssuesQuerySchema schema)
    {
        super(IssuesSchema.getInstance().getTableInfoIssueListDef(), schema);

        ActionURL url = new ActionURL(InsertIssueDefAction.class, getContainer()).
                addParameter(QueryParam.schemaName, IssuesSchema.getInstance().getSchemaName()).
                addParameter(QueryParam.queryName, IssuesQuerySchema.TableType.IssueListDef.name());
        setInsertURL(new DetailsURL(url));
        addAllColumns();
    }

    @Nullable
    public static String nameFromLabel(String label)
    {
        if (label != null)
        {
            return ColumnInfo.legalNameFromName(label).toLowerCase();
        }
        return null;
    }

    private void addAllColumns()
    {
        setDescription("Contains one row for each issue list");
        setName("Issue List Definitions");

        addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("RowId"))).setHidden(true);

        // don't show the name, it's derived from label
        ColumnInfo nameCol = addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("Name")));
        nameCol.setHidden(true);
        nameCol.setShownInInsertView(false);

        ColumnInfo labelCol = addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("Label")));
        DetailsURL url = new DetailsURL(new ActionURL(IssuesController.ListAction.class, getContainer()),
                Collections.singletonMap("issueDefName", "name"));
        labelCol.setURL(url);

        ColumnInfo containerCol = addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("Container")));
        ContainerForeignKey.initColumn(containerCol, getUserSchema());

        addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("Created")));
        UserIdForeignKey.initColumn(addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("CreatedBy"))));
        addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("Modified")));
        UserIdForeignKey.initColumn(addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("ModifiedBy"))));
    }

    @Nullable
    @Override
    public QueryUpdateService getUpdateService()
    {
        return new UpdateService(this);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (getUpdateService() != null)
        {
            if (perm.equals(InsertPermission.class) || perm.equals(DeletePermission.class))
                return _userSchema.getContainer().hasPermission(user, AdminPermission.class);
        }
        return false;
    }

    @Override
    public ActionURL getImportDataURL(Container container)
    {
        return LINK_DISABLER_ACTION_URL;
    }

    private class UpdateService extends DefaultQueryUpdateService
    {
        public UpdateService(IssuesListDefTable table)
        {
            super(table, table.getRealTable());
        }

        @Override
        protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row) throws SQLException, ValidationException
        {
            String label = (String)row.get("label");
            if (StringUtils.isBlank(label))
                throw new ValidationException("Label required", "label");

            if (IssuesQuerySchema.getReservedTableNames().contains(label))
                throw new ValidationException("The table name : " + label + " is reserved.");

            try
            {
                IssueListDef def = new IssueListDef();
                def.setName(nameFromLabel(label));
                def.setLabel(label);
                BeanUtils.populate(def, row);

                def = def.save(user);

                return new CaseInsensitiveHashMap((Map<String,Object>)BeanUtils.describe(def));
            }
            catch (Exception e)
            {
                throw new ValidationException(e.getMessage());
            }
        }

        @Override
        protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
        {
            throw new UnsupportedOperationException("Update not supported.");
        }

        @Override
        protected void _delete(Container c, Map<String, Object> row) throws InvalidKeyException
        {
            Integer rowId = (Integer)row.get("rowId");
            if (rowId == null)
                throw new InvalidKeyException("Issue Definition rowId required");

            try
            {
                IssueManager.deleteIssueListDef(rowId, c, getUserSchema().getUser());
            }
            catch (Exception e)
            {
                throw new RuntimeException(e.getMessage());
            }
        }
    }
}
