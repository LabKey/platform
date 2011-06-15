<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.assay.FileUploadDataCollector" %>
<%@ page import="org.labkey.api.study.assay.AssayDataCollector" %>

<%
    JspView<FileUploadDataCollector> me = (JspView<FileUploadDataCollector>) HttpView.currentView();
    FileUploadDataCollector bean = me.getModelBean();
%>

<table id="file-upload-tbl"></table>

<script type="text/javascript">
    var _fileUploadIndex = 0;
    var _maxFileInputs = <%= bean.getMaxFileInputs() %>;

    /**
     * Add a new row to the file upload table with a file input element
     */
    function addFileUploadInputRow(btn)
    {
        // if the add button was clicked and it was disabled, do nothing
        if (btn && btn.className.indexOf("labkey-disabled-button") != -1)
        {
            return;
        }

        // return without adding row if we have already reached the max
        if (_fileUploadIndex >= _maxFileInputs)
        {
            return;
        }

        var tbl = document.getElementById("file-upload-tbl");
        var row = tbl.insertRow(-1);
        row.id = "file-upload-row" + _fileUploadIndex;

        // add a cell for the file upload input
        var cell = row.insertCell(0);
        var id = "<%= AssayDataCollector.PRIMARY_FILE %>" + _fileUploadIndex;
        var name = "<%= AssayDataCollector.PRIMARY_FILE %>" + (_fileUploadIndex > 0 ? _fileUploadIndex : "");
        cell.innerHTML = "<input type='file' size='40' id='" + id + "' name='" + name + "' onChange='toggleAddRemoveButtons();' />";

        // if the given assay type allows for multiple file uploads, add the add and remove buttons
        if (_maxFileInputs > 1)
        {
            // add a cell with a button for removing the given row
            cell = row.insertCell(1);
            cell.innerHTML = "<a id='file-upload-remove" + _fileUploadIndex + "' class='labkey-disabled-button' onClick='removeFileUploadInputRow(this, " + _fileUploadIndex + ");'><span>&#45;</span></a>";

            // add a cell with a button for adding another row
            cell = row.insertCell(2);
            cell.innerHTML = "<a id='file-upload-add" + _fileUploadIndex + "' class='labkey-disabled-button' onClick='addFileUploadInputRow(this);'><span>&#43;</span></a>";

            _fileUploadIndex++;

            toggleAddRemoveButtons();
        }
    }

    /**
     * Remove the specified row from the file upload table
     * @param index - the index of the row to be removed
     */
    function removeFileUploadInputRow(btn, index)
    {
        // if the remove button was clicked and it was disabled, do nothing
        if (btn && btn.className.indexOf("labkey-disabled-button") != -1)
        {
            return;
        }

        // don't allow removal of the last file ulpoad row
        if (_fileUploadIndex <= 1)
        {
            return;
        }

        //delete the entire table row for the selected index
        var tbl = document.getElementById("file-upload-tbl");
        var row = document.getElementById("file-upload-row" + index);
        if (row)
        {
            tbl.deleteRow(row.rowIndex);
            _fileUploadIndex--;

            reindexFileUploadInputRows();
            toggleAddRemoveButtons();
        }
    }

    /**
     *  Loops through the file input rows and reindexes them accordingly
     */
    function reindexFileUploadInputRows()
    {
        var tbl = document.getElementById("file-upload-tbl");
        for (var i = 0; i < tbl.rows.length; i++)
        {
            var row = tbl.rows[i];

            // get the previous row number for this row to help with resetting the input name
            var prevRowNum = row.id.substring(row.id.indexOf("-row")+4);
            row.id = "file-upload-row" + i;

            document.getElementById("<%= AssayDataCollector.PRIMARY_FILE %>" + prevRowNum).name = "<%= AssayDataCollector.PRIMARY_FILE %>" + (i > 0 ? i : "");
            document.getElementById("<%= AssayDataCollector.PRIMARY_FILE %>" + prevRowNum).id = "<%= AssayDataCollector.PRIMARY_FILE %>" + i;
            row.cells[1].innerHTML = "<a id='file-upload-remove" + i + "' class='labkey-button' onClick='removeFileUploadInputRow(this, " + i + ");'><span>&#45;</span></a>";
            document.getElementById("file-upload-add" + prevRowNum).id = "file-upload-add" + i;
        }
    }

    /**
     * Enable/disable the add and remove buttons according to how many table rows are present
     */
    function toggleAddRemoveButtons()
    {
        for (var i = _fileUploadIndex - 1; i >= 0; i--)
        {
            // disable the remove button if there is only one row
            if (_fileUploadIndex <= 1)
            {
                enableDisableButton("remove", i, false);
            }
            else{
                enableDisableButton("remove", i, true);
            }

            // only enable the add button that is on the last row (if the file input is available)
            var inputEl = document.getElementById("__primaryFile__" + i);
            if (i == _fileUploadIndex - 1 && inputEl.value != "")
            {
                enableDisableButton("add", i, true);
            }
            else{
                enableDisableButton("add", i, false);
            }
        }
    }

    /**
     * Enable/disable the given add or remove button
     */
    function enableDisableButton(type, index, enable)
    {
        var el = Ext.get("file-upload-" + type + index);
        if (el)
        {
            if (enable)
            {
                el.replaceClass("labkey-disabled-button", "labkey-button");
            }
            else
            {
                el.replaceClass("labkey-button", "labkey-disabled-button");
            }
        }
    }    

    Ext.onReady(addFileUploadInputRow);
</script>