Ext4.define('LABKEY.dataregion.panel.Facet', {

    extend : 'Ext.Panel',

    constructor : function(config) {

        if (!config.dataRegion) {
            console.error('A DataRegion object must be provided for Faceted Search.');
            return;
        }

        Ext4.applyIf(config, {
            renderTo : 'dataregion_facet_' + config.dataRegion.name,
            title : 'Faceted Search',
            collapsed : true,
            collapsible : true,
            collapseDirection : 'left',
            collapseMode : 'mini',
            width : 260,
            minHeight : 450,
            anchorSize : 400,
            style : 'padding-right: 5px;',
            header : {
                xtype : 'header',
                title : 'Filter',
                cls : 'facet_header'
            },
            autoScroll: true,
            height : Ext4.get('dataregion_' + config.dataRegion.name).getBox().height
        });

        this.filterTask = new Ext4.util.DelayedTask(this._filterTask, this);

        fp = this;

        this.callParent([config]);
    },

    initComponent : function() {

        this.items = [
            this.getFilterCfg()
        ];

        this.callParent(arguments);

        var task = new Ext4.util.DelayedTask(function(){
            this.expand();
        }, this);

        this.on('afterrender', function() { task.delay(200); });
        this.on('beforeexpand', function() {
            this.show();
        }, this);
        this.on('collapse', function() {
            this.hide();
        }, this);

        // Data Region Listeners
        if (this.dataRegion) {
            this.dataRegion.on('afterpanelshow', this.onDataRegionResize, this);
            this.dataRegion.on('afterpanelhide', this.onDataRegionResize, this);
        }
    },

    getFilterCfg : function() {

        var me = this;

        return {
            xtype : 'participantfilter',
            width     : 260,
            layout    : 'fit',
            bodyStyle : 'padding: 8px',
            normalWrap : true,
            overCls   : 'iScroll',

            // Filter specific config
            filterType  : 'group',
            subjectNoun : this.subjectNoun,

            listeners : {
                afterrender : function(panel) {
                    me.filterPanel = panel;
                },
                selectionchange : this.onFilterChange,
                scope : this
            },

            scope : this
        };
    },

    onDataRegionResize : function() {
        var newHeight = Ext4.get('dataregion_' + this.dataRegion.name).getBox().height;
        this.animate({
            to : { height: newHeight }
        });
    },

    // DO NOT CALL DIRECTLY. Use filterTask.delay
    _filterTask : function() {

        this.dataRegion.doDestroy();

        var json = [];

        // Current all being selected === none being selected
        var filters = this.filterPanel.getSelection(true, true);

        if (filters.length == 0) {
            this.onResolveFilter([]);
            return;
        }

        for (var f=0; f < filters.length; f++) {
            if (filters[f].get('type') != 'participant') {
                json.push(filters[f].data);
            }
            // deal with participant case
        }

        this.resolveFilter(json, [], this.onResolveFilter, this);
    },

    onFilterChange : function() {
        this.filterTask.delay(100);
    },

    resolveFilter : function(groups, subjects, callback, scope) {
        // Ignore subjects for now
        Ext4.Ajax.request({
            url    : LABKEY.ActionURL.buildURL('participant-group', 'getSubjectsFromGroups.api'),
            method : 'POST',
            jsonData : Ext4.encode({
                groups : groups
            }),
            success : function(resp) {
                var json = Ext4.decode(resp.responseText);
                var subjects = json.subjects ? json.subjects : [];
//                if(participants && participants.length)
//                    subjects = subjects.concat(participants);
                callback.call(scope || this, subjects);
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    onResolveFilter : function(subjects) {

        var filterArray = [];
        if (subjects && subjects.length > 0) {
            var subjectFilter = '', sep='';
            for (var s=0; s < subjects.length; s++) {
                subjectFilter += sep + subjects[s];
                sep = ';';
            }
            filterArray = [
                LABKEY.Filter.create('ParticipantId', subjectFilter, LABKEY.Filter.Types.CONTAINS_ONE_OF)
            ];
        }

        var qwp = new LABKEY.QueryWebPart({
            schemaName : this.dataRegion.schemaName,
            queryName  : this.dataRegion.queryName,
            frame      : false,
            showPagination : true,
            filterArray: filterArray,
            listeners  : {
                render : function() {
                    this.dataRegion = LABKEY.DataRegions[qwp.dataRegionName];
                }
            }
        });

        // Render over previous Data Region
        qwp.render('dataregion_' + this.dataRegion.name);
    },

    onFailure : function(resp) {
        var o;
        try {
            o = Ext4.decode(resp.responseText);
        }
        catch (error) {
            Ext4.Msg.alert('Failure', 'An unknown error occurred.');
        }

        var msg = "";
        if(resp.status == 401){
            msg = resp.statusText || "Unauthorized";
        }
        else if(o != undefined && o.exception){
            msg = o.exception;
        }
        else {
            msg = "There was a failure. If the problem persists please contact your administrator.";
        }
        this.unmask();
        Ext4.Msg.alert('Failure', msg);
    }
});