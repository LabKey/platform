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
         *  overriding drop method of Dropzone.js to zip matching directory patterns before upload.
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

                for (var _it = 0; _it < items.length; _it++) {
                    if (items[_it].webkitGetAsEntry != null) {
                        this.entries.push(items[_it].webkitGetAsEntry())
                    }
                }

                console.log(this.entries);

                var patternMatch = false;
                var itemCount = this.entries.length - 1; // to keep track of all items in drag event

                zipLoad(this.entries[itemCount]);

                function zipLoad(entry) {
                    if (entry.isDirectory) {
                        files = [];
                        me.cbCount = 0; //to keep track of directory level callbacks
                        me.fileCbCount = 0; //to keep track of file level callbacks
                        getFilesFromDirectory(files, entry, entry.fullPath, me, e, function (files) {
                            //check for matching directory patterns
                            var tree = buildTree(files);
                            //check each pattern for each node
                            for (var _p = 0; _p < patterns.length; _p++) {
                                for (var _tn = 0; _tn < tree.nodes.length; _tn++) {
                                    checkPattern(tree.nodes[_tn], patterns[_p]);
                                }

                            }

                            var filesToZip = [];
                            var filesToUpload = [];
                            for (var _tn = 0; _tn < tree.nodes.length; _tn++) {
                                var _node = tree.nodes[_tn];
                                sepZipFiles(_node);
                            }

                            function sepZipFiles(node) {
                                var nodeName = node.name;
                                if (node.zip) {
                                    //grab all the files from this directory
                                    grabFilesFromDirectory(node, true, nodeName);

                                }
                                else if (node.directories.length > 0) {
                                    for (var _nf = 0; _nf < node.files.length; _nf++) {
                                        filesToUpload.push({file: node.files[_nf].file, dir: nodeName});
                                    }
                                    for (var _nd = 0; _nd < node.directories.length; _nd++) {
                                        sepZipFiles(node.directories[_nd]);
                                    }
                                }
                                else {
                                    //grab all the files from this directory
                                    grabFilesFromDirectory(node, false, nodeName);
                                }
                            }

                            function grabFilesFromDirectory(node, zip, _nodeName) {
                                var nodeName = _nodeName;
                                if (node.files.length > 0) { //all files in node
                                    for (var _nf = 0; _nf < node.files.length; _nf++) {
                                        if (zip) {
                                            filesToZip.push({file: node.files[_nf].file, dir: nodeName});
                                        }
                                        else {
                                            filesToUpload.push({file: node.files[_nf].file, dir: nodeName});
                                        }

                                    }
                                }
                                if (node.directories.length > 0) { //all files in subDirs
                                    for (var _nd = 0; _nd < node.directories.length; _nd++) {
                                        grabFilesFromDirectory(node.directories[_nd], zip, nodeName);
                                    }
                                }
                            }

                            if (filesToZip.length > 0) {
                                //separating filesToZip to its own directory
                                var _filesToZip = [];
                                var temp = [];
                                var _tempName = filesToZip[0].dir;
                                temp.push(filesToZip[0]);
                                for (var _fz = 1; _fz < filesToZip.length; _fz++) {
                                    if (filesToZip[_fz].dir === _tempName) {
                                        temp.push(filesToZip[_fz]);
                                    }
                                    else {
                                        _filesToZip.push(temp);
                                        temp = [];
                                        _tempName = filesToZip[_fz].dir;
                                        temp.push(filesToZip[_fz]);
                                    }
                                }

                                if (temp.length > 0) {
                                    _filesToZip.push(temp);
                                }

                                //separate directories in parts for 4GB limit
                                var _filezToZip = [];
                                for (var _ftz = 0; _ftz < _filesToZip.length; _ftz++) {
                                    var kb = 1024;
                                    var mb = kb * 1024;
                                    var gb = mb * 1024;
                                    var limit = 4 * gb; // 4GB limit
                                    var sum = 0;
                                    var _holder = _filesToZip[_ftz];

                                    for (var _h = 0; _h < _holder.length; _h++) {
                                        sum = +_holder[_h].file.size;
                                    }

                                    if (sum > limit) { //create parts
                                        createParts(_holder, limit);
                                    }
                                    else {
                                        _filezToZip.push(_holder);
                                    }
                                }

                                function createParts(_holder, limit) {
                                    var _temp = [];
                                    var sum = 0;
                                    var ind = 1;
                                    for (var _s = 0; _s < _holder.length; _s++) {
                                        sum += _holder[_s].file.size;
                                        if (_holder[_s].file.size >= limit) {
                                            filesToUpload.push(_holder[_s]);
                                        }
                                        else if (sum >= limit) {
                                            _filezToZip.push(_temp);
                                            ind++;
                                            _temp = [];
                                            sum = 0;
                                        }
                                        else {
                                            _holder[_s].dir = _holder[_s].dir + ind;
                                            _temp.push(_holder[_s]);
                                        }
                                    }

                                    if (_temp.length > 0) {
                                        _filezToZip.push(_temp);
                                    }
                                }

                                var zC = _filezToZip.length - 1;
                                console.time("ZIP_START");
                                _zipFiles(_filezToZip[zC], me);

                                //zip each directory
                                function _zipFiles(files, me) {
                                    console.log("zipping",_filezToZip[zC][0].dir);
                                    console.time(_filezToZip[zC][0].dir);
                                    zipFiles(files, me, function (zippedBlob) {
                                        console.timeEnd(_filezToZip[zC][0].dir);
                                        var dirName = _filezToZip[zC][0].dir;
                                        var nameToUse = '';
                                        var _name = _filesToZip[zC][0].file.name;
                                        var _nameParts = _name.split('/');
                                        _nameParts.shift();
                                        var _np = 1;
                                        var flag = false;

                                        for (var _nps=1; _nps<_nameParts.length; _nps++) {
                                            if(_nameParts[_nps] === dirName) {
                                                flag = true;
                                            }
                                        }

                                        if(entry.name === _nameParts[0] && flag) {
                                            while (_nameParts[_np] !== dirName) {
                                                nameToUse = nameToUse + _nameParts[_np] + '/';
                                                _np++;
                                            }
                                        }
                                        nameToUse = nameToUse + dirName;
                                        //determine the correct zip path
                                        var _path = '';
                                        if (entry.name === dirName) {
                                            _path = entry.name;
                                            console.log("Zipped - " + _path);
                                        }
                                        else {
                                            _path = entry.name + '/' + nameToUse;
                                            console.log("Zipped - " + _path);
                                        }
                                        me.addFile(new File([zippedBlob], _path + '.zip', {
                                            type: 'application/zip',
                                            lastModified: Date.now()
                                        }));

                                        zC--;

                                        if (zC < 0) { //no more files/directories to zip
                                            console.timeEnd("ZIP_START");
                                            for (var _up = 0; _up < filesToUpload.length; _up++) {
                                                me.addFile(filesToUpload[_up].file);
                                            }
                                            itemCount--;
                                            if (itemCount >= 0) {
                                                zipLoad(me.entries[itemCount]);
                                            }
                                        }
                                        else {
                                            _zipFiles(_filezToZip[zC], me);
                                        }
                                    });
                                }
                            }
                            else { //no zip files
                                for (var _up = 0; _up < filesToUpload.length; _up++) {
                                    me.addFile(filesToUpload[_up].file);
                                }
                                itemCount--;
                                if (itemCount >= 0) {
                                    zipLoad(me.entries[itemCount]);
                                }
                            }

                        });
                    }
                    else {//file
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
                                for (var _f = 0; _f < tr.files.length; _f++) {
                                    if (fileExt.test(tr.files[_f].name)) {
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

                                for (var _d = 0; _d < tr.directories.length; _d++) {
                                    if (subDirExt.test(tr.directories[_d].name)) {
                                        subDirMatch = true;
                                        checkPattern(tr.directories[_d], pattern.SubDirectory);
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

                            for (var _d = 0; _d < tr.directories.length; _d++) {
                                var trDir = tr.directories[_d];
                                if (dirExt.test(tr.directories[_d].name)) {
                                    dirMatch = true;
                                    checkPattern(tr.directories[_d], pattern);
                                } else { //check in all subDirs
                                    if(trDir.directories.length > 0) {
                                        for(var _trd=0; _trd<trDir.directories.length;_trd++) {
                                            checkPattern(trDir.directories[_trd], pattern);
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

                    function _buildTree(parts, _file) {
                        for (var _j = 0; _j < parts.length; _j++) {
                            //var lastDir;
                            if (_j === parts.length - 1) {
                                //leaf node
                                setChildren(tmp, parts[_j], true, _file);
                            }
                            else if (_j > 0 && _j < parts.length - 1) {
                                dirInTree = false;
                                //check if sub directory already there
                                for (var _t = 0; _t < tmp.directories.length; _t++) {
                                    if (parts[_j] === tmp.directories[_t].name) {
                                        tmp = tmp.directories[_t];
                                        dirInTree = true;
                                    }
                                }
                                //create sub directory
                                if (!dirInTree) {
                                    tmp = setChildren(tmp, parts[_j], false);
                                }

                            }
                            else if (_j === 0) {
                                //set root directory as a node
                                if (tree.nodes.length > 0) {
                                    var nodeFound = false;
                                    for (var _n = 0; _n < tree.nodes.length; _n++) {
                                        var n = tree.nodes[_n];
                                        if (n.name === parts[_j]) {
                                            tmp = tree.nodes[_n];
                                            nodeFound = true;
                                        }
                                    }
                                    if (!nodeFound) {
                                        var x = tree.nodes.push(setNode({}, parts[_j]));
                                        tmp = tree.nodes[x - 1];

                                    }
                                }
                                else {
                                    var x = tree.nodes.push(setNode({}, parts[_j]));
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

                    for (var _f = 0; _f < files.length; _f++) {
                        var parts = files[_f].name.split('/');
                        parts.shift();
                        _buildTree(parts, files[_f]);
                    }

                    return tree;
                }

                function zipFiles(files, scope, callback) {
                    var zipWriter, writer;

                    var addIndex = 0;

                    function nextFile() {
                        var file = files[addIndex].file;
                        var filePath = file.name.split('/');
                        filePath.shift();
                        var newFileName = '';
                        for (var _fp = 1; _fp < filePath.length; _fp++) {
                            newFileName += filePath[_fp];
                            if (_fp !== filePath.length - 1) {
                                newFileName += '/';
                            }
                        }
                        console.time(newFileName);
                        zipWriter.add(newFileName, new zip.BlobReader(file), function () {
                            console.timeEnd(newFileName);
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

                    if (zipWriter)
                        nextFile();
                    else {
                        writer = new zip.BlobWriter();
                        createZipWriter();
                    }

                }

                function getFilesFromDirectory(files, entry, path, scope, e, callback) {
                    var dirReader, entriesReader;
                    dirReader = entry.createReader();
                    entriesReader = (function (scope) {
                        return function (entries) {
                            var _entry, _i, _len;
                            for (_i = 0, _len = entries.length; _i < _len; _i++) {
                                _entry = entries[_i];
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
