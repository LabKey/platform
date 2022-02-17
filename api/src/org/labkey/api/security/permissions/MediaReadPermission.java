package org.labkey.api.security.permissions;

// We extend ReadPermission because in FilteredTable.hasPermission, we check
//      if (ReadPermission.class.isAssignableFrom(perm)) ...
// as a gate to checking the user schema and contextual roles
public class MediaReadPermission extends ReadPermission
{
    public MediaReadPermission()
    {
        super("Media Read", "Can read media (recipes, recipe batches, ingredients, raw materials) data.");
    }
}
