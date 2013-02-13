Ext4.define('LABKEY.dataregion.panel.Facet', {

    extend : 'Ext.Panel',

    constructor : function(config) {

        if (!config.dataRegion) {
            console.error('A DataRegion object must be provided for Faceted Search.');
            return;
        }

        var renderTarget = 'dataregion_facet_' + config.dataRegion.name;
        var targetHTML = '<div id="' + renderTarget + '" style="float: left;"></div>';

        Ext4.get(config.dataRegion.name).up('div').insertHtml('beforeBegin', targetHTML);

        Ext4.applyIf(config, {
            renderTo : renderTarget,
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

        if (!this.qwp) {

            // Wrap the corresponding Data Region with a QWP
            this.qwp = LABKEY.QueryWebPart.constructFromDataRegion({
                dataRegion : this.dataRegion,
                parameters : {
                    facet : true
                },
                success : function(dr) {

                    // Give access to to this filter panel to the Data Region
                    if (dr) {
                        LABKEY.DataRegions[dr.name].setFacet(this);
                    }
                },
                scope : this
            });

//            this.qwp = new LABKEY.QueryWebPart({
//                dataRegion : this.dataRegion,
//                parameters : {
//                    facet : true
//                },
//                success : function(dr) {
//
//                    // Give access to to this filter panel to the Data Region
//                    if (dr) {
//                        LABKEY.DataRegions[dr.name].setFacet(this);
//                    }
//                },
//                scope : this
//            });
        }

        // Already have a QWP, so just Ajax as a normal filter
        var dr = LABKEY.DataRegions[this.qwp.dataRegionName];
        if (dr) {
            var paramValPairs = this.qwp.userFilters;
            if (filterArray.length > 0) {
                var f = filterArray[0];

                var newValArray = [];
                for (var p in paramValPairs) {
                    if (paramValPairs.hasOwnProperty(p)) {
                        newValArray.push([p, paramValPairs[p]]);
                    }
                }

                var paramName = f.getURLParameterName().replace('query.', this.dataRegion.name + '.');
                newValArray.push([paramName, f.getURLParameterValue()]);
                dr.changeFilter(newValArray, LABKEY.DataRegion.buildQueryString(newValArray));
            }
            else {
                // Clear any filters for this field
                dr.clearFilter('ParticipantId');
            }
        }
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