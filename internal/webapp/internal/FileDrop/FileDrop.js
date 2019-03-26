/*
 * Copyright (c) 2014-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if (!LABKEY.internal)
    LABKEY.internal = {};

LABKEY.internal.FileDrop = new function () {

    var registered = [];
    var registeredEvents = false;
    var shouldShowDropzones = false;
    var showingDropzones = false;
    var timeout = -1;

    function showDropzones()
    {
        if (showingDropzones)
            return;

        showingDropzones = true;

        // show all dropzones
        // CONSIDER: iterate Dropzone.instances instead of keeping our own list of registered Dropzone
        for (var i = 0, len = registered.length; i < len; i++)
        {
            var dropzone = registered[i];

            var peer = dropzone.peer;
            if (peer instanceof Function)
                peer = peer();

            // get location of peer
            var peerEl = Ext4.Element.get(peer);
            var r = peerEl.getViewRegion();

            dropzone.element.style.top = r.top + "px";
            dropzone.element.style.left = r.left + "px";
            dropzone.element.style.height = (r.bottom - r.top) + "px";
            dropzone.element.style.width = (r.right - r.left) + "px";

            // Use table-cell to vertically align the inner content
            dropzone.element.style.display = 'table';
        }
    }

    function hideDropzones()
    {
        if (!showingDropzones)
            return;

        showingDropzones = false;

        // hide all dropzones
        for (var i = 0, len = registered.length; i < len; i++)
        {
            var dropzone = registered[i];

            dropzone.element.style.display = 'none';
        }
    }

    function docDragStart(e)
    {
        //console.log("document.dragstart:", e);
    }

    function docDragEnter(e)
    {
        //console.log("document.dragenter:", e);
        e.stopEvent();

        shouldShowDropzones = true;
        showDropzones();
    }

    function docDragOver(e)
    {
        //console.log("document.dragover:", e);
        // NOTE: important to cancel this event -- stops the browser from navigating on file drop
        e.stopEvent();
        shouldShowDropzones = true;
    }

    function docDragLeave(e)
    {
        //console.log("document.dragleave: ", e);
        e.stopEvent();

        shouldShowDropzones = false;
        clearTimeout(timeout);
        timeout = setTimeout(function () {
            if (!shouldShowDropzones) {
                hideDropzones();
            }
        }, 200);
    }

    function docDragEnd(e)
    {
        //console.log("document.dragend:", e);
    }

    function docDrop(e)
    {
        //console.log("document.drop:", e);
        e.stopEvent();

        shouldShowDropzones = false;
        hideDropzones();
    }

    function zoneDragEnter()
    {
        //console.log("zone.dragenter");
        shouldShowDropzones = true;
    }

    function zoneDragOver()
    {
        //console.log("zone.dragover");
        shouldShowDropzones = true;
    }

    function zoneDrop()
    {
        //console.log("zone.drop");
        shouldShowDropzones = false;
        hideDropzones();
    }

    function registerEvents()
    {
        if (registeredEvents)
            return;

        registeredEvents = true;
        Ext4.EventManager.addListener(document, "dragenter", docDragEnter, this);
        Ext4.EventManager.addListener(document, "dragover", docDragOver, this);
        Ext4.EventManager.addListener(document, "dragleave", docDragLeave, this);
        Ext4.EventManager.addListener(document, "dragend", docDragEnd, this);
        Ext4.EventManager.addListener(document, "drop", docDrop, this);
    }

    function createDefaultZone()
    {
        var zone = document.createElement("div");
        zone.style.display = "none";
        zone.style.verticalAlign = "middle";
        zone.style.position = "absolute";
        zone.style.zIndex = "1000";

        // TODO: move the presentation styles into the dropzone/css/basic.css
        zone.style.borderSpacing = "6px";
        zone.style.background = "white";
        zone.style.opacity = "0.8";

        zone.classList.add("dropzone");
        zone.classList.add("dz-clickable");
        zone.innerHTML =
                "<div class='dz-message' style='display:table-cell;vertical-align:middle;" +
                    "background: rgba(10, 10, 10, 0.1);" +
                    "opacity: 0.8;" +
                    "border: 6px dashed rgba(10, 10, 10, 0.4);" +
                    "color: rgba(10, 10, 10, 0.4);" +
                    "font-size: 40px;" +
                    "text-align:center;'>" +
                "<span style='font-weight: bold'>" +
                (!Ext4.isIE ? "Drop files or folders here" : "Drop files here<br>Folder upload is not supported by Internet Explorer") +
                "</span>" +
                "</div>";

        return zone;
    }

    function _registerDropzone(config)
    {
        Dropzone.autoDiscover = false;

        var peer = config.peer;
        if (!peer)
            throw new Error("peer required");

        var el = config.el || createDefaultZone();
        // TODO: listen for el removed event to cleanup the Dropzone

        // this (passing the dropzone) is silly but it works.
        Dropzone.prototype.setEnabled = function(enabled) {
            if (enabled) {
                this.enable();
                // 34847: HierarchyRequestError on IE/Edge
                try { document.body.appendChild(this.element); } catch (e) { }
            }
            else {
                this.disable();
                // 34847: HierarchyRequestError on IE/Edge
                try { document.body.removeChild(this.element); } catch (e) { }
            }
        };

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL("fileContent", "getZipUploadRecognizer.api"),
            success: function (response) {
                Dropzone.patterns = JSON.parse(response.responseText).rows;

            },
            failure: function () {
                console.log("in failure");// remove this
            }
        });

        /**
         *  overriding drop method of dropzone.js to zip matching directory patterns before upload.
         * */

        Dropzone.prototype.drop = function (e) {
           var patterns = Dropzone.patterns;
            var files = [], items;

            if (!e.dataTransfer) {
                return;
            }

            items = e.dataTransfer.items;

            if (!patterns) { // no registered patterns
                this._originalDrop(e, this);
            }
            else {
                this.emit("drop", e);
                //this.emit("addedfiles", e.dataTransfer.files);
                zip.useWebWorkers = false;

                var me = this;

                this.entries = [];

                for (var it = 0; it < items.length; it++) {
                    if (items[it].webkitGetAsEntry != null) {
                        this.entries.push(items[it].webkitGetAsEntry())
                    }
                }

                console.log(this.entries);

                var patternMatch = false;
                var itemCount = this.entries.length - 1; // to keep track of all the items in drag event

                zipLoad(this.entries[itemCount]);

                function zipLoad(entry) {
                    if (entry && entry.isDirectory) {
                        files = [];
                        me.cbCount = 0; //to keep track of directory level callbacks
                        me.fileCbCount = 0; //to keep track of file level callbacks
                        getFilesFromDirectory(files, entry, entry.fullPath, me, e, function (files) {
                            //check for matching directory patterns
                            var tree = buildTree(files);
                            //check each pattern for each node
                            for (var p = 0; p < patterns.length; p++) {
                                for (var tn = 0; tn < tree.nodes.length; tn++) {
                                    checkPattern(tree.nodes[tn], patterns[p]);
                                }
                            }

                            var filesToZip = [];
                            var filesToUpload = [];
                            for (var tn = 0; tn < tree.nodes.length; tn++) {
                                var node = tree.nodes[tn];
                                sepZipFiles(node);
                            }

                            function sepZipFiles(node) {
                                var nodeName = node.name;
                                if (node.zip) {
                                    //grab all the files from this directory
                                    grabFilesFromDirectory(node, true, nodeName);
                                }
                                else if (node.directories.length > 0) {
                                    for (var nf = 0; nf < node.files.length; nf++) {
                                        filesToUpload.push({file: node.files[nf].file, dir: nodeName});
                                    }
                                    for (var nd = 0; nd < node.directories.length; nd++) {
                                        sepZipFiles(node.directories[nd]);
                                    }
                                }
                                else {
                                    //grab all the files from this directory
                                    grabFilesFromDirectory(node, false, nodeName);
                                }
                            }

                            function grabFilesFromDirectory(node, zip, nodeName) {
                                if (node.files.length > 0) { //all files in node
                                    for (var nf = 0; nf < node.files.length; nf++) {
                                        if (zip) {
                                            filesToZip.push({file: node.files[nf].file, dir: nodeName});
                                        }
                                        else {
                                            filesToUpload.push({file: node.files[nf].file, dir: nodeName});
                                        }
                                    }
                                }
                                if (node.directories.length > 0) { //all files in subDirs
                                    for (var nd = 0; nd < node.directories.length; nd++) {
                                        grabFilesFromDirectory(node.directories[nd], zip, nodeName);
                                    }
                                }
                            }

                            if (filesToZip.length > 0) {
                                //separating filesToZip to its own directory
                                var filesToZipPerDirectory = [];
                                var temp = [];
                                var tempName = filesToZip[0].dir;
                                temp.push(filesToZip[0]);
                                for (var fz = 1; fz < filesToZip.length; fz++) {
                                    if (filesToZip[fz].dir === tempName) {
                                        temp.push(filesToZip[fz]);
                                    }
                                    else {
                                        filesToZipPerDirectory.push(temp);
                                        temp = [];
                                        tempName = filesToZip[fz].dir;
                                        temp.push(filesToZip[fz]);
                                    }
                                }

                                if (temp.length > 0) {
                                    filesToZipPerDirectory.push(temp);
                                }

                                //separate directories in parts for 4GB limit
                                var filesToZipPerDirectoryParts = [];
                                for (var ftz = 0; ftz < filesToZipPerDirectory.length; ftz++) {
                                    var kb = 1024;
                                    var mb = kb * 1024;
                                    var gb = mb * 1024;
                                    var limit = 4 * gb; // 4GB limit
                                    var sum = 0;
                                    var holder = filesToZipPerDirectory[ftz];

                                    for (var h = 0; h < holder.length; h++) {
                                        sum += holder[h].file.size;
                                    }

                                    if (sum > limit) { //create parts
                                        createParts(holder, limit);
                                    }
                                    else {
                                        filesToZipPerDirectoryParts.push(holder);
                                    }
                                }

                                function createParts(holder, limit) {
                                    var temp = [];
                                    var sum = 0;
                                    var ind = 1;
                                    for (var s = 0; s < holder.length; s++) {
                                        if (holder[s].file.size >= limit) {
                                            filesToUpload.push(holder[s]);
                                        } else {
                                            sum += holder[s].file.size;
                                            if (sum >= limit) {
                                                for (var tp = 0; tp < temp.length; tp++) {
                                                    temp[tp].dir = temp[tp].dir + ind;
                                                }
                                                filesToZipPerDirectoryParts.push(temp);
                                                ind++;
                                                temp = [];
                                                sum = 0;
                                            }
                                            else {
                                                temp.push(holder[s]);
                                            }
                                        }
                                    }

                                    if (temp.length > 0) {
                                        if (ind > 1) {
                                            for (var tp = 0; tp < temp.length; tp++) {
                                                temp[tp].dir = temp[tp].dir + ind;
                                            }
                                        }
                                        filesToZipPerDirectoryParts.push(temp);
                                    }
                                }


                                function getCurrentZipFile() {
                                    this.currentZipFileText = Ext4.create('Ext.form.Label', {
                                        text: '',
                                        style: 'display: inline-block ;text-align: left',
                                        width: 250,
                                        border: false
                                    });
                                    return this.currentZipFileText;
                                }

                                function getCurrentFileNumber() {
                                    this.currentFileNumber = Ext4.create('Ext.form.Label', {
                                        text: '',
                                        style: 'display: inline-block ;text-align: right',
                                        width: 250,
                                        border: false
                                    });
                                    return this.currentFileNumber;
                                }

                                function setStatusText(text) {
                                    this.statusText = Ext4.create('Ext.form.Label', {
                                        text: text,
                                        style: 'display: inline-block ;text-align: center',
                                        width: 500,
                                        margin: 4,
                                        border: false
                                    });
                                    return this.statusText;
                                }

                                function getStatusText() {
                                    return this.statusText;
                                }

                                function getProgressBar() {
                                    this.progressBar = Ext4.create('Ext.ProgressBar', {
                                        width: 500,
                                        height: 25,
                                        border: false,
                                        autoRender : true,
                                        style: 'background-color: transparent; -moz-border-radius: 5px; -webkit-border-radius: 5px; -o-border-radius: 5px; -ms-border-radius: 5px; -khtml-border-radius: 5px; border-radius: 5px;'
                                    });
                                    return this.progressBar;
                                }

                                function getZipProgressWindow() {

                                    var zipFileAndDirectoryContainer = Ext4.create('Ext.container.Container', {
                                        width: 500,
                                        margin: 4,
                                        layout: 'hbox',
                                        items: [getCurrentZipFile(),getCurrentFileNumber()]
                                    });

                                    var progressBarContainer = Ext4.create('Ext.container.Container', {
                                        width: 500,
                                        margin: 4,
                                        items: [getProgressBar()]
                                    });

                                    this.zipProgressWindow = Ext4.create('Ext.window.Window', {
                                        title: 'Zip Progress',
                                        layout: 'vbox',
                                        bodyPadding: 5,
                                        closable: false,
                                        border: false,
                                        items: [getStatusText(), zipFileAndDirectoryContainer, progressBarContainer]
                                    });

                                    return this.zipProgressWindow;
                                }

                                this.showZipProgressWindow = function(text) {
                                    setStatusText(text);
                                    getZipProgressWindow().show();
                                };

                                function hideZipProgressWindow() {
                                    setStatusText('');
                                    this.zipProgressWindow.hide();
                                }

                                var zipDirectoryCount = filesToZipPerDirectoryParts.length - 1;
                                console.time("ZIP DONE IN");
                                zipDirectory(filesToZipPerDirectoryParts[zipDirectoryCount], me);

                                //zip each directory
                                function zipDirectory(files, me) {
                                    var totalSize = 0;
                                    for(var s=0; s<files.length; s++) {
                                        totalSize += files[s].file.size;
                                    }

                                    this.showZipProgressWindow("Zipping directory " + filesToZipPerDirectoryParts[zipDirectoryCount][0].dir );
                                    var totalDone = 0;
                                    var prevDone = 0;
                                    zipFiles(files, me, function (current, total) {
                                        totalDone = (current - prevDone) + totalDone ;
                                        this.progressBar.updateProgress(totalDone / totalSize);
                                        prevDone = current;
                                        if(current===total) {
                                            prevDone = 0;
                                        }
                                    }, function (zippedBlob) {
                                        hideZipProgressWindow();
                                        var dirName = files[0].dir;
                                        var nameToUse = '';
                                        var filename = files[0].file.name; //used for getting correct zip path
                                        var filenameParts = filename.split('/');
                                        filenameParts.shift();
                                        var fileNamePartsIndex = 1;
                                        var foundDirectoryName = false;

                                        for (var nps = 1; nps < filenameParts.length; nps++) {
                                            if (filenameParts[nps] === dirName) {
                                                foundDirectoryName = true;
                                            }
                                        }

                                        if (entry.name === filenameParts[0] && foundDirectoryName) {
                                            while (filenameParts[fileNamePartsIndex] !== dirName) {
                                                nameToUse = nameToUse + filenameParts[fileNamePartsIndex] + '/';
                                                fileNamePartsIndex++;
                                            }
                                        }
                                        nameToUse = nameToUse + dirName;
                                        //determine the correct zip path
                                        var correctZipPath = '';
                                        if (entry.name === dirName) {
                                            correctZipPath = entry.name;
                                        }
                                        else {
                                            correctZipPath = entry.name + '/' + nameToUse;
                                        }
                                        me.addFile(new File([zippedBlob], correctZipPath + '.zip', {
                                            type: 'application/zip',
                                            lastModified: Date.now()
                                        }));

                                        moveToNextDirectory();
                                    }, function () {
                                        moveToNextDirectory();
                                    });


                                    function moveToNextDirectory() {
                                        zipDirectoryCount--;
                                        if (zipDirectoryCount < 0) { //no more files/directories to zip
                                            console.timeEnd("ZIP DONE IN");
                                            for (var _up = 0; _up < filesToUpload.length; _up++) {
                                                me.addFile(filesToUpload[_up].file);
                                            }
                                            itemCount--;
                                            if (itemCount >= 0) { //move to next dropped item
                                                zipLoad(me.entries[itemCount]);
                                            }
                                        }
                                        else {
                                            zipDirectory(filesToZipPerDirectoryParts[zipDirectoryCount], me);
                                        }
                                    }
                                }
                            }
                            else { //no zip files
                                for (var up = 0; up < filesToUpload.length; up++) {
                                    me.addFile(filesToUpload[up].file);
                                }
                                itemCount--;
                                if (itemCount >= 0) {
                                    zipLoad(me.entries[itemCount]);
                                }
                            }

                        });
                    }
                    else if(entry) {//file
                        entry.file(function (_file) {
                            me.addFile(_file);
                            itemCount--;
                            if (itemCount >= 0) {
                                zipLoad(me.entries[itemCount]);
                            }
                        });

                    }

                }

                function checkPattern(tr, pattern) { //tree is the uploaded directory and pattern is the registered directory pattern by the module
                    if (pattern.DirectoryName) {
                        var dirExt = new RegExp(pattern.DirectoryName);
                        if (dirExt.test(tr.name)) {
                            if (pattern.File) { //match file in registered pattern if present
                                var fileMatch = false;
                                var fileExt = new RegExp(pattern.File);
                                for (var f = 0; f < tr.files.length; f++) {
                                    if (fileExt.test(tr.files[f].name)) {
                                        fileMatch = true;
                                    }
                                }

                                if (!fileMatch) {
                                    patternMatch = false;//File present in pattern but not in tree
                                    if(!tr.zip)
                                    tr.zip = false;
                                    return;
                                }
                            }
                            if (pattern.SubDirectory) {// match subDir in registered pattern if present
                                var subDirMatch = false;
                                var subDirExt = new RegExp(pattern.SubDirectory.DirectoryName);

                                for (var d = 0; d < tr.directories.length; d++) {
                                    if (subDirExt.test(tr.directories[d].name)) {
                                        subDirMatch = true;
                                        checkPattern(tr.directories[d], pattern.SubDirectory);
                                    }
                                }

                                if (!subDirMatch) {
                                    patternMatch = false; //SubDirectory present in pattern but not in tree
                                    if(!tr.zip)
                                    tr.zip = false;
                                    return;
                                }
                            }
                            //pattern matched
                            patternMatch = true;
                            tr.zip = true;
                        }
                        else if (tr.directories.length > 0) { // pattern directory name not matched with tree's directory, check all the subDirs of tree
                            var dirMatch = false;
                            var dirExt = new RegExp(pattern.DirectoryName);

                            for (var d = 0; d < tr.directories.length; d++) {
                                var trDir = tr.directories[d];
                                if (dirExt.test(tr.directories[d].name)) {
                                    dirMatch = true;
                                    checkPattern(tr.directories[d], pattern);
                                } else { //check in all subDirs
                                    if(trDir.directories.length > 0) {
                                        for(var trd=0; trd<trDir.directories.length; trd++) {
                                            checkPattern(trDir.directories[trd], pattern);
                                        }
                                    }
                                }
                            }
                            if (!dirMatch) { // no matched directories in tree
                                patternMatch = false;
                                if(!tr.zip)
                                tr.zip = false;
                                return;
                            }
                        }
                    }
                    else { // no pattern registered for the module
                        patternMatch = false;
                        if(!tr.zip)
                        tr.zip = false;
                        return;
                    }
                }

                function buildTree(files) {
                    var tree = {name: 'root', nodes: []};

                    for (var f = 0; f < files.length; f++) {
                        var parts = files[f].name.split('/');
                        parts.shift();
                        _buildTree(parts, files[f]);
                    }

                    return tree;

                    function _buildTree(parts, file) {
                        for (var j = 0; j < parts.length; j++) {
                            //var lastDir;
                            if (j === parts.length - 1) {
                                //leaf node
                                setChildren(tmp, parts[j], true, file);
                            }
                            else if (j > 0 && j < parts.length - 1) {
                                dirInTree = false;
                                //check if sub directory already there
                                for (var t = 0; t < tmp.directories.length; t++) {
                                    if (parts[j] === tmp.directories[t].name) {
                                        tmp = tmp.directories[t];
                                        dirInTree = true;
                                    }
                                }
                                //create sub directory
                                if (!dirInTree) {
                                    tmp = setChildren(tmp, parts[j], false);
                                }

                            }
                            else if (j === 0) {
                                //set root directory as a node
                                if (tree.nodes.length > 0) {
                                    var nodeFound = false;
                                    for (var _n = 0; _n < tree.nodes.length; _n++) {
                                        var n = tree.nodes[_n];
                                        if (n.name === parts[j]) {
                                            tmp = tree.nodes[_n];
                                            nodeFound = true;
                                        }
                                    }
                                    if (!nodeFound) {
                                        var x = tree.nodes.push(setNode({}, parts[j]));
                                        tmp = tree.nodes[x - 1];

                                    }
                                }
                                else {
                                    var x = tree.nodes.push(setNode({}, parts[j]));
                                    tmp = tree.nodes[x - 1];
                                }
                            }
                        }
                    }

                    function setChildren(tmp, val, isFile, _file) {
                        if (isFile) {
                            tmp.files.push({name: val, isFile: true, file: _file});
                            return tmp;
                        }
                        else {
                            var x = tmp.directories.push({name: val, isFile: false, files: [], directories: []});
                            return tmp.directories[x - 1];
                        }
                    }

                    function setNode(node, val) {
                        var tmp = node;

                        node.name = val;
                        node.files = [];
                        node.directories = [];
                        node.isFile = false;

                        return tmp;
                    }
                }

                function zipFiles(files, scope, onprogress, callback, onZipFail) {
                    var zipWriter, writer;

                    var addIndex = 0;

                    this.zipProgressName = '';
                    this.directoryBeingZipped = '';

                    var onerror = function () {
                        this.zipProgressWindow.hide();
                        onZipFail();
                        scope.uploadPanel.showErrorMsg("Zip Error", "Error zipping file - " + this.zipProgressName + " in directory - " + this.directoryBeingZipped);
                    };

                    if (zipWriter)
                        nextFile();
                    else {
                        writer = new zip.BlobWriter();
                        createZipWriter();
                    }

                    function nextFile() {
                        var file = files[addIndex].file;
                        this.directoryBeingZipped = files[addIndex].dir;
                        var filePath = file.name.split('/');
                        filePath.shift();
                        var newFileName = '';
                        for (var _fp = 1; _fp < filePath.length; _fp++) {
                            newFileName += filePath[_fp];
                            if (_fp !== filePath.length - 1) {
                                newFileName += '/';
                            }
                        }
                        this.zipProgressName = filePath[filePath.length-1];
                        this.currentZipFileText.update("Adding file - " + zipProgressName);
                        this.currentFileNumber.update(addIndex + '/' + files.length);
                        zipWriter.add(newFileName, new zip.BlobReader(file), function () {
                            addIndex++;
                            if (addIndex < files.length)
                                nextFile();
                            else
                                zipWriter.close(callback);
                        }, onprogress);
                    }

                    function createZipWriter() {
                        zip.createWriter(writer, function (writer) {
                            zipWriter = writer;

                            nextFile();
                        }, onerror);
                    }

                }

                function getFilesFromDirectory(files, entry, path, scope, e, callback) {
                    var dirReader, entriesReader;
                    dirReader = entry.createReader();
                    entriesReader = (function (scope) {
                        return function (entries) {
                            var _entry, i, len;
                            for (i = 0, len = entries.length; i < len; i++) {
                                _entry = entries[i];
                                if (_entry.isFile) {
                                    scope.fileCbCount++;
                                    _entry.file(function (file) {
                                        scope.fileCbCount--;

                                        file.name = path + "/" + file.name;
                                        var updatedFile = new File([file], path + "/" + file.name, {type: file.type});

                                        files.push(updatedFile);

                                        if (scope.cbCount === 0 && scope.fileCbCount === 0) {
                                            callback(files);
                                        }
                                    });
                                }
                                else if (_entry.isDirectory) {
                                    getFilesFromDirectory(files, _entry, _entry.fullPath, scope, e, callback);
                                }
                            }
                            if (entries.length >= 100) {
                                scope.cbCount++;
                                //read next batch (readEntries only read 100 files in 1 batch)
                                dirReader.readEntries(entriesReader, function (error) {
                                    return typeof console !== "undefined" && console !== null ? typeof console.log === "function" ? console.log(error) : void 0 : void 0;
                                });
                            }
                            scope.cbCount--;
                        };
                    })(scope);


                    scope.cbCount++;
                    dirReader.readEntries(entriesReader, function (error) {
                        return typeof console !== "undefined" && console !== null ? typeof console.log === "function" ? console.log(error) : void 0 : void 0;
                    });
                }

            }

        };

        Dropzone.prototype._originalDrop = function (e, scope) {
            var files, items;
            if (!e.dataTransfer) {
                return;
            }
            scope.emit("drop", e);
            files = e.dataTransfer.files;
            scope.emit("addedfiles", files);
            if (files.length) {
                items = e.dataTransfer.items;
                if (items && items.length && (items[0].webkitGetAsEntry != null)) {
                    scope._addFilesFromItems(items);
                }
                else {
                    scope.handleFiles(files);
                }
            }
        };

        var dropzone = new Dropzone(el, config);
        dropzone.peer = peer;
        dropzone.on("dragenter", zoneDragEnter);
        dropzone.on("dragover", zoneDragOver);
        dropzone.on("drop", zoneDrop);

        if (config.disabled)
            dropzone.disable();
        else
            document.body.appendChild(dropzone.element);

        registered.push(dropzone);

        registerEvents();

        //Show dropzone after registration
        if (config.show)
            showDropzones();

        return dropzone;
    }


    /** @scope LABKEY.internal.FileDrop */
    return {
        isSupported : function () {
            return window.Dropzone && window.Dropzone.isBrowserSupported();
        },

        registerDropzone : function (config) {
            if (this.isSupported()) {
                return _registerDropzone(config);
            }
        },

        showDropzones: function(){
            shouldShowDropzones = true;
            showDropzones();
        },

        hideDropzones: function() {
            shouldShowDropzones = false;
            hideDropzones();
        }
    }
};
