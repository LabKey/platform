package org.labkey.study.model;

import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.ReadSomePermission;
import org.labkey.api.security.permissions.UpdatePermission;

/**
 * User: bimber
 * Date: 8/23/13
 * Time: 8:15 PM
 */
public enum GroupSecurityType
{
    UPDATE_ALL("UPDATE"),
    READ_ALL("READ"),
    PER_DATASET("READOWN"),
    NONE("NONE");

    private String _paramName;

    GroupSecurityType(String paramName)
    {
        _paramName = paramName;
    }

    public String getParamName()
    {
        return _paramName;
    }

    public static GroupSecurityType getTypeForGroup(Group group, StudyImpl study)
    {
        boolean includeEditOption = study.getSecurityType() == SecurityType.ADVANCED_WRITE;
        SecurityPolicy folderPolicy = study.getContainer().getPolicy();
        SecurityPolicy studyPolicy = SecurityPolicyManager.getPolicy(study);
        boolean hasFolderRead = folderPolicy.hasPermission(group, ReadPermission.class);
        boolean hasUpdatePerm = studyPolicy.hasNonInheritedPermission(group, UpdatePermission.class);
        boolean hasReadSomePerm = studyPolicy.hasNonInheritedPermission(group, ReadSomePermission.class);
        boolean hasReadAllPerm = (!hasUpdatePerm) && studyPolicy.hasNonInheritedPermission(group, ReadPermission.class);
        if (!includeEditOption && hasUpdatePerm)
            hasReadAllPerm = true;

        if (hasUpdatePerm)
            return GroupSecurityType.UPDATE_ALL;
        else if (hasReadAllPerm)
            return GroupSecurityType.READ_ALL;
        else if (!hasReadAllPerm && !hasUpdatePerm && hasReadSomePerm)
            return GroupSecurityType.PER_DATASET;
        else if (!hasReadAllPerm && !hasReadSomePerm)
            return GroupSecurityType.NONE;

        return null;
    }
}
