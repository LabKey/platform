/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
//
// this must be Ext agnostic code in terms of object
// creation
//
if (typeof LABKEY.app == 'undefined')
{
    LABKEY.app = {};
}

if (typeof LABKEY.app.controller == 'undefined')
{
    LABKEY.app.controller = {};
}

LABKEY.app.controller.Filter = new function() {

    var filtersFromJSON = function(jsonFilter) {
        var filterWrapper = Ext4.decode(jsonFilter);
        return filterWrapper.filters;
    };

    var filtersToJSON = function(filters, isLive) {
        return Ext4.encode({
            isLive : isLive,
            filters : Ext4.Array.pluck(filters, 'data')
        });
    };

    var lookupOperator = function(data) {
        if (data.operator)
            return data.operator;

        var ops = LABKEY.app.controller.Filter.Operators;

        switch (data.hierarchy) {
            case 'Study':
                return ops.UNION;
            case 'Participant.Race':
                return ops.UNION;
            case 'Participant.Country':
                return ops.UNION;
            case 'Participant.Sex':
                return ops.UNION;
            default:
                return ops.INTERSECT;
        }
    };

    var getOlapFilter = function(data) {
        var filter = {
            operator : lookupOperator(data),
            arguments: []
        };

        if (data.hierarchy == 'Participant') {

            filter.arguments.push({
                hierarchy : 'Participant',
                members  : data.members
            });
            return filter;
        }

        for (var m=0; m < data.members.length; m++) {
            filter.arguments.push({
                hierarchy : 'Participant',
                membersQuery : {
                    hierarchy : data.hierarchy,
                    members   : [data.members[m]]
                }
            });
        }

        return filter;
    };


    var getOlapFilters = function(datas) {
        var olapFilters = [];
        for (var i = 0; i < datas.length; i++) {
            olapFilters.push(getOlapFilter(datas[i]));
        }
        return olapFilters;
    };


    var doGroupUpdate = function(mdx, grpData, onGroupUpdated) {
        mdx.queryParticipantList({
            filter : getOlapFilters(filtersFromJSON(grpData.filters)),
            group : grpData,
            success : function (cs, mdx, config) {
                var group = config.group;
                var ids = Ext4.Array.pluck(Ext4.Array.flatten(cs.axes[1].positions),'name');
                LABKEY.ParticipantGroup.updateParticipantGroup({
                    rowId : group.rowId,
                    participantIds : ids,
                    success : function(group, response)
                    {
                        if (onGroupUpdated)
                            onGroupUpdated.call(this, group);
                    }
                });
            }
        });
    };

    // pass in a config object for the group data.  Note that this group data should contain
    // cds filters and
    var doGroupSave = function(mdx, onSuccess, onFailure, grpData) {
        mdx.queryParticipantList({
            useNamedFilters : ['statefilter'],
            success : function(cs) {
                var group = {
                    label : grpData.label,
                    participantIds : Ext.Array.pluck(Ext.Array.flatten(cs.axes[1].positions),'name'),
                    description : grpData.description,
                    shared : false,
                    type : 'list',
                    filters : filtersToJSON(grpData.filters, grpData.isLive)
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
    };

    return    {
        doGroupUpdate: doGroupUpdate,
        doGroupSave: doGroupSave,
        lookupOperator: lookupOperator,
        getOlapFilter: getOlapFilter,
        Operators: {
            UNION: 'UNION',
            INTERSECT: 'INTERSECT'
        }
    };
};










