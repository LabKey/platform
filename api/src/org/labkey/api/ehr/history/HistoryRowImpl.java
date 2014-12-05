/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
    private HistoryDataSource _source;
    private String _subjectId;
    private Date _date;
    private Date _enddate;
    private Integer _projectId;
    private String _primaryGroup;
    private String _categoryText;
    private String _categoryColor;
    private String _performedBy;
    private String _caseId;
    private String _runId;
    private String _encounterId;
    private String _qcStateLabel;
    private Boolean _isPublicData;
    private Boolean _showTime = false;
    private String _taskId = null;
    private Integer _taskRowId;
    private String _formType;
    private String _objectId;
    private String _html;

    protected static final Logger _log = Logger.getLogger(HistoryRowImpl.class);

    protected final SimpleDateFormat _dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    protected final SimpleDateFormat _timeFormat = new SimpleDateFormat("kk:mm");

    public HistoryRowImpl(HistoryDataSource source, String categoryText, String primaryGroup, String categoryColor, String subjectId, Date date, String html, String qcStateLabel, Boolean publicData, String taskId, Integer taskRowId, String formType, String objectId)
    {
        _source = source;
        _categoryText = categoryText;
        _categoryColor = categoryColor;
        _primaryGroup = primaryGroup;
        _subjectId = subjectId;
        _date = date;
        _html = html;
        _qcStateLabel = qcStateLabel;
        _isPublicData = publicData;
        _taskId = taskId;
        _taskRowId = taskRowId;
        _formType = formType;
        _objectId = objectId;
    }

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();

        json.put("source", _source.getName());
        json.put("dateGroup", _subjectId + "_" + getSortDateString());
        json.put("typeGroup", _subjectId + "_" + _primaryGroup);

        json.put("type", _primaryGroup);
        json.put("sortDate", getSortDateString());

        json.put("id", _subjectId);
        json.put("category", _categoryText);
        json.put("categoryColor", _categoryColor);
        json.put("date", _date);
        json.put("enddate", _enddate);
        json.put("project", _projectId);
        json.put("caseId", _caseId);
        json.put("encounterId", _encounterId);
        json.put("runId", _runId);
        json.put("performedby", _performedBy);
        json.put("qcStateLabel", _qcStateLabel);
        json.put("publicData", _isPublicData);

        json.put("taskId", _taskId);
        json.put("taskRowId", _taskRowId);
        json.put("taskFormType", _formType);
        json.put("objectId", _objectId);

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

            //note: Date formats are not synchronized, so create a new instance here.
            return new SimpleDateFormat("yyyy-MM-dd").format(_date);
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            _log.error("Invalid date: " + _date + " for table: " + _primaryGroup + " and animal " + _subjectId, e);
            return "";
        }
        catch (Exception e)
        {
            _log.error("Error creating sortDateString for animal: " + _subjectId + ", " + _primaryGroup + " with date: " + _date, e);
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

    public String getCategoryColor()
    {
        return _categoryColor;
    }

    public String getTaskId()
    {
        return _taskId;
    }

    public Integer getTaskRowId()
    {
        return _taskRowId;
    }

    public String getFormType()
    {
        return _formType;
    }

    public String getObjectId()
    {
        return _objectId;
    }

    public String getPrimaryGroup()
    {
        return _primaryGroup;
    }

    public Boolean getPublicData()
    {
        return _isPublicData;
    }

    public void setPublicData(Boolean publicData)
    {
        _isPublicData = publicData;
    }

    public String getQcStateLabel()
    {
        return _qcStateLabel;
    }

    public void setQcStateLabel(String qcStateLabel)
    {
        _qcStateLabel = qcStateLabel;
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