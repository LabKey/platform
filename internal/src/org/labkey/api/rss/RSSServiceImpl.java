/*
 * Copyright (c) 2014-2017 LabKey Corporation
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

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.SyndFeedOutput;
import com.rometools.rome.io.XmlReader;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;

import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Nick Arnold on 5/16/14.
 */
public class RSSServiceImpl implements RSSService
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

    public void aggregateFeeds(List<RSSFeed> feeds, User user, Writer writer)
    {
        SyndFeed feed = new SyndFeedImpl();

        List<SyndEntry> entries = new ArrayList<>();

        feed.setEntries(entries);
        feed.setFeedType("rss_2.0");
        feed.setTitle("Generated Aggregate Feed");
        feed.setDescription("Anonymous Aggregated Feed");
        feed.setAuthor("anonymous");
        feed.setLink("https://www.labkey.org");

        for (RSSFeed _feed : feeds)
        {
            XmlReader reader = null;

            try
            {
                reader = getReader(_feed, user);
            }
            catch (IOException e)
            {
                Logger.getLogger(RSSService.class).error("Invalid RSS Feed: " + e.getMessage());
            }

            if (null != reader)
            {
                try
                {
                    SyndFeedInput input = new SyndFeedInput();
                    SyndFeed inFeed = input.build(reader);

                    entries.addAll(inFeed.getEntries());
                }
                catch (FeedException fe)
                {
                    Logger.getLogger(RSSService.class).error("Invalid Feed (FeedException): " + _feed.getFeedURL());
                }
            }
        }

        entries.sort((o1, o2) ->
        {
            if (o1 == null && o2 == null)
                return 0;
            else if (o1 == null)
                return -1;
            else if (o2 == null)
                return 1;

            Date d1 = o1.getPublishedDate();
            Date d2 = o2.getPublishedDate();

            if (d1 == null || d2 == null)
                return -1;

            return d2.compareTo(d1);
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


    @Nullable
    private XmlReader getReader(RSSFeed feed, User user) throws IOException
    {
        XmlReader reader;

        if (feed.getFeedURL().startsWith("webdav:/"))
        {
            WebdavResource resource = WebdavService.get().lookup(feed.getFeedURL().replace("webdav:/", ""));

            if (null != resource && resource.isFile())
            {
                reader = new XmlReader(resource.getInputStream(user));
            }
            else
            {
                throw new IOException(feed.getFeedURL() + ". If attempting to use webdav ensure the URL begins with webdav:/" + WebdavService.getPath() + " and is not URL encoded.");
            }
        }
        else
        {
            reader = new XmlReader(new URL(feed.getFeedURL()));
        }

        return reader;
    }
}
