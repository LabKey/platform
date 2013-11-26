/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
//
// This class maps to what was formerly known as LABKEY.util.PipelineAction
//
Ext4.define('File.data.Pipeline', {

    extend: 'Ext.data.Model',

    statics: {
        /**
         * Parses a json array and returns an array of PipelineActions, the input config would take the form
         * returned by the server pipeline actions:
         *
         *  {
         *      files:[],
         *      multiSelect: boolean,
         *      emptySelect: boolean,
         *      description: '',
         *      links: {
         *          id: '',
         *          text: '',
         *          items: [{
         *              id: '',
         *              text: '',
         *              leaf: boolean,
         *              href: ''
         *          }]
         *      }
         *  }
         *
         * and create a PipelineAction for each object in the items array.
         */
        parseActions : function(actions) {
            var pipelineActions = [];
            if (actions && actions.length)
            {
                for (var i=0; i < actions.length; i++)
                {
                    var action = actions[i];
                    var config = {
                        files: action.files,
                        groupId: action.links.id,
                        groupLabel: action.links.text,
                        multiSelect: action.multiSelect,
                        emptySelect: action.emptySelect,
                        description: action.description
                    };

                    // only a single target for the action (no submenus)
                    if (!action.links.items && action.links.text && action.links.href)
                    {
                        config.id = action.links.id;
                        config.link = {text: action.links.text, id: action.links.id, href: action.links.href};

                        pipelineActions.push(Ext4.create('File.data.Pipeline', config));
                    }
                    else
                    {
                        for (var j=0; j < action.links.items.length; j++)
                        {
                            var item = action.links.items[j];

                            config.id = item.id;
                            config.link = item;

                            pipelineActions.push(Ext4.create('File.data.Pipeline', config));
                        }
                    }
                }
            }
            return pipelineActions;
        }
    },

    fields: [
        {name: 'id'}, // mapping: 'link.id'},
        {name: 'link'},
        {name: 'description'},
        {name: 'files'},
        {name: 'msgShort'},
        {name: 'msgLong'},
        {name: 'multiSelect', type: 'boolean'},
        {name: 'emptySelect', type: 'boolean'},
        {name: 'enabled', type: 'boolean'},
        {name: 'groupId'},
        {name: 'groupLabel'}
    ],

    getLink : function() { return this.get('link'); },

    getText : function() { return this.data.link ? this.data.link.text : ''; },

    getFiles : function() { return this.get('files'); },

    getEnabled : function() { return this.get('enabled'); },

    getLongMessage : function() { return this.get('msgLong'); },

    getShortMessage : function() { return this.get('msgShort'); },

    setEnabled : function(enabled) { this.set('enabled', enabled); },

    setMessage : function(msgShort, msgLong) {
        this.set('msgShort', msgShort);
        this.set('msgLong', msgLong);
    },

    supportsEmptySelect : function() {
        return (this.get('emptySelect') ? true : false);
    },

    supportsMultiSelect : function() {
        return (this.get('multiSelect') ? true : false);
    }
});

//
// This class maps to what was formerly known as LABKEY.PipelineActionConfig
//
Ext4.define('File.data.PipelineAction', {

    extend: 'Ext.data.Model',

    fields: [
        {name: 'display'},
        {name: 'id'},
        {name: 'label'},
        {name: 'links'} // array
    ],

    addLink : function(id, display, label) {
        var links = this.get('links');

        if (!Ext4.isArray(links)) {
            links = [];
            console.warn('invalid value set for File.data.PipelineAction.link. Expected Array');
        }

        links.push({id: id, display: display, label: label});
        this.set('links', links);
    },

    isDisplayOnToolbar : function() {

        var links = this.get('links'), link;

        if (Ext4.isArray(links)) {

            for (var i=0; i < links.length; i++) {

                link = links[i];
                if (link && link.display == 'toolbar') {
                    return link;
                }
            }
        }
    },

    getLink : function(linkId) {

        var links = this.get('links'), link;

        if (Ext4.isArray(links)) {

            for (var i=0; i < links.length; i++) {

                link = links[i];
                if (link && link.id == linkId) {
                    return link;
                }
            }
        }
    }
});