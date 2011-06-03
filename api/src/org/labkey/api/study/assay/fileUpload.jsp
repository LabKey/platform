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
        if(btn && btn.className.indexOf("labkey-disabled-button") != -1){
            return;
        }

        // return without adding row if we have already reached the max
        if(_fileUploadIndex >= _maxFileInputs){
            return;
        }

        var tbl = document.getElementById("file-upload-tbl");
        var row = tbl.insertRow(-1);
        row.id = "file-upload-row" + _fileUploadIndex;

        // add a cell for the file upload input
        var cell = row.insertCell(0);
        var id = "<%= AssayDataCollector.PRIMARY_FILE %>" + _fileUploadIndex;
        var name = "<%= AssayDataCollector.PRIMARY_FILE %>" + (_fileUploadIndex > 0 ? _fileUploadIndex : "");
        cell.innerHTML = "<input type='file' size='40' id='" + id + "' name='" + name + "' onChange='enableAddButton(this);' />";

        // add a cell with a button for removing the given row
        cell = row.insertCell(1);
        cell.innerHTML = _fileUploadIndex > 0 // don't allow removal of the first row
                ? "<a class='labkey-button' onClick='removeFileUploadInputRow(" + _fileUploadIndex + ");'><span>Remove</span></a>"
                : "&nbsp;";

        // add a cell with a button for adding another row
        cell = row.insertCell(2);
        cell.innerHTML = "<a id='file-upload-add" + _fileUploadIndex + "' class='labkey-disabled-button' onClick='addFileUploadInputRow(this);'><span>Add</span></a>";

        _fileUploadIndex++;

        showLastAddButton();
    }

    /**
     * When a file is selected for a given file input, enable the add button for that row
     */
    function enableAddButton(input){
        var rowId = input.id.substring("<%= AssayDataCollector.PRIMARY_FILE %>".length);
        Ext.get("file-upload-add" + rowId).replaceClass("labkey-disabled-button", "labkey-button");
    }

    /**
     * Remove the specified row from the file upload table
     * @param index - the index of the row to be removed
     */
    function removeFileUploadInputRow(index){
        //delete the entire table row for the selected index
        var tbl = document.getElementById("file-upload-tbl");
        var row = document.getElementById("file-upload-row" + index);
        if(row){
            tbl.deleteRow(row.rowIndex);
            _fileUploadIndex--;

            reindexFileUploadInputRows();
            showLastAddButton();
        }
    }

    /**
     *  Loops through the file input rows and reindexes them accordingly
     */
    function reindexFileUploadInputRows(){
        var tbl = document.getElementById("file-upload-tbl");
        for(var i = 1; i < tbl.rows.length; i++){
            var row = tbl.rows[i];

            // get the previous row number for this row to help with resetting the input name
            var prevRowNum = row.id.substring(row.id.indexOf("-row")+4);

            row.id = "file-upload-row" + i;
            document.getElementById("<%= AssayDataCollector.PRIMARY_FILE %>" + prevRowNum).name = "<%= AssayDataCollector.PRIMARY_FILE %>" + i;
            document.getElementById("<%= AssayDataCollector.PRIMARY_FILE %>" + prevRowNum).id = "<%= AssayDataCollector.PRIMARY_FILE %>" + i;
            row.cells[1].innerHTML = "<a class='labkey-button' onClick='removeFileUploadInputRow(" + i + ");'><span>Remove</span></a>";
            document.getElementById("file-upload-add" + prevRowNum).id = "file-upload-add" + i;
        }
    }

    /**
     * If there are more than one file upload rows and we haven't reached the max,
     * show the Add button on the last file upload row only
     */
    function showLastAddButton(){
        // only show the add button that is on the last row
        var foundLastVisible = false;
        for(var i = _fileUploadIndex; i >= 0; i--){
            var elem = document.getElementById("file-upload-add" + i);
            if(elem){
                if(foundLastVisible || _fileUploadIndex >= _maxFileInputs){
                    elem.style.display = "none";
                }
                else{
                    elem.style.display = "inline";
                    foundLastVisible = true;
                }
            }
        }
    }

    Ext.onReady(addFileUploadInputRow);
</script>