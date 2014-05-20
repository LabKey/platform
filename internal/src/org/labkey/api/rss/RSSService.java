package org.labkey.api.rss;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.io.Writer;
import java.util.List;

/**
 * Created by Nick Arnold on 5/16/14.
 */
abstract public class RSSService
{
    static private RSSService instance;
    private static final Logger LOG = Logger.getLogger(RSSService.class);

    static public RSSService get()
    {
        return instance;
    }

    static public void set(RSSService impl)
    {
        instance = impl;
    }

    abstract public List<RSSFeed> getFeeds(Container container, User user);

    abstract public void aggregateFeeds(List<RSSFeed> feeds, Writer writer);
}
