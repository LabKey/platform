<%
/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
<script type="text/javascript">
    LABKEY.requiresScript("study/redesignUtils.js", true);
</script>
<style type="text/css">
    .labkey-specimen-search-toggle {
	display: block;
	margin: 5px 0 15px 2px;
	color: #ccc;
}

.labkey-specimen-search-toggle a {
	font-weight: normal;
}

.labkey-specimen-search-toggle a.youarehere {
	color: #000;
	font-weight: bold;
}

#labkey-specimen-search {

}

#labkey-vial-search {
	display: none;
}

td.labkey-padright {
	padding-right: 10px;
	white-space: nowrap;
}

td.labkey-specimen-search-button {
	padding: 10px 0;
}

.labkey-wp-footer {
	width: 98%;
	text-align: right;
	padding: 5px 5px 6px 5px;
	margin-top: 10px;
}

span.labkey-advanced-search {
	display: inline;
	padding: 2px 10px 0 0;
}

select {
	font-family: verdana, arial, helvetica, sans serif;
	font-size: 100%;
}
</style>
<script type="text/javascript">
    var studyMetadata = null;

    function populateSearchDropDowns()
    {
        populateDropDown("participantId", 'study', studyMetadata.SubjectNounSingular, studyMetadata.SubjectColumnName, 'Any ' + studyMetadata.SubjectNounSingular, '');
        populateDropDown('primaryType', 'study', 'SpecimenPrimaryType', 'Description', 'Any Primary Type', '');
        populateDropDown('derivativeType', 'study', 'SpecimenDerivative', 'Description', 'Any Derivative Type', '');
        populateDropDown('additiveType', 'study', 'SpecimenAdditive', 'Description', 'Any Additive Type', '');

        LABKEY.Query.selectRows({
            schemaName: 'study',
            queryName: 'Visit',
            columns: "SequenceNumMin,Label",
            sort: "DisplayOrder,Label",
            success: getDropDownPopulator('visit', 'Any Visit', '', function(row)
            {
                var text = row.Label ? row.Label : row.SequenceNumMin;
                return { text : text, value : row.SequenceNumMin };
            })
        });
    }

    function toggleSearchType(selectId)
    {
        var specimenSelected = selectId == 'specimen-search-selector';
        var deselectId = specimenSelected ? 'vial-search-selector' : 'specimen-search-selector';
        document.getElementById(selectId).className = 'youarehere';
        document.getElementById(deselectId).className = '';
        document.getElementById('globalUniqueIdLabel').style.display = specimenSelected ? 'none' : 'block';
        document.getElementById('globalUniqueIdChooser').style.display = specimenSelected ? 'none' : 'block';
        return false;
    }

    function addSearchParameter(params, paramName, paramElementId)
    {
        var value = getDropDownValue(paramElementId);
        if (value)
            params[paramName] = value;
    }

    function submitSearch()
    {
        var vialSearch = (document.getElementById('vial-search-selector').className == 'youarehere');
        var params = {
            showVials: vialSearch
        };

        var paramBase = vialSearch ? "SpecimenDetail." : "SpecimenSummary.";
        if (vialSearch)
        {
            var guid = document.getElementById('globalUniqueId').value;
            var compareType = getDropDownValue('globalUniqueId.compareType');
            if (compareType)
                params[paramBase + "GlobalUniqueId~" + compareType] = guid;
        }

        addSearchParameter(params, paramBase + studyMetadata.SubjectColumnName + '~eq', 'participantId');
        addSearchParameter(params, paramBase + 'PrimaryType/Description~eq', 'primaryType');
        addSearchParameter(params, paramBase + 'DerivativeType/Description~eq', 'derivativeType');
        addSearchParameter(params, paramBase + 'AdditiveType/Description~eq', 'additiveType');
        addSearchParameter(params, paramBase + 'Visit/SequenceNumMin~eq', 'visit');

        document.location = LABKEY.ActionURL.buildURL('study-samples', 'samples', LABKEY.ActionURL.getContainer(), params);
    }

    function verifySpecimenData()
    {
        var multi = new LABKEY.MultiRequest();
        var requestFailed = false;
        var errorMessages = [];

        multi.add(LABKEY.Query.selectRows, {schemaName:"study",
            queryName:"StudyProperties",
            success:function (result) {
                if (result.rows.length > 0)
                {
                    studyMetadata = result.rows[0];
                    Ext.get("participantIdLabel").update(studyMetadata.SubjectNounSingular);
                }
                else
                    errorMessages.push("<i>No study found in this folder</i>");
            },
            failure: function(result) {
                errorMessages.push("<i>Could not retrieve study information for this folder: " + result.exception);
            },
        columns:"*"});

        // Test query to verify that there's specimen data in this study:
        multi.add(LABKEY.Query.selectRows,
            {
                schemaName: 'study',
                queryName: 'SimpleSpecimen',
                maxRows: 1,
                success : function(data)
                {
                    if (data.rows.length == 0)
                         errorMessages.push('<i>No specimens found.</i>');
                },
                failure: function(result) {
                    errorMessages.push("<i>Could not retrieve specimen information for this folder: </i>" + result.exception);
                }
        });

        multi.send(function() {
            if (errorMessages.length > 0)
                document.getElementById('specimen-search-webpart-content').innerHTML = errorMessages.join("<br>");
            else
                populateSearchDropDowns();
        })
    }

    Ext.onReady(verifySpecimenData);
