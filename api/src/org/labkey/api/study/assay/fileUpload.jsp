<%
/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.study.assay.AssayDataCollector" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="org.labkey.api.study.assay.AssayRunUploadContext" %>
<%@ page import="org.labkey.api.study.assay.FileUploadDataCollector" %>
<%@ page import="org.labkey.api.study.assay.PreviouslyUploadedDataCollector" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<FileUploadDataCollector> me = (JspView<FileUploadDataCollector>) HttpView.currentView();
    FileUploadDataCollector<? extends AssayRunUploadContext<? extends AssayProvider>> bean = me.getModelBean();
%>

<table id="file-upload-tbl"></table>

<script type="text/javascript">
    LABKEY.requiresCss("fileAddRemoveIcon.css");
</script>

<script type="text/javascript">
    var MAX_FILE_INPUTS = <%= bean.getMaxFileInputs() %>;
    var PREFIX = "<%= h(AssayDataCollector.PRIMARY_FILE) %>";

    // Keep a list of all of the files (new uploads and reuse candidates so that we can track down their
    // corresponding UI elements easily
    var _files = [];

    /**
     * Spins through all of the files and sees how many are active and will be used. We immediately remove
     * new uploads from the list, but leave previous uploads (with a strikethrough) - and we don't want to count them.
     */
    function getActiveFileCount()
    {
        var count = 0;
        for (var i = 0; i < _files.length; i++)
        {
            if (_files[i].active)
            {
                count++;
            }
        }
        return count;
    }

    /**
     * Initialize the input with any previously uploaded files we want to offer for reuse
     */
    function initializeFileUploadInput()
    {
        // Add an entry for all files that can be reused from a previous upload
        <%
        PreviouslyUploadedDataCollector reuseDataCollector = new PreviouslyUploadedDataCollector(Collections.emptyMap(), PreviouslyUploadedDataCollector.Type.ReRun);
        for (Map.Entry<String, File> entry : bean.getReusableFiles().entrySet()) { %>
            addFileUploadInputRow(null, <%= PageFlowUtil.jsString(entry.getValue().getName())%>, <%= PageFlowUtil.jsString(reuseDataCollector.getHiddenFormElementHTML(getContainer(), entry.getKey(), entry.getValue()))%>);
        <% } %>

        // Be sure that we always have at least one file in the list
        <% if (bean.getReusableFiles().isEmpty()) { %>
            addFileUploadInputRow();
        <% } %>
    }

    /**
     * Add a new row to the file upload table with a file input element
     */
    function addFileUploadInputRow(btn, fileName, hiddenFormFields)
    {
        // if the add button was clicked and it was disabled, do nothing
        if (btn && btn.className.indexOf("labkey-file-add-icon-disabled") != -1)
        {
            return;
        }

        // return without adding row if we have already reached the max
        if (getActiveFileCount() >= MAX_FILE_INPUTS)
        {
            return;
        }

        var currentIndex = getActiveFileCount();

        var file = {
            active: true,
            reused: fileName != null
        };
        _files.push(file);

        var tbl = document.getElementById("file-upload-tbl");
        file.mainRow = tbl.insertRow(-1);

        // add a cell for the file upload input
        var fileCell = file.mainRow.insertCell(0);
        fileCell.style.whiteSpace = 'nowrap';
        fileCell.style.paddingTop = '3px';

        var name = PREFIX + (currentIndex > 0 ? currentIndex : "");

        if (fileName)
        {
            fileCell.innerHTML = "<span>" + LABKEY.Utils.encodeHtml(fileName) + "</span>" + hiddenFormFields;
            file.fileNameSpan = fileCell.children[0];
            file.hidden1 = fileCell.children[1];
            file.hidden2 = fileCell.children[2];
        }
        else
        {
            fileCell.innerHTML = "<input type='file' size='40' name='" + name + "' />";
            file.fileInput = fileCell.children[0];
            file.fileInput.style.border = 'none';
            file.fileInput.onchange = function() {
                checkForDuplicateFileName(file);
            };
        }

        // if the given assay type allows for multiple file uploads, add the add and remove buttons
        if (MAX_FILE_INPUTS > 1)
        {
            // add a cell with a button for removing the given row
            var removeCell = file.mainRow.insertCell(1);
            removeCell.innerHTML = "<a class='labkey-file-remove-icon labkey-file-remove-icon-disabled'><span>&nbsp;</span></a>";
            file.removeButtonAnchor = removeCell.children[0];
            file.removeButtonAnchor.onclick = function() {
                removeFileUploadInputRow(this, file);
            };

            // add a cell with a button for adding another row
            var addCell = file.mainRow.insertCell(2);
            addCell.innerHTML = "<a class='labkey-file-add-icon labkey-file-add-icon-disabled'><span>&nbsp;</span></a>";
            file.addButtonAnchor = addCell.children[0];
            file.addButtonAnchor.onclick = function() {
                addFileUploadInputRow(this);
            };

            toggleAddRemoveButtons();
        }

        // add a cell to show the file name after selection
        var fileNameCell = file.mainRow.insertCell(-1);
        fileNameCell.width = "100%";
        fileNameCell.innerHTML = '<div style="padding-left: 5px;"></div>';
        file.fileNameLabel = fileNameCell.children[0];

        // add a new row for error messages, collapsed by default
        file.errorRow = tbl.insertRow(-1);
        var errorCell = file.errorRow.insertCell(-1);
        errorCell.colSpan = 20;
        errorCell.innerHTML = "<div class='labkey-error'></div>";
        file.errorLabel = errorCell.children[0];

        reindexFileUploadInputRows();
    }

    /**
     * Remove the specified row from the file upload table
     * @param index - the index of the row to be removed
     */
    function removeFileUploadInputRow(btn, file)
    {
        // if the remove button was clicked and it was disabled, do nothing
        if (btn && btn.className.indexOf("labkey-file-remove-icon-disabled") != -1)
        {
            return;
        }

        // don't allow removal of the last file upload row
        if (getActiveFileCount() <= 1)
        {
            return;
        }

        if (file.reused)
        {
            // This is a reused file. Don't remove it, but strike it out and disable the form fields so they don't
            // actually post their values, which means the file won't be used
            file.fileNameSpan.style['textDecoration'] = 'line-through';
            file.hidden1.disabled = true;
            file.hidden2.disabled = true;
            file.active = false;
        }
        else
        {
            //delete the entire table row for the selected file
            var tbl = document.getElementById("file-upload-tbl");
            var rowIndex = file.mainRow.rowIndex;
            tbl.deleteRow(rowIndex);

            // delete a second row for the (possibly empty) error message row
            rowIndex = file.errorRow.rowIndex;
            tbl.deleteRow(rowIndex);
            _files.remove(file);
        }

        reindexFileUploadInputRows();
    }

    /**
     *  Loops through the file input rows and reindexes them accordingly
     */
    function reindexFileUploadInputRows()
    {
        var index = 0;
        for (var i = 0; i < _files.length; i++)
        {
            var file = _files[i];
            if (file.fileInput)
            {
                file.fileInput.name = PREFIX + (index > 0 ? index : "");
                index++;
            }
        }

        toggleAddRemoveButtons();
    }

    /**
     * Enable/disable the add and remove buttons according to how many table rows are present
     * also, check to see if the user has selected the same file more than once
     */
    function toggleAddRemoveButtons()
    {
        for (var i = _files.length - 1; i >= 0; i--)
        {
            // disable the remove button if there is only one file left in use
            var file = _files[i];
            if (getActiveFileCount() <= 1 || !file.active)
            {
                enableDisableButton('remove', file.removeButtonAnchor, false);
            }
            else
            {
                enableDisableButton('remove', file.removeButtonAnchor, true);
            }

            // only enable the add button that is on the last row (if the file input is available)
            if (i == _files.length - 1 && getActiveFileCount() < MAX_FILE_INPUTS && (!file.fileInput || file.fileInput.value != ""))
            {
                enableDisableButton('add', file.addButtonAnchor, true);
            }
            else
            {
                enableDisableButton('add', file.addButtonAnchor, false);
            }
        }
    }

    /**
     * Enable/disable the given add or remove button
     */
    function enableDisableButton(type, anchor, enable)
    {
        var el = Ext.get(anchor);
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
     * @param file - the file record that was changed
     */
    function updateFileLabel(file)
    {
        showPathname(file.fileInput, file.fileNameLabel);
    }

    /**
     * Check if a file of the same name has already been uploaded to the server
     */
    function checkServerForDuplicateFileName(file)
    {
        // Fire off an AJAX request
        var duplicateCheckURL = LABKEY.ActionURL.buildURL("assay", "assayFileDuplicateCheck.api");
        var fileName = file.fileInput.value;
        if (!fileName || fileName == '')
        {
            Ext.get(file.errorLabel).update("");
            return;
        }
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
                var element = Ext.get(file.errorLabel);
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
                    response += "If you continue, the renamed file '" + Ext.util.Format.htmlEncode(jsonResponse.newFileName) +
                                "' will be used. To abort click Cancel. To use an alternate file name, " +
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
    function checkForDuplicateFileName(file)
    {
        checkServerForDuplicateFileName(file);

        // loop through the other selected files to see if they are all unique within the current set of files to be
        // uploaded
        var dupFound = false;
        for (var i = 0; i < _files.length; i++)
        {
            // alert the user and remove the file input if the selected file has already been added to this run
            var inputEl = _files[i].fileInput;
            if (inputEl && file.fileInput != inputEl && file.fileInput.value == inputEl.value)
            {
                Ext.Msg.show({
                   title:'Error',
                   msg: 'A file with the same name has already been selected for this run. The duplicate file input will be removed.',
                   buttons: Ext.Msg.OK,
                   fn: function() {
                       removeFileUploadInputRow(null, file);
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
            updateFileLabel(file);
        }
    }

    Ext.onReady(initializeFileUploadInput);
</script>