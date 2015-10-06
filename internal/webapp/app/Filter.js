/*
 * Copyright (c) 2014-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
// This file is used outside of the application environment, so ExtJS 4.X must be referenced as Ext4.
Ext.define('LABKEY.app.model.Filter', {
    extend: 'Ext.data.Model',

    fields : [
        {name : 'hierarchy'},
        {name : 'level'},
        {name : 'members', defaultValue: []},
        {name : 'membersName'},
        {name : 'perspective'},
        {name : 'operator'},
        {name : 'isGrid', type: 'boolean', defaultValue: false},
        {name : 'isPlot', type: 'boolean', defaultValue: false},
        {name : 'ranges', defaultValue: []},

        // array of LABKEY.Filter instances
        {name : 'gridFilter', defaultValue: [], convert: function(raw) {
            var filters = [];
            if (Ext.isArray(raw)) {
                Ext.each(raw, function(r) {
                    if (Ext.isString(r)) {
                        if (r === "_null") {
                            filters.push(null);
                        }
                        else {
                            var build = LABKEY.Filter.getFiltersFromUrl(r, 'query');
                            if (Ext.isArray(build)) {
                                filters.push(build[0]); // assume single filters
                            }
                        }
                    }
                    else if (Ext.isDefined(r)) {
                        filters.push(r);
                    }
                });
            }
            else if (Ext.isDefined(raw)) {
                filters.push(raw);
            }
            return filters;
        }},

        // array of measures
        {name : 'plotMeasures', defaultValue: [null, null, null], convert: function(o) {
            var arr = [null, null, null];

            if (Ext.isArray(o)) {
                if (o.length == 1) {
                    arr[1] = o[0]; // If there's only 1 element then it's the y measure.
                }
                else if (o.length == 2) {
                    arr[0] = o[0];
                    arr[1] = o[1];
                }
                else if (o.length == 3) {
                    arr = o;
                }
                else {
                    console.warn('You provided an invalid value for plotMeasures.');
                }
            }

            if (Ext.isArray(arr)) {
                Ext.each(arr, function (measure) {
                    if (Ext.isDefined(measure) && measure && Ext.isDefined(measure.filterArray)) {
                        if (Ext.isArray(measure.filterArray)) {
                            var filters = [];
                            Ext.each(measure.filterArray, function (filter) {
                                if (Ext.isString(filter)) {
                                    if (filter === "_null") {
                                        filters.push(null);
                                    }
                                    else {
                                        var build = LABKEY.Filter.getFiltersFromUrl(filter, 'query');
                                        if (Ext.isArray(build)) {
                                            filters.push(build[0]); // assume single filters
                                        }
                                    }
                                }
                                else if (Ext.isDefined(filter)) {
                                    filters.push(filter);
                                }
                            });
                            measure.filterArray = filters;
                        }
                    }
                });
            }

            return arr;
        }},

        {name : 'isWhereFilter', type: 'boolean', defaultValue: false},
        {name : 'showInverseFilter', type: 'boolean', defaultValue: false},
        {name : 'filterSource', defaultValue: 'OLAP'} // OLAP or GETDATA
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
         *         description - optional description for the group
         *         filters - array of LABKEY.app.model.Filter instances to apply
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

            var controller = config.isArgos ? 'argos' : 'participant-group';
            var action = config.isArgos ? 'createPatientGroup' : 'createParticipantCategory';
            var jsonData =  {
                label : group.label,
                participantIds : [],
                description : group.description,
                ownerId : LABKEY.user.id,
                type : 'list',
                visibility : group.visibility,
                filters : m.toJSON(group.filters, group.isLive)
            };
            if (config.isArgos && group.share !== undefined) {

                jsonData['sharePrincipalIds'] = group.share.principals;
                jsonData['shareSendEmail'] = group.share.sendEmail;
            }

            // setup config for save call
            var requestConfig = {
                url: LABKEY.ActionURL.buildURL(controller, action),
                method: 'POST',
                success: config.success,
                failure: config.failure || m.getErrorCallback(),
                jsonData: jsonData,
                headers: {"Content-Type": 'application/json', "X-LABKEY-CSRF":LABKEY.CSRF}
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
         * @param config, an object which takes the following configuration properties.
         * @param {Object} [config.mdx] mdx object against which participants are queried
         * @param {String} [config.subjectName] subject name for the cube
         * @param {Function} [config.failure] Optional.  Function called when the save action fails.  If not specified
         *        then a default function will be provided
         * @param {Function} [config.success] Function called when the save action is successful
         * @param {Object} [config.group] group definition.  The group object should have the following fields
         *         rowId - the id of the category.  Assumes a 1:1 mapping between the group and category
         *         label - name of the group
         *         participantIds - array of participant Ids
         *         description - optional description for the group
         *         filters - array of LABKEY.app.model.Filter instances to apply
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
                    visibility : group.visibility,
                    ownerId : LABKEY.user.id,
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
         * Updates a participant group's visibility option for non-study backed modules
         *
         * @param config, an object which takes the following configuration properties.
         * @param {Function} [config.success] Function called when the update action is successful
         * @param {Function} [config.failure] Function called when the update action fails.  If not specified
         *        then a default function will be provided
         * @param {Object} [config.group] group definition.  The group object should have the following fields
         *         rowId - the id of the category.  Assumes a 1:1 mapping between the group and category
         *         visibility - the enum value of the visibility option, must be one of: {hidden, grid, dashboard)
         */
        updateGroupVisibility : function(config) {
            if (!config)
                throw "You must specify a config object";

            if (!config.group || !config.success)
                throw "You must specify group, and success members in the config";

            var group = config.group;
            var m = LABKEY.app.model.Filter;

            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL('argos', 'updatePatientGroupVisibility'),
                method: 'POST',
                success: config.success,
                failure: config.failure || m.getErrorCallback(),
                jsonData: {
                    rowId : group.rowId,
                    visibility : group.visibility
                },
                headers: {'Content-Type': 'application/json'}
            });
        },

        /**
         * Modifies the participants in a participant group for a study-backed module.
         */
        doParticipantUpdate : function(mdx, successFn, failureFn, grpData, subjectName) {
            var m = LABKEY.app.model.Filter;
            mdx.queryParticipantList({
                filter : m.getOlapFilters(mdx, m.fromJSON(grpData.filters), subjectName),
                group : grpData,
                success : function (cs, mdx, config) {
                    var group = config.group;
                    var ids = Ext.Array.pluck(Ext.Array.flatten(cs.axes[1].positions),'name');
                    LABKEY.ParticipantGroup.updateParticipantGroup({
                        rowId : group.rowId,
                        participantIds : ids,
                        success : function(group, response) {
                            if (Ext.isFunction(successFn))
                                successFn.call(this, group);
                        },
                        failure : function() {
                            if (Ext.isFunction(failureFn)) {
                                failureFn.apply(this, arguments);
                            }
                        }
                    });
                }
            });
        },

        /**
         * Deletes participant categories
         *
         * @param config an object which takes the following configuration properties.
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

        /**
         *
         * @param {Array} filters Array of LABKEY.app.model.Filter instances to encode
         * @param {boolean} isLive
         * @returns {*}
         */
        toJSON : function(filters, isLive) {

            var jsonFilters = [];
            Ext.each(filters, function(f) {
                jsonFilters.push(f.jsonify());
            });

            return Ext.encode({
                isLive : isLive,
                filters : jsonFilters
            });
        },

        getGridHierarchy : function(data) {
            var gf = data['gridFilter'];
            if (!Ext.isEmpty(gf)) {
                return LABKEY.app.model.Filter.getGridFilterLabel(gf[0]);
            }
            return LABKEY.app.model.Filter.emptyLabelText;
        },

        getGridFilterLabel : function(gf) {
            var endLabel = "";

            if (Ext.isFunction(gf.getColumnName))
            {
                var splitLabel = gf.getColumnName().split('/');
                var first = splitLabel[0].split('_');
                var real = first[first.length-1];

                if (splitLabel.length > 1) {
                    // we're dealing with a presumed lookup filter
                    endLabel = real + '/' + splitLabel[splitLabel.length-1];
                }
                else {
                    // Just a normal column
                    endLabel = real;
                }
            }
            else {
                console.warn('invalid filter object being processed.');
                endLabel = LABKEY.app.model.Filter.emptyLabelText;
            }

            return endLabel;
        },

        getGridLabel : function(data) {
            var filterLabel = function(gf) {
                if (gf) {
                    if (!Ext.isFunction(gf.getFilterType))
                    {
                        console.warn('invalid label being processed');
                        return LABKEY.app.model.Filter.emptyLabelText;
                    }
                    var value = gf.getValue();
                    if (!value) {
                        value = '';
                    }
                    return LABKEY.app.model.Filter.getShortFilter(gf.getFilterType().getDisplayText()) + ' ' + Ext.htmlEncode(value);
                }
                return LABKEY.app.model.Filter.emptyLabelText;
            };

            if (data['gridFilter']) {
                var label = '';
                var sep = '';
                Ext.each(data.gridFilter, function(gf) {
                    label += sep + filterLabel(gf);
                    sep = ', ';
                });
                return label;
            }

            return filterLabel(data);
        },

        // Data Filter Provider
        dfProvider : undefined,

        registerDataFilterProvider : function(providerFn, scope) {
            LABKEY.app.model.Filter.dfProvider = {fn: providerFn, scope: scope};
        },

        _buildGetDataFilter : function(filter, data) {
            var M = LABKEY.app.model.Filter;
            return  M.dfProvider.fn.call(M.dfProvider.scope, filter, data);
        },

        getOlapFilter : function(mdx, data, subjectName) {
            if (!Ext.isDefined(mdx) || mdx.$className !== 'LABKEY.query.olap.MDX') {
                console.error('must provide mdx to getOlapFilter');
            }

            var M = LABKEY.app.model.Filter;

            var filter = {
                filterType: data.isWhereFilter === true ? 'WHERE' : 'COUNT'
            };

            if (data.filterSource === 'GETDATA') {

                if (Ext.isDefined(M.dfProvider)) {
                    // TODO: Figure out how this works with perspectives, maybe it doesn't care at all?
                    filter = M._buildGetDataFilter(filter, data);
                }
                else {
                    console.error('Failed to register a data filter provider. See', M + '.registerDataFilterProvider()');
                }
            }
            else {

                filter.operator = M.lookupOperator(data);
                filter.arguments = [];

                if (data.perspective) {

                    filter.perspective = data.perspective;

                    //
                    // The target hierarchy is
                    //
                    if (data.hierarchy == subjectName) {
                        filter.arguments.push({
                            hierarchy: subjectName,
                            members: data.members
                        });
                    }
                    else {
                        Ext.each(data.members, function(member) {
                            filter.arguments.push({
                                level: mdx.perspectives[data.perspective].level,
                                membersQuery: {
                                    hierarchy: data.hierarchy,
                                    members: [member]
                                }
                            });
                        });
                    }
                }
                else {
                    if (M.usesMemberName(data, subjectName)) {

                        var m = data.members;
                        if (data.membersName && data.membersName.length > 0) {
                            m = { namedSet: data.membersName };
                        }

                        filter.arguments.push({
                            hierarchy: subjectName,
                            members: m
                        });
                    }
                    else {
                        for (var m=0; m < data.members.length; m++) {
                            filter.arguments.push({
                                hierarchy : subjectName,
                                membersQuery : {
                                    hierarchy : data.hierarchy,
                                    members   : [data.members[m]]
                                }
                            });
                        }
                    }
                }
            }

            return filter;
        },

        usesMemberName : function(data, subjectName) {
            return data.hierarchy == subjectName;
        },

        getOlapFilters : function(mdx, datas, subjectName) {
            if (!Ext.isFunction(mdx.getDimension)) {
                console.error('must provide mdx to getOlapFilter');
            }
            var olapFilters = [];
            for (var i = 0; i < datas.length; i++) {
                olapFilters.push(LABKEY.app.model.Filter.getOlapFilter(mdx, datas[i], subjectName));
            }
            return olapFilters;
        },

        getShortFilter : function(displayText) {
            switch (displayText) {
                case "Does Not Equal":
                    return '&#8800;';
                case "Equals":
                    return '=';
                case "Is Greater Than":
                    return '>';
                case "Is Less Than":
                    return '<';
                case "Is Greater Than or Equal To":
                    return '&#8805;';
                case "Is Less Than or Equal To":
                    return '&#8804;';
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
            }

            return LABKEY.app.model.Filter.Operators.INTERSECT;
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
        },

        sorters: {
            // 0 == 'auto / no special treatment', 1 == 'first', 2 == 'last',
            SORT_EMPTY_TYPE: {
                AUTO: 0,
                FIRST: 1,
                LAST: 2
            },
            SORT_EMPTY: 0,
            _reA: /[^a-zA-Z]/g,
            _reN: /[^0-9]/g,
            /**
             * A valid Array.sort() function that sorts an Array of strings alphanumerically. This sort is case-insensitive
             * and only permits valid instances of string (not undefined, null, etc).
             * @param a
             * @param b
             * @returns {number}
             */
            alphaNum : function(a, b) {
                a = a.toLowerCase(); b = b.toLowerCase();

                var _empty = LABKEY.app.model.Filter.sorters.handleEmptySort(a, b);
                if (_empty !== undefined) {
                    return _empty;
                }

                var aA = a.replace(LABKEY.app.model.Filter.sorters._reA, "");
                var bA = b.replace(LABKEY.app.model.Filter.sorters._reA, "");
                if (aA === bA) {
                    var aN = parseInt(a.replace(LABKEY.app.model.Filter.sorters._reN, ""), 10);
                    var bN = parseInt(b.replace(LABKEY.app.model.Filter.sorters._reN, ""), 10);
                    return aN === bN ? 0 : aN > bN ? 1 : -1;
                }
                return aA > bA ? 1 : -1;
            },
            natural : function (aso, bso) {
                // http://stackoverflow.com/questions/19247495/alphanumeric-sorting-an-array-in-javascript
                var a, b, a1, b1, i= 0, n, L,
                        rx=/(\.\d+)|(\d+(\.\d+)?)|([^\d.]+)|(\.\D+)|(\.$)/g;
                if (aso === bso) return 0;
                a = aso.toLowerCase().match(rx);
                b = bso.toLowerCase().match(rx);

                var _empty = LABKEY.app.model.Filter.sorters.handleEmptySort(a, b);
                if (_empty !== undefined) {
                    return _empty;
                }

                L = a.length;
                while (i < L) {
                    if (!b[i]) return 1;
                    a1 = a[i]; b1 = b[i++];
                    if (a1 !== b1) {
                        n = a1 - b1;
                        if (!isNaN(n)) return n;
                        return a1 > b1 ? 1 : -1;
                    }
                }
                return b[i] ? -1 : 0;
            },
            handleEmptySort : function(a, b) {
                if (LABKEY.app.model.Filter.sorters.SORT_EMPTY !== 0 && (a == 'null' || b == 'null')) {
                    var aEmpty = a == 'null';
                    var bEmpty = b == 'null';

                    // both are empty
                    if (aEmpty && bEmpty) {
                        return 0;
                    }

                    if (LABKEY.app.model.Filter.sorters.SORT_EMPTY === 1 /* first */) {
                        return aEmpty ? -1 : 1;
                    }
                    else if (LABKEY.app.model.Filter.sorters.SORT_EMPTY === 2 /* last */) {
                        return aEmpty ? 1 : -1;
                    }
                }
                // return undefined;
            },
            resolveSortStrategy : function(sortStrategy) {
                switch (sortStrategy) {
                    case 'ALPHANUM':
                        return LABKEY.app.model.Filter.sorters.alphaNum;
                    case 'ALPHANUM-RANGE':
                        return function(a, b) {
                            return LABKEY.app.model.Filter.sorters.alphaNum(a.split('-')[0], b.split('-')[0]);
                        };
                    case 'NATURAL':
                        return LABKEY.app.model.Filter.sorters.natural;
                    case 'SERVER':
                    default:
                        return false;
                }
            }
        },

        mergeRanges : function(filterA, filterB) {

            // If filterA is a member list and the new filter is a range, drop the range from filterB and merge will be a member list
            // if filterA is a range and the filterB is a member list, drop the range from filterA and merge will be a member list
            // else concatenate the ranges filters

            var numRangesA = filterA.getRanges().length,
                numRangesB = filterB.getRanges().length;

            // no ranges to merge
            if (numRangesA === 0 && numRangesB === 0) {
                return;
            }

            if (numRangesA === 0 && numRangesB > 0) {
                filterB.set('ranges', []);
            }
            else if (numRangesA > 0 && numRangesB === 0) {
                filterA.set('ranges', []);
            }
            else {
                // They both contain ranges
                filterA.set('ranges', filterA.getRanges().concat(filterB.getRanges()));
            }
        }
    },

    getOlapFilter : function(mdx, subjectName) {
        return LABKEY.app.model.Filter.getOlapFilter(mdx, this.data, subjectName);
    },

    getHierarchy : function() {
        return this.get('hierarchy');
    },

    getRanges : function() {
        return this.get('ranges');
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
     * - isGrid, isPlot, hierarchy, member length, and member set (member order insensitive)
     * @param f - Filter to compare this object against.
     */
    isEqual : function(f) {
        var eq = false;

        if (Ext.isDefined(f) && Ext.isDefined(f.data)) {
            var d = this.data;
            var fd = f.data;

            eq = (d.isGrid == fd.isGrid) &&
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

    /**
     * Complex comparator that says two filters can be merged. This should always be called
     * in advance of calling merge() to be safe.
     * @param f
     */
    canMerge : function(f) {
        var data = this.data,
            fdata = f.data,
            _merge = false;

        // If any plot/grid settings are configured to true
        if (data.isPlot || fdata.isPlot || data.isGrid || fdata.isGrid) {
            if (data.isPlot === fdata.isPlot && data.isGrid === fdata.isGrid) {

                // if plot, check plotMeasures and gridFilter properties
                if (data.isPlot) {

                    var _mergeMeasures = true, pm, fpm;

                    for (var i=0; i < data.plotMeasures.length; i++) {
                        pm = data.plotMeasures[i];
                        fpm = fdata.plotMeasures[i];

                        if (pm === null) {
                            if (fpm !== null) {
                                _mergeMeasures = false;
                                break;
                            }

                            // they are both null, OK
                        }
                        else {
                            // equivalent if they have the same alias
                            if (pm.measure && fpm.measure) {
                                if (pm.measure.alias !== fpm.measure.alias) {
                                    _mergeMeasures = false;
                                    break;
                                }
                            }
                            else {
                                console.warn('Unknown plot measure configuration. Expected to have \'measure\' property on each \'plotMeasure\'. Unable to determine merge strategy.');
                                _mergeMeasures = false;
                                break;
                            }
                        }
                    }

                    if (_mergeMeasures) {
                        // check gridFilters
                        for (i=0; i < data.gridFilter.length; i++) {
                            pm = data.gridFilter[i];
                            fpm = fdata.gridFilter[i];

                            if (pm === null) {
                                if (fpm !== null) {
                                    _mergeMeasures = false;
                                    break;
                                }

                                // they are both null, OK
                            }
                            else {
                                // equivalent if they have the same URL prefix -- value can change
                                if (pm.getURLParameterName().toLowerCase() !== fpm.getURLParameterName().toLowerCase()) {
                                    _mergeMeasures = false;
                                    break;
                                }
                            }
                        }
                    }

                    _merge = _mergeMeasures;
                }
                else {
                    _merge = true;
                }
            }
        }
        else if (data.hierarchy && fdata.hierarchy && data.hierarchy === fdata.hierarchy) {
            _merge = true;
        }

        return _merge;
    },

    merge : function(f) {

        var update = {
            members: this._mergeMembers(this.data.members, f.data.members)
        };

        if (this.data.isPlot) {
            update.gridFilter = this._mergeGridFilters(this.data.gridFilter, f.data.gridFilter);
        }

        this.set(update);

        return this;
    },

    _mergeMembers : function(aMembers, bMembers) {
        var _members = Ext.Array.clone(aMembers);
        for (var i=0; i < bMembers.length; i++) {
            if (!this._hasMember(_members, bMembers[i])) {
                _members.push(bMembers[i]);
            }
        }
        return _members;
    },

    _mergeGridFilters : function(aGridFilters, bGridFilters) {
        var _measures = Ext.Array.clone(aGridFilters);

        for (var i=0; i < bGridFilters.length; i++) {
            _measures[i] = Ext.clone(bGridFilters[i]);
        }

        return _measures;
    },

    _hasMember : function(memberArray, newMember) {
        // issue 19999: don't push duplicate member if re-selecting
        for (var k = 0; k < memberArray.length; k++) {
            if (!memberArray[k].hasOwnProperty('uniqueName') || !newMember.hasOwnProperty('uniqueName'))
                continue;

            if (memberArray[k].uniqueName == newMember.uniqueName)
                return true;
        }

        return false;
    },

    isGrid : function() {
        return this.get('isGrid');
    },

    isPlot : function() {
        return this.get('isPlot');
    },

    isWhereFilter : function() {
        return this.get('isWhereFilter');
    },

    usesCaching : function(subjectName) {
        return LABKEY.app.model.Filter.usesMemberName(this.data, subjectName);
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

    getValue : function(key) {
        return this.data[key];
    },

    /**
     * This method should be called before attempting to write a filter model to JSON.
     * This is due to the fact that some properties do not represent themselves properly using
     * JSON.stringify and they have to be manually curated.
     * @returns {Object}
     */
    jsonify : function() {
        var jsonable = Ext.clone(this.data);
        if (Ext.isArray(jsonable.gridFilter)) {
            var jsonGridFilters = [];
            Ext.each(jsonable.gridFilter, function(filter) {
                if (Ext.isDefined(filter)) {
                    if (filter === null) {
                        jsonGridFilters.push("_null");
                    }
                    else if (Ext.isString(filter)) {
                        jsonGridFilters.push(filter);
                    }
                    else {
                        var composed = filter.getURLParameterName() + '=' + filter.getURLParameterValue();
                        jsonGridFilters.push(composed);
                    }
                }
            });
            jsonable.gridFilter = jsonGridFilters;
        }

         if (Ext.isArray(jsonable.plotMeasures)) {
             Ext.each(jsonable.plotMeasures, function(measure) {
                if (Ext.isDefined(measure)) {
                    if (measure === null || Ext.isString(measure)) {
                        return;
                    }
                    else {
                        if (measure && Ext.isArray(measure.filterArray)) {
                            var jsonFilters = [];
                            Ext.each(measure.filterArray, function (filter) {
                                if (Ext.isDefined(filter)) {
                                    if (filter === null) {
                                        jsonFilters.push("_null");
                                    }
                                    else if (Ext.isString(filter)) {
                                        jsonFilters.push(filter);
                                    }
                                    else {
                                        var composed = filter.getURLParameterName() + '=' + filter.getURLParameterValue();
                                        jsonFilters.push(composed);
                                    }
                                }
                            });
                            measure.filterArray = jsonFilters;
                        };
                    }
                }
            });
        }

        // remove properties that do not persist across refresh
        delete jsonable.id;
        delete jsonable.membersName;

        return jsonable;
    }
});







