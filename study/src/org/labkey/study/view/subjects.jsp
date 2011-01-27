<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.view.SubjectsWebPart" %>
<%
/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SubjectsWebPart.SubjectsBean> me = (JspView<SubjectsWebPart.SubjectsBean>) HttpView.currentView();
    SubjectsWebPart.SubjectsBean bean = me.getModelBean();
    Container container = bean.getViewContext().getContainer();
    String singularNoun = StudyService.get().getSubjectNounSingular(container);
    String pluralNoun = StudyService.get().getSubjectNounPlural(container);
    String colName = StudyService.get().getSubjectColumnName(container);
    ActionURL subjectUrl = new ActionURL(StudyController.ParticipantAction.class, container);
    subjectUrl.addParameter("participantId", "");
    String urlTemplate = subjectUrl.getEncodedLocalURIString();
    int cols = bean.getCols();
    int rows = bean.getRows();
    int maxSubjects = cols * rows;
    String divId = "subjectList";
%>
<style type="text/css">
ul.subjectlist li {
    margin: 0;
    padding: 0;
    text-indent: 3px;    
    list-style-type: none;
}

ul.subjectlist {
    padding-left: 1em;
}

</style>

<script type="text/javascript">

    var _subjects = [];
    var _urlTemplate = '<%= urlTemplate %>';
    var _maxSubjects = <%= maxSubjects %>;
    var _subjectColName = '<%= colName %>';
    var _singularNoun = '<%= singularNoun %>';
    var _pluralNoun = '<%= pluralNoun %>';
    var _cols = <%= cols %>;
    var _divId = '<%= divId %>';

    function onFailure(errorInfo, options, responseObj)
    {
        var html;
        if (errorInfo && errorInfo.exception)
            html = "Failure: " + errorInfo.exception;
        else
            html = "Failure: " + responseObj.statusText;
        document.getElementById(_divId).innerHTML = html;
    }

    function onSuccess(data)
    {
        _subjects = [];
        for (var rowIndex = 0; rowIndex < data.rows.length; rowIndex++)
        {
            var dataRow = data.rows[rowIndex];
            _subjects[_subjects.length] = dataRow[_subjectColName];
        }
        renderSubjects(_maxSubjects, _cols, undefined);
    }

    var _initialRenderComplete = false;
    function renderSubjects(maxCount, cols, substr)
    {
        if (_subjects.length == 0)
        {
            document.getElementById(_divId + 'wrapper').innerHTML = 'No ' + _pluralNoun.toLowerCase() + " were found in this study.  " +
                    _singularNoun + " IDs will appear here after specimens or datasets are imported.";
            return;
        }

        // Lock the initial div size on first render, but allow it to change if we're filtering:
        if (_initialRenderComplete)
        {
            var outerDiv = document.getElementById(_divId + 'wrapper');
            outerDiv.removeAttribute('style');
        }
        _initialRenderComplete = true;

        var maxPerCol = Math.ceil(maxCount / cols);
        var html = '<table><tr><td valign="top"><ul class="subjectlist">';
        var count = 0;
        for (var subjectIndex = 0; subjectIndex < _subjects.length; subjectIndex++)
        {
            var subjectId = _subjects[subjectIndex];
            if (!substr || subjectId.indexOf(substr) >= 0)
            {
                if (++count > 1 && count % maxPerCol == 1 && count <= maxCount)
                    html += '</ul></td><td  valign="top"><ul class="subjectlist">';

                if (count <= maxCount)
                    html += '<li><a href="' + _urlTemplate + subjectId + '">' + subjectId + '</a></li>';
            }
        }

        html += '</ul></td></tr></table>';
        if (count > maxCount)
        {
            if (substr)
                html += 'Showing ' + maxCount + ' of ' + count + ' matching ' + _pluralNoun.toLowerCase() + '.';
            else
                html += 'Showing first ' + maxCount + ' of ' + count + ' ' + _pluralNoun.toLowerCase() + '.';
            var substrParam = substr ? '\'' + substr + '\'' : 'undefined';
            html += ' <a href="#" onClick="renderSubjects(' + count + ', ' + cols + ', ' + substrParam + '); return false;">Show all</a>';
        }
        else if (count > 0)
        {
            if (substr)
                html += 'Found ' + count + ' ' + (count > 1 ? _pluralNoun.toLowerCase() : _singularNoun.toLowerCase()) + '.';
            else
                html += 'Showing all ' + count + ' ' + _pluralNoun.toLowerCase() + '.';
        }
        else
            html += 'No ' + _singularNoun.toLowerCase() + ' IDs contain \"' + substr + '\".';
        document.getElementById(_divId).innerHTML = html;
    }

    function updateSubjects(substring)
    {
        // Use a timer to coalesce keystrokes
        clearTimeout();
        var refreshScript = 'renderSubjects(' + _maxSubjects + ', ' + _cols + ', "' + substring + '");';
        setTimeout(refreshScript, 100);
    }

    function loadParticipants()
    {
        LABKEY.Query.selectRows({
                schemaName: 'study',
                queryName: _singularNoun,
                columns: _subjectColName,
                sort: _subjectColName,
                success: onSuccess,
                failure: onFailure
            });
    }

    Ext.onReady(function() {
        loadParticipants();
    });
</script>
<div id="<%= divId %>wrapper" style="height:<%= 1.5 * rows + 4 %>em">
    Filter: <input type="text" size="15" onKeyUp="updateSubjects(this.value); return false;">
    <div id="<%= divId %>">Loading...</div>
</div>