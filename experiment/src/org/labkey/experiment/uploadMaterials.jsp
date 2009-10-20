<%
/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.exp.api.ExpSampleSet"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.samples.UploadMaterialSetForm" %>

<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<UploadMaterialSetForm> view = (JspView<UploadMaterialSetForm>) HttpView.currentView();
    UploadMaterialSetForm form = view.getModelBean();
    ExpSampleSet sampleSet = form.getSampleSet();
%>

<form onSubmit="return validateKey();" action="showUploadMaterials.view" method="post">
<labkey:errors />
<table>
    <tr>
        <td class="labkey-form-label">Name</td>
        <td>
            <% if (form.isImportMoreSamples() || form.getNameReadOnly()) {  %>
                <input type="hidden" name="importMoreSamples" value="<%=h(form.isImportMoreSamples())%>"/>
                <input type="hidden" name="nameReadOnly" value="<%=h(form.getNameReadOnly())%>"/>
                <input id="name" type="hidden" name="name" value="<%=h(form.getName())%>"><%= h(form.getName())%>
            <% }
            else
            { %>
                <input id="name" type="text" name="name" value="<%=h(form.getName())%>">
            <% }%>
        </td>
    </tr>
    <% if (form.isImportMoreSamples()) { %>
        <tr>
            <td class="labkey-form-label">Update Options</td>
            <td>This sample set already exists.  Please choose how the uploaded samples should be merged with the existing samples.<br>
                <labkey:radio name="overwriteChoice" id="ignoreOverwriteChoice" value="<%=UploadMaterialSetForm.OverwriteChoice.ignore%>" currentValue="<%=form.getOverwriteChoice()%>" /> Skip new samples that already exist.<br>
                <labkey:radio name="overwriteChoice" id="replaceOverwriteChoice" value="<%=UploadMaterialSetForm.OverwriteChoice.replace%>" currentValue="<%=form.getOverwriteChoice()%>" /> Replace existing samples with new ones.<br>
            </td>
        </tr>
    <% } %>
    <tr>
        <td class="labkey-form-label">Sample Set Data</td>
        <td>
            Sample set uploads must formatted as tab separated values (TSV). Copy/paste from Microsoft Excel works well.<br>
            The first row should contain column names, and subsequent rows should contain the data.<br>
            <textarea id="textbox" onchange="updateIds(this)" rows=25 cols="120" style="width: 100%;" name="data" wrap="off"><%=h(form.getData())%></textarea>
            <script type="text/javascript">
                Ext.EventManager.on('textbox', 'keydown', handleTabsInTextArea);
            </script>
        </td>
    </tr>
    <tr>
        <td class="labkey-form-label">Id Columns<%= helpPopup("Id Columns", "Id columns must form a unique key for every row.")%></td>
        <td>
                <% if (form.isImportMoreSamples() && sampleSet != null && sampleSet.hasIdColumns())
                { %>
                    <%= h(sampleSet.getIdCol1().getName()) %><%
                    if (sampleSet.getIdCol2() != null)
                    {
                        %>, <%= h(sampleSet.getIdCol2().getName()) %><%
                    }
                    if (sampleSet.getIdCol3() != null)
                    {
                        %>, <%= h(sampleSet.getIdCol3().getName()) %><%
                    }
                }
                else
                { %>
                <table>
                    <tr>
                        <td align="right">#1:</td>
                        <td>
                            <select id="idCol1" name="idColumn1" >
                                <labkey:options value="<%=form.getIdColumn1()%>" map="<%=form.getKeyOptions(false)%>" />
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>#2 (if needed):</td>
                        <td>
                            <select id="idCol2" name="idColumn2">
                                <labkey:options value="<%=form.getIdColumn2()%>" map="<%=form.getKeyOptions(true)%>" />
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>#3 (if needed):</td>
                        <td>
                            <select id="idCol3" name="idColumn3">
                                <labkey:options value="<%=form.getIdColumn3()%>" map="<%=form.getKeyOptions(true)%>" />
                            </select>
                        </td>
                    </tr>
                </table>
            <% } %>
        </td>
    </tr>
    <tr>
        <td class="labkey-form-label">Parent Column<%= helpPopup("Parent Column", "The column that name of a parent sample that is visible from this folder. Parent samples are automatically linked to child samples. You may comma separate the names if a sample has more than one parent.")%></td>
        <td>
            <% if (form.isImportMoreSamples() && sampleSet != null && sampleSet.getParentCol() != null)
            { %>
                <%= h(sampleSet.getParentCol().getName())%>
            <% }
            else
            { %>
            <select id="parentCol" name="parentColumn">
                <labkey:options value="<%=form.getParentColumn()%>" map="<%=form.getKeyOptions(true)%>" />
            </select>
            <% } %>
        </td>
    </tr>
    <tr>
        <td/>
        <td>
            <%=PageFlowUtil.generateSubmitButton("Submit")%>
            <%=PageFlowUtil.generateButton("Clear", "", "javascript:clearValues()")%></td>
    </tr>

</table>

<div style="display:none" id="uploading"><blink>Please wait while data is uploaded.</blink></div>
</form>
<script type="text/javascript">
var fields = new Array();

