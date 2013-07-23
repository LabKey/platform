/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.ehr.history;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * User: bimber
 * Date: 2/17/13
 * Time: 6:29 PM
 */
public class HistoryRowImpl implements HistoryRow
{
    private String _subjectId;
    private Date _date;
    private Date _enddate;
    private Integer _projectId;
    private String _categoryGroup;
    private String _categoryText;
    private String _performedBy;
    private String _caseId;
    private String _runId;
    private String _encounterId;
    private Boolean _showTime = false;
    private String _html;

    protected static final Logger _log = Logger.getLogger(HistoryRowImpl.class);

    protected final static SimpleDateFormat _dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    protected final static SimpleDateFormat _timeFormat = new SimpleDateFormat("kk:mm");

    public HistoryRowImpl(String categoryText, String categoryGroup, String subjectId, Date date, String html)
    {
        _categoryText = categoryText;
        _categoryGroup = categoryGroup;
        _subjectId = subjectId;
        _date = date;
        _html = html;
    }

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();

        json.put("dateGroup", _subjectId + "_" + getSortDateString());
        json.put("typeGroup", _subjectId + "_" + _categoryGroup);

        json.put("type", _categoryGroup);
        json.put("sortDate", getSortDateString());

        json.put("id", _subjectId);
        json.put("category", _categoryText);
        json.put("date", _date);
        json.put("enddate", _enddate);
        json.put("project", _projectId);
        json.put("caseId", _caseId);
        json.put("encounterId", _encounterId);
        json.put("runId", _runId);
        json.put("performedby", _performedBy);

        json.put("html", _html);

        if (_showTime)
            json.put("timeString", getTimeString());

        return json;
    }

    public String getSortDateString()
    {
        assert _date != null;

        try
        {
            if (_date == null)
                return "";

            return _dateFormat.format(_date);
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            _log.error("Invalid date: " + _date + " for table: " + _categoryGroup + " and animal " + _subjectId, e);
            return "";
        }
        catch (Exception e)
        {
            _log.error("Error creating sortDateString for animal: " + _subjectId + ", " + _categoryGroup, e);
            return "";
        }
    }

    public void setShowTime(Boolean showTime)
    {
        _showTime = showTime;
    }

    public String getSubjectId()
    {
        return _subjectId;
    }

    public Date getDate()
    {
        return _date;
    }

    public String getCategoryText()
    {
        return _categoryText;
    }

    public String getCategoryGroup()
    {
        return _categoryGroup;
    }

    public String getTimeString()
    {
        return _timeFormat.format(_date);
    }

    public String getHtml()
    {
        return _html;
    }
}