/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 8.1
 * @license Copyright (c) 2008 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */

function _concatPaths(a,b)
{
    var c = 0;
    if (a.length > 0 && a.charAt(a.length-1) == '/') c++;
    if (b.length > 0 && b.charAt(0) == '/') c++;
    switch (c)
    {
        case 1: return a + b;
        case 2: return a + b.substr(1);
        default: return a + "/" + b;
    }
}

function _pathParts(path)
{
    if (path.charAt(0) != '/')
        path = '/' + path;
    if (path.charAt(path.length-1) == '/')
        path = path.substring(0, path.length-1);
    return path.split('/');
}

Ext.namespace('LABKEY', 'LABKEY.ext');

LABKEY.ext.WebDavTreeLoader = function (config)
{
    Ext.apply(this, config);

    this.addEvents("beforeload", "load", "loadexception");

    var loader = this;
    var PropfindResponse = Ext.data.Record.create([
        {name: 'id', mapping: 'href',
            convert : function (v, rec) {
                return v.replace(loader.url, ""); // remove contextPath/webdav
            }
        },
        {name: 'text', mapping: 'propstat/prop/displayname'},
        {name: 'leaf', mapping: 'href', type: 'boolean',
            convert : function (v, rec) {
//                var c = Ext.DomQuery.is(rec, 'propstat/prop/resourcetype:has(collection)');
                return v.length > 0 && v.charAt(v.length-1) != '/';
            }
        },
        {name: 'creationdate', mapping: 'propstat/prop/creationdate', type: 'date',
            dateFormat : "c" // "Y-m-d\\TH:i:s\\Z"
        },
        {name: 'lastmodified', mapping: 'propstat/prop/getlastmodified', type: 'date'},
        {name: 'size', mapping: 'propstat/prop/getcontentlength', type: 'int'}
    ]);
    this.propfindReader = new Ext.data.XmlReader({
        record : "response",
        id : "href"
    }, PropfindResponse);

    LABKEY.ext.WebDavTreeLoader.superclass.constructor.call(this);
};

Ext.extend(LABKEY.ext.WebDavTreeLoader, Ext.tree.TreeLoader, {
    /**
     * @cfg {Regex} fileFilter (optional) Only files matching the pattern are shown.
     */
    fileFilter : null,
    displayFiles: true,

    requestData : function(node, callback) {
        if (this.fireEvent("beforeload", this, node, callback) !== false) {
            document.getElementById('labkey-main-title').innerHTML= _concatPaths(this.url, node.id);
            this.transId = Ext.Ajax.request({
                method: "GET",
                headers: {"Method" : "PROPFIND", "Depth" : "1,noroot"},
                url : _concatPaths(this.url,node.id),
                success: this.handleResponse,
                failure: this.handleFailure,
                scope: this,
                argument: {callback: callback, node: node}
            });
        } else {
            // if the load is cancelled, make sure we notify
            // the node that we are done
            if (typeof callback == "function") {
                callback();
            }
        }
    },

    createNode : function (data) {
        if (data.leaf)
        {
            if (!this.displayFiles)
                return null;
            if (this.fileFilter)
                data.disabled = !this.fileFilter.test(data.text);
        }
        return LABKEY.ext.WebDavTreeLoader.superclass.createNode.call(this, data);
    },

    processResponse : function (response, node, callback) {
        try {
            var xml = response.responseXML;
            node.beginUpdate();
            var result = this.propfindReader.readRecords(xml);
            if (result && result.records)
            {
                for (var i = 0; i < result.records.length; i++)
                {
                    var record = result.records[i];
                    var data = record.data;
                    var n = this.createNode(data);
                    if (n) {
                        node.appendChild(n);
                    }
                }
            }
            node.endUpdate();
            if (typeof callback == "function") {
                callback(this, node);
            }
        } catch (e) {
            this.handleFailure(response);
        }
    }
});

LABKEY.ext.FileTreeMultiSelectionModel = function (config) {
    Ext.apply(this, config);
    this.addEvents("beforeselect", "selectionchange");
    LABKEY.ext.FileTreeMultiSelectionModel.superclass.constructor.call(this);
};

Ext.extend(LABKEY.ext.FileTreeMultiSelectionModel, Ext.tree.MultiSelectionModel, {
    select : function (node, e, keepExisting) {
        if (this.fireEvent('beforeselect', this, node, this.lastSelNode) !== false) {
            return LABKEY.ext.FileTreeMultiSelectionModel.superclass.select.call(this, node, e, keepExisting);
        }
        return node;
    }
});

LABKEY.ext.FileTreeNodeUI = Ext.extend(Ext.tree.ColumnNodeUI, {
    initEvents : function () {
        LABKEY.ext.FileTreeNodeUI.superclass.initEvents.call(this);
         // XXX: Ext-2.2 doesn't initialize disabled state
        this.onDisableChange(this.node, this.node.disabled);
    },

    onClick : function (e) {
        LABKEY.ext.FileTreeNodeUI.superclass.onClick.call(this, e);
        // if using single selection model, clear the selection if the node was not selected
        var selModel = this.node.getOwnerTree().getSelectionModel();
        if (selModel instanceof Ext.tree.DefaultSelectionModel && !this.node.isSelected())
        {
            selModel.clearSelections();
        }
    },

    onOver : function (e) {
        if (this.node.disabled) {
            return;
        }
        LABKEY.ext.FileTreeNodeUI.superclass.onOver.call(this, e);
    }
});

