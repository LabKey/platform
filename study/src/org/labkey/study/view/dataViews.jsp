<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    User u = me.getViewContext().getUser();
    int webPartId = me.getModelBean().getRowId();
%>
<script type="text/javascript">

    LABKEY.requiresExt4Sandbox(true);

    LABKEY.requiresCss("studyRedesign/redesign.css");
    LABKEY.requiresScript("studyRedesign/utils.js", true);
</script>
<style type="text/css">
    div.x4-grid-cell-inner.x4-unselectable {cursor:pointer}
</style>

<!-- Definition of Grid -->
<script type="text/javascript">

    function init()
    {
        Ext4.require(['Ext.tip.*']);

        // Define an override for RowModel to fix page jumping on first click
        // LabKey Issue : 12940: Data Browser - First click on a row scrolls down slightly, doesn't trigger metadata popup
        // Ext issue    : http://www.sencha.com/forum/showthread.php?142291-Grid-panel-jump-to-start-of-the-panel-on-click-of-any-row-in-IE.In-mozilla-it-is-fine&p=640415
        Ext4.define('Ext.selection.RowModelFixed', {
            extend : 'Ext.selection.RowModel',
            alias  : 'selection.rowmodelfixed',

            onRowMouseDown: function(view, record, item, index, e) {
                this.selectWithEvent(record, e);
            }
        });

        // Views Model
        Ext4.define('Dataset.Browser.View', {
            extend : 'Ext.data.Model',
            fields : ['modifiedBy', 'category', 'editUrl', 'inherited', 'type',
            'runUrl', 'reportId', 'editable', 'version', 'modified', 'schema', 'createdBy',
            'created', 'description', 'container', 'name', 'permissions',
            'icon', 'thumbnail']
        });

        var jStoreId = Ext4.id();
        var jStore = Ext4.create('Ext.data.Store', {
            storeId : jStoreId,
            pageSize: 100,
            model   : 'Dataset.Browser.View',
            autoLoad: true,
            proxy   : {
                type   : 'ajax',
                url    : LABKEY.ActionURL.buildURL('study', 'browseData.api'),
                extraParams : {
                    // These parameters are required for specific webpart filtering
                    pageId : <%= PageFlowUtil.jsString(me.getModelBean().getPageId()) %>,
                    index  : <%= me.getModelBean().getIndex() %>
                },
                reader : {
                    type : 'json',
                    root : 'data'
                }
            },
            groupField : 'category',
            listeners : {
                load : function(s, recs, success, operation, ops) {
                    console.log('load completed.');
                    s.sort('name', 'ASC');
                }
            }
        });

        var filterTimer;

        var searchField = Ext4.create('Ext.form.field.Text', {
            emptyText       : 'name, category, etc.',
            enableKeyEvents : true,
            cls             : 'dataset-search',
            size            : 57,
            listeners       : {
                change       : function(cmp, e){
                    if (filterTimer)
                        clearTimeout(filterTimer);
                    filterTimer = setTimeout(filterSearch, 350);
                }
            }
        });

        function filterSearch()
        {
            var val = searchField.getValue();
            var s = Ext4.data.StoreManager.lookup(jStoreId);
            s.clearFilter();
            if (val) {
                s.filter([{
                    fn : function(rec) {
                        if (rec.data)
                        {
                            var t = new RegExp(Ext4.escapeRe(val), 'i');
                            var s = '';
                            if (rec.data.name)
                                s += rec.data.name;
                            if (rec.data.category)
                                s += rec.data.category;
                            if (rec.data.type)
                                s += rec.data.type;
                            return t.test(s);
                        }
                    }
                }]);
            }
        }

        var search = Ext4.create('Ext.panel.Panel',{
            border: false, frame : false,
            layout: {type: 'table'},
            items : [searchField, {
                xtype : 'box',
                autoEl: {
                    tag : 'img',
                    style : {
                        position : 'relative',
                        left     : '-20px'
                    },
                    src : LABKEY.ActionURL.getContextPath() + '/_images/search.png'
                }
            }]
        });

        /**
         * Column render templates
         */
        var typeTpl = new Ext4.XTemplate('<tpl if="icon == undefined || icon == \'\'">{type}</tpl><tpl if="icon != undefined && icon != \'\'">' +
                '<img height="16px" width="16px" src="{icon}" alt="{type}">' + // must set height/width explicitly for layout engine to work properly
                '</tpl>').compile();
        function typeRenderer(value, p, record)
        {
            return typeTpl.apply(record.data);
        }

        var groupingFeature = Ext4.create('Ext4.grid.feature.Grouping', {
            groupHeaderTpl: 'Category: {name}'
        });

        var panel;
        var grid = Ext4.create('Ext.grid.Panel', {
            id       : 'data-browser-grid-<%= webPartId %>',
            store    : Ext4.data.StoreManager.lookup(jStoreId),
            border   : false, frame : false,
            layout   : 'fit',
            autoScroll: true,
            columns  : [
                {
                    text     : 'Name',
                    flex     : 1,
                    sortable : true,
                    dataIndex: 'name'
                },{
                    text     : 'Category',
                    flex     : 1,
                    sortable : true,
                    dataIndex: 'category',
                    hidden   : true
                },{
                    text     : 'Type',
                    width    : 120,
                    sortable : true,
                    dataIndex: 'type',
                    tdCls    : 'type-column',
                    renderer : typeRenderer
                },{
                    header   : 'Access',
                    width    : 120,
                    sortable : false,
                    dataIndex: 'permissions'
                }
            ],
            viewConfig : {
                stripRows : true,
                listeners : {
                    render : initToolTip,
                    scope : this
                }
            },
            selType: 'rowmodelfixed',
            features  : [groupingFeature],
            listeners : {
                itemclick : function(v,r,i,idx,e,o) {
                    window.location = r.data.runUrl;
                },
                afterlayout : function(p) {
                    grid.setLoading(false);
                    /* Apply selector for tests */
                    var el = Ext4.query("*[class=x4-grid-table x4-grid-table-resizer]");
                    if (el && el.length == 1) {
                        el = el[0];
                        if (el && el.setAttribute) {
                            el.setAttribute('name', 'data-browser-table');
                        }
                    }
                }
            }
        });

        panel = Ext4.create('Ext.Panel', {
            renderTo : 'dataset-browsing-<%= me.getModelBean().getIndex() %>',
            layout   : 'fit',
            border   : false,
            frame    : false,
            items    : [search, grid]
        });

        grid.setLoading(true);


        Ext4.QuickTips.init();
    }

    /**
     * Tooltip Template
     */
    var tipTpl = new Ext4.XTemplate('<tpl>' +
            '<div class="tip-content">' +
                '<table cellpadding="20" cellspacing="100" width="100%">' +
                    '<tpl if="data.category != undefined && data.category.length">' +
                        '<tr><td>Source:</td><td>{data.category}</td></tr>' +
                    '</tpl>' +
                    '<tpl if="data.createdBy != undefined && data.createdBy.length">' +
                        '<tr><td>Author:</td><td>{data.createdBy}</td></tr>' +
                    '</tpl>' +
                    '<tpl if="data.type != undefined && data.type.length">' +
                    '<tr><td>Type:</td><td>{data.type}&nbsp;&nbsp;' +
                         '<tpl if="data.icon != undefined && data.icon.length"><img src="{data.icon}"/></tpl>' +
                    '</td></tr>' +
                    '</tpl>' +
                    '<tpl if="data.description != undefined && data.description.length">' +
                        '<tr><td valign="top">Description:</td><td>{data.description}</td></tr>' +
                    '</tpl>' +
                    '<tpl if="data.thumbnail != undefined && data.thumbnail.length">' +
                        '<tr><td colspan="2" align="center"><img style="height:100px;" src="{data.thumbnail}"/></td></tr>' +
                    '</tpl>' +
                '</table>' +
            '</div>' +
            '</tpl>').compile();

    var _tipID = Ext4.id();
    function getTipPanel()
    {
        var tipPanel = Ext4.create('Ext.panel.Panel', {
            id     : _tipID,
            layout : 'fit',
            border : false, frame : false,
            height : '100%',
            cls    : 'tip-panel',
            tpl    : tipTpl,
            defaults : {
                style : 'padding: 5px;'
            },
            renderTipRecord : function(rec){
                tipPanel.update(rec);
            }
        });

        return tipPanel;
    }

    function initToolTip(view)
    {
        var _w = 200;
        var _h = 200;
        var _active;

        function renderToolTip(tip)
        {
            if (_active)
                tip.setTitle(_active.get('name'));
            var content = Ext4.getCmp(_tipID);
            if (content)
            {
                content.renderTipRecord(_active);
            }
        }

        function loadRecord(t) {
            var r = view.getRecord(t.triggerElement.parentNode);
            if (r) _active = r;
            else { /* This usually occurs when mousing over grouping headers */  _active = null; return false; }
            return true;
        }

        view.tip = Ext4.create('Ext.tip.ToolTip', {
            target   : view.el,
            delegate : '.x4-grid-cell',
            trackMouse: true,
            width    : _w,
            height   : _h,
            html     : null,
            anchorToTarget : false,
            anchorOffset : 100,
            autoHide : true,
            defaults : { border: false, frame: false},
            items    : [getTipPanel()],
            listeners: {
                activate  : loadRecord,
                // Change content dynamically depending on which element triggered the show.
                beforeshow: function(tip) {
                    loadRecord(tip);
                    renderToolTip(tip);
                    tip.ismoused = false;
                },
                afterrender : function(tip) {
                    tip.getEl().on('mouseover', function(){
                        tip.ismoused = true;
                    });
                    tip.getEl().on('mouseout', function(){
                        tip.ismoused = false;
                    });
                },
                beforeclose: function(tip){
                    tip.ismoused = false;
                },
                beforehide: function(tip) {
                    return !tip.ismoused;
                },
                scope : this
            },
            scope : this
        });
    }

    Ext4.onReady(init);

    <% if (me.getViewContext().getContainer().hasPermission(u, AdminPermission.class)) { %>
    function customizeDataViews(pageId, index) {

        function success(data, window) {

            var cbItems = [];

            if (data.types)
            {
                for (var type in data.types) {
                    if(data.types.hasOwnProperty(type))
                    {
                        console.log(type + ": " + data.types[type]);
                        cbItems.push({boxLabel : type, name : type, checked : data.types[type], uncheckedValue : '0'});
                    }
                }
            }

            var panel = Ext4.create('Ext.form.Panel',{
                layout   : 'anchor',
                bodyPadding: 10,
                defaults : {
                    anchor : '100%'
                },
                fieldDefaults  :{
                    labelAlign : 'top',
                    labelWidth : 130,
                    labelSeparator : ''
                },
                items : [{
                    xtype      : 'textfield',
                    fieldLabel : 'Data Views Name (Visible upon Refresh)',
                    name       : 'webpart.title',
                    allowBlank : false,
                    maxWidth   : 250,
                    style      : 'padding-bottom: 10px;',
                    value      : data.webpart.title ? data.webpart.title : data.webpart.name
                },{
                    xtype      : 'checkboxgroup',
                    fieldLabel : 'Data Types to Display in this Data Views (All Users)',
                    maxWidth   : 400,
                    columns    : 2,
                    items      : cbItems
                },{
                    xtype   : 'hidden',
                    name    : 'webPartId',
                    value   : <%= webPartId %>
                }],
                buttons : [{
                    text     : 'Cancel',
                    handler  : function() {
                        window.close();
                    }
                },{
                    text     : 'Submit',
                    formBind : true,
                    handler  : function() {
                        var form = this.up('form').getForm();
                        if (form.isValid())
                        {
                            Ext4.Ajax.request({
                                url    : LABKEY.ActionURL.buildURL('project', 'customizeWebPartAsync.api', null, form.getValues()),
                                method : 'POST',
                                success : function(){
                                    window.hide();
                                    var g = Ext4.ComponentManager.get('data-browser-grid-<%= webPartId %>');
                                    if (g)
                                    {
                                        g.setLoading(true);
                                        g.getStore().load();
                                    }
                                },
                                failure : function(){
                                    Ext4.Msg.alert('Failure');
                                }
                            });
                        }
                    }
                }]
            });

            window.add(panel);
            window.doLayout();
        }

        function ready() {

            var extraParams = {
                // These parameters are required for specific webpart filtering
                includeData : false,
                pageId : pageId,
                index  : index
            };

            var p = Ext4.ComponentManager.get('dataset-browsing-customize-<%=me.getModelBean().getPageId()%>-panel');
            console.log('rendering to dataset-browsing-customize-<%=me.getModelBean().getPageId()%>');
            if (p === undefined)
            {
                console.log('p did not exist');
                p = Ext4.create('Ext.panel.Panel', {
                    id     : 'dataset-browsing-customize-<%=me.getModelBean().getPageId()%>-panel',
                    renderTo : 'dataset-browsing-customize-<%=me.getModelBean().getPageId()%>',
                    height : 150,
                    border : false, frame : false,
                    layout : 'fit',
                    hidden : true,
                    items  : [],
                    listeners : {
                        render : function() {
                            Ext4.Ajax.request({
                                url    : LABKEY.ActionURL.buildURL('study', 'browseData.api', null, extraParams),
                                method : 'GET',
                                success: function(response) {
                                    var json = Ext4.decode(response.responseText);
                                    success(json, p);
                                },
                                failure : function() {
                                    Ext4.Msg.alert('Failure');
                                }
                            });
                        }
                    }
                });
                console.log('showing panel 1');
                p.show();
            }
            else if (p.isVisible())
            {
                console.log('closing panel');
                p.close();
            }
            else
            {
                console.log('showing panel 2');
                p.show();
            }
        }
        Ext4.onReady(ready);
    }
    <% } %>
</script>
<div>
    <div id='dataset-browsing-customize-<%=me.getModelBean().getPageId()%>'></div>
    <div id='dataset-browsing-<%=me.getModelBean().getIndex()%>'></div>
</div>