function updateIdSelect(select, header, allowBlank)
{
    if (select == null)
    {
        return;
    }
    var selectedIndex = select.selectedIndex;
    select.options.length = 0;
    if (header.length == 0)
    {
        var option = new Option("<Paste sample set data, then choose a field>", 0);
        select.options[select.options.length] = option;
        return;
    }
    if (allowBlank)
    {
        var option = new Option("", -1);
        select.options[select.options.length] = option;
    }
    for (var i = 0; i < header.length; i ++)
    {
        var option = new Option(header[i] == "" ? "column" + i : header[i], i);
        select.options[select.options.length] = option;
    }
    if (selectedIndex < select.options.length)
    {
        select.selectedIndex = selectedIndex;
    }
}
function updateIds(textbox)
{
var txt = textbox.value.trim();
var rows = txt.split("\n");
var header = [];
fields = new Array();
if (rows.length >= 2)
  {
  for (var i = 0; i < rows.length; i++)
    {
    fields[i] = rows[i].split("\t");
    }
    header = fields[0];
  }

updateIdSelect(document.getElementById("idCol1"), header, false);
updateIdSelect(document.getElementById("idCol2"), header, true);
updateIdSelect(document.getElementById("idCol3"), header, true);
updateIdSelect(document.getElementById("parentCol"), header, true);
}

function clearValues()
{
    var textbox = document.getElementById("textbox");
    textbox.value = "";
    updateIds(textbox);
}

function validateKey()
{
var name = document.getElementById("name").value;
if (!(name != null && name.trim().length > 0))
{
    alert("Name is required");
    return false;
}

var text = document.getElementById("textbox");
if (text.value.match("/^\\s*\$/"))
{
    alert("Please paste data in text field.");
    return false;
}
if (fields == null || fields.length < 2)
{
    alert("Please paste data with at least a header and one row of data.");
    return false;
}
var select1 = document.getElementById("idCol1");
var col1 = -1;
var col2 = -1;
var col3 = -1;
var col1Name;
var col2Name;
var col3Name;
if (select1)
{
    col1 = parseInt(select1.options[select1.selectedIndex].value);
    col1Name = select1.options[select1.selectedIndex].text;
    var select2 = document.getElementById("idCol2");
    col2 = parseInt(select2.options[select2.selectedIndex].value);
    col2Name = select2.options[select2.selectedIndex].text;
    var select3 = document.getElementById("idCol3");
    col3 = parseInt(select3.options[select3.selectedIndex].value);
    col3Name = select3.options[select3.selectedIndex].text;

    if (col2 != -1 && col1 == col2)
    {
        alert("You cannot use the same id column twice.");
        return false;
    }
    if (col3 != -1 && (col1 == col3 || col2 == col3))
    {
        alert("You cannot use the same id column twice.");
        return false;
    }
    // Check if they selected a column 3 but not a column 2
    if (col3 != -1 && col2 == -1)
    {
        col2 = col3;
        col3 = -1;
    }
}
else
{
    col1Name = '<%= h(sampleSet == null || sampleSet.getIdCol1() == null ? "" : sampleSet.getIdCol1().getName())%>';
    col2Name = '<%= h(sampleSet == null || sampleSet.getIdCol2() == null ? "" : sampleSet.getIdCol2().getName())%>';
    col3Name = '<%= h(sampleSet == null || sampleSet.getIdCol3() == null ? "" : sampleSet.getIdCol3().getName())%>';
    for (var col = 0; col < fields[0].length; col++)
    {
        if (fields[0][col] == '<%= h(sampleSet == null || sampleSet.getIdCol1() == null ? "" : sampleSet.getIdCol1().getName())%>')
        {
            col1 = col;
        }
        if (fields[0][col] == '<%= h(sampleSet == null || sampleSet.getIdCol2() == null ? "" : sampleSet.getIdCol2().getName())%>')
        {
            col2 = col;
        }
        if (fields[0][col] == '<%= h(sampleSet == null || sampleSet.getIdCol3() == null ? "" : sampleSet.getIdCol3().getName())%>')
        {
            col3 = col;
        }
    }
    if (col1Name != '' && col1 == -1)
    {
        alert("You must include the Id column '" + col1Name + "' in your data.");
        return false;
    }
    if (col2Name != '' && col2 == -1)
    {
        alert("You must include the Id column '" + col2Name + "' in your data.");
        return false;
    }
    if (col3Name != '' && col3 == -1)
    {
        alert("You must include the Id column '" + col3Name + "' in your data.");
        return false;
    }
}

var hash = new Object();
for (var i = 1; i < fields.length; i++)
{
    var val1 = fields[i][col1];
    if (!val1 || "" == val1)
    {
        alert("All samples must include a value in the '" + col1Name + "' column.");
        return false;
    }

    var val = "" + val1;
    if (col2 >= 0)
    {
        var val2 = fields[i][col2];
        if (!val2 || "" == val2)
        {
            alert("All samples must include a value in the '" + col2Name + "' column.");
            return false;
        }
        val = val + "-" + val2;
    }
    if (col3 >= 0)
    {
        var val3 = fields[i][col3];
        if (!val3 || "" == val3)
        {
            alert("All samples must include a value in the '" + col3Name + "' column.");
            return false;
        }
        val = val + "-" + val3;
    }
    if (hash[val])
    {
        alert("The ID columns chosen do not form a unique key. The key " + val + " is used more than once.");
        return false;
    }
    hash[val]= true;
}
document.getElementById("uploading").style.display = "";
return true;
}
updateIds(document.getElementById("textbox"));
</script>
