<script type="text/javascript">

var urlGetIssueApi = LABKEY.ActionURL.buildURL('issues', 'getIssue.api');

var IssueApplication = Ext.extend(Ext.util.Observable,
{
    // globals
    viewport: null,
    issueStore:null,
    previewForm:null,

    // properties (with change events)
    selectedIssueId:-1,
    selectedIssue:null,

    constructor : function()
    {
        this.addEvents
        (
            'selectIssue'
        );
    },

    _eqIssue : function(a,b)
    {
        if (!a || !b)
            return false;
        return a.issueId == b.issueId && this._eqDate(a.modified,b.modified);
    },
    _eqDate : function(a,b)
    {
        if (!a || !b)
            return false;
        if (!Ext.isDate(a))
            a = new Date(a);
        if (!Ext.isDate(b))
            b = new Date(b);
        return a.getTime() == b.getTime();
    },

    setSelectedIssueId : function(issueId, modified)
    {
        this.selectedIssueId = issueId;
        this.getIssue(issueId, modified, function(issue)
        {
            if (this._eqIssue(this.selectedIssue,issue))
                return;
            this.selectedIssue = issue;
            this.fireEvent('selectIssue', issue);
        }, this);
    },


    getIssue : function(issueId, modified, successFn, scope)
    {
        // UNDONE: use extjs 4 provider interface
        var issue;
        var key = urlGetIssueApi + "/" + issueId;
        var item = window.localStorage.getItem(key);
        if (item)
        {
            issue = Ext.util.JSON.decode(item);
            if (this._eqDate(issue.modified, modified))
            {
                successFn.call(scope||this, issue);
                return;
            }
        }
        var cb = function(response, options)
        {
            if (response.status == 200)
            {
                issue = Ext.util.JSON.decode(response.responseText);
                if (issue.success)
                {
                    window.localStorage.setItem(key,Ext.util.JSON.encode(issue));
                    successFn.call(scope||this, issue);
                }
            }
        };
        Ext.Ajax.request(
        {
            url : urlGetIssueApi,
            success:cb,
            params : {issueId:issueId}
        });
    }
});



var app = new IssueApplication();


function updatePreviewForm(issue)
{
    app.previewForm.getForm().setValues(issue);
    app.previewForm.setTitle(issue.issueId + " : " + issue.title);
    var html = [];
    for (var i=0 ; i<issue.comments.length ; i++)
    {
        html.push(issue.comments[i].comment);
    }
    app.previewForm.setComment(html.join(''));
}


