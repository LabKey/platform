package org.labkey.api.security.permissions;

public class MediaReadPermission extends AbstractPermission
{
    public MediaReadPermission()
    {
        super("Media Read", "Can read media (recipes, recipe batches, ingredients, raw materials) data.");
    }
}
