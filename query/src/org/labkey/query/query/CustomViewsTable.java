package org.labkey.query.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.query.QueryUserSchema;
import org.labkey.query.controllers.QueryController;
import org.labkey.query.persist.QueryManager;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CustomViewsTable extends FilteredTable<QueryUserSchema>
{
    public CustomViewsTable(@NotNull QueryUserSchema userSchema, ContainerFilter cf)
    {
        super(QueryDbSchema.getInstance().getTableInfoCustomView(), userSchema, cf);

        setName(QueryUserSchema.CUSTOM_VIEWS_TABLE_NAME);
        setDescription("Contains a row for each saved custom view. Available to folder administrators.");

        setImportURL(LINK_DISABLER);
        setUpdateURL(new DetailsURL(new ActionURL(QueryController.InternalSourceViewAction.class, getContainer()), Collections.singletonMap("CustomViewId", "CustomViewId")));
        setInsertURL(new DetailsURL(new ActionURL(QueryController.InternalNewViewAction.class, getContainer())));

        wrapAllColumns(true);
        var customViewIdCol = getMutableColumnOrThrow("CustomViewId");
        customViewIdCol.setKeyField(true);

        getMutableColumnOrThrow("EntityId");
        getMutableColumnOrThrow("Schema").setLabel("Schema Name");
        getMutableColumnOrThrow("Name").setLabel("View Name");

        var ownerCol = getMutableColumnOrThrow("CustomViewOwner");
        ownerCol.setLabel("Owner");
        ownerCol.setConceptURI(BuiltInColumnTypes.USERID_CONCEPT_URI);

        var folderCol = getMutableColumnOrThrow(FieldKey.fromString("Container"));
        folderCol.setLabel("Folder");
        folderCol.setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);

        getMutableColumnOrThrow("CreatedBy").setConceptURI(BuiltInColumnTypes.USERID_CONCEPT_URI);
        getMutableColumnOrThrow("ModifiedBy").setConceptURI(BuiltInColumnTypes.USERID_CONCEPT_URI);
        getMutableColumnOrThrow("Columns");
        getMutableColumnOrThrow("Filter");

        setDefaultVisibleColumns(List.of(
                FieldKey.fromParts("Schema"),
                FieldKey.fromParts("QueryName"),
                FieldKey.fromParts("Name"),
                FieldKey.fromParts("CustomViewOwner"),
                FieldKey.fromParts("Container"),
                FieldKey.fromParts("Flags"),
                FieldKey.fromParts("Created"),
                FieldKey.fromParts("CreatedBy"),
                FieldKey.fromParts("Modified"),
                FieldKey.fromParts("ModifiedBy")
        ));
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, AdminPermission.class);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new CustomViewsUpdateService(this, QueryDbSchema.getInstance().getTableInfoCustomView());
    }

    protected static class CustomViewsUpdateService extends DefaultQueryUpdateService
    {
        public CustomViewsUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
        {
            Integer id = (Integer)oldRowMap.get("customViewId");
            if (id != null)
            {
                var view = QueryManager.get().getCustomView(container, id);
                if (view != null)
                {
                    if (view.getCustomViewOwner() == null)
                    {
                        if (!container.hasPermission(user, EditSharedViewPermission.class))
                            throw new UnauthorizedException();
                    }
                    else
                    {
                        // must be owner or site admin
                        if (!user.hasSiteAdminPermission() && view.getCustomViewOwner().intValue() != user.getUserId())
                            throw new UnauthorizedException();
                    }
                    QueryManager.get().delete(user, view);
                }
            }
            return oldRowMap;
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
        {
            // for now just rely on the existing internal action
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
        {
            // for now just rely on the existing internal action
            throw new UnsupportedOperationException();
        }
    }
}
