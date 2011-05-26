package org.labkey.api.data;

import java.sql.SQLException;

/**
 * User: jeckels
 * Date: May 26, 2011
 */
public class SQLGenerationException extends SQLException
{
    public SQLGenerationException(String message)
    {
        super(message);
    }
}
