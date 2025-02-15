package org.labkey.api.studydesign.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;

import java.util.Set;

public abstract class StudyDesignBaseTable extends FilteredTable<StudyDesignQuerySchema>
{
    public StudyDesignBaseTable(StudyDesignQuerySchema schema, TableInfo realTable, ContainerFilter cf)
    {
        this(schema, realTable, cf, false);
    }

    public StudyDesignBaseTable(StudyDesignQuerySchema schema, TableInfo realTable, ContainerFilter cf, boolean includeSourceStudyData)
    {
        super(realTable, schema);

        if (includeSourceStudyData && null != schema._study && !schema._study.isDataspaceStudy())
        {
            boolean hasReaderRole = getContextualRoles().contains(RoleManager.getRole(ReaderRole.class));
            _setContainerFilter(new ContainerFilter.StudyAndSourceStudy(schema.getContainer(), schema.getUser(), hasReaderRole));
        }
        else if (null != cf && supportsContainerFilter())
            _setContainerFilter(cf);
        else
            _setContainerFilter(getDefaultContainerFilter());
    }

    protected MutableColumnInfo addContainerColumn()
    {
        BaseColumnInfo containerCol = new AliasedColumn(this, "Container", _rootTable.getColumn("Container"));
        containerCol.setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);

        return addColumn(containerCol);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        checkedPermissions.add(perm);
        // Most tables should not be editable in Dataspace
        if (!perm.equals(ReadPermission.class) && getContainer().isDataspace())
            return false;
        return hasPermissionOverridable(user, perm);
    }

    protected boolean hasPermissionOverridable(UserPrincipal user, Class<? extends Permission> perm)
    {
        return checkHasReadPermission(user, perm);
    }

    protected boolean checkHasReadPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return ReadPermission.class == perm && _userSchema.getContainer().hasPermission(user, perm, getContextualRoles());
    }

    protected boolean checkReadOrIsAdminPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return ReadPermission.class == perm && _userSchema.getContainer().hasPermission(user, perm, getContextualRoles()) ||
                _userSchema.getContainer().hasPermission(user, AdminPermission.class, getContextualRoles());
    }

    protected Set<Role> getContextualRoles()
    {
        return getUserSchema().getContextualRoles();
    }

    protected boolean checkContainerPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return _userSchema.getContainer().hasPermission(user, perm, getContextualRoles());
    }
}
