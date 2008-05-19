<%
/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.experiment.samples.UploadMaterialSetForm"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>

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
        <td class="ms-searchform">Name</td>
        <td class="ms-vb">
            <% if (form.getNameReadOnly()) { %>
                <input type="hidden" name="nameReadOnly" value="true" />
                <input type="hidden" name="name" value="<%=h(form.getName())%>"><%= h(form.getName())%>
            <% }
            else
            { %>
                <input id="name" type="text" name="name" value="<%=h(form.getName())%>">
            <% }%>
        </td>
    </tr>
    <% if (sampleSet != null) { %>
        <tr>
            <td class="ms-searchform">Update Options</td>
            <td class="ms-vb">This dataset already exists.  Please choose how the uploaded samples should be merged with the existing samples.<br>
                <labkey:radio name="overwriteChoice" value="<%=UploadMaterialSetForm.OverwriteChoice.ignore%>" currentValue="<%=form.getOverwriteChoice()%>" /> Skip new samples that already exist.<br>
                <labkey:radio name="overwriteChoice" value="<%=UploadMaterialSetForm.OverwriteChoice.replace%>" currentValue="<%=form.getOverwriteChoice()%>" /> Replace existing samples with new ones.<br>
            </td>
        </tr>
    <% } %>
    <tr>
        <td class="ms-searchform">Sample Set Data</td>
        <td class="ms-vb">
            Sample set uploads must formatted as tab separated values (TSV). Copy/paste from Microsoft Excel works well.<br>
            The first row should contain column names, and subsequent rows should contain the data.<br>
            <textarea id="textbox" onchange="updateIds(this)" rows=25 cols="120" style="width: 100%;" name="data" wrap="off"><%=h(form.getData())%></textarea>
        </td>
    </tr>
    <tr>
        <td class="ms-searchform">Id Columns<%= helpPopup("Id Columns", "Id columns must form a unique key for every row.")%></td>
        <td class="ms-vb">
                <% if (sampleSet != null)
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
                #1:
                <select id="idCol1" name="idColumn1" >
                    <labkey:options value="<%=form.getIdColumn1()%>" map="<%=form.getKeyOptions(false)%>"/>
                </select><br>
                #2 (if needed):<select id="idCol2" name="idColumn2">
                    <labkey:options value="<%=form.getIdColumn2()%>" map="<%=form.getKeyOptions(true)%>" />
                </select><br>
                #3 (if needed):<select id="idCol3" name="idColumn3">
                    <labkey:options value="<%=form.getIdColumn3()%>" map="<%=form.getKeyOptions(true)%>" />
                </select>
            <% } %>
        </td>
    <tr>
        <td></td>
        <td><input type="image" src="<%=PageFlowUtil.submitSrc()%>"> <a href="javascript:clearValues()"><img src="<%= PageFlowUtil.buttonSrc("Clear") %>" alt="Clear"></a></td>
    </tr>

</table>

<div style="display:none" id="uploading"><blink>Please Wait While Data is Uploaded.</blink></div>
</form>
<script>
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
        if (!allowBlank)
        {
            var option = new Option("<Paste Data, then choose id field>", 0);
            select.options[select.options.length] = option;
            return;
        }
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
}

function clearValues()
{
    var textbox = document.getElementById("textbox");
    textbox.value = "";
    updateIds(textbox);
}

function validateKey()
{
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
var col1 = parseInt(select1.options[select1.selectedIndex].value);
var select2 = document.getElementById("idCol2");
var col2 = parseInt(select2.options[select2.selectedIndex].value);
var select3 = document.getElementById("idCol3");
var col3 = parseInt(select3.options[select3.selectedIndex].value);

var hash = new Object();
for (var i = 1; i < fields.length; i++)
  {
  var val = "" + fields[i][col1];
  if (col2 >= 0)
  {
    val = val + "-" + fields[i][col2];
  }
  if (col3 >= 0)
  {
    val = val + "-" + fields[i][col3];
  }
  if (hash[val])
    {
    alert("The ID columns chosen do not form a unique key:: " + val);
    return false;
    }
  hash[val]= true;
  }
  document.getElementById("uploading").style.display = "";
  return true;
}
updateIds(document.getElementById("textbox"));
</script>
