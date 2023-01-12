/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if (!LABKEY.internal)
    LABKEY.internal = {};

LABKEY.internal.ZipLoad = new function () {

    var dirPatterns;
    var dropZone;
    var filePattern =new RegExp(".*\\.zipme$");
    var checkReadEntriesByBatch;
    var testFile = false;

    var zipWriter, writer;
    var addIndex;

    var itemsDropped;
    var itemCount;   //to keep track of items dropped
    var dirCbCount;  //to keep track of directory level callbacks
    var fileCbCount; //to keep track of file level callbacks
    var tree;

    var allFiles;
    var filesToZip;
    var filesToUpload =[];
    var filesToZipPerDirectory;
    var filesToZipPerDirectoryParts;
    var zipDirectoryCount = 0;
    var parentItemName;
    var filesBeingZipped;
    var testFilesToZip;

    var totalSize;
    var totalDone;
    var prevDone;

    function onerror() {
        this.zipProgressWindow.hide();
        zipFail();
        dropZone.uploadPanel.showErrorMsg("Zip Error", "Error zipping file - " + this.zipProgressName + " in directory - " + this.directoryBeingZipped);
    }

    function getCurrentZipFile() {
        if (!this.currentZipFileText) {
            this.currentZipFileText = Ext4.create('Ext.form.Label', {
                text: '',
                style: 'display: inline-block ;text-align: left',
                width: 250,
                border: false
            });
        }
        return this.currentZipFileText;
    }

    function getCurrentFileNumber() {
        if(!this.currentFileNumber) {
            this.currentFileNumber = Ext4.create('Ext.form.Label', {
                text: '',
                style: 'display: inline-block ;text-align: right',
                width: 250,
                border: false
            });
        }
        return this.currentFileNumber;
    }

    function getStatusText() {
        if(!this.statusText) {
            this.statusText = Ext4.create('Ext.form.Label', {
                text: '',
                style: 'display: inline-block ;text-align: center',
                width: 500,
                margin: 4,
                border: false
            });
        }
        return this.statusText;
    }

    function getProgressBar() {
        if(!this.progressBar) {
            this.progressBar = Ext4.create('Ext.ProgressBar', {
                width: 500,
                height: 25,
                border: false,
                autoRender: true,
                style: 'background-color: transparent; -moz-border-radius: 5px; -webkit-border-radius: 5px; -o-border-radius: 5px; -ms-border-radius: 5px; -khtml-border-radius: 5px; border-radius: 5px;'
            });
        }
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

    function getZipError() {
        if(!this.zipError) {
            this.zipError = Ext4.create('Ext.form.Label', {
                text: '',
                style: 'display: inline-block ;text-align: center',
                width: 500,
                margin: 4,
                border: false
            });
        }
        return this.zipError;
    }

    function getZipErrorWindow() {
        if(!this.zipErrorWindow) {
            this.zipErrorWindow = Ext4.create('Ext.window.Window', {
                title: 'Zip Error',
                layout: 'vbox',
                bodyPadding: 5,
                border: false,
                closable: false,
                items: [getZipError()],
                autoShow : true,
                buttons : [{
                    xtype: 'button',
                    align: 'right',
                    text: 'OK',
                    scope: this,
                    handler: function(){
                        this.zipErrorWindow.hide();
                    }
                }]
            });
        }
        return this.zipErrorWindow;
    }

    function showZipProgressWindow() {
        getZipProgressWindow().show();
    }

    function hideZipProgressWindow() {
        this.zipProgressWindow.hide();
    }

    function nextFile() {
        var file = filesBeingZipped[addIndex].file;
        this.directoryBeingZipped = filesBeingZipped[addIndex].dir;
        var filePath = file.fullPath.split('/');
        filePath.shift();
        var zipProgressName = filePath[filePath.length-1];
        var fileZipPath = '';

        for(var fp=0; fp<filePath.length; fp++) {
            if(this.directoryBeingZipped === filePath[fp]) {
                var index = fp;
                while(index+1 < filePath.length) {
                    fileZipPath += filePath[++index] +'/';
                }
            }
        }

        getCurrentZipFile().update("Adding file - " + zipProgressName);
        getCurrentFileNumber().update(addIndex + '/' + filesBeingZipped.length);

        // Check for a path mismatch for real patterns, but not for our simple test case
        if(!fileZipPath.length>0 && this.directoryBeingZipped !== 'TestZipMeDir') {
            dropZone.uploadPanel.showErrorMsg("Relative path of file - " + file.name + " is incorrect");
            console.log("Relative path of file - " + file.name + " is incorrect");
            fileZipPath = file.name;
        }

        //Issue 38826: removing extra slash from file path
        zipWriter.add(fileZipPath.slice(0,-1), new zip.BlobReader(file), function () {
            addIndex++;
            fileZipPath = '';
            if (addIndex < filesBeingZipped.length)
                nextFile();
            else
                zipWriter.close(zipSuccess);
        }, zipProgress);
    }

    function createZipWriter() {
        zip.createWriter(writer, function (writer) {
            zipWriter = writer;

            nextFile();
        }, onerror);
    }

    function moveToNextDirectory() {
        zipDirectoryCount--;
        if (zipDirectoryCount < 0) { //no more files/directories to zip
            for (var _up = 0; _up < filesToUpload.length; _up++) {
                dropZone.addFile(filesToUpload[_up].file);
            }
            itemCount--;
            if (itemCount >= 0) { //move to next dropped item
                parentItemName = itemsDropped[itemCount];
                _zipLoad(itemsDropped[itemCount]);
            }
        }
        else {
            zipDirectory(filesToZipPerDirectoryParts[zipDirectoryCount]);
        }
    }

    function zipFail() {
        moveToNextDirectory();
    }

    function zipProgress(current, total) {
        totalDone = (current - prevDone) + totalDone ;
         getProgressBar().updateProgress(totalDone / totalSize);
        prevDone = current;
        if(current===total) {
            prevDone = 0;
        }
    }

    function zipSuccess(zippedBlob) {
        getStatusText().update('');
        hideZipProgressWindow();
        var dirName = filesBeingZipped[0].dir;
        var filename = filesBeingZipped[0].file.fullPath; //used for getting correct zip path
        var filenameParts = filename.split('/');
        filenameParts.shift();
        var correctPath = '';

        //determine the correct zip path
        for (var nps = 0; nps < filenameParts.length; nps++) {
            if (filenameParts[nps] === dirName) {
                if(nps === 0) {
                    correctPath = dirName;
                }
                else {
                    correctPath +=  dirName;
                }
                break;
            }
            else {
                correctPath += filenameParts[nps] +'/';
            }
        }

        var zipBlobBlob = new Blob([zippedBlob], {type: 'application/zip'});
        zipBlobBlob.lastModified = Date.now();
        zipBlobBlob.name = dirName + '.zip';

        if(testFile) {
            zipBlobBlob.fullPath = dirName + '.zip';
        }
        else {
            zipBlobBlob.fullPath = correctPath + '.zip';
        }
        dropZone.addFile(zipBlobBlob);

        moveToNextDirectory();
    }

    function zipFiles(files) {
        addIndex = 0;
        zipWriter = null;
        writer = null;

        filesBeingZipped = files;

        this.zipProgressName = '';
        this.directoryBeingZipped = '';

        if (zipWriter)
            nextFile();
        else {
            writer = new zip.BlobWriter();
            createZipWriter();
        }

    }

    function zipDirectory(files) {
        totalSize = 0;
        totalDone = 0;
        prevDone = 0;

        if(files) {
            for (var s = 0; s < files.length; s++) {
                totalSize += files[s].file.size;
            }
            showZipProgressWindow();
            getStatusText().update("Zipping directory " + files[0].dir);
            zipFiles(files);
        }
    }

    function sepZipFilesPerDirectory() {

        //separating filesToZip to its own directory

        var filePerZipDir = [];
        var tempName = filesToZip[0].dir;
        filePerZipDir.push(filesToZip[0]);
        for (var fz = 1; fz < filesToZip.length; fz++) {
            if (filesToZip[fz].dir === tempName) {
                filePerZipDir.push(filesToZip[fz]);
            }
            else {
                filesToZipPerDirectory.push(filePerZipDir);
                filePerZipDir = [];
                tempName = filesToZip[fz].dir;
                filePerZipDir.push(filesToZip[fz]);
            }
        }

        if (filePerZipDir.length > 0) {
            filesToZipPerDirectory.push(filePerZipDir);
        }

    }

    function createPartialZipDirs(holder, limit) {
        var filesPerZipLimit = [];
        var sum = 0;
        var ind = 1;
        for (var s = 0; s < holder.length; s++) {
            if (holder[s].file.size >= limit) {
                filesToUpload.push(holder[s]);
            } else {
                sum += holder[s].file.size;
                if (sum >= limit) {
                    for (var tp = 0; tp < filesPerZipLimit.length; tp++) {
                        filesPerZipLimit[tp].dir = filesPerZipLimit[tp].dir + ind;
                    }
                    filesToZipPerDirectoryParts.push(filesPerZipLimit);
                    ind++;
                    filesPerZipLimit = [];
                    sum = 0;
                }
                else {
                    filesPerZipLimit.push(holder[s]);
                }
            }
        }

        if (filesPerZipLimit.length > 0) {
            if (ind > 1) {
                for (var tp = 0; tp < filesPerZipLimit.length; tp++) {
                    filesPerZipLimit[tp].dir = filesPerZipLimit[tp].dir + ind;
                }
            }
            filesToZipPerDirectoryParts.push(filesPerZipLimit);
        }
    }

    function sepZipFilesPerDirectoryParts() {
        //separate directories in parts for 4GB limit
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
                getZipError().update('The directory that you have selected to upload, ' + parentItemName +', exceeds 4GB in size.'+ '\\n' + 'Please manually create a Zip file for it, and upload that file instead.');
                getZipErrorWindow().show();
                filesToUpload = [];
                zipFail();
                // createPartialZipDirs(holder, limit);
            }
            else {
                filesToZipPerDirectoryParts.push(holder);
            }
        }
    }

    function grabFilesFromDirectory(node, zip, nodeName) {
        if (node.files.length > 0) { //all files in node
            for (var nf = 0; nf < node.files.length; nf++) {
                if (zip) {
                    filesToZip.push({file: node.files[nf].file, dir: nodeName, realDir: nodeName});
                }
                else {
                    filesToUpload.push({file: node.files[nf].file, dir: nodeName, realDir: nodeName});
                }
            }
        }
        if (node.directories.length > 0) { //all files in subDirs
            for (var nd = 0; nd < node.directories.length; nd++) {
                grabFilesFromDirectory(node.directories[nd], zip, nodeName);
            }
        }
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
                        //SubDirectory present in pattern but not in tree
                        if(!tr.zip)
                            tr.zip = false;
                        return;
                    }
                }
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
                    if(!tr.zip)
                        tr.zip = false;
                    return;
                }
            }
        }
        else { // no pattern registered for the module
            if(!tr.zip)
                tr.zip = false;
            return;
        }
    }

    function _buildTree(parts, file) {
        var dirInTree, nextDir ={};
        for (var j = 0; j < parts.length; j++) {
            //var lastDir;
            if (j === parts.length - 1) {
                //leaf node
                setChildren(nextDir, parts[j], true, file);
            }
            else if (j > 0 && j < parts.length - 1) {
                dirInTree = false;
                //check if sub directory already there
                for (var t = 0; t < nextDir.directories.length; t++) {
                    if (parts[j] === nextDir.directories[t].name) {
                        nextDir = nextDir.directories[t];
                        dirInTree = true;
                    }
                }
                //create sub directory
                if (!dirInTree) {
                    nextDir = setChildren(nextDir, parts[j], false);
                }

            }
            else if (j === 0) {
                //set root directory as a node
                if (tree.nodes.length > 0) {
                    var nodeFound = false;
                    for (var _n = 0; _n < tree.nodes.length; _n++) {
                        var n = tree.nodes[_n];
                        if (n.name === parts[j]) {
                            nextDir = tree.nodes[_n];
                            nodeFound = true;
                        }
                    }
                    if (!nodeFound) {
                        var nodeIndex = tree.nodes.push(setNode({}, parts[j]));
                        nextDir = tree.nodes[nodeIndex - 1];

                    }
                }
                else {
                    nodeIndex = tree.nodes.push(setNode({}, parts[j]));
                    nextDir = tree.nodes[nodeIndex - 1];
                }
            }
        }
    }

    function setChildren(nextDirectory, val, isFile, _file) {
        if (isFile) {
            nextDirectory.files.push({name: val, isFile: true, file: _file});
            return nextDirectory;
        }
        else {
            var x = nextDirectory.directories.push({name: val, isFile: false, files: [], directories: []});
            return nextDirectory.directories[x - 1];
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

    function buildTree(files) {
        tree = {name: 'root', nodes: []};

        for (var f = 0; f < files.length; f++) {
            var parts = files[f].fullPath.split('/');
            parts.shift();
            _buildTree(parts, files[f]);
        }

        return tree;
    }

    function filesSuccess() {

        filesToZip =[];
        filesToUpload = [];
        filesToZipPerDirectory = [];
        filesToZipPerDirectoryParts = [];
        zipDirectoryCount = 0;

        var tree = buildTree(allFiles);

        //check each pattern for each node
        for (var p = 0; p < dirPatterns.length; p++) {
            for (var tn = 0; tn < tree.nodes.length; tn++) {
                checkPattern(tree.nodes[tn], dirPatterns[p]);
            }
        }

        for (var tn = 0; tn < tree.nodes.length; tn++) {
            var node = tree.nodes[tn];
            sepZipFiles(node);
        }

        if (filesToZip.length > 0) {
            sepZipFilesPerDirectory();
            sepZipFilesPerDirectoryParts();

            //zip each directory
            zipDirectoryCount = filesToZipPerDirectoryParts.length-1;
            zipDirectory(filesToZipPerDirectoryParts[zipDirectoryCount]);
        }
        else {
            //Issue 37867: Row creation in Exp.Data in Panorama Folder
            var entry = itemsDropped[itemCount];
            if (entry.isDirectory) {
                if (dropZone.options.acceptDirectory)
                    dropZone.options.acceptDirectory.call(dropZone, entry);
            }
            for (var up = 0; up < filesToUpload.length; up++) {
                dropZone.addFile(filesToUpload[up].file);
            }
            itemCount--;
            if (itemCount >= 0) { //move to next dropped item
                parentItemName = itemsDropped[itemCount];
                _zipLoad(itemsDropped[itemCount]);
            }
        }
    }

    function getFilesFromDirectory(allFiles, entry, scope, callback) {
        var dirReader, entriesReader;
        dirReader = entry.createReader();
        entriesReader = (function (scope) {
            return function (entries) {
                var _entry, i, len;
                for (i = 0, len = entries.length; i < len; i++) {
                    _entry = entries[i];
                    if (_entry.isFile) {
                        fileCbCount++;
                        _entry.file(function (file) {
                            fileCbCount--;

                            file.fullPath = entry.fullPath + "/" + file.name;

                            allFiles.push(file);

                            if (dirCbCount === 0 && fileCbCount === 0) {
                                callback();
                            }
                        });
                    }
                    else if (_entry.isDirectory) {
                        getFilesFromDirectory(allFiles, _entry, scope, callback);
                    }
                }
                if (!checkReadEntriesByBatch) {
                    if (entries.length >= 100) {
                        dirCbCount++;
                        //read next batch (readEntries only read 100 files in 1 batch for chrome)
                        dirReader.readEntries(entriesReader, function (error) {
                            return console.log(error);
                        });
                    }
                }
               dirCbCount--;
            };
        })(scope);


        dirCbCount++;
        dirReader.readEntries(entriesReader, function (error) {
            return console.log(error);
        });
    }

    function checkFilePattern(file) {
        return filePattern.test(file.name);
    }

    function _zipLoad(entry) {
        if (entry && entry.isDirectory) {
            dirCbCount = 0;
            fileCbCount = 0;

            allFiles = [];
            parentItemName = entry.name;
            getFilesFromDirectory(allFiles, entry, dropZone, filesSuccess);
        }
        else if(entry) {//file
            //code for file pattern - test integration
            var _file = entry.isFile ? entry : entry.getAsFile();
            if (checkFilePattern(_file)) {
                testFile = true;
                parentItemName = 'TestZipMeDir';
                _file.fullPath =  _file.name;
                testFilesToZip.push({file: _file, dir: parentItemName});
            }
            else {
                _file.file(function (file) {
                    dropZone.addFile(file);
                });
            }
            itemCount--;
            if (itemCount >= 0) {
                _zipLoad(itemsDropped[itemCount]);
            }
            else {
                if (testFilesToZip.length > 0) {
                    zipDirectory(testFilesToZip);
                }
            }
        }
    }

    return {
        zipLoad: function (entries, me, patterns, isFirefox) {
            dirPatterns = patterns;
            dropZone = me;
            itemsDropped = entries;
            itemCount = itemsDropped.length-1;
            testFilesToZip = [];
            checkReadEntriesByBatch = isFirefox;
            _zipLoad(itemsDropped[itemCount]);
        }
    }
};