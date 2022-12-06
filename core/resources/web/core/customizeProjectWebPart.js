function _customizeProjectWebpart(Ext4, webpartId, pageId, index, config)
{
    function shouldCheck(btn){
        return (btn.iconSize===config.iconSize && btn.labelPosition===config.labelPosition)
    }
    Ext4.create('Ext.window.Window', {
        title: 'Customize Webpart',
        modal: true,
        width: 400,
        border: false,
        layout: 'fit',
        items: [{
            xtype: 'form',
            border: false,
            bodyStyle: 'padding: 5px;',
            items: [{
                xtype: 'textfield',
                name: 'title',
                fieldLabel: 'Title',
                itemId: 'title',
                value: config.title
            },{
                xtype: 'radiogroup',
                name: 'style',
                itemId: 'style',
                fieldLabel: 'Icon Style',
                border: false,
                columns: 1,
                defaults: {
                    xtype: 'radio',
                    width: 300
                },
                items: [{
                    boxLabel: 'Details',
                    inputValue: {iconSize: 'small',labelPosition: 'side'},
                    checked: shouldCheck({iconSize: 'small',labelPosition: 'side'}),
                    name: 'style'
                },{
                    boxLabel: 'Medium',
                    inputValue: {iconSize: 'medium',labelPosition: 'bottom'},
                    checked: shouldCheck({iconSize: 'medium',labelPosition: 'bottom'}),
                    name: 'style'
                },{
                    boxLabel: 'Large',
                    inputValue: {iconSize: 'large',labelPosition: 'bottom'},
                    checked: shouldCheck({iconSize: 'large',labelPosition: 'bottom'}),
                    name: 'style'
                }]
            },{
                xtype: 'radiogroup',
                name: 'folderTypes',
                itemId: 'folderTypes',
                fieldLabel: 'Folders To Display',
                border: false,
                columns: 1,
                defaults: {
                    xtype: 'radio',
                    width: 300
                },
                items: [{
                    boxLabel: 'All Projects',
                    inputValue: 'project',
                    checked: config.containerTypes && config.containerTypes.match(/project/),
                    name: 'folderTypes'
                },{
                    boxLabel: 'Subfolders',
                    inputValue: 'subfolders',
                    checked: config.containerTypes && config.containerTypes.match(/folder/) && !config.containerPath,
                    name: 'folderTypes'
                },{
                    boxLabel: 'Specific Folder',
                    inputValue: 'folder',
                    checked: config.containerTypes && config.containerTypes.match(/folder/) && config.containerPath,
                    name: 'folderTypes'
                },{
                    xtype: 'labkey-combo',
                    itemId: 'containerPath',
                    width: 200,
                    disabled: config.containerTypes && !config.containerTypes.match(/folder/) || !config.containerPath,
                    displayField: 'Path',
                    valueField: 'EntityId',
                    initialValue: config.containerPath,
                    value: config.containerPath,
                    store: Ext4.create('LABKEY.ext4.Store', {
                        schemaName: 'core',
                        queryName: 'Containers',
                        containerFilter: 'AllFolders',
                        columns: 'Name,Path,EntityId',
                        autoLoad: true,
                        //sort: '-Path',
                        filterArray: [
                            LABKEY.Filter.create('type', 'workbook', LABKEY.Filter.Types.NOT_EQUAL),
                            LABKEY.Filter.create('name', LABKEY.Security.getHomeContainer(), LABKEY.Filter.Types.NOT_EQUAL),
                            LABKEY.Filter.create('name', LABKEY.Security.getSharedContainer(), LABKEY.Filter.Types.NOT_EQUAL)
                        ],
                        listeners: {
                            load: function(store){
                                //NOTE: the raw value of the path column is name, so we sort locally
                                store.sort('Path', 'ASC');
                                store.fireEvent('datachanged');
                            }
                        }
                    })
                },{
                    xtype: 'checkbox',
                    boxLabel: 'Include Direct Children Only',
                    disabled: (config.containerTypes && config.containerTypes.match(/project/)) || !config.containerPath,
                    checked: (config.containerFilter === 'CurrentAndFirstChildren'),
                    itemId: 'directDescendants'
                },{
                    xtype: 'checkbox',
                    boxLabel: 'Include Workbooks',
                    disabled: (config.containerTypes && config.containerTypes.match(/project/)) || !config.containerPath,
                    checked: (config.containerTypes.match(/project/) || config.containerTypes.match(/workbook/)),
                    itemId: 'includeWorkbooks'
                },{
                    xtype: 'checkbox',
                    boxLabel: 'Hide Create Button',
                    checked: config.hideCreateButton==='true'||config.hideCreateButton===true,
                    itemId: 'hideCreateButton'
                }],
                listeners: {
                    buffer: 20,
                    change: function(field, val){
                        var window = field.up('form');
                        window.down('#containerPath').setDisabled(val.folderTypes !== 'folder');
                        window.down('#includeWorkbooks').setDisabled(val.folderTypes !== 'folder');
                        window.down('#directDescendants').setDisabled(val.folderTypes !== 'folder');

                        window.doLayout();
                        field.up('window').doLayout();

                    }
                }
            }]
        }],
        buttons: [{
            text: 'Submit',
            handler: function(btn) {
                var mode = btn.up('window').down('#folderTypes').getValue().folderTypes;

                if(mode === 'project'){
                    config.containerFilter = 'CurrentAndSiblings';
                    config.containerTypes = 'project';
                    config.containerPath = LABKEY.Security.getHomeContainer();
                    config.noun = 'Project';
                }
                else if(mode === 'subfolders'){
                    config.containerFilter = 'CurrentAndFirstChildren';
                    config.containerTypes = 'folder';
                    config.containerPath = null;
                    config.noun = 'Folder';
                }
                else {
                    var container = btn.up('window').down('#containerPath').getValue();
                    if(!container){
                        alert('Must choose a folder');
                        return;
                    }
                    config.containerPath = container;

                    config.containerFilter = 'Current'; //null;  //use default
                    config.containerTypes = ['folder'];
                    if(btn.up('window').down('#includeWorkbooks').getValue())
                        config.containerTypes.push('workbook');
                    config.containerTypes = config.containerTypes.join(';');
                    config.noun = 'Subfolder';

                    var directDescendants = btn.up('window').down('#directDescendants').getValue();
                    config.containerFilter = directDescendants ? 'CurrentAndFirstChildren' : 'CurrentAndSubfolders';
                }

                config.hideCreateButton =  btn.up('window').down('#hideCreateButton').getValue();
                config.iconSize = btn.up('window').down('#style').getValue().style.iconSize;
                config.labelPosition = btn.up('window').down('#style').getValue().style.labelPosition;
                config.title = btn.up('window').down('#title').getValue();
                config.webPartId = webpartId;

                config['@hideCreateButton'] = '1'; // for spring binding of boolean
                Ext4.Ajax.request({
                    url    : LABKEY.ActionURL.buildURL('project', 'customizeWebPartAsync.api', null, config),
                    method : 'POST',
                    failure : LABKEY.Utils.onError,
                    success : function() {window.location.reload();},
                    scope : this
                });
            },
            scope: this
        },{
            text: 'Cancel',
            handler: function(btn){
                btn.up('window').hide();
            },
            scope: this
        }]
    }).show();
}
