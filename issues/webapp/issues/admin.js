/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
// when the specific default user select is changed, track that change
function updateCurDefaultUser() {
    var e = document.getElementsByName("defaultUser")[0];
    curDefaultUser = e.options[e.selectedIndex].value;
}

// when there is a change to the selected group, update the list of assigned to users
// TODO: consider better name
function updateAssignedToUser() {
    //NOTE: need to handle special user groups
    var e = document.getElementsByName("assignedToGroup")[0]

    if (e.length != 0)
    {
        var config = {allMembers: true, active: true, permissions: LABKEY.Security.effectivePermissions.update};
        // if "All project Users" is selected than groupId is not used to obtain all project users
        if (!document.getElementsByName("assignedToMethod")[0].checked)
        {
            config["groupId"] = parseInt(e.options[e.selectedIndex].value);
        }

        config["success"] = function(data) {
            var e = document.getElementsByName("defaultUser")[0];
            e.options.length = 0;

            Ext4.each(data.users, function(user){
                var option = document.createElement("option");
                var uid = user.userId;
                option.text = user.displayName;
                option.value = uid;
                if (uid == curDefaultUser)
                    option.selected = true;

                e.add(option);
            }, this);
        }

        LABKEY.Security.getUsers(config);
    }
}

// load the defaultUser list with current user select
// NOTE: at this point curDefaultUser needs to have been set!
Ext4.onReady(updateAssignedToUser);

// create a combo box with text label (without label formating) for moving an issue
// TODO: consider abstraction here.
Ext4.onReady( function() {
    if (!Ext4.ModelManager.isRegistered('Issues.model.Containers')) {
        Ext4.define('Issues.model.Containers', {
            extend: 'Ext.data.Model',
            fields: [
                {name: 'containerId', type: 'string'},
                {name: 'containerPath', type: 'string'}
            ]
        });
    }

    var store = Ext4.create('Ext.data.Store', {
        model: 'Issues.model.Containers',
        autoLoad: true,
        proxy: {
            type: 'ajax',
            url: LABKEY.ActionURL.buildURL('issues', 'getContainers', LABKEY.container.path),
            reader: {
                type: 'json',
                root: 'containers'
            }
        }
    });

    var label = Ext4.create('Ext.Component', {
        tpl: new Ext4.XTemplate('<span>{text:htmlEncode}</span>'),
        data: {
            text: 'Specific Container'
        }
    });

    // Create the combo box, attached to the states data store
    var comboBox = Ext4.create('Ext.form.ComboBox', {
        name: 'moveToContainerSelect',
        margin: '0 0 0 5',
        width: 250,
        multiSelect: true,
        delimiter: ';',
        store: store,
        queryMode: 'local',
        allowBlank: false,
        valueField: 'containerId',
        displayField: 'containerPath',
        disabled: (curDefaultContainers.length == 0),
        listConfig : {
            getInnerTpl: function (displayField) {
                return '{' + displayField + ':htmlEncode}';
            }
        }
    });

    Ext4.create('Ext.panel.Panel', {
        width: 380,
        renderTo: Ext4.get(Ext4.dom.Query.select('.moveToContainerCheckCombo')[0]),
        border: false, frame: false,
        bodyStyle: 'background-color: transparent',
        layout: {
            type: 'hbox',
            align: 'stretch',
            pack: 'start'
        },
        items: [label, comboBox]
    });

    // set default
    //comboBox.reset();
    comboBox.setValue(curDefaultContainers);

});

function toggleMoveToContainerSelect() {
    if (document.querySelector("input[name=moveToContainer]").checked)
        Ext4.ComponentQuery.query('combo[@name=moveToContainerSelect]')[0].setDisabled(true);
    else
        Ext4.ComponentQuery.query('combo[@name=moveToContainerSelect]')[0].setDisabled(false);
}

Ext4.onReady(toggleMoveToContainerSelect);

