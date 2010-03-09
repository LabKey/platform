LABKEY.FilesWebPartPanelEx = Ext.extend(LABKEY.FilesWebPartPanel, {
    propertiesSelectResults:null,
    annotationConfig:null,
    extraFileProperties:null,
    detailsComponent:null,
    notifyGroupMembers:null,

    constructor : function(config)
    {
        LABKEY.FilesWebPartPanelEx.superclass.constructor.call(this, config);
    },

    initComponent : function()
    {
        LABKEY.FilesWebPartPanelEx.superclass.initComponent.call(this);

        this.enableAnnotation(this.annotationConfig);
    },

    getItems: function()
    {
        var items = LABKEY.FilesWebPartPanelEx.superclass.getItems.call(this);
        this.detailsPanel = new Ext.Panel({region: 'south',
            split: true,
            height: 80,
            minSize: 0,
            maxSize: 250,
            margins: '0 5 5 5',
            layout: 'fit'
        });

        items.push(this.detailsPanel);

        return items;
    },

    enableAnnotation: function ()
    {
        var annotationConfig = this.annotationConfig;

        this.on(BROWSER_EVENTS.transferstarted, this.annotateFiles, this);

        var securityCheckRequired = annotationConfig.containerPath != null && annotationConfig.containerPath != LABKEY.container.path;

        if (!securityCheckRequired)
        {
            annotationConfig.canUpdate = LABKEY.Security.currentUser.canUpdate;
            annotationConfig.canUpdateOwn = LABKEY.Security.currentUser.canUpdateOwn;
            annotationConfig.canInsert = LABKEY.Security.currentUser.canInsert;
            LABKEY.Query.selectRows({schemaName:annotationConfig.schemaName, queryName:annotationConfig.queryName, containerPath:annotationConfig.containerPath,
                successCallback:initFormCallback, errorCallback:function(result) {this.propertiesSelectResults = false}, scope:this});

        }
        else
        {
            function securityCallback(securityOptions)
            {
                annotationConfig.canUpdate = (securityOptions.container.permissions & LABKEY.Security.permissions.update) != 0;
                annotationConfig.canUpdateOwn = annotationConfig.canUpdate || ((securityOptions.container.permissions & LABKEY.Security.permissions.updateOwn ) != 0);
                annotationConfig.canInsert = (securityOptions.container.permissions & LABKEY.Security.permissions.insert) != 0;
                LABKEY.Query.selectRows({schemaName:annotationConfig.schemaName, queryName:annotationConfig.queryName, containerPath:annotationConfig.containerPath,
                    successCallback:initFormCallback, errorCallback:function(result) {this.propertiesSelectResults = false}, scope:this});
            }
            LABKEY.Security.getUserPermissions({containerPath:annotationConfig.containerPath, successCallback:securityCallback, scope:this});
        }

        function initFormCallback(result)
        {
            this.propertiesSelectResults = result;
            var selectRowsResult = this.propertiesSelectResults;
            var idCol = result.metaData.id;
            this.annotationConfig.id = idCol;
            this.on(BROWSER_EVENTS.transferstarted, this.annotateFiles, this);

            //Create a map from id to properties for looking things up later.
            //Arguably should create a store for this....
            this.extraFileProperties = {};
            for (var i = 0; i < result.rows.length; i++)
                this.extraFileProperties[result.rows[i][idCol]] = result.rows[i];

            //Hack to make long text fields not quite so tall..
            for (var i =  0; i < selectRowsResult.metaData.fields.length; i++)
            {
                var field = selectRowsResult.metaData.fields[i];
                if (field.rows && field.rows > 5)
                    field.rows = 5;
                //if annotations go in another folder might have to fix up lookups to work around
                if (field.lookup && annotationConfig.containerPath && null == field.lookup.container)
                    field.lookup.container = annotationConfig.containerPath;
            }
            this.detailsFormPanel = this.createFormPanel();
            //this.detailsFormPanel.setDisabled(true);
            this.detailsPanel.add(this.detailsFormPanel);
            this.detailsPanel.doLayout();
            var height = this.detailsFormPanel.getInnerHeight();
            this.detailsPanel.setHeight(height);
            this.enableFormItems(false);
            this.doLayout();
        }

        this.on(BROWSER_EVENTS.selectionchange, function()
        {
            var selections = this.grid.selModel.getSelections();
            var record = null;
            if (selections.length > 0)
                record = selections[0];

            this.updateFileProperties(record);
        }, this);
    },

    enableFormItems: function(state) {
        this.detailsFormPanel.items.each(function(field) {
            if (null != field.setDisabled)
                field.setDisabled(!state);
        })
    },

    saveFileProperties: function() {
        var id = this.selectedFile;
        if (null == id)
            return;

        var oldProps = this.extraFileProperties[id];
        var newProps = this.detailsFormPanel.getForm().getValues();

        if (null == oldProps)
        {
            newProps.id = id;
            newProps.url = this.toFullPath(id);
            newProps.name = this.fileSystem.fileName(newProps.url);

            this.extraFileProperties[id] = newProps;

            LABKEY.Query.insertRows({schemaName:this.annotationConfig.schemaName, queryName:this.annotationConfig.queryName, containerPath:this.annotationConfig.containerPath,
                rowDataArray:[newProps],
                successCallback:function() {},
                failureCallback:function (result) {alert("failure: " + result.exception);}});
        }
        else
        {
            Ext.apply(oldProps, newProps);
            LABKEY.Query.updateRows({schemaName:this.annotationConfig.schemaName, queryName:this.annotationConfig.queryName, containerPath:this.annotationConfig.containerPath,
                rowDataArray:[oldProps],
                successCallback:function() {},
                failureCallback:function (result) {alert("failure: " + result.exception);}});
        }
    },

    blankRecord: function() {
        var record = {};
        var selectRowsResult = this.propertiesSelectResults;
        if (null == selectRowsResult)
            return;

        var formFields = {};
        var columnModel = selectRowsResult.columnModel;
        for (var i = 0; i < columnModel.length; i++)
        {
            var fieldName = columnModel[i].dataIndex;
            formFields[fieldName] = null;
        }

        return formFields;
    },

    updateFileProperties: function(record) {
        if (!this.detailsFormPanel) //Not initialized yet
            return;
        if (!record || !record.data)
        {
            if (this.selectedFile && this.detailsFormPanel.getForm().isDirty())
                this.saveFileProperties();

            this.selectedFile = null;
            this.detailsFormPanel.getForm().setValues(this.blankRecord());
            return;
        }
        var data = record.data;
        var id = this.toPermanentPath(record.id);

        if (this.selectedFile == id) //Just a duplicate file
            return;

        this.selectedFile = id;
        var fileInfo = this.extraFileProperties[id];
        if (null == fileInfo)
        {
            fileInfo = this.blankRecord(); //Need to have values for every field
            fileInfo.id = id;
            fileInfo.url = record.id;
            fileInfo.name = record.data.name;
        }
        this.detailsFormPanel.getForm().setValues(fileInfo);

        //this.detailsFormPanel.getForm().setDirty(false);
        this.detailsFormPanel.setDisabled(false);
        this.enableFormItems(this.annotationConfig.canUpdate);
    },

    createFormPanel: function() {
        var selectRowsResult = this.propertiesSelectResults;
        if (null == selectRowsResult)
            throw "Properties not available in createFormPanel";

        var formFields = [];
        var columnModel = selectRowsResult.columnModel;
        for (var i = 0; i < columnModel.length; i++)
        {
            var fieldName = columnModel[i].dataIndex;
            if (fieldName.toLowerCase() == "id" || fieldName.toLowerCase() == "name" || fieldName.toLowerCase() == "url")
                continue;
            formFields.push({name:fieldName, lazyInit:false});
        }

        var formPanel = new LABKEY.ext.FormPanel({columnModel:columnModel, metaData:selectRowsResult.metaData, addAllFields:false, width:700,
            items:formFields, autoHeight:true, trackResetOnLoad:true});

        return formPanel;

    },

    getAnnotationFormPanel:function(callback, scope) {
        var selectRowsResult = this.propertiesSelectResults; //Local stash, because "this" doesn't make its way through inner functions...

        if (null === selectRowsResult) //Uninitialized. Wait for "enableAnnotation" to finish and try again
            this.getAnnotationFormPanel.defer(250,this,[callback, scope]);
        else if (selectRowsResult) //False is a flag that we're not configured properly. Bail if false
            callback.call(scope || this, this.createFormPanel());
    },

    notify: function (rowData) {
        if (null == this.notifyGroup)
            return;

        if (null == this.notifyGroupMembers)
        {
            //Haven't gotten the group members yet. Need to get them before emailing.
            LABKEY.Security.getUsers({group:this.notifyGroup, successCallback:function(userResponse) {
                this.notifyGroupMembers = userResponse.users || [];
                this.notify(rowData); //Try again
            },
            errorCallback:function(response) {
                if(console && console.log) {
                    console.log("Failed getting users for group " + this.notifyGroup);
                }
            },
            scope:this});
        }
        else
        {
            var messageContent;
            var subject = LABKEY.Security.currentUser.displayName
                   + " uploaded " + (rowData.length == 1 ? rowData[0].name : rowData.length + " files") + " to  " + LABKEY.container.path + LABKEY.container.name;
           messageContent = "<b>" + LABKEY.Security.currentUser.displayName
                   + " uploaded " + (rowData.length == 1 ? rowData[0].name : rowData.length + " files") + " to  <a href='" + window.location + "'> " + $h(LABKEY.container.path + LABKEY.container.name) +"</a></b></br>";
            messageContent += "<ul>";
            var prefix = window.location.protocol + "//" + window.location.host;
            for (var i = 0; i < rowData.length; i++)
            {
                var row = rowData[i];
                messageContent += "<li>";
                messageContent += "<a href='" + prefix + row.url + "'>" + $h(row.name) + "</a></li>";
                if (this.annotationConfig)
                {
                    messageContent += "<ul>";
                    for (var key in row)
                        if (key != "url" && key != "id" && key != "name" && typeof row[key] != "function")
                            messageContent += "<li>" + $h(key) + ":" + $h(row[key]);
                    messageContent += "</ul>";
                }
                messageContent += "</li>";
            }
            messageContent += "</ul>";

            var msgTo = [];
            for (var i = 0; i < this.notifyGroupMembers.length; i++)
            {
                msgTo.push(LABKEY.Message.createRecipient(LABKEY.Message.recipientType.to, this.notifyGroupMembers[i].email));
            }
            LABKEY.Message.sendMessage({
                msgFrom: this.notifyFrom || "donotreply@" + window.location.hostname,
                msgRecipients:msgTo,
                msgSubject: subject,
                msgContent: [LABKEY.Message.createMsgContent(LABKEY.Message.msgType.html, messageContent)],
                successCallback: function(result) {}
            });
            LABKEY.Message.createMsgContent(LABKEY.Message.msgType.html, messageContent);
        }
    },

    annotateFiles: function(transfer)
    {
        var rowData = [];
        for (var i = 0; i < transfer.files.length; i++)
            rowData.push({id:this.toPermanentPath(transfer.files[i].id), url:transfer.files[i].id, name:transfer.files[i].name});

        if (!this.annotationConfig.canInsert)
        {
            this.notify(rowData);
            return;
        }

        var annotationPanel = null;          //Possibly an async call to populate this
        var me = this; //stash in a local.

        this.getAnnotationFormPanel(startAnnotation, this);

        function startAnnotation(panel)
        {
            annotationPanel = panel;
            LABKEY.Query.deleteRows({schemaName:me.annotationConfig.schemaName, queryName:me.annotationConfig.queryName,  containerPath:this.annotationConfig.containerPath,
                rowDataArray:rowData, successCallback:showAnnotationDialog, errorCallback:showError, scope:me});
        }

        function showError(result) {
            alert("Cannot annotate " + result.exception);
        }

        function iconSrc(name)
        {
            var i = name.lastIndexOf(".");
            var ext = i >= 0 ? name.substring(i) : name;
            return LABKEY.contextPath + "/project/icon.view?name=" + ext;
        }

        function showAnnotationDialog(result)
        {
            var fileIndex = 0;
            var imgId = Ext.id();
            var nameId = Ext.id();
            var file = rowData[fileIndex];
            var fileHtml = "<div><img width=16 height=16 src='" + iconSrc("x.txt") + "' id='" + imgId + "'> <span style='font-weight:bold' id='" + nameId + "'>" + Ext.util.Format.htmlEncode(file.name) + "</span></div>";
            var buttons;
            var doneButton = new Ext.Button({text:"Done", handler:doDone});
            var nextButton;
            var prevButton;
            var formItems = [{html:fileHtml}, annotationPanel];
            if (rowData.length > 1)
            {
                var applyCheckbox = new Ext.form.Checkbox({boxLabel:"Apply to all remaining files", handler:applyAll });
                formItems.push(applyCheckbox);
                doneButton.setDisabled(true);
                prevButton = new Ext.Button({text:"< Prev", enabled:false, handler:doPrev});
                nextButton = new Ext.Button({text:"Next >", handler:doNext});
                buttons = [prevButton, nextButton, doneButton];
            }
            else
                buttons = [doneButton];

            function doNext()
            {
                saveFormValues();
                applyCheckbox.setValue(false);
                if (fileIndex < rowData.length)
                   fileIndex++;
                updateFormState();
            }

            function doPrev()
            {
                saveFormValues();
                applyCheckbox.setValue(false);
                if (fileIndex > 0)
                    fileIndex --;
                updateFormState();
            }

            function doDone()
            {
                if (applyCheckbox && applyCheckbox.getValue())     //If checkbox is checked, make sure most recent values are copied.
                    applyAll(applyCheckbox, true);
                saveFormValues();
                insertRows();
                me.notify(rowData);
            }

            function saveFormValues()
            {
                file = rowData[fileIndex];
                Ext.apply(file, annotationPanel.getForm().getValues());
            }

            function updateFormState()
            {
                file = rowData[fileIndex];
                Ext.getDom(imgId).src = iconSrc(file.name);
                Ext.getDom(nameId).innerHTML = Ext.util.Format.htmlEncode(file.name);
                if (rowData.length > 1)
                {
                    doneButton.setDisabled(fileIndex < rowData.length - 1 && !applyCheckbox.getValue());
                    prevButton.setDisabled(fileIndex == 0);
                    nextButton.setDisabled(fileIndex == rowData.length -1);
                }
                annotationPanel.getForm().setValues(file);
            }

            var win = new Ext.Window({
                autoHeight:true,
                width:704,
                modal:true,
                style:"z-index:20001",
                top:this.getBox().y + 160,
                title:rowData.length > 1 ? "Save information for " + rowData.length + " files." : "Save information for file " + file.name,
                items: formItems,
                buttons:buttons
            });
            win.show();
            updateFormState();

            function applyAll(checkbox, state)
            {
                if (state)
                    for (var i = fileIndex + 1; i < rowData.length; i++)
                        Ext.applyIf(rowData[i], annotationPanel.getForm().getValues());
                updateFormState();
            }

            function insertRows() {
                LABKEY.Query.insertRows({schemaName:me.annotationConfig.schemaName, queryName:me.annotationConfig.queryName, containerPath:me.annotationConfig.containerPath,
                    rowDataArray:rowData,
                    successCallback:function() {updateCachedProperties(); win.close();},
                    failureCallback:function (result) {alert("failure: " + result.exception);}});
            }

            function updateCachedProperties()
            {
                var cachedProperties = me.extraFileProperties;
                for (var i = 0; i < rowData.length; i++)
                {
                    var row = rowData[i];
                    var cachedRow = cachedProperties[rowData[i].id];
                    if (null == cachedRow)
                    {
                        cachedRow = {};
                        cachedProperties[row.id] = cachedRow;
                    }

                    Ext.apply(cachedRow, row);
                }
            }

            function onCancel() {
                win.close();
            }
        }
    },

    //In 10.1, file ids in the UI are contextPath/_webdav/container path/file path/filename  ENCODEDED
    //Permanement path is containerGuid/filePath, unencoded
    toPermanentPath: function(p)
    {
        var pathElements = p.split("/");
        if (pathElements[0] == "")
            pathElements.shift();
        if (LABKEY.contextPath.length)
            pathElements.shift();
        pathElements.shift(); //get rid of /_webdav

        for (var i = 0; i < pathElements.length; i++)
            pathElements[i] = decodeURIComponent(pathElements[i]);

        var url = "/" + pathElements.join("/");
        var containerPath = LABKEY.container.path.toLowerCase();
        if (url.toLowerCase().indexOf(containerPath) != 0)
            throw "Unmatched container in toPermanentPath";

        url = url.substring(containerPath.length);
        return LABKEY.container.id + url;
    },

    //In 10.1, file ids in the UI are contextPath/_webdav/container path/file path/filename  ENCODEDED
    toFullPath: function(p)
    {
        var pathElements = p.split("/");
        for (var i = 0; i < pathElements.length; i++)
            pathElements[i] = encodeURIComponent(pathElements[i]);

        if (p.indexOf(LABKEY.container.id) == 0)
            pathElements.shift();

        return LABKEY.contextPath + "/_webdav" + LABKEY.container.path + pathElements.join("/");
    }
});
