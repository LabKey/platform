package org.labkey.api.ehr.dataentry;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * User: bimber
 * Date: 12/3/13
 * Time: 12:40 PM
 */
public interface DataEntryFormFactory
{
    public DataEntryForm createForm(DataEntryFormContext ctx);
}
