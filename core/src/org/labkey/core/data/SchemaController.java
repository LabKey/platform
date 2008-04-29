package org.labkey.core.data;

import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.BeehivePortingActionResolver;

/**
 * User: adam
 * Date: Apr 29, 2008
 * Time: 8:11:45 AM
 */
public class SchemaController extends SpringActionController
{
    private static ActionResolver _actionResolver = new BeehivePortingActionResolver(DataController.class, SchemaController.class);

    public SchemaController()
    {
        super();
        setActionResolver(_actionResolver);
    }
}