function createIssueForm(config)
{
    var disabled = config.editable !== true;
    var commentsLabel;

    var form = new Ext.FormPanel(
    {
        //labelAlign: 'top',
        frame:true,
        title: 'Multi Column, Nested Layouts and Anchoring',
        bodyStyle:'padding:5px 5px 0',
        //width: 600,
        items: [
        {
            layout:'column',
            items:[{
                //xtype:'fieldset',
                padding:'5px',
                margin:'5px',
                columnWidth:0.20,
                layout: 'form',
                items: [
                {
                    xtype:'textfield',
                    fieldLabel: 'Status',
                    name: 'status',
                    disabled: disabled,
                    anchor:'95%'
                }, {
                    xtype:'textfield',
                    fieldLabel: 'Assigned To',
                    name: 'assignedTo',
                    disabled: disabled,
                    anchor:'95%'
                }, {
                    xtype:'textfield',
                    fieldLabel: 'Type',
                    name: 'type',
                    disabled: disabled,
                    anchor:'95%'
                }, {
                    xtype:'textfield',
                    fieldLabel: 'Area',
                    name: 'area',
                    disabled: disabled,
                    anchor:'95%'
                }, {
                    xtype:'textfield',
                    fieldLabel: 'Priority',
                    name: 'priority',
                    disabled: disabled,
                    anchor:'95%'
                }, {
                    xtype:'textfield',
                    fieldLabel: 'Milestone',
                    name: 'milestone',
                    disabled: disabled,
                    anchor:'95%'
                }]
            },{
                //xtype:'fieldset',
                padding:'5px',
                margin:'5px',
                columnWidth:0.20,
                layout: 'form',
                items: [{
                    xtype:'textfield',
                    fieldLabel: 'Opened',
                    name: 'opened',
                    anchor:'95%'
                },{
                    xtype:'textfield',
                    fieldLabel: 'Changed',
                    name: 'changed',
                    anchor:'95%'
                }, {
                    xtype:'textfield',
                    fieldLabel: 'Resolved',
                    name: 'resolved',
                    anchor:'95%'
                }, {
                    xtype:'textfield',
                    fieldLabel: 'Resolution',
                    name: 'resolution',
                    anchor:'95%'
                }]
            },{
                //xtype:'fieldset',
                padding:'5px',
                margin:'5px',
                columnWidth:0.20,
                layout: 'form',
                items:[{
                    xtype:'textfield',
                    fieldLabel: 'Closed',
                    name: 'closed',
                    anchor:'95%'
                }, {
                    xtype:'textfield',
                    fieldLabel: 'Sponsor',
                    name: 'sponsor',
                    anchor:'95%'
                }, {
                    xtype:'textfield',
                    fieldLabel: 'Triage',
                    name: 'triage',
                    anchor:'95%'
                }, {
                    xtype:'textfield',
                    fieldLabel: 'Sprint',
                    name: 'sprint',
                    anchor:'95%'
                }]
            },
            {
                padding:'5px',
                margin:'5px',
                columnWidth:0.40,
                layout:'fit',
                items: (commentsLabel = new Ext.form.Label())
            }]
        }],

//        buttons: [{
//            text: 'Save'
//        },{
//            text: 'Cancel'
//        }]
    });
    form.commentsLabel = commentsLabel;
    form.setComment = function(comment)
    {
        this.commentsLabel.setText(comment,false);
    };
    return form;
}


function startIssueApplication()
{
    Ext.getBody().addClass("extContainer");
    Ext.getBody().update("");

    app.issueStore = new LABKEY.ext.Store(
    {
        schemaName: 'issues',
        queryName: 'Issues',
        columns: 'IssueId, Title, Status, Priority, AssignedTo, Milestone, Modified'
    });

    //create a grid using that store as the data source
    var grid = new LABKEY.ext.EditorGridPanel({
        store: app.issueStore,
        width: 800,
        //autoHeight: true,
        title: 'Issues',
        editable: false
    });
    grid.on("columnmodelcustomize", function(model,index){
        for (var p in index) index[p].showLink = false;
    });
    grid.on("rowclick", function(grid,rowIndex,e)
    {
        var record = app.issueStore.getAt(rowIndex);
        var issueId = record.get("IssueId");
        var modified = record.get("Modified");
        app.setSelectedIssueId(issueId, modified);
    });


    app.previewForm = createIssueForm({preview:true, editable:false});


    app.viewport = new Ext.Viewport(
    {
        layout: 'border',
        items: [{
            region: 'north',
            html: '<h1 class="x-panel-header">Page Title</h1>',
            autoHeight: true,
            border: false,
            margins: '0 0 5 0'
        }, {
            region: 'west',
            collapsible: true,
            title: 'Navigation',
            width: 200
            // the west region might typically utilize a {@link Ext.tree.TreePanel TreePanel} or a Panel with {@link Ext.layout.AccordionLayout Accordion layout}
        }, {
            region: 'south',
            title: 'Title for Panel',
            collapsible: false,
            split: true,
            height: 200,
            minHeight: 100,
            items : app.previewForm
        }, {
            region: 'east',
            title: 'Preview',
            collapsible: true,
            split: true,
            width: 200,
            xtype: 'panel',
            //items: previewForm
            // remaining grid configuration not shown ...
            // notice that the GridPanel is added directly as the region
            // it is not "overnested" inside another Panel
        }, {
            id : 'tabpanel',
            region: 'center',
            xtype: 'tabpanel', // TabPanel itself has no title
            items: grid
        }
    ]});

    Ext.getCmp('tabpanel').setActiveTab(0);

    app.on('selectIssue', updatePreviewForm);
}



Ext.onReady(startIssueApplication);


</script>