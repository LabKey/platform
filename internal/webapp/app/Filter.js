/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.define('LABKEY.app.model.Filter', {
    extend: 'Ext.data.Model',

    fields : [
        {name : 'hierarchy'},
        {name : 'level'},
        {name : 'members', defaultValue: []},
        {name : 'operator'},
        {name : 'isGroup', type: 'boolean', defaultValue: false},
        {name : 'isGrid', type: 'boolean', defaultValue: false}, // TODO: rename to isSql
        {name : 'isPlot', type: 'boolean', defaultValue: false},
        {name : 'gridFilter', convert: function(o){ // TODO: rename to sqlFilters
            return Ext.isArray(o) ? o : [o];
        }, defaultValue: []}, // array of LABKEY.filter instances.
        {name : 'plotMeasures', defaultValue: [null, null, null], convert: function(o){
            var arr = [null, null, null];

            if (Ext.isArray(o)) {
                if (o.length == 1) {
                    arr[1] = o[0]; // If there's only 1 element then it's the y measure.
                } else if (o.length == 2) {
                    arr[0] = o[0];
                    arr[1] = o[1];
                } else if (o.length == 3) {
                    arr = o;
                }
            }

            return arr;
        }}, // array of measures
        {name : 'plotScales', defaultValue: []} // array of scales
    ],

    statics : {

        getErrorCallback : function () {
            return function(response)
            {
                var json = Ext.decode(response.responseText);
                if (json.exception) {
                    if (json.exception.indexOf('There is already a group named') > -1 ||
                            json.exception.indexOf('duplicate key value violates') > -1) {
                        // custom error response for invalid name
                        Ext.Msg.alert("Error", json.exception);
                    }
                    else {
                        Ext.Msg.alert("Error", json.exception);
                    }
                }
                else {
                    Ext.Msg.alert('Failed to Save', response.responseText);
                }
            }
        },

        /**
         * Save a participant group/filter either to a study-backed module (dataspace) or
         * for Argos.
         *
         * @param config, an object which takes the following configuation properties.
         * @param {Object} [config.mdx] mdx object against which participants are queried
         * @param  {Boolean} [config.isArgos] - Optional.  If true, then save as part of the argos schema,
         *         otherwise use study participant groups.
         * @param {Function} [config.failure] Optional.  Function called when the save action fails.  If not specified
         *        then a default function will be provided
         * @param {Function} [config.success] Function called when the save action is successful
         * @param {Object} [config.group] group definition.  The group object should have the following fields
         *         label - name of the group
         *         participantIds - array of participant Ids
         *         description - optional description for the gruop
         *         filters - array of filters to apply
         *         isLive - boolean, true if this is a query or false if just a group of participant ids.
         */
        doGroupSave : function(config) {
            if (!config)
                throw "You must specify a config object";

            if (!config.mdx || !config.group || !config.success)
                throw "You must specify mdx, group, and success members in the config";

            var group = config.group;
            var mdx = config.mdx;
            var m = LABKEY.app.model.Filter;

            // setup config for save call
            var requestConfig = {
                url: config.isArgos ? LABKEY.ActionURL.buildURL('argos', 'createPatientGroup')
                        : LABKEY.ActionURL.buildURL('participant-group', 'createParticipantCategory'),
                method: 'POST',
                success: config.success,
                failure: config.failure || m.getErrorCallback(),
                jsonData: {
                    label : group.label,
                    participantIds : [],
                    description : group.description,
                    shared : false,
                    type : 'list',
                    filters : m.toJSON(group.filters, group.isLive)
                },
                headers: {'Content-Type': 'application/json'}
            };

            if (config.isArgos && group.isLive) {
                //
                // for Argos we don't bother sending participant IDs if the group is live (save as query)
                //
                Ext.Ajax.request(requestConfig);
            }
            else {
                //
                // for study-backed modules we always save participant IDs so fetch them first
                //
                mdx.queryParticipantList({
                    useNamedFilters : ['statefilter'],
                    success : function(cs) {
                        // add the fetched participant ids to our json data
                        requestConfig.jsonData.participantIds = Ext.Array.pluck(Ext.Array.flatten(cs.axes[1].positions),'name');
                        Ext.Ajax.request(requestConfig);
                    }
                });
            }
        },
        /**
         * Updates a participant group for non-study backed modules
         *
         * @param config, an object which takes the following configuation properties.
         * @param {Object} [config.mdx] mdx object against which participants are queried
         * @param {String} [config.subjectName] subject name for the cube
         * @param {Function} [config.failure] Optional.  Function called when the save action fails.  If not specified
         *        then a default function will be provided
         * @param {Function} [config.success] Function called when the save action is successful
         * @param {Object} [config.group] group definition.  The group object should have the following fields
         *         rowId - the id of the category.  Assumes a 1:1 mapping between the group and category
         *         label - name of the group
         *         participantIds - array of participant Ids
         *         description - optional description for the gruop
         *         filters - array of filters to apply
         *         isLive - boolean, true if this is a query or false if just a group of participant ids.
         */
        doGroupUpdate : function(config) {
            if (!config)
                throw "You must specify a config object";

            if (!config.mdx || !config.group || !config.success)
                throw "You must specify mdx, group, and success members in the config";

            var group = config.group;
            var mdx = config.mdx;
            var subjectName = config.subjectName;
            var m = LABKEY.app.model.Filter;

            // setup config for save call
            var requestConfig = {
                url: LABKEY.ActionURL.buildURL('argos', 'updatePatientGroup'),
                method: 'POST',
                success: config.success,
                failure: config.failure || m.getErrorCallback(),
                jsonData: {
                    rowId : group.rowId,
                    label : group.label,
                    participantIds : [],
                    description : group.description,
                    shared : false,
                    type : 'list',
                    filters : m.toJSON(group.filters, group.isLive)
                },
                headers: {'Content-Type': 'application/json'}
            };

            if (group.isLive) {
                // don't bother sending participant ids for live filters
                Ext.Ajax.request(requestConfig);
            }
            else {
                mdx.queryParticipantList({
                    filter : m.getOlapFilters(group.filters, subjectName),
                    success : function(cs) {
                        // add the fetched participant ids to our json data
                        requestConfig.jsonData.participantIds = Ext.Array.pluck(Ext.Array.flatten(cs.axes[1].positions),'name');
                        Ext.Ajax.request(requestConfig);
                    }
                });
            }
        },

        /**
         * Modifies the participants in a participant group for a study-backed module.
         *
         * @param mdx object against which participants are queried
         * @param  onUpdateFailure - called if the function fails
         * @param  grpData
         */
        doParticipantUpdate : function(mdx, onUpdateSuccess, onUpdateFaiure, grpData) {
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

        /**
         * Deletes participant categories
         *
         * @param config, an object which takes the following configuation properties.
         * @param {Array} [config.categoryIds] array of rowids for each category to delete
         * @param {Function} [config.failure] Optional.  Function called when the save action fails.  If not specified
         *        then a default function will be provided
         * @param {Function} [config.success] Function called when the delete action is successful
         */
        deleteGroups : function(config) {
            if (!config)
                throw "You must specify a config object";

            if (!config.categoryIds || !config.success)
                throw "You must specify categoryIds and success members in the config";

            var m = LABKEY.app.model.Filter;
            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL('argos', 'deletePatientGroups'),
                method: 'POST',
                success: config.success,
                failure: config.failure || m.getErrorCallback(),
                jsonData: {
                    categoryIds : config.categoryIds
                },
                headers: {'Content-Type': 'application/json'}
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
            if (data['gridFilter']) { // TODO: change to look for sqlFilters
                for (var i = 0; i < data['gridFilter'].length; i++) {
                    var gf = data['gridFilter'][i];

                    if (!Ext.isFunction(gf.getColumnName))
                    {
                        console.warn('invalid filter object being processed.');
                        return 'Unknown';
                    }
                    var splitLabel = gf.getColumnName().split('/');
                    var endLabel = "", first, real;
                    if (splitLabel.length > 1) {
                        // we're dealing with a presumed lookup filter
                        first = splitLabel[0].split('_');
                        real = first[first.length-1];

                        endLabel = real + '/' + splitLabel[splitLabel.length-1];
                    }
                    else {
                        // Just a normal column
                        first = splitLabel[0].split('_');
                        real = first[first.length-1];

                        endLabel = real;
                    }

                    return endLabel;
                }
            }
            return 'Unknown';
        },

        getGridLabel : function(data) {
            if (data['gridFilter']) { // TODO: change to look for sqlFilters
                var gf = data.gridFilter[0]; // TODO: Find a better way than hard coding this.
                if (!Ext.isFunction(gf.getFilterType))
                {
                    console.warn('invalid label being processed');
                    return 'Unknown';
                }
                var value = gf.getValue();
                if (!value) {
                    value = "";
                }
                return LABKEY.app.model.Filter.getShortFilter(gf.getFilterType().getDisplayText()) + ' ' + value;
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

        dynamicOperatorTypes: false,

        lookupOperator : function(data) {

            if (LABKEY.app.model.Filter.dynamicOperatorTypes) {
                return LABKEY.app.model.Filter.convertOperatorType(data.operator);
            }
            else {
                // Backwards compatible
                if (data.operator) {
                    return data.operator;
                }

                var ops = LABKEY.app.model.Filter.Operators;

                // TODO: Remove this switch once fb_infopane is merged as this is Dataspace specific
                switch (data.hierarchy) {
                    case '[Study]':
                        return ops.UNION;
                    case '[Subject.Race]':
                        return ops.UNION;
                    case '[Subject.Country]':
                        return ops.UNION;
                    case '[Subject.Sex]':
                        return ops.UNION;
                    default:
                        return ops.INTERSECT;
                }
            }
        },

        emptyLabelText: 'Unknown',

        getMemberLabel : function(member) {
            var label = member;
            if (!Ext.isString(label) || label.length === 0 || label === "#null") {
                label = LABKEY.app.model.Filter.emptyLabelText;
            }
            return label;
        },

        convertOperatorType : function(type) {

            if (!type || (Ext.isString(type) && type.length == 0)) {
                return LABKEY.app.model.Filter.Operators.INTERSECT;
            }

            var TYPES = LABKEY.app.model.Filter.OperatorTypes;
            var OPS = LABKEY.app.model.Filter.Operators;

            switch (type) {
                case TYPES.AND:
                    return OPS.INTERSECT;
                case TYPES.REQ_AND:
                    return OPS.INTERSECT;
                case TYPES.OR:
                    return OPS.UNION;
                case TYPES.REQ_OR:
                    return OPS.UNION;
            }

            console.error('invalid operator type:', type);
        },

        convertOperator : function(operator) {
            var TYPES = LABKEY.app.model.Filter.OperatorTypes;
            var OPS = LABKEY.app.model.Filter.Operators;

            switch (operator) {
                case OPS.UNION:
                    return TYPES.OR;
                case OPS.INTERSECT:
                    return TYPES.AND;
            }

            console.error('invalid operator:', operator);
        },

        OperatorTypes: {
            AND: 'AND',
            REQ_AND: 'REQ_AND',
            OR: 'OR',
            REQ_OR: 'REQ_OR'
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

    removeMember : function(memberUniqueName) {

        // Allow for removal of the entire filter if a unique name is not provided
        var newMembers = [];
        if (memberUniqueName) {
            var dataUniqueName;

            for (var m=0; m < this.data.members.length; m++) {
                dataUniqueName = this.data.members[m].uniqueName;
                if (memberUniqueName !== dataUniqueName)
                {
                    newMembers.push(this.data.members[m]);
                }
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
                    (d.members.length == fd.members.length) && (d.operator == fd.operator);

            if (eq) {
                // member set equivalency
                var keys = {}, m, uniqueName;
                for (m=0; m < d.members.length; m++) {
                    uniqueName = d.members[m].uniqueName;
                    keys[uniqueName] = true;
                }

                for (m=0; m < fd.members.length; m++) {
                    uniqueName = fd.members[m].uniqueName;
                    if (!Ext.isDefined(keys[uniqueName])) {
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







