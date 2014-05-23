/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.rss;


import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.SyndFeedImpl;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.SyndFeedOutput;
import com.sun.syndication.io.XmlReader;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Nick Arnold on 5/16/14.
 */
public class RSSServiceImpl extends RSSService
{
    @Override
    public List<RSSFeed> getFeeds(Container container, User user)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, "announcement");
        TableInfo info = schema.getTable("RSSFeeds");

        List<RSSFeed> feeds = new ArrayList<>();

        if (info != null)
        {
            feeds = new TableSelector(info, null, null).getArrayList(RSSFeed.class);
        }

        return feeds;
    }

    public void aggregateFeeds(List<RSSFeed> feeds, Writer writer)
    {
        SyndFeed feed = new SyndFeedImpl();

        List entries = new ArrayList();

        feed.setEntries(entries);
        feed.setFeedType("rss_2.0");
        feed.setTitle("Generated Aggregate Feed");
        feed.setDescription("Anonymous Aggregated Feed");
        feed.setAuthor("anonymous");
        feed.setLink("https://www.labkey.org");

        for (RSSFeed _feed : feeds)
        {
            URL inputURL = null;

            try
            {
                inputURL = new URL(_feed.getFeedURL());
            }
            catch (MalformedURLException e)
            {
                Logger.getLogger(RSSService.class).error("Invalid Feed (MalformedURLException): " + _feed.getFeedURL());
            }

            if (null != inputURL)
            {
                try
                {
                    SyndFeedInput input = new SyndFeedInput();
                    SyndFeed inFeed = input.build(new XmlReader(inputURL));

                    entries.addAll(inFeed.getEntries());
                }
                catch (IOException e)
                {
                    Logger.getLogger(RSSService.class).error("Invalid Feed (IOException): " + inputURL);
                }
                catch (FeedException fe)
                {
                    Logger.getLogger(RSSService.class).error("Invalid Feed (FeedException): " + inputURL);
                }
            }
        }

        Collections.sort(entries, new Comparator()
        {
            @Override
            public int compare(Object o1, Object o2)
            {
                return ((SyndEntryImpl) o2).getPublishedDate().compareTo(((SyndEntryImpl) o1).getPublishedDate());
            }
        });

        try
        {
            SyndFeedOutput output = new SyndFeedOutput();
            output.output(feed, writer);
        }
        catch (Exception e)
        {
            Logger.getLogger(RSSService.class).error("Error", e);
        }
    }
}
