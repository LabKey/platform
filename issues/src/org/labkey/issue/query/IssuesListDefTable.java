package org.labkey.issue.query;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
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

        addAllColumns();
    }

    private void addAllColumns()
    {
        setDescription("Contains one row for each issue list");
        setName("Issue List Definitions");

        wrapAllColumns(true);

        ContainerForeignKey.initColumn(getColumn(FieldKey.fromParts("Container")), getUserSchema());
        UserIdForeignKey.initColumn(getColumn(FieldKey.fromParts("CreatedBy")));
        UserIdForeignKey.initColumn(getColumn(FieldKey.fromParts("ModifiedBy")));

        DetailsURL url = new DetailsURL(QueryService.get().urlFor(getUserSchema().getUser(), getContainer(),
                QueryAction.executeQuery,
                IssuesSchema.getInstance().getSchemaName(), null),
                Collections.singletonMap("queryName", "name"));
        getColumn(FieldKey.fromParts("Name")).setURL(url);
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

    private class UpdateService extends DefaultQueryUpdateService
    {
        public UpdateService(IssuesListDefTable table)
        {
            super(table, table.getRealTable());
        }

        @Override
        protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row) throws SQLException, ValidationException
        {
            String name = (String)row.get("name");
            if (StringUtils.isBlank(name))
                throw new ValidationException("Name required", "name");

            if (IssuesQuerySchema.getReservedTableNames().contains(name))
                throw new ValidationException("The table name : " + name + " is reserved.");

            try
            {
                IssueListDef def = new IssueListDef();
                def.setName(name);
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
                IssueManager.deleteIssueDef(rowId, c, getUserSchema().getUser());
            }
            catch (Exception e)
            {
                throw new RuntimeException(e.getMessage());
            }
        }
    }
}