LABKEY.ext.FileTree = function (config)
{
    Ext.apply(this, config);

    this.path = config.path || _concatPaths(LABKEY.ActionURL.getContainer(),(this.browsePipeline ? "/%40pipeline" : ""));
    this.baseURL = config.baseURL || _concatPaths(LABKEY.ActionURL.getContextPath(), "_webdav");

    if (this.multiSelect)
    {
        this.selModel = new LABKEY.ext.FileTreeMultiSelectionModel();
    }

    if (!this.root)
    {
        this.root = new Ext.tree.AsyncTreeNode({
            id: this.path,
            text:'<root>',
            listeners: {
                'load': function (node) {
                    if (!node.hasChildNodes())
                    {
                        node.appendChild({
                            text: "&lt;no files found in root>",
                            size: 0,
                            disabled: true,
                            leaf: true,
                            iconCls: 'labkey-tree-error-icon'
                        });
                    }
                }
            }
        });
    }

    if (!this.loader)
    {
        this.loader = new LABKEY.ext.WebDavTreeLoader({
            url : this.baseURL,
            baseAttrs:{uiProvider:'col'},
            uiProviders:{'col': LABKEY.ext.FileTreeNodeUI},
            fileFilter : this.fileFilter
        });
    }
    this.loader.on("loadexception", function (treeloader, node, response) {
        if (node)
        {
            var qtip = "error loading files from server";
            if (response && response.responseText)
                qtip = response.responseText;
            node.appendChild({
                text: "&lt;error loading files from server>",
                size: 0,
                disabled: true,
                leaf: true,
                qtip: qtip,
                iconCls: 'labkey-tree-error-icon'
            });
        }
    });

    LABKEY.ext.FileTree.superclass.constructor.call(this);

    this.getSelectionModel().addListener("beforeselect",
        function (model, newnode, oldnode)
        {
            return (model.tree.filesSelectable && newnode.leaf) ||
                   (model.tree.dirsSelectable && !newnode.leaf);
        });

    var inputEl = null;
    if (this.inputId)
        inputEl = document.getElementById(this.inputId);

    var originalTitle = this.title;
    this.getSelectionModel().addListener("selectionchange", function (model, newnode) {
        var selectedValues = model.tree.getSelectedValues();
        if (inputEl)
            inputEl.value = selectedValues;
        if (selectedValues)
            model.tree.setTitle("Selected: " + model.tree.getSelectedValues("text"));
        else
            model.tree.setTitle(originalTitle);
    });

    var sel = this.initialSelection;
    if (sel && sel.length > 0)
    {
        var keys = _pathParts(sel);
        for (var i = 0; i < keys.length; i++)
        {
            keys[i] = decodeURIComponent(keys[i]);
        }
        this.selectPath(_concatPaths('/<root>',keys.join('/')), 'text');
    }
};

Ext.extend(LABKEY.ext.FileTree, Ext.tree.ColumnTree, {
    /**
     * @cfg {Boolean} browsePipeline (optional) Defaults to false.
     * Initializes the root node to start at the pipeline root.
     */
    browsePipeline : false,

    /**
     * @cfg {Boolean} filesSelectable (optional) Defaults to true. Allow file items
     * to be selected.
     */
    filesSelectable : true,

    /**
     * @cfg {Boolean} dirsSelectable (optional) Defaults to true. Allow directory items
     * to be selected.
     */
    dirsSelectable : true,

    /**
     * @cfg {Boolean} multiSelect (optional) Defaults to false.  Allow more
     * than one item to be selected at a time.
     */
    multiSelect : false,

    /**
     * @cfg {String} initialSelection (optional) Select the node given
     * by the path.
     */

    /**
     * @cfg {Regex} fileFilter (optional) Only files matching the pattern are shown.
     */
    fileFilter : null,

    /**
     * @cfg {String} inputId (optional) id of a hidden form input that will
     * be updated when the selection changes.  The value of the input will
     * be a comma separated list of hrefs.
     */
    inputId : null,

    /**
     * @cfg {String} relativeToRoot (optional) remove root's id prefix from
     * the selected nodes' ids as returned by getSelectedValues().
     */
    relativeToRoot : false,

    autoHeight:true,
    rootVisible:false,
    title: 'File Browser',
    useArrows:true,
    autoScroll:true,
    animate:true,
    enableDD:false,
    containerScroll:true,

    columns:[{
        header:'Name',
        width:350,
        dataIndex:'text'
    },{
        header:'Created',
        width:200,
        dataIndex:'creationdate',
        renderer: function (v,n,a) {
           return v ? v.format('n/d/Y g:i:sa') : "&nbsp;";
        }
    },{
// XXX: Java doesn't provide file creation time API so the creationdate is always lastModified
//        header:'Modified',
//        width:200,
//        dataIndex:'lastmodified',
//        renderer: function (v,n,a) {
//           return v ? v.format('n/d/Y g:i:sa') : "&nbsp;";
//        }
//    },{
        header:'Size',
        width:100,
        dataIndex:'size',
        renderer: function(v,n,a) {
            if (n.attributes["leaf"])
                return Ext.util.Format.fileSize(v);
            return "&nbsp;";
        }
    }
    ],

    getSelectedValues: function (attr)
    {
        attr = attr || "id";
        var self = this;
        function stripId(id)
        {
            var rootId = self.getRootNode()[attr];
            if (self.relativeToRoot && id.indexOf(rootId) == 0)
                return id.substring(rootId.length);
            return id;
        }

        var selModel = this.getSelectionModel();
        if (!selModel)
            return "";
        if (selModel.getSelectedNode)
        {
            var node = selModel.getSelectedNode();
            return node ? stripId(node[attr]) : "";
        }
        else if (selModel.getSelectedNodes)
        {
            var nodes = selModel.getSelectedNodes();
            var value = "";
            var sep = "";
            for (var i = 0; i < nodes.length; i++)
            {
                value += sep + stripId(nodes[i][attr]);
                sep = ",";
            }
            return value;
        }
    }

});


