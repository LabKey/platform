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
    LABKEY.requiresCss("fileAddRemoveIcon.css");
</script>

<script type="text/javascript">
    var _fileUploadIndex = 0;
    var _maxFileInputs = <%= bean.getMaxFileInputs() %>;
    var _prefix = "<%= AssayDataCollector.PRIMARY_FILE %>";

    /**
     * Add a new row to the file upload table with a file input element
     */
    function addFileUploadInputRow(btn)
    {
        // if the add button was clicked and it was disabled, do nothing
        if (btn && btn.className.indexOf("labkey-file-add-icon-disabled") != -1)
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
        var id = _prefix + _fileUploadIndex;
        var name = _prefix + (_fileUploadIndex > 0 ? _fileUploadIndex : "");
        cell.innerHTML = "<input type='file' size='40' id='" + id + "' name='" + name + "' onChange='checkForDuplicateFileName(this);' />";
        var currentIndex = _fileUploadIndex;

        // if the given assay type allows for multiple file uploads, add the add and remove buttons
        if (_maxFileInputs > 1)
        {
            // add a cell with a button for removing the given row
            cell = row.insertCell(1);
            cell.innerHTML = "<a id='file-upload-remove" + _fileUploadIndex + "' class='labkey-file-remove-icon labkey-file-remove-icon-disabled' onClick='removeFileUploadInputRow(this, " + _fileUploadIndex + ");'><span>&nbsp;</span></a>";

            // add a cell with a button for adding another row
            cell = row.insertCell(2);
            cell.innerHTML = "<a id='file-upload-add" + _fileUploadIndex + "' class='labkey-file-add-icon labkey-file-add-icon-disabled' onClick='addFileUploadInputRow(this);'><span>&nbsp;</span></a>";

            _fileUploadIndex++;

            toggleAddRemoveButtons();
        }

        // add a cell to show the file name after selection
        cell = row.insertCell(-1);
        cell.width = "100%";
        cell.innerHTML = '<label id="label' + id + '"></label>'; 

        // add a new row for file-upload-warning error messages, collapses by default
        var row = tbl.insertRow(-1);
        cell = row.insertCell(-1);
        cell.colSpan = 20;
        cell.innerHTML = "<label class='labkey-error' id='file-upload-warning" + currentIndex + "'></label>";
    }

    /**
     * Remove the specified row from the file upload table
     * @param index - the index of the row to be removed
     */
    function removeFileUploadInputRow(btn, index)
    {
        // if the remove button was clicked and it was disabled, do nothing
        if (btn && btn.className.indexOf("labkey-file-remove-icon-disabled") != -1)
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
            rowIndex = row.rowIndex;
            tbl.deleteRow(rowIndex);  // delete a second row for the (possibly empty) error message row
            tbl.deleteRow(rowIndex);
            _fileUploadIndex--;

            reindexFileUploadInputRows();
        }
    }

    /**
     *  Loops through the file input rows and reindexes them accordingly
     */
    function reindexFileUploadInputRows()
    {
        var tbl = document.getElementById("file-upload-tbl");
        for (var j = 0; j < tbl.rows.length; j=j+2)
        {
            // A given file upload control consists of 2 rows, 1 upload 1 error
            var i = j/2;
            var row = tbl.rows[j];  // use j to select the appropriate row in the table

            // get the previous row number for this row to help with resetting the input name
            var prevRowNum = row.id.substring(row.id.indexOf("-row")+4);
            row.id = "file-upload-row" + i;

            // all elements for a give file-upload element should be reindexed
            document.getElementById(_prefix + prevRowNum).name = _prefix + (i > 0 ? i : "");
            document.getElementById(_prefix + prevRowNum).id = _prefix + i;
            row.cells[1].innerHTML = "<a id='file-upload-remove" + i + "' class='labkey-file-remove-icon labkey-file-remove-icon-disabled' onClick='removeFileUploadInputRow(this, " + i + ");'><span>&nbsp;</span></a>";
            document.getElementById("file-upload-add" + prevRowNum).id = "file-upload-add" + i;
            document.getElementById("label" + _prefix + prevRowNum).id = "label" + _prefix + i;
            document.getElementById("file-upload-warning" + prevRowNum).id = "file-upload-warning" + i;
        }

        toggleAddRemoveButtons();
    }

    /**
     * Enable/disable the add and remove buttons according to how many table rows are present
     * also, check to see if the user has selected the same file more than once
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
            else
            {
                enableDisableButton("remove", i, true);
            }

            // only enable the add button that is on the last row (if the file input is available)
            var inputEl = document.getElementById(_prefix + i);
            if (i == _fileUploadIndex - 1 && inputEl.value != "")
            {
                enableDisableButton("add", i, true);
            }
            else
            {
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
                el.removeClass("labkey-file-" + type + "-icon-disabled");
                el.addClass("labkey-file-" + type + "-icon-enabled");
            }
            else
            {
                el.removeClass("labkey-file-" + type + "-icon-enabled");
                el.addClass("labkey-file-" + type + "-icon-disabled");
            }
        }
    }

    /**
     * Update the label cell with the selected file name
     * @param fileInput - the input element that was changed
     */
    function updateFileLabel(fileInput)
    {
        if (fileInput.value)
            showPathname(fileInput, "label" + fileInput.id);
    }

    /**
     * Check if a file of the same name has already been uploaded to the server
     */
    function checkServerForDuplicateFileName(currFileInput, index)
    {
        // Fire off an AJAX request
        var duplicateCheckURL = LABKEY.ActionURL.buildURL("assay", "assayFileDuplicateCheck.api");
        var fileName = currFileInput.value;
        var slashIndex = Math.max(fileName.lastIndexOf("/"), fileName.lastIndexOf("\\"));
        if (slashIndex != -1)
        {
            fileName = fileName.substring(slashIndex + 1);
        }
        Ext.Ajax.request({
            url: duplicateCheckURL,
            jsonData: {fileName: fileName},
            success: function(response, options)
            {
                var jsonResponse = Ext.decode(response.responseText);
                // Show or clear the warning
                var element = Ext.get("file-upload-warning" + index);
                if (jsonResponse.duplicate)
                {
                    runNames = jsonResponse.runNames;
                    response = "A file with name '" + Ext.util.Format.htmlEncode(fileName) + "' already exists.  ";
                    if (runNames.length > 0)
                    {
                        response += "This file is associated with the Run ID(s): '" + Ext.util.Format.htmlEncode(runNames) + "'.  ";
                    }
                    else
                    {
                        response += "This file is not associated with a run.  "
                    }
                    response += "To continue with the renamed file '" + Ext.util.Format.htmlEncode(jsonResponse.newFileName) +
                                "' click Save and Finish. To abort click Cancel. To use an alternate file name, " +
                                "change the file name on your computer and then reselect the file.";
                    element.update(response);
                }
                else
                {
                    element.update("");
                }
            },
            failure: LABKEY.Utils.getCallbackWrapper(null, this, true)
        });
    }
    
    /**
     * Check if the selected file name is already in the list of selected files
     */
    function checkForDuplicateFileName(currFileInput)
    {
        // get the file index from the input id
        var index = parseInt(currFileInput.id.replace(_prefix, ""));

        checkServerForDuplicateFileName(currFileInput, index);

        // loop through the other selected files to see if they are all unique
        var dupFound = false;
        for (var i = 0; i < _fileUploadIndex; i++)
        {
            // alert the user and remove the file input if the selected file has already been added to this run
            var inputEl = document.getElementById(_prefix + i);
            if (currFileInput.id != inputEl.id && currFileInput.value == inputEl.value)
            {
                Ext.Msg.show({
                   title:'Error',
                   msg: 'A file with the same name has already been selected for this run. The duplicate file input will be removed.',
                   buttons: Ext.Msg.OK,
                   fn: function() {
                       removeFileUploadInputRow(null, index);
                   },
                   icon: Ext.MessageBox.ERROR
                });

                dupFound = true;
                break;
            }
        }

        if (!dupFound)
        {
            toggleAddRemoveButtons();
            updateFileLabel(currFileInput);
        }
    }

    Ext.onReady(addFileUploadInputRow);
</script>