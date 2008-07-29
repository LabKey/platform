<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ColumnInfo"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.List" %>
<%
    StudyController.ImportTypeForm form = (StudyController.ImportTypeForm)HttpView.currentModel();
    int colsPerRow = 6;
    String errors = PageFlowUtil.getStrutsError(request, "main");
    String decimalFormatHelp =
            "The following table has an abbreviated guide to pattern symbols:<br/>" +
            "<table class=\"labkey-format-helper\">" +
            "<tr class=\"labkey-format-helper-header\"><th align=left>Symbol<th align=left>Location<th align=left>Localized?<th align=left>Meaning</tr>" +
            "<tr valign=top><td><code>0</code><td>Number<td>Yes<td>Digit</tr>" +
            "<tr valign=top class=\"labkey-format-helper-alternate-row\"><td><code>#</code><td>Number<td>Yes<td>Digit, zero shows as absent</tr>" +
            "<tr valign=top><td><code>.</code><td>Number<td>Yes<td>Decimal separator or monetary decimal separator</tr>" +
            "<tr valign=top class=\"labkey-format-helper-alternate-row\"><td><code>-</code><td>Number<td>Yes<td>Minus sign</tr>" +
            "<tr valign=top><td><code>,</code><td>Number<td>Yes<td>Grouping separator</tr></table>";
    String dateFormatHelp = 
            "The following table has a partial guide to pattern symbols:<br/>" +
            "<table class=\"labkey-format-helper\">" +
            "<tr class=\"labkey-format-helper-header\"><th align=left>Letter<th align=left>Date or Time Component<th align=left>Examples</tr>" +
            "<tr><td><code>G</code><td>Era designator<td><code>AD</code></tr>" +
            "<tr class=\"labkey-format-helper-alternate-row\"><td><code>y</code><td>Year<td><code>1996</code>; <code>96</code></tr>" +
            "<tr><td><code>M</code><td>Month in year<td><code>July</code>; <code>Jul</code>; <code>07</code></tr>" +
            "<tr class=\"labkey-format-helper-alternate-row\"><td><code>w</code><td>Week in year<td><code>27</code></td></tr>" +
            "<tr><td><code>W</code><td>Week in month<td><code>2</code></td></tr>" +
            "<tr class=\"labkey-format-helper-alternate-row\"><td><code>D</code><td>Day in year<td><code>189</code></td></tr>" +
            "<tr><td><code>d</code><td>Day in month<td><code>10</code></tr>" +
            "<tr class=\"labkey-format-helper-alternate-row\"><td><code>F</code><td>Day of week in month<td><code>2</code></tr>" +
            "<tr><td><code>E</code><td>Day in week<td><code>Tuesday</code>; <code>Tue</code></tr>" +
            "<tr class=\"labkey-format-helper-alternate-row\"><td><code>a</code><td>Am/pm marker<td><code>PM</code></tr>" +
            "<tr><td><code>H</code><td>Hour in day (0-23)<td><code>0</code></tr>" +
            "<tr class=\"labkey-format-helper-alternate-row\"><td><code>k</code><td>Hour in day (1-24)<td><code>24</code></tr>" +
            "<tr><td><code>K</code><td>Hour in am/pm (0-11)<td><code>0</code></tr>" +
            "<tr class=\"labkey-format-helper-alternate-row\"><td><code>h</code><td>Hour in am/pm (1-12)<td><code>12</code></tr></table>";


%>
<%=errors%>
<p>
Use this form to define the properties of a dataset.
</p>
<%--
<p>
Paste in a tab delimited file with the following fields.  Additional fields will just be ignored.
</p>
<table class="labkey-bulk-import-data-">
    <tr>
        <th align="left"><u>Column Header</u></th>
        <th align="left"><u>Description</u></th>
        <th align="left"><u>Sample Value</u></th>
    </tr>
    <tr>
        <th align=left valign=top nowrap>Property<span style="color:red;">*</span></th>
        <td valign=top>The property name as it will appear when data is later imported</td>
        <td valign=top><code>PTID</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>Label</th>
        <td valign=top>Display Name</td>
        <td valign=top><code>Participant ID</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>ConceptURI</th>
        <td valign=top>The concept links to a definition</td>
        <td valign=top><code>SCHARP#Participant_ID</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>RangeURI<span style="color:red;">*</span></th>
        <td valign=top>The storage type of this value</td>
        <td valign=top><code>xsd:int</code></td>
    </tr>
    <tr>
        <th align="left" colspan="3"><span style="color:red;">*Required</span></th>
    </tr>
