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

import org.labkey.api.data.Entity;

import java.util.Date;

/**
 * Created by Nick Arnold on 5/16/14.
 */
public class RSSFeed extends Entity
{
    private int _rowId = 0;
    private String _feedName = null;
    private String _feedURL = null;
    private Date _lastDate = null;
    private String _content = null;

    /**
     * Standard constructor.
     */
    public RSSFeed()
    {
    }

    /**
     * Returns the rowId
     *
     * @return the rowId
     */
    public int getRowId()
    {
        return _rowId;
    }


    /**
     * Sets the rowId
     *
     * @param rowId the new rowId value
     */
    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }


    /**
     * Returns the feedName
     *
     * @return the feedName
     */
    public String getFeedName()
    {
        return _feedName;
    }


    /**
     * Sets the feedName
     *
     * @param feedName the new feedName value
     */
    public void setFeedName(java.lang.String feedName)
    {
        _feedName = feedName;
    }


    /**
     * Returns the feedURL
     *
     * @return the feedURL
     */
    public String getFeedURL()
    {
        return _feedURL;
    }


    /**
     * Sets the feedURL
     *
     * @param feedURL the new feedURL value
     */
    public void setFeedURL(java.lang.String feedURL)
    {
        _feedURL = feedURL;
    }


    /**
     * Returns the lastDate
     *
     * @return the lastDate
     */
    public Date getLastDate()
    {
        return _lastDate;
    }


    /**
     * Sets the lastDate
     *
     * @param lastDate the new lastDate value
     */
    public void setLastDate(Date lastDate)
    {
        _lastDate = lastDate;
    }


    /**
     * Returns the content
     *
     * @return the content
     */
    public String getContent()
    {
        return _content;
    }

    /**
     * Sets the content
     *
     * @param content the new content value
     */
    public void setLastDate(String content)
    {
        _content = content;
    }
}
