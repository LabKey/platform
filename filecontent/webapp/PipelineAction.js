/*
 * Copyright (c) 2007-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 Ext.ux.clone = function(o) {
    if(!o || 'object' !== typeof o) {
        return o;
    }
    var c = '[object Array]' === Object.prototype.toString.call(o) ? [] : {};
    var p, v;
    for(p in o) {
        if(o.hasOwnProperty(p)) {
            v = o[p];
            if(v && 'object' === typeof v) {
                c[p] = Ext.ux.clone(v);
            }
            else {
                c[p] = v;
            }
        }
    }
    return c;
};

LABKEY.PipelineActionUtil = function(config){

    Ext.apply(this, config);

    // only a single target for the action (no submenus)
    if (!this.links.items && config.links.text && config.links.href)
    {
        this.links.items = [{text: config.links.text, id: config.links.id, display: config.links.display, href: config.links.href}];
    }
};

LABKEY.PipelineActionUtil.prototype = {

    multiSelect : undefined,
    description : undefined,

    links : {},
    files : [],

    getText : function() {
        return this.links.text;
    },

    getLinks : function() {
        return this.links.items;
    },

    getFiles : function() {
        return this.files;
    },

    clearLinks : function() {
        this.links.items = [];
    },

    addLink : function(link)
    {
        if (!this.links.items)
            this.links.items = [];

        link.files = this.files;
        this.links.items.push(link);
    },

    clone : function()
    {
        var config = Ext.ux.clone(this);
        return new LABKEY.PipelineActionUtil(config);
    },

    getActionConfig : function()
    {
        // an action with a menu button
        if (this.links.items && this.links.items.length > 1)
        {
            return {
                text: this.getText(),
                multiSelect: this.multiSelect,
                description: this.description,
                files: this.files,
                links: this.links,
                menu: {
                    cls: 'extContainer',
                    items: this.links.items
                }
            }
        }
        else if (this.links.items && this.links.items.length == 1)
        {
            var item = this.links.items[0];
            return {
                text: this.getText(),
                multiSelect: this.multiSelect,
                description: this.description,
                files: this.files,
                links: this.links,
                href: item.href,
                listeners: item.listeners,
                handler: item.handler,
                scope: item.scope
            }
        }
    }
};

LABKEY.PipelineAction = Ext.extend(Ext.Action, {

    constructor : function(config)
    {
        Ext.Action.prototype.constructor.call(this, config);

/*
        if (config.multiSelect)
            this.multiSelect = config.multiSelect;
        if (config.description)
            this.description = config.description;

        this.text = this.initialConfig.links.text;
*/
    },

    getFiles : function(){
        return this.initialConfig.files;
    },

    // returns an array of executable sub-actions
    getLinks : function()
    {
        var actions = [];
        if (this.initialConfig.links.items != undefined)
        {
            for (var i=0; i < this.initialConfig.links.items.length; i++)
            {
                var item = this.initialConfig.links.items[i];
                if (item.href)
                {
                    //item.handler = this.initialConfig.handler;
                    //item.scope = this.initialConfig.scope;
                    actions.push(item);
                }
            }
        }
        else if (this.initialConfig.links.text && this.initialConfig.links.href)
        {
            var item = this.initialConfig.links;
            actions.push({text: item.text, id: item.id, display: item.display, href: item.href});
        }
        return actions;
    }
});