</table>
--%>
<table>
<%
    for (ObjectError e : (List<ObjectError>) form.getErrors().getAllErrors())
    {
        %><tr><td colspan=3><font class="labkey-error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
    }
%>
</table>

<form id="typeDefForm" name=typeDefForm action="defineDatasetType.post" method="POST" onsubmit="return doSubmit()" enctype="multipart/form-data">
    <input type=hidden name=create value="<%=form.isCreate()%>">
    <table id=typeDefTable class="labkey-type-def">
        <tr>
            <td >
            <table>
                <tr>
                    <td class=labkey-form-label>Short Dataset Name&nbsp;<%=helpPopup("Name", "Short unique name, e.g. 'DEM1'")%></td>
                    <td><input name="typeName" style="width:100%" value="<%=h(form.getTypeName())%>"></td>
                </tr>
                <tr>
                    <td class=labkey-form-label >Dataset Id <%=PageFlowUtil.helpPopup("Dataset Id", "The dataset id is an integer number that must be unique for all datasets in a study.")%></td>
                    <td><input id=datasetId type=text name=dataSetIdStr value="" <%=form.isAutoDatasetId() ? "disabled" : "" %> size=6>
                        <input type=checkbox name="autoDatasetId" <%=form.isAutoDatasetId() ? "checked" : "" %> value="true" onclick="toggleAutoDatasetId(this)">Define Dataset Id Automatically</td>
                </tr>
            </table>
            </td>
        </tr>
<%--        <tr>
            <td>
        <table id="typeEditorTable">
        < %-- UNDONE: generate this list from TableInfo (DatasetDefinition.getStandardPropertiesSet() --% >
        <tr>
        <td></td>
        <th>Field Name</th>
        <th>Friendly Name</th>
        <th>Data Type</th>
            <th>Required</th>
            <th>Description</th>
        </tr>

        <tr>
        <td>(required)</td>
        <td>PTID</td>
        <td>Participant Id</td>
        <td><%=ColumnInfo.getFriendlyTypeName(String.class)%></td>
            <td><input type=checkbox disabled checked></td>
        <td>Standard participant id</td>
        </tr>

        <tr>
        <td>(required)</td>
        <td>SequenceNum</td>
        <td>Sequence Num</td>
        <td><%=ColumnInfo.getFriendlyTypeName(Double.class)%></td>
            <td><input type=checkbox disabled checked></td>
        <td>A numeric sequence number (Visit Id) as defined in the study map.</td>
        </tr>

        <tr id="addRow">
        <td>[<a href="#" onclick="addField(this)">add field</a>]</td>
        </tr>
            </table>
            </td>
        </tr> --%>
        <tr>
            <td colspan=5><input type=image src="<%=PageFlowUtil.buttonSrc("Next")%>">&nbsp;<%= buttonLink("Cancel", "manageTypes.view") %></td>
        </tr>
    </table>
</form>

<script type="text/javascript">
var lastFieldNum = -1;
var typeDropDown;

function showTSV(src)
{
    document.getElementById("textAreaSpan").style.display = src.checked ? "" : "none";
//    document.getElementById("typeEditorTable").style.display = src.checked ? "none" : "";
}
        
function addField(src)
{
	var table = document.getElementById("typeDefTable");
	var rows = table.getElementsByTagName("TR");
	var newRow = createRow(++lastFieldNum);
    while (src.tagName.toLowerCase() != "tr")
        src = src.parentNode;

    src.parentNode.insertBefore(newRow, src);
	newRow.getElementsByTagName("INPUT")[0].focus();
    return false;
}

function createRow(fieldNum)
{
	var row = document.createElement("TR");
	row.appendChild(cell("[<a href='#' onclick='deleteRow(this)'>delete</a>]"));
	row.appendChild(cell(nameInput(fieldNum)));
	row.appendChild(cell(labelInput(fieldNum)));
	row.appendChild(cell(typeSelect(fieldNum)));
    row.appendChild(cell(requiredCheckbox(fieldNum)));
    row.appendChild(cell(descInput(fieldNum)));

    return row;
}

function deleteRow(src)
{
	while(src.tagName.toLowerCase() != "tr")
		src = src.parentNode;

	src.parentNode.removeChild(src);
}

function cell(content)
{
	var cell = document.createElement("TD");
    if (typeof content == "string")
		cell.innerHTML = content;
	else
		cell.appendChild(content);
	return cell;
}

function text(str)
{
	return document.createTextNode(str);
}

function setLabel(event)
{
	if (!event) //For IE
		event = window.event;
	var fieldNameElem = event.target;
	if (!fieldNameElem)
		fieldNameElem = event.srcElement;

	var fieldLabelElemName = fieldNameElem.name.replace("name", "label");
	var fieldName = fieldNameElem.value;
	if (fieldName == null || fieldName == "")
		return false;

	if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(fieldName))
	{
		alert("Field names must start with a letter and contain only letters and numbers");
		fieldNameElem.focus();
		return false;
	}

	var fieldLabelElem = document.typeDefForm[fieldLabelElemName];
	var fieldLabel = fieldLabelElem.value;
	if (fieldLabel == null || fieldLabel == "")
	{
		fieldLabelElem.value = fieldNameElem.value;
	}
    return true;
}

function nameInput(index)
{
	var elem = input(index, "name");
	elem.onchange = setLabel;
	return elem;
}

function labelInput(index)
{
	return input(index, "label");
}

function descInput(index)
{
    var elem = input(index, "description");
    elem.size = 40;
    return elem;
}

function requiredCheckbox(index)
{
    var elem = input(index, "required");
    elem.type = "checkbox";
    return elem;
}

function input(index, fieldName)
{
	var elem = document.createElement("INPUT");
	elem.name = "fields[" + index +"]." + fieldName;
	return elem;
}

function typeSelect(index)
{
    if (null == typeDropDown)
    {
        typeDropDown = document.createElement("SELECT");

        var options = new Object();
        options["xsd:string"] = "<%=ColumnInfo.getFriendlyTypeName(String.class)%>";
        options["xsd:double"] = "<%=ColumnInfo.getFriendlyTypeName(Double.class)%>";
        options["xsd:int"] = "<%=ColumnInfo.getFriendlyTypeName(Integer.class)%>";
        options["xsd:dateTime"] = "<%=ColumnInfo.getFriendlyTypeName(Date.class)%>";
        options["xsd:boolean"] = "<%=ColumnInfo.getFriendlyTypeName(Boolean.class)%>";
        for (name in options)
        {
            var option = document.createElement("OPTION");
            option.value = name;
            option.text = options[name];
            typeDropDown.options[typeDropDown.options.length] = option;
        }
    }

    var elem = typeDropDown.cloneNode(true);
	elem.name = "fields[" + index +"].type";
	return elem;
}

function getNameValue(i)
{
	var elem = document.typeDefForm["fields[" + i +"].name"];
	return null == elem ? null : elem.value;
}

function getLabelValue(i)
{
	var elem = document.typeDefForm["fields[" + i +"].label"];
	return null == elem ? null : elem.value;
}

function getDescValue(i)
{
	var elem = document.typeDefForm["fields[" + i +"].description"];
	return null == elem ? null : elem.value;
}

function getRequiredValue(i)
{
    var elem = document.typeDefForm["fields[" + i +"].required"];
    return null == elem ? null : elem.checked;

}

function getTypeValue(i)
{
	var elem = document.typeDefForm["fields[" + i +"].type"];
	if (elem.tagName.toLowerCase() == "select")
		return null == elem ? null : elem.options[elem.selectedIndex].value;
	else
		return elem.value;
}

function toggleAutoDatasetId(ck)
{
    var datasetIdInput = document.getElementById("datasetId");
    datasetIdInput.value = "";
    datasetIdInput.disabled = ck.checked;
}

function doSubmit()
{
/*
    var fieldNames = new Object();
	var formElem = document.typeDefForm;
    if (formElem.noscript.checked)
        return true;

    var str = "Property\tLabel\tRangeURI\tNotNull\tDescription\n";
	var table = document.getElementById("typeEditorTable");
	var rows = table.getElementsByTagName("TR");
	for (var i = 0; i <= lastFieldNum; i++)
	{
		var name = getNameValue(i);
		if (null == name || "" == name.replace(" ", ""))
			continue;

        if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(name))
        {
            alert(name + " is not a valid field name. Field names must start with a letter and contain only letters and numbers");
            var elem = document.typeDefForm["fields[" + i +"].name"];
            if (null != elem)
                elem.focus();
            return false;
        }

        if (fieldNames[name])
		{
			alert("Duplicate field name: " + name);
			return false;
		}
		fieldNames[name] = true;

		var labelValue = getLabelValue(i);
		if (null == labelValue || "" == labelValue.replace(" ", ""))
			labelValue = name;

        str += name + "\t" + labelValue + "\t" + getTypeValue(i) + "\t" + getRequiredValue(i) + "\t" + getDescValue(i) +"\n";
	}
	document.getElementById("tsvTextArea").value = str;
    return true;
*/
}
</script>