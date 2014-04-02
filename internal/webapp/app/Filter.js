/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.define('LABKEY.app.model.Filter', {
    extend: 'Ext.data.Model',

    fields : [
        {name : 'id'},
        {name : 'hierarchy'},
        {name : 'members'},
        {name : 'operator'},
        {name : 'isGroup', type: 'boolean'},
        {name : 'isGrid', type: 'boolean'},
        {name : 'isPlot', type: 'boolean'},
        {name : 'gridFilter'}, // instance of LABKEY.Filter
        {name : 'plotMeasures'}, // array of measures
        {name : 'plotScales'} // array of scales
    ],

    statics : {
        doGroupSave : function(mdx, onSuccess, onFailure, grpData) {
            var m = LABKEY.app.model.Filter;
            mdx.queryParticipantList({
                useNamedFilters : ['statefilter'],
                success : function(cs) {
                    var group = {
                        label : grpData.label,
                        participantIds : Ext.Array.pluck(Ext.Array.flatten(cs.axes[1].positions),'name'),
                        description : grpData.description,
                        shared : false,
                        type : 'list',
                        filters : m.toJSON(grpData.filters, grpData.isLive)
                    };

                    Ext.Ajax.request({
                        url : LABKEY.ActionURL.buildURL('participant-group', 'createParticipantCategory'),
                        method: 'POST',
                        success: onSuccess,
                        failure : onFailure,
                        jsonData: group,
                        headers : {'Content-Type' : 'application/json'}
                    });
                }
            });
        },

        doGroupUpdate : function(mdx, onUpdateSuccess, onUpdateFaiure, grpData) {
            var m = LABKEY.app.model.Filter;
            mdx.queryParticipantList({
                filter : m.getOlapFilters(m.fromJSON(grpData.filters)),
                group : grpData,
                success : function (cs, mdx, config) {
                    var group = config.group;
                    var ids = Ext.Array.pluck(Ext.Array.flatten(cs.axes[1].positions),'name');
                    LABKEY.ParticipantGroup.updateParticipantGroup({
                        rowId : group.rowId,
                        participantIds : ids,
                        success : function(group, response) {
                            if (onUpdateSuccess)
                                onUpdateSuccess.call(this, group);
                        }
                    });
                }
            });
        },

        fromJSON : function(jsonFilter) {
            var filterWrapper = Ext.decode(jsonFilter);
            return filterWrapper.filters;
        },

        toJSON : function(filters, isLive) {
            return Ext.encode({
                isLive : isLive,
                filters : filters
            });
        },

        getGridHierarchy : function(data) {
            if (data['gridFilter']) {
                if (!Ext.isFunction(data['gridFilter'].getColumnName))
                {
                    console.warn('invalid filter object being processed.');
                    return 'Unknown';
                }
                var label = data['gridFilter'].getColumnName().split('/');

                // check lookups
                if (label.length > 1) {
                    label = label[label.length-2];
                }
                else {
                    // non-lookup column
                    label = label[0];
                }

                label = label.split('_');
                return Ext.String.ellipsis(label[label.length-1], 9, false);
            }
            return 'Unknown';
        },

        getGridLabel : function(data) {
            if (data['gridFilter']) {
                var gf = data.gridFilter;
                if (!Ext.isFunction(gf.getFilterType))
                {
                    console.warn('invalid label being processed');
                    return 'Unknown';
                }
                return LABKEY.app.model.Filter.getShortFilter(gf.getFilterType().getDisplayText()) + ' ' + gf.getValue();
            }
            return 'Unknown';
        },

        getOlapFilter : function(data, subjectName) {
            var filter = {
                operator : LABKEY.app.model.Filter.lookupOperator(data),
                arguments: []
            };

            if (data.hierarchy == subjectName) {

                filter.arguments.push({
                    hierarchy : subjectName,
                    members  : data.members
                });
                return filter;
            }

            for (var m=0; m < data.members.length; m++) {
                filter.arguments.push({
                    hierarchy : subjectName,
                    membersQuery : {
                        hierarchy : data.hierarchy,
                        members   : [data.members[m]]
                    }
                });
            }

            return filter;
        },

        getOlapFilters : function(datas, subjectName) {
            var olapFilters = [];
            for (var i = 0; i < datas.length; i++) {
                olapFilters.push(LABKEY.app.model.Filter.getOlapFilter(datas[i], subjectName));
            }
            return olapFilters;
        },

        getShortFilter : function(displayText) {
            switch (displayText) {
                case "Does Not Equal":
                    return '!=';
                case "Equals":
                    return '=';
                case "Is Greater Than":
                    return '>';
                case "Is Less Than":
                    return '<';
                case "Is Greater Than or Equal To":
                    return '>=';
                case "Is Less Than or Equal To":
                    return '<=';
                default:
                    return displayText;
            }
        },

        lookupOperator : function(data) {
            if (data.operator)
                return data.operator;

            var ops = LABKEY.app.model.Filter.Operators;

            switch (data.hierarchy) {
                case 'Study':
                    return ops.UNION;
                case 'Subject.Race':
                    return ops.UNION;
                case 'Subject.Country':
                    return ops.UNION;
                case 'Subject.Sex':
                    return ops.UNION;
                default:
                    return ops.INTERSECT;
            }
        },

        Operators: {
            UNION: 'UNION',
            INTERSECT: 'INTERSECT'
        }
    },

    getOlapFilter : function(subjectName) {
        return LABKEY.app.model.Filter.getOlapFilter(this.data, subjectName);
    },

    getHierarchy : function() {
        return this.get('hierarchy');
    },

    getMembers : function() {
        return this.get('members');
    },

    removeMember : function(memberUname) {

        // Allow for removal of the entire filter if a uname is not provided
        if (!memberUname) {
            return [];
        }

        var newMembers = [];
        var memberUniqueName = '[' + memberUname.join('].[') + ']';
        var dataUniqueName;

        for (var m=0; m < this.data.members.length; m++) {
            dataUniqueName = '[' + this.data.members[m].uname.join('].[') + ']';
            if (memberUniqueName !== dataUniqueName)
            {
                newMembers.push(this.data.members[m]);
            }
        }
        return newMembers;
    },

    /**
     * Complex comparator that says two filters are equal if and only if they match on the following:
     * - isGroup, isGrid, isPlot, hierarchy, member length, and member set (member order insensitive)
     * @param f - Filter to compare this object against.
     */
    isEqual : function(f) {
        var eq = false;

        if (Ext.isDefined(f) && Ext.isDefined(f.data)) {
            var d = this.data;
            var fd = f.data;

            eq = (d.isGroup == fd.isGroup) && (d.isGrid == fd.isGrid) &&
                    (d.isPlot == fd.isPlot) && (d.hierarchy == fd.hierarchy) &&
                    (d.members.length == fd.members.length);

            if (eq) {
                // member set equivalency
                var keys = {}, uname, key, sep, m, u;
                for (m=0; m < d.members.length; m++) {
                    uname = d.members[m].uname; key = ''; sep = '';
                    for (u=0; u < uname.length; u++) {
                        key += sep + uname[u];
                        sep = ':::';
                    }
                    keys[key] = true;
                }

                for (m=0; m < fd.members.length; m++) {
                    uname = fd.members[m].uname; key = ''; sep = '';
                    for (u=0; u < uname.length; u++) {
                        key += sep + uname[u];
                        sep = ':::';
                    }
                    if (!Ext.isDefined(keys[key])) {
                        eq = false;
                        break;
                    }
                }
            }
        }

        return eq;
    },

    isGrid : function() {
        return this.get('isGrid');
    },

    isPlot : function() {
        return this.get('isPlot');
    },

    getGridHierarchy : function() {
        return LABKEY.app.model.Filter.getGridHierarchy(this.data);
    },

    getGridLabel : function() {
        return LABKEY.app.model.Filter.getGridLabel(this.data);
    },

    /**
     * Returns abbreviated display value. (E.g. 'Equals' returns '=');
     * @param displayText - display text from LABKEY.Filter.getFilterType().getDisplayText()
     */
    getShortFilter : function(displayText) {
        return LABKEY.app.model.Filter.getShortFilter(displayText);
    },

    isGroup : function() {
        return false;
    },

    getValue : function(key) {
        return this.data[key];
    }
});







