<%
/*
 * Copyright (c) 2005-2015 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.data.ColumnInfo"%>
<%@ page import="org.labkey.api.exp.api.ExpSampleSet"%>
<%@ page import="org.labkey.api.exp.api.ExperimentUrls"%>
<%@ page import="org.labkey.api.exp.property.DomainProperty" %>
<%@ page import="org.labkey.api.exp.query.ExpMaterialTable" %>
<%@ page import="org.labkey.api.exp.query.SamplesSchema" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.query.QueryUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="org.labkey.experiment.samples.UploadMaterialSetForm" %>
<%@ page import="org.labkey.experiment.samples.UploadMaterialSetForm.InsertUpdateChoice" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<UploadMaterialSetForm> view = (JspView<UploadMaterialSetForm>) HttpView.currentView();
    UploadMaterialSetForm form = view.getModelBean();
    ExpSampleSet sampleSet = form.getSampleSet();

    ActionURL templateURL = PageFlowUtil.urlProvider(QueryUrls.class).urlCreateExcelTemplate(getContainer(), SamplesSchema.SCHEMA_NAME, form.getName());
%>

<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext3"));
        resources.add(ClientDependency.fromPath("FileUploadField.js"));
        return resources;
    }

    public String getDisplayName(DomainProperty prop)
    {
        if (prop.getLabel() != null)
            return prop.getLabel();
        return ColumnInfo.labelFromName(prop.getName());
    }

    public String dumpDomainProperty(DomainProperty dp)
    {
        if (dp == null)
            return "null";

        String displayName = getDisplayName(dp);

        StringBuilder sb = new StringBuilder("{");
        sb.append("name : ").append(q(dp.getName())).append(",\n");
        sb.append("label : ").append(q(displayName)).append(",\n");

        sb.append("aliases : [");
        sb.append(q(dp.getName())).append(", ");
        sb.append(q(displayName)).append(", ");
        for (String alias : dp.getImportAliasSet())
            sb.append(alias).append(", ");
        sb.append(q(dp.getPropertyURI()));
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    public String dumpSampleSet(ExpSampleSet ss)
    {
        if (ss == null)
            return "null";

        StringBuilder sb = new StringBuilder("{");
        if (ss.hasNameAsIdCol())
        {
            sb.append("col1 : {");
            sb.append("name: '").append(ExpMaterialTable.Column.Name).append("',\n");
            sb.append("label: '").append(ExpMaterialTable.Column.Name).append("',\n");
            sb.append("aliases: [ '").append(ExpMaterialTable.Column.Name).append("' ]");
            sb.append("},\n");

            sb.append("col2 : null,\n");
            sb.append("col3 : null\n");
        }
        else
        {
            sb.append("col1 : ").append(dumpDomainProperty(ss.getIdCol1())).append(",\n");
            sb.append("col2 : ").append(dumpDomainProperty(ss.getIdCol2())).append(",\n");
            sb.append("col3 : ").append(dumpDomainProperty(ss.getIdCol3())).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
%>
<style type="text/css">
    #upload-field .x-form-field-wrap,
    #upload-field #upload-run-field-file {
        width: 212px !important;
    }
</style>
<labkey:errors />
<p>If you have an existing sample set definition in the XAR file format (a .xar or .xar.xml file), you can
    <a href="<%= urlProvider(ExperimentUrls.class).getUploadXARURL(getContainer()) %>">upload the XAR file directly</a>
    or place the file in this folder's pipeline directory and import using the
    <a href="<%= urlProvider(PipelineUrls.class).urlBrowse(getContainer(), getActionURL()) %>">Data Pipeline</a>.
</p>
<labkey:form id="sampleSetUploadForm" action="<%=h(buildURL(ExperimentController.ShowUploadMaterialsAction.class))%>" method="POST">
<table>
    <tr>
        <td class="labkey-form-label" width="100">Name</td>
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
            <td class="labkey-form-label">Insert/Update Options</td>
            <td>This sample set already exists.  Please choose how the uploaded samples should be merged with the existing samples.<br>
                <labkey:radio name="insertUpdateChoice" id="insertOnlyChoice" value="<%=InsertUpdateChoice.insertOnly%>" currentValue="<%=form.getInsertUpdateChoice()%>" /> <label for="insertOnlyChoice">Insert only new samples; error if trying to update an existing sample.</label><br>
                <labkey:radio name="insertUpdateChoice" id="insertIgnoreChoice" value="<%=InsertUpdateChoice.insertIgnore%>" currentValue="<%=form.getInsertUpdateChoice()%>" /> <label for="insertIgnoreChoice">Insert only new samples; ignore any existing samples.</label><br>
                <labkey:radio name="insertUpdateChoice" id="insertOrUpdateChoice" value="<%=InsertUpdateChoice.insertOrUpdate%>" currentValue="<%=form.getInsertUpdateChoice()%>" /> <label for="insertOrUpdateChoice">Insert any new samples and update existing samples.</label><br>
                <labkey:radio name="insertUpdateChoice" id="updateOnlyChoice" value="<%=InsertUpdateChoice.updateOnly%>" currentValue="<%=form.getInsertUpdateChoice()%>" /> <label for="updateOnlyChoice">Update only existing samples with new values; error if sample doesn't already exist.</label><br>

                <br>
                By default, any additional columns in the uploaded sample data will be ignored.<br>
                <labkey:checkbox name="createNewColumnsOnExistingSampleSet" id="createNewColumnsOnExistingSampleSet" value="true" />
                <label for="createNewColumnsOnExistingSampleSet">Add any new columns found in the uploaded sample data to the existing sample set columns.</label>
            </td>
        </tr>
    <% } %>
    <tr>
        <td class="labkey-form-label">Upload Type</td>
        <td>
            <input type="radio" name="uploadType" value="paste" checked="checked" onchange="disableFileUpload()">Cut/Paste
            <input type="radio" name="uploadType" value="file" onchange="enableFileUpload()">File
        </td>
    </tr>
    <tr id="file-field" style="display: none;">
        <td class="labkey-form-label">Upload File</td>
        <td id="upload-field"></td>
    </tr>
    <tr id="sampleSetData">
        <td class="labkey-form-label">Sample Set Data</td>
        <td>
            Sample set uploads must formatted as tab separated values (TSV).<br>
            The first row should contain column names; subsequent rows should contain the data.<br>
            Copy/paste from Microsoft Excel works well. <% if (sampleSet != null) { %><%=textLink("Download an Excel template workbook", templateURL)%><% } %><br>
            <% if (!form.isImportMoreSamples()) { %>
                <b>Note:</b> If there is a <em>'Name'</em> column, it will be used as each sample's unique identifier.
                <br>
            <% } %>
            <br>
            <textarea id="textbox" rows=25 cols="120" style="width: 100%;" name="data" wrap="off"><%=h(form.getData())%></textarea>
            <script type="text/javascript">LABKEY.Utils.tabInputHandler('#textbox');</script>
        </td>
    </tr>
    <tr>
        <td class="labkey-form-label">Id Columns<%= helpPopup("Id Columns", "Id columns must form a unique key for every row.")%></td>
        <td>
                <% if (form.isImportMoreSamples() && sampleSet != null && sampleSet.hasIdColumns())
                {
                    if (sampleSet.hasNameAsIdCol())
                    {
                        %><%=ExpMaterialTable.Column.Name%><%
                    }
                    else
                    {
                        %><%= h(getDisplayName(sampleSet.getIdCol1())) %><%
                        if (sampleSet.getIdCol2() != null)
                        {
                            %>, <%= h(getDisplayName(sampleSet.getIdCol2())) %><%
                        }
                        if (sampleSet.getIdCol3() != null)
                        {
                            %>, <%= h(getDisplayName(sampleSet.getIdCol3())) %><%
                        }
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
        <td class="labkey-form-label">Parent Column<%= helpPopup("Parent Column", "The column that provides the name of a parent sample that is visible from this folder. Parent samples are automatically linked to child samples. You may comma separate the names if a sample has more than one parent.")%></td>
        <td>
            <% if (form.isImportMoreSamples() && sampleSet != null && sampleSet.getParentCol() != null)
            { %>
                <%= h(getDisplayName(sampleSet.getParentCol()))%>
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
        <td></td>
        <td>
            <%= button("Submit").submit(true).id("submit-form-btn") %>
            <%= button("Clear").id("clear-form-btn") %>
        </td>
    </tr>
    <input type="hidden" name="tsvData" value=""/>
</table>
</labkey:form>
<div style="display:none" id="uploading">Please wait while data is uploaded.</div>
<script type="text/javascript">

function disableFileUpload() {
    Ext.get('file-field').setDisplayed('none');
    Ext.get('sampleSetData').setDisplayed('table-row');
    Ext.getCmp('upload-run-field').disable();
}

function enableFileUpload() {
    Ext.get('file-field').setDisplayed('table-row');
    Ext.get('sampleSetData').setDisplayed('none');
    Ext.getCmp('upload-run-field').enable();
}

function runner() {
    console.log('called runner!');
    return true;
}

(function($) {

    var fields = [];
    var header = [];
    var nameColIndex = -1;
    var sampleSet = <%=dumpSampleSet(sampleSet)%>;
    var btnText = 'Upload TSV, XLS, or XLSX File...';

    var clearValues = function() {
        var textbox = document.getElementById("textbox");
        textbox.value = "";
        updateIds(textbox.value);

        var fileField = Ext.getCmp('upload-run-field');
        if (fileField) {
            fileField.reset();
            fileField.button.setText(btnText);
        }
    };

    var getNameColIndex = function() {
        nameColIndex = -1;
        for (var i = 0; i < header.length; i++)
        {
            if (header[i].toLowerCase() == "name")
            {
                nameColIndex = i;
                break;
            }
        }
    };

    var onTextAreaChange = function(event, textbox) {
        updateIds(textbox.value.trim());
    };

    var updateIds = function(txt) {
        var rows = txt.trim().split("\n");
        header = [];
        fields = [];
        if (rows.length >= 2)
        {
            for (var i = 0; i < rows.length; i++)
            {
                fields[i] = rows[i].split("\t");
            }
            header = fields[0];
        }

        getNameColIndex();
        updateIdSelects();
    };

    var updateIdSelect = function(select, header, allowBlank) {
        if (!select) {
            return;
        }

        var selectedIndex = select.selectedIndex;
        select.options.length = 0;

        if (header.length == 0)
        {
            var option = new Option("<Paste sample set data, then choose a field>", 0);
            select.options[select.options.length] = option;
            select.disabled = false;
            return;
        }
        if (allowBlank)
        {
            var option = new Option("", -1);
            select.options[select.options.length] = option;
        }

        for (var i = 0; i < header.length; i ++)
        {
            if (header[i].toLowerCase() == "name")
            {
                // Select the 'Name' column for idCol1, unselect for idCol2 and idCol3
                if (select.id == "idCol1")
                    selectedIndex = select.options.length;
                else if (select.id == "idCol2" || select.name == "idCol3")
                    selectedIndex = -1;
            }

            var option = new Option(header[i] == "" ? "column" + i : header[i], i);
            select.options[select.options.length] = option;
        }
        if (selectedIndex < select.options.length)
        {
            select.selectedIndex = selectedIndex;
        }

        // Enable/disable idCol1, idCol2, and idCol3 if "Name" column is present
        if (select.id in {idCol1:true, idCol2:true, idCol3:true})
            select.disabled = nameColIndex != -1;
    };

    var updateIdSelects = function() {
        updateIdSelect(document.getElementById("idCol1"), header, false);
        updateIdSelect(document.getElementById("idCol2"), header, true);
        updateIdSelect(document.getElementById("idCol3"), header, true);
        updateIdSelect(document.getElementById("parentCol"), header, true);
    };

    var updateIdsWithData = function(data) {
        header = [];
        fields = [];
        if (data.length >= 2) // why?
        {
            for (var i = 0; i < data.length; i++)
            {
                fields[i] = data[i];
            }
            header = fields[0];
        }

        getNameColIndex();
        updateIdSelects();
    };

    var validateKey = function() {
        console.log('called validate key!');
        var name = document.getElementById("name").value;

        <% if (form.isImportMoreSamples()) { %>
        var insertOnlyChoice = document.getElementById("insertOnlyChoice");
        var insertIgnoreChoice = document.getElementById("insertIgnoreChoice");
        var insertOrUpdateChoice = document.getElementById("insertOrUpdateChoice");
        var updateOnlyChoice = document.getElementById("updateOnlyChoice");
        if (!(insertOnlyChoice.checked || insertIgnoreChoice.checked || insertOrUpdateChoice.checked || updateOnlyChoice.checked))
        {
            alert("Please select how to deal with duplicates by selecting one of the insert/update options.");
            return false;
        }
        <% } %>

        if (fields == null || fields.length < 2)
        {
            alert("Please paste data with at least a header and one row of data.");
            return false;
        }
        var select1 = document.getElementById("idCol1");
        var select2 = document.getElementById("idCol2");
        var select3 = document.getElementById("idCol3");
        var colIndex = [ -1, -1, -1 ];
        var colNames = [ '', '', '' ];
        if (select1)
        {
            if (nameColIndex != -1)
            {
                colIndex[0] = nameColIndex;
                colNames[0] = header[nameColIndex];
            }
            else
            {
                colIndex[0] = parseInt(select1.options[select1.selectedIndex].value);
                colNames[0] = select1.options[select1.selectedIndex].text;

                colIndex[1] = parseInt(select2.options[select2.selectedIndex].value);
                colNames[1] = select2.options[select2.selectedIndex].text;

                colIndex[2] = parseInt(select3.options[select3.selectedIndex].value);
                colNames[2] = select3.options[select3.selectedIndex].text;
            }

            if (colIndex[1] != -1 && colIndex[0] == colIndex[1])
            {
                alert("You cannot use the same id column twice.");
                return false;
            }
            if (colIndex[2] != -1 && (colIndex[0] == colIndex[2] || colIndex[1] == colIndex[2]))
            {
                alert("You cannot use the same id column twice.");
                return false;
            }
            // Check if they selected a column 3 but not a column 2
            if (colIndex[2] != -1 && colIndex[1] == -1)
            {
                colIndex[1] = colIndex[2];
                colIndex[2] = -1;
            }
        }
        else
        {
            if (sampleSet != null)
            {
                for (var colNum = 0; colNum < 3; colNum++)
                {
                    var sampleSetCol = sampleSet["col" + (colNum+1)];
                    if (!sampleSetCol)
                        continue;

                    colNames[colNum] = sampleSetCol.label;

                    for (var col = 0; col < header.length; col++)
                    {
                        var heading = header[col];
                        if (!heading)
                            continue;
                        heading = heading.toLowerCase();
                        for (var aliasIndex = 0; aliasIndex < sampleSetCol.aliases.length; aliasIndex++)
                        {
                            var alias = sampleSetCol.aliases[aliasIndex];
                            if (alias && alias.toLowerCase() == heading)
                            {
                                colIndex[colNum] = col;
                                break;
                            }
                        }
                    }

                    if (colIndex[colNum] == -1)
                    {
                        alert("You must include the Id column '" + colNames[colNum] + "' in your data.");
                        return false;
                    }
                }
            }
        }

        var hash = {};
        for (var i = 1; i < fields.length; i++)
        {
            var val = undefined;
            for (var colNum = 0; colNum < 3; colNum++)
            {
                var index = colIndex[colNum];
                if (colNum > 0 && index == -1)
                    continue;
                var colVal = fields[i][index];
                if (!colVal || "" == colVal)
                {
                    alert("All samples must include a value in the '" + colNames[colNum] + "' column.");
                    return false;
                }

                val = (colNum == 0 ? colVal : val + "-" + colVal);
            }

            if (hash[val])
            {
                alert("The ID columns chosen do not form a unique key. The key " + val + " is on rows " + hash[val] + " and " + i + ".");
                return false;
            }
            hash[val] = i;
        }

        // As the last step, make sure we don't post the select2 and select3 values
        if (select1 && nameColIndex > -1)
        {
            select1.selectedIndex = nameColIndex;
            select1.disabled = false; // disabled inputs don't post values
            if (select2) select2.selectedIndex = -1;
            if (select3) select3.selectedIndex = -1;
        }

        document.getElementById("uploading").style.display = "";
        return true;
    };

    var init = function() {

        // initialize form submit
        var formEl = Ext.get('sampleSetUploadForm');
        formEl.dom.onsubmit = validateKey;

        // initialize the textarea
        var textbox = Ext.get('textbox');
        textbox.on('change', onTextAreaChange);
        updateIds(textbox.getValue());

        var clearBtn = Ext.get('clear-form-btn');
        clearBtn.on('click', clearValues);

        // prepare the file upload field.
        new Ext.form.FileUploadField({
            id: "upload-run-field",
            renderTo: 'upload-field',
            name: 'file',
            buttonText: btnText,
            buttonOnly: true,
            disabled: true,
            buttonCfg: {cls: 'labkey-button'},
            listeners: {
                fileselected: function (fb, v) {

                    if (v && v.length > 0 && v.indexOf('\\') >= 0) {
                        var path = v.split('\\');
                        fb.button.setText(path[path.length-1]);
                    }

                    // Update submit button
                    var submitBtn = Ext.get('submit-form-btn');
                    submitBtn.addClass('labkey-disabled-button');
                    submitBtn.update('<span>One moment...</span>');

                    var form = new Ext.form.BasicForm(Ext.get('sampleSetUploadForm'), {
                        url: LABKEY.ActionURL.buildURL('experiment', 'parseFile'),
                        fileUpload: true
                    });

                    var processResponse = function(form, action) {

                        submitBtn.removeClass('labkey-disabled-button');
                        submitBtn.update('<span>Submit</span>');

                        if (action.response.responseText.indexOf('<pre') === 0) {
                            var fileContents = Ext.decode($(action.response.responseText).html());
                            var data = fileContents.sheets[0].data;

                            // issue 22851
                            updateIdsWithData(data);

                            // convert data back into the tsv format (which is expected by this action)
                            var textData = '';
                            for (var i=0; i < fields.length; i++) {
                                textData += fields[i].join('\t') + '\n';
                            }

                            document.forms['sampleSetUploadForm'].elements['data'].value = textData;
                            document.forms['sampleSetUploadForm'].elements['tsvData'].value = textData;
                        }
                    };

                    form.submit({
                        success: processResponse,
                        failure: processResponse
                    });
                }
            }
        });
    };

    Ext.onReady(init);
})(jQuery);

</script>