</script>
<!-- specimen search -->
<span id="specimen-search-webpart-content">
<div class="labkey-specimen-search-toggle">
    <a id="vial-search-selector" href="#"  class="youarehere" onclick="return toggleSearchType('vial-search-selector');">Individual Vials</a> |
    <a href="#" id="specimen-search-selector" onclick="return toggleSearchType('specimen-search-selector');">Grouped Vials</a><a
                                       onmouseover="return showHelpDivDelay(this, 'Grouped Vials', 'Vial group search returns a single row per subject, time point, and sample type.  These results may be easier to read and navigate, but lack vial-level detail.');"
                                       onmouseout="return hideHelpDivDelay();"
                                       onclick="return showHelpDiv(this, 'Grouped Vials', 'Vial group search returns a single row per subject, time point, and sample type.  These results may be easier to read and navigate, but lack vial-level detail.');"
                                       tabindex="-1"
                                       href="#"><span class="labkey-help-pop-up">?</span></a>
</div>
<div id="labkey-specimen-search">

    <form>
    <table>
        <tr>
            <td class="labkey-padright"><span id="globalUniqueIdLabel" style="display:block">Global Unique ID</span></td>
            <td>
                <span id="globalUniqueIdChooser" style="display:block">
                    <select id="globalUniqueId.compareType" onchange="document.getElementById('globalUniqueId').disabled = (this.value) ? false : true; document.getElementById('globalUniqueId').style.display = (this.value) ? 'block' : 'none';">
                        <option value="">Any Global Unique ID</option>
                        <option value="eq">Equals</option>
                        <option value="neq">Does Not Equal</option>
                        <option value="isblank">Is Blank</option>
                        <option value="isnonblank">Is Not Blank</option>
                        <option value="gt">Is Greater Than</option>
                        <option value="lt">Is Less Than</option>
                        <option value="gte">Is Greater Than or Equal To</option>
                        <option value="lte">Is Less Than or Equal To</option>
                        <option value="contains">Contains</option>
                        <option value="doesnotcontain">Does Not Contain</option>
                        <option value="doesnotstartwith">Does Not Start With</option>
                        <option value="startswith">Starts With</option>
                        <option value="in">Equals One Of (e.g. 'a;b;c')</option>
                    </select>&nbsp;<input type="text" size="30" id="globalUniqueId"  style="display:none" disabled>
                </span>
            </td>
        </tr>
        <tr>
            <td id="participantIdLabel" class="labkey-padright">Participant ID</td>
            <td>
                <select id="participantId">
                    <option value="">Loading...</option>
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-padright">Visit</td>
            <td>
                <select id="visit">
                    <option value="">Loading...</option>
                </select>
            </td>
        </tr>
        <tr>
            <td  class="labkey-padright">Primary Type</td>
            <td>
                <select id="primaryType">
                    <option value="">Loading...</option>
                </select>
            </td>
        </tr>
        <tr>
            <td  class="labkey-padright">Derivative Type</td>
            <td>
                <select id="derivativeType">
                    <option value="">Loading...</option>
                </select>
            </td>
        </tr>
        <tr>
            <td  class="labkey-padright">Additive Type</td>
            <td>
                <select id="additiveType">
                    <option value="">Loading...</option>
                </select>
            </td>
        </tr>
        <tr>
            <td></td>
            <td class="labkey-specimen-search-button">
                <a class="labkey-button" href="#" onclick="submitSearch(); return false;">
                    <span>Search</span>
                </a>
            </td>
        </tr>
    </table>
</form>
</div>
<!-- end specimen search -->

<!-- webpart footer -->
<div class="labkey-wp-footer">
<span class="labkey-advanced-search">Advanced Search:</span>
<a class="labkey-text-link" href="#" onClick="return clickLink('study-samples', 'showSearch', {showAdvanced: 'true', showVials: 'true'});">Individual Vials</a>
    <a class="labkey-text-link" href="#" onClick="return clickLink('study-samples', 'showSearch', {showAdvanced: 'true', showVials: 'false'});">Grouped Vials</a>
</div>
<!-- end webpart footer -->
</span>