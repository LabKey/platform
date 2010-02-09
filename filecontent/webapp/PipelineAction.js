/*
 * Copyright (c) 2010 LabKey Corporation
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
/*
    multiSelect     // does this action support handling more than one file
    emptySelect     // if this action can operate on an empty directory
    description

    links : {},
    files : [],
*/

    Ext.apply(this, config);

    // only a single target for the action (no submenus)
    if (!this.links.items && config.links.text && config.links.href)
    {
        this.links.items = [{text: config.links.text, id: config.links.id, display: config.links.display, href: config.links.href}];
    }
};

LABKEY.PipelineActionUtil.prototype = {

    getText : function() {
        return this.links.text;
    },

    getId : function() {
        return this.links.id;
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
        if (!this.links)
            this.links = {items:[]};

        link.files = this.files;
        this.links.items.push(link);
    },

    getShortMessage : function() {
        return this.msgShort ? this.msgShort : '';
    },

    getLongMessage : function() {
        return this.msgLong ? this.msgLong : '';
    },

    setMessage : function(short, long) {
        this.msgShort = short;
        this.msgLong = long;
    },

    setEnabled : function(enabled) {
        this.enabled = enabled;
    },

    getEnabled : function() {
        return this.enabled;
    },

    getLink : function(id) {

        var links = this.getLinks();
        if (links && links.length)
        {
            for (var i=0; i < links.length; i++)
            {
                var link = links[i];

                if (link && link.id == id)
                    return link;
            }
        }
    },
    
    clone : function()
    {
        var config = Ext.ux.clone(this);
        return new LABKEY.PipelineActionUtil(config);
    }
};

LABKEY.PipelineActionConfig = function(config){
/*
    id : undefined,
    display : undefined,
    label : undefined,

    links : [],
*/
    Ext.apply(this, config);
};

LABKEY.PipelineActionConfig.prototype = {

    getLink : function(id) {

        if (this.links && this.links.length)
        {
            for (var i=0; i < this.links.length; i++)
            {
                var link = this.links[i];

                if (link && link.id == id)
                    return link;
            }
        }
    },

    addLink : function(id, display, label) {

        if (!this.links)
            this.links = [];
        
        this.links.push({id: id, display: display, label: label});
    },

    isDisplayOnToolbar : function() {

        if (this.links && this.links.length)
        {
            for (var i=0; i < this.links.length; i++)
            {
                if (this.links[i].display == 'toolbar')
                    return true;
            }
        }
        return false;
    },

    createButtonAction : function(handler, scope)
    {
        if (!this.links && !this.links.length) return null;

        var items = [];
        for (var i=0; i < this.links.length; i++)
        {
            var link = this.links[i];
            if (link.display == 'toolbar')
                items.push({id: link.id,  actionId: this.id, text: link.label, handler: handler, scope: scope, tooltip: link.label});
        }

        // an action with a menu button
        if (items.length > 1)
        {
            return new Ext.Action({text: this.label, tooltip: this.label, menu: {
                    cls: 'extContainer',
                    items: items
                }
            });
        }
        else if (items.length == 1)
            return new Ext.Action(items[0]);

        return null;
    }
};
