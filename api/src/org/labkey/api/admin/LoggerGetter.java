package org.labkey.api.admin;

import org.apache.log4j.Logger;

/**
 * Simple interface that lets import/export contexts get access to a Logger. Pipeline jobs can't hold on to the Logger
 * directly because it's not Serializable.
 * User: jeckels
 * Date: 10/30/12
 */
public interface LoggerGetter
{
    public Logger getLogger();
}
