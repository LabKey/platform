<%
/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.assay.AssayDataCollector" %>
<%@ page import="org.labkey.api.assay.AssayProvider" %>
<%@ page import="org.labkey.api.assay.AssayRunUploadContext" %>
<%@ page import="org.labkey.api.assay.FileUploadDataCollector" %>
<%@ page import="org.labkey.api.assay.PreviouslyUploadedDataCollector" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext3");
    }
%>
<%
    JspView<FileUploadDataCollector> me = (JspView<FileUploadDataCollector>) HttpView.currentView();
    FileUploadDataCollector<? extends AssayRunUploadContext<? extends AssayProvider>> bean = me.getModelBean();
%>

<table id="file-upload-tbl"></table>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    LABKEY.requiresCss("fileAddRemoveIcon.css");
</script>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
<labkey:loadClientDependencies>

    var MAX_FILE_INPUTS = <%= bean.getMaxFileInputs() %>;
    var PREFIX = "<%= h(AssayDataCollector.PRIMARY_FILE) %>";

    // Keep a list of all of the file groups (new uploads and reuse candidates) so that we can track down their
    // corresponding UI elements easily
    var _fileGroups = [];

    /**
     * Spins through all of the files and sees how many are active and will be used. We immediately remove
     * new uploads from the list, but leave previous uploads (with a strikethrough) - and we don't want to count them.
     */
    function getActiveFileCount()
    {
        var count = 0;
        for (var i = 0; i < _fileGroups.length; i++) {
            var fileGroup = _fileGroups[i];

            if (fileGroup.active) {
                if (fileGroup.fileInput && fileGroup.fileInput.files) {
                    count += fileGroup.fileInput.files.length;
                }
                else if (fileGroup.reused) {
                    // no file input fields in this case but always one file and it's active, so add 1
                    count++;
                }
            }
        }
        return count;
    }

    function getActiveFileGroupCount()
    {
        var count = 0;
        for (var i = 0; i < _fileGroups.length; i++) {
            var fileGroup = _fileGroups[i];
            if (fileGroup.active) {
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
        // Add an entry for all file groups that can be reused from a previous upload
        <%
        PreviouslyUploadedDataCollector reuseDataCollector = new PreviouslyUploadedDataCollector(Collections.emptyMap(), PreviouslyUploadedDataCollector.Type.ReRun);
        for (Map.Entry<String, File> entry : bean.getReusableFiles().entrySet()) { %>
            addFileUploadInputRow(null, <%= q(entry.getValue().getName())%>, <%= q(reuseDataCollector.getHiddenFormElementHTML(getContainer(), entry.getKey(), entry.getValue()))%>);
        <% } %>

        // Be sure that we always have at least one file group in the list
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
        if (btn && btn.className.indexOf("labkey-file-add-icon-disabled") !== -1)
        {
            return;
        }

        // return without adding row if we have already reached the max number of files
        if (getActiveFileCount() >= MAX_FILE_INPUTS)
        {
            return;
        }

        var currentIndex = getActiveFileGroupCount();

        var fileGroup = {
            active: true,
            reused: fileName != null
        };
        _fileGroups.push(fileGroup);

        var tbl = document.getElementById("file-upload-tbl");
        fileGroup.mainRow = tbl.insertRow(-1);

        // add a cell for the file upload input
        var fileCell = fileGroup.mainRow.insertCell(0);
        fileCell.style.whiteSpace = 'nowrap';
        fileCell.style.paddingTop = '3px';

        var name = PREFIX + (currentIndex > 0 ? currentIndex : "");

        if (fileName)
        {
            fileCell.innerHTML = "<span>" + LABKEY.Utils.encodeHtml(fileName) + "</span>" + hiddenFormFields;
            fileGroup.fileNameSpan = fileCell.children[0];
            fileGroup.hidden1 = fileCell.children[1];
            fileGroup.hidden2 = fileCell.children[2];
        }
        else
        {
            // if the given assay type allows for multiple file uploads, allow multiselect in OS file picker (and allow drag-and-drop to this field)
            // note that this allows for multiple files in one row (add/remove buttons make multiple rows)
            if (MAX_FILE_INPUTS > 1)
                fileCell.innerHTML = "<input type='file' size='40' name='" + name + "' multiple />";
            else
                fileCell.innerHTML = "<input type='file' size='40' name='" + name + "'/>";
            fileGroup.fileInput = fileCell.children[0];
            fileGroup.fileInput.style.border = 'none';
            fileGroup.fileInput.onchange = function() {
                checkForDuplicateFileName(fileGroup);
            };
        }

        // if the given assay type allows for multiple file uploads, add the add and remove buttons
        if (MAX_FILE_INPUTS > 1)
        {
            // add a cell with a button for removing the given row
            var removeCell = fileGroup.mainRow.insertCell(1);
            removeCell.innerHTML = "<a class='labkey-file-remove-icon labkey-file-remove-icon-disabled'><span>&nbsp;</span></a>";
            fileGroup.removeButtonAnchor = removeCell.children[0];
            fileGroup.removeButtonAnchor.onclick = function() {
                removeFileUploadInputRow(this, fileGroup);
            };

            // add a cell with a button for adding another row
            var addCell = fileGroup.mainRow.insertCell(2);
            addCell.innerHTML = "<a class='labkey-file-add-icon labkey-file-add-icon-disabled'><span>&nbsp;</span></a>";
            fileGroup.addButtonAnchor = addCell.children[0];
            fileGroup.addButtonAnchor.onclick = function() {
                addFileUploadInputRow(this);
            };

            toggleAddRemoveButtons();
        }

        // add cells to show the file name(s) after selection
        fileGroup.fileNameLabels = [];
        for (var i = 0; i < MAX_FILE_INPUTS; i++) {
            var fileNameCell = fileGroup.mainRow.insertCell(-1);
            fileNameCell.width = "100%";
            fileNameCell.innerHTML = '<div style="padding-left: 5px;"></div>';
            fileGroup.fileNameLabels[i] = fileNameCell.children[0];
        }

        // add a new row for error messages, collapsed by default
        fileGroup.errorRow = tbl.insertRow(-1);
        var errorCell = fileGroup.errorRow.insertCell(-1);
        errorCell.colSpan = 20;
        errorCell.innerHTML = "<div class='labkey-error'></div>";
        fileGroup.errorLabel = errorCell.children[0];

        reindexFileUploadInputRows();
    }

    /**
     * Remove the specified row from the file upload table
     */
    function removeFileUploadInputRow(btn, fileGroup)
    {
        // if the remove button was clicked and it was disabled, do nothing
        if (btn && btn.className.indexOf("labkey-file-remove-icon-disabled") !== -1)
        {
            return;
        }

        // if this is last remaining file row, replace with an empty row (existing row will be removed later)
        if (getActiveFileGroupCount() <= 1)
        {
            addFileUploadInputRow();
        }

        if (fileGroup.reused)
        {
            // This is a reused file group. Don't remove it, but strike it out and disable the form fields so they don't
            // actually post their values, which means the file group won't be used
            fileGroup.fileNameSpan.style['textDecoration'] = 'line-through';
            fileGroup.hidden1.disabled = true;
            fileGroup.hidden2.disabled = true;
            fileGroup.active = false;
        }
        else
        {
            //delete the entire table row for the selected file group
            var tbl = document.getElementById("file-upload-tbl");
            var rowIndex = fileGroup.mainRow.rowIndex;
            tbl.deleteRow(rowIndex);

            // delete a second row for the (possibly empty) error message row
            rowIndex = fileGroup.errorRow.rowIndex;
            tbl.deleteRow(rowIndex);
            _fileGroups.remove(fileGroup);
        }

        reindexFileUploadInputRows();
    }

    /**
     *  Loops through the fileGroup input rows and reindexes them accordingly
     */
    function reindexFileUploadInputRows()
    {
        var index = 0;
        for (var i = 0; i < _fileGroups.length; i++)
        {
            var fileGroup = _fileGroups[i];
            if (fileGroup.fileInput)
            {
                fileGroup.fileInput.name = PREFIX + (index > 0 ? index : "");
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
        for (var i = _fileGroups.length - 1; i >= 0; i--) {
            // disable the remove button if there is only one file group left in use
            var fileGroup = _fileGroups[i];
            if (getActiveFileGroupCount() <= 1 || !fileGroup.active) {
                enableDisableButton('remove', fileGroup.removeButtonAnchor, false);
            }
            else {
                enableDisableButton('remove', fileGroup.removeButtonAnchor, true);
            }

            // only enable the add button that is on the last row (if the file group input is available)
            if (i === _fileGroups.length - 1 && getActiveFileCount() < MAX_FILE_INPUTS &&
                    (!fileGroup.fileInput || fileGroup.fileInput.value !== ""))  // don't allow multiple empty lines at end
            {
                enableDisableButton('add', fileGroup.addButtonAnchor, true);
            }
            else {
                enableDisableButton('add', fileGroup.addButtonAnchor, false);
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
     * Update the label cell with the selected file name(s)
     * @param fileGroup - the file group record that was changed
     */
    function updateFileLabel(fileGroup)
    {
        var numberOfFiles = fileGroup.fileInput.files.length;
        var fileName;

        // update all slots that have filenames which exist
        for (var i = 0; i < numberOfFiles; i++) {
            fileName = fileGroup.fileInput.files[i].name;
            // this is a little silly, but it's trying to avoid changing showPathname()
            var fileNameWrapped = {value: fileName};
            showPathname(fileNameWrapped, fileGroup.fileNameLabels[i]);
        }

        // set all other filename elements to empty
        for (var j = (MAX_FILE_INPUTS - 1); j >= numberOfFiles; j--) {
            // this is a little silly, but it's trying to avoid changing showPathname()
            fileName = {value: ''};
            showPathname(fileName, fileGroup.fileNameLabels[j]);
        }
    }

    /**
     * Check if any of the filenames in a file group has already been uploaded to the server
     */
    function checkServerForDuplicateFileName(fileGroup)
    {
        var fileNames = [];
        if (fileGroup && fileGroup.fileInput && fileGroup.fileInput.files) {
            for (var i = 0; i < fileGroup.fileInput.files.length; i++) {
                var file = fileGroup.fileInput.files[i];
                var fileName = file.name;
                if (fileName)
                    fileNames.push(fileName);

                // We'll show an error for directories, because they cause us to have strange errors after upload.
                // Sadly, true cross-browser detection of directories in the FileList API is terrible.
                // You can't rely on sizes (especially on Macs) or on types (this is derived from any detected
                // extension, even if it's not a real extension).
                // webkitGetAsEntry is not currently supported by Safari or Internet Explorer.
                // The only way left, then, is to attempt reading each entry and assume errors are probably directories.
                // See https://stackoverflow.com/q/8856628 for more details.

                var reader = new FileReader();
                reader.readAsDataURL(file);
                reader.onerror = (function (fileName) {  // failure, probably directory
                    return function (event) {
                        Ext.Msg.show({
                            title: 'Error',
                            msg: 'One or more directories (or unreadable files) were selected for upload, including "'
                                + fileName + '". Directory submission is not supported. The entire input row will be removed.' ,
                            buttons: Ext.Msg.OK,
                            icon: Ext.MessageBox.ERROR,
                            fn: function () {
                                removeFileUploadInputRow(null, fileGroup);
                            }
                        });
                    }
                })(fileName);
            }
        }

        if (!fileNames.length)
        {
            Ext.get(fileGroup.errorLabel).update("");
            return;
        }

        for (var j = 0; j < fileNames.length; j++) {
            var fileNameShort = fileNames[j];
            var slashIndex = Math.max(fileNameShort.lastIndexOf("/"), fileNameShort.lastIndexOf("\\"));
            if (slashIndex !== -1)
            {
                fileNameShort = fileNameShort.substring(slashIndex + 1);
            }
            fileNames[j] = fileNameShort;
        }

        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("assay", "assayFileDuplicateCheck.api"),
            jsonData: {fileNames: fileNames},
            success: function(response)
            {
                var jsonResponse = Ext.decode(response.responseText);
                // Show or clear the warning
                var element = Ext.get(fileGroup.errorLabel);
                if (jsonResponse.duplicate)
                {
                    var runNamesPerFile = jsonResponse.runNamesPerFile;
                    var newFileNames = jsonResponse.newFileNames;
                    response = "Already existing files were found. They are: <br>";
                    for (var i = 0; i < fileNames.length; i++) {
                        if (newFileNames[i]) {
                            response += "A file with name '" + Ext.util.Format.htmlEncode(fileNames[i]) + "' already exists.  ";
                            if (runNamesPerFile.length > 0) {
                                response += "This file is associated with the Run ID(s): '" + Ext.util.Format.htmlEncode(runNamesPerFile[i]) + "'.  ";
                            }
                            else {
                                response += "This file is not associated with a run.  "
                            }
                            response += "If you continue, the renamed file '" + Ext.util.Format.htmlEncode(newFileNames[i]) +
                                    "' will be used. To abort click Cancel. To use an alternate file name, " +
                                    "change the file name on your computer and then reselect the file.<br>";
                        }
                    }
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
     * Check if the selected file group names are already in the list of selected files
     */
    function checkForDuplicateFileName(fileGroup)
    {
        checkServerForDuplicateFileName(fileGroup);

        var fileCount = getActiveFileCount();
        var tooManyFiles = (getActiveFileCount() > MAX_FILE_INPUTS);
        var duplicateFilenames = [];

        if (tooManyFiles) {
            Ext.Msg.show({
                title: 'Error',
                msg: 'Too many files chosen for upload. Max is ' + MAX_FILE_INPUTS + ' file(s), but ' + fileCount + ' file(s) found.'
                        + ' The entire input row containing this file will be removed.' ,
                buttons: Ext.Msg.OK,
                icon: Ext.MessageBox.ERROR,
                fn: function () {
                    removeFileUploadInputRow(null, fileGroup);
                }
            });
        }
        else {
            // loop through the other selected sets of files to see if all those files are unique within the current sets of files to be uploaded
            var dupFound = false;
            for (var i = 0; i < _fileGroups.length; i++) {
                var inputEl = _fileGroups[i].fileInput;

                if (inputEl && fileGroup.fileInput !== inputEl) {
                    for (var j = 0; j < fileGroup.fileInput.files.length; j++) {
                        for (var k = 0; k < inputEl.files.length; k++) {
                            if (fileGroup.fileInput.files[j].name === inputEl.files[k].name) {
                                duplicateFilenames.push(fileGroup.fileInput.files[j].name);
                                dupFound = true;
                            }
                        }
                    }
                }
            }
        }

        // alert the user and remove the file group input if one of its files has already been added to this run
        if (dupFound) {
            Ext.Msg.show({
                title: 'Error',
                msg: 'A file with the same name ("' + Ext.util.Format.htmlEncode(duplicateFilenames.join(", "))
                        + '") has already been selected for this run. The entire input row containing these file(s) will be removed.',
                buttons: Ext.Msg.OK,
                icon: Ext.MessageBox.ERROR,
                fn: function () {
                    removeFileUploadInputRow(null, fileGroup);
                }
            });
        }

        if (!tooManyFiles && !dupFound) {
            toggleAddRemoveButtons();
            updateFileLabel(fileGroup);
        }
    }

    Ext.onReady(initializeFileUploadInput);

    </labkey:loadClientDependencies>
</script>