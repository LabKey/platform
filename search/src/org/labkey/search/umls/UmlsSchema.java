package org.labkey.search.umls;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;

/**
 * User: adam
 * Date: 12/2/13
 * Time: 7:35 PM
 */
public class UmlsSchema
{
    public static DbSchema getSchema()
    {
        return DbSchema.get("umls");
    }

    public static DbScope getScope()
    {
        return getSchema().getScope();
    }
}
