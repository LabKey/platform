/*
 * Copyright (c) 2013-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */


/*
 * These apis are fairly closely modeled on olap4j (except for being async, of course).
 */
(function() {

    var _private = new function() {

        var _cache = new Ext4.util.MixedCollection();

        var createUniqueName = function(names)
        {
            var ret = "";
            var delim = "[";
            for (var a=0 ; a<arguments.length ; a++)
            {
                var parts = Ext4.isArray(arguments[a]) ? arguments[a] : [arguments[a]];
                for (var p=0 ; p<parts.length ; p++)
                {
                    ret += delim + parts[p];
                    delim = "].[";
                }
            }
            ret += "]";
            return ret;
        };

        var parseUniqueName = function(uniqueName)
        {
            if (uniqueName.charAt(0) != '[' || uniqueName.charAt(uniqueName.length-1) != ']')
            {
                console.log("is this really a uniqueName: " + uniqueName);
                return [uniqueName];
            }
            return uniqueName.substring(1,uniqueName.length-1).split("].[");
        };

        var executeMdx = function(config)
        {
            if (!config.configId)
                console.error("OLAP: no configId specified");

            var container = config.container || LABKEY.ActionURL.getContainer();
            config._cacheKey = container + ":" + config.query;

            var cacheObj = _cache.removeAtKey(config._cacheKey);
            if (cacheObj)
            {
                cacheObj.time = new Date().getTime();
                _cache.add(config._cacheKey, cacheObj);   // lru
                if (Ext4.isFunction(config.success))
                    config.success.apply(config.scope||window, [cacheObj.cellset, config]);
                removeStale();
                return;
            }

            return Ext4.Ajax.request({
                url : LABKEY.ActionURL.buildURL("olap", "executeMdx", config.containerPath),
                method : 'POST',
                success: function(r){postProcessExecuteMdx(r,config);},
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData : { query:config.query, configId:config.configId, schemaName:config.schemaName },
                headers : { 'Content-Type' : 'application/json' }
            });
        };


        var executeJson = function(config)
        {
            if (!config.configId)
                console.error("OLAP: no configId specified");

            var container = config.container || LABKEY.ActionURL.getContainer();
            var str = JSON.stringify(config.query);
            config._cacheKey = container + ":" + str;

            var cacheObj = _cache.removeAtKey(config._cacheKey);
            if (cacheObj)
            {
                cacheObj.time = new Date().getTime();
                _cache.add(config._cacheKey, cacheObj);   // lru
                if (Ext4.isFunction(config.success))
                    config.success.apply(config.scope||window, [cacheObj.cellset, config]);
                removeStale();
                return;
            }

            var action = config.originalConfig.useJsonQuery ? "jsonQuery.api" : "countDistinctQuery.api";

            return Ext4.Ajax.request(
                    {
                        url : LABKEY.ActionURL.buildURL("olap", action, config.containerPath),
                        method : 'POST',
                        success: function(r){postProcessExecuteMdx(r,config);},
                        failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                        jsonData : { query:config.query, configId:config.configId, schemaName:config.schemaName, cubeName:config.cubeName },
                        headers : { 'Content-Type' : 'application/json' }
                    });
        };


        var flattenCellSet = function(cs)
        {
            var rows = [], r;
            for (r=0 ; r<cs.cells.length ; r++)
                for (var c=0; c<cs.cells[r].length ; c++)
                    rows.push(cs.cells[r][c]);
            for (r=0 ; r<rows.length ; r++)
            {
                var row = rows[r];
                for (var p=0 ; p<row.positions.length ; p++)
                {
                    var position = row.positions[p];
                    for (var m=0 ; m<position.length ; m++)
                    {
                        var member = position[m];
                        row[member.level.uniqueName] =  member.name;
                    }
                }
            }
            return rows;
        };

        var getCubeDefinition = function(config)
        {
            config.includeMembers = (config && 'includeMembers' in config) ? config.includeMembers : true;

            Ext4.Ajax.request({
                url : LABKEY.ActionURL.buildURL('olap', 'getCubeDefinition.api', config.containerPath),
                method : 'POST',
                jsonData: {
                    cubeName:config.name,
                    configId:config.configId,
                    contextName:config.contextName,
                    schemaName:config.schemaName,
                    includeMembers:config.includeMembers,
                    memberExclusionFields:config.memberExclusionFields
                },
                success: function(r){postProcessGetCubeDefinition(r, config);},
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        };

        var postProcessCellSet = function(cs)
        {
            var idMap = {};
            if (cs.metadata && cs.metadata.cube)
                idMap = postProcessCube(cs.metadata.cube);

            // look for level references
            for (var a=0 ; a<cs.axes.length ; a++)
            {
                var axis = cs.axes[a];
                for (var p=0 ; p<axis.positions.length ; p++)
                {
                    var position = axis.positions[p];
                    for (var m=0 ; m<position.length ; m++)
                    {
                        var member = position[m];
                        var level = member.level;
                        if (Ext4.isString(level) && level.charAt(0) == '#')
                            member.level = idMap[level];
                    }
                }
            }

            // fix up cell data with pointers to row/column position
            for (var r=0 ; r<cs.cells.length ; r++)
            {
                for (var c=0; c<cs.cells[r].length ; c++)
                {
                    var cell = cs.cells[r][c];
                    if (!Ext4.isObject(cell))
                        cs.cells[r][c] = cell = {value:cell};
                    cell.coordinateList = [c,r];
                    cell.positions = [cs.axes[0].positions[c], cs.axes[1].positions[r]];
                }
            }
        };

        var postProcessCube = function(cube)
        {
            var idMap = {};
            for (var d=0 ; d<cube.dimensions.length ; d++)
            {
                var dim = cube.dimensions[d];
                dim.cube = cube;
                if (dim.id)
                    idMap['#' + dim.id] = dim;
                for (var h=0 ; h<dim.hierarchies.length ; h++)
                {
                    var hier = dim.hierarchies[h];
                    hier.dimension = dim;
                    if (hier.id)
                        idMap['#' + hier.id] = hier;
                    for (var l=0 ; l<hier.levels.length ; l++)
                    {
                        var level = hier.levels[l];
                        level.hierarchy = hier;
                        if (level.id)
                            idMap['#' + level.id] = level;
                    }
                }
            }
            return idMap;
        };

        var postProcessExecuteMdx = function(response,config)
        {
            var cellset = Ext4.decode(response.responseText);
            postProcessCellSet(cellset);

            removeStale();
            _cache.add(config._cacheKey, {time:new Date().getTime(), cellset:cellset});

            if (Ext4.isFunction(config.success))
                config.success.apply(config.scope||window, [cellset, config]);
        };

        var postProcessGetCubeDefinition = function(response,config)
        {
            var o = Ext4.decode(response.responseText);
            var cube = o.cube;
            var context = o.context;
            postProcessCube(cube);
            if (Ext4.isFunction(config.success))
                config.success.apply(config.scope||window, [cube, context, config]);
        };

        var removeStale = function()
        {
            var young = new Date().getTime() - 60*1000;
            while (_cache.getCount()>20 && _cache.getAt(0).time<young)
                _cache.removeAt(0);
        };

        return {
            createUniqueName:createUniqueName,
            parseUniqueName:parseUniqueName,
            getCubeDefinition:getCubeDefinition,
            executeMdx:executeMdx,
            executeJson:executeJson,
            flattenCellSet:flattenCellSet
        }
    };

    /*
     * CUBE METADATA
     */
    Ext4.define('LABKEY.query.olap.metadata.MetadataElement', {

        getName : function()
        {
            return this.name;
        },

        getUniqueName : function()
        {
            if (!this.uniqueName && this.uname)
                this.uniqueName = _private.createUniqueName(this.uname);
            return this.uniqueName;
        },

        getUniqueNameArray : function()
        {
            if (!this.uname && this.uniqueName)
                this.uname = _private.parseUniqueName(this.uniqueName);
            return this.uname;
        },

        getCaption : function()
        {
            return this.caption;
        },

        getDescription : function()
        {
            return this.description;
        },

        isVisible : function()
        {
            return this.visible !== false;
        }
    });

    Ext4.define('LABKEY.query.olap.metadata.Member', {
        extend: 'LABKEY.query.olap.metadata.MetadataElement',

        constructor : function(member, parent)
        {
            Ext4.apply(this,member);
            this.level = parent;
            this.level.hierarchy.dimension.cube.uniqueNameMap[this.uniqueName] = this;
        },
        getLevel : function()
        {
            return this.level;
        },
        getParent : function()
        {
            return this.level;
        }
    });

    Ext4.define('LABKEY.query.olap.metadata.Level', {
        extend: 'LABKEY.query.olap.metadata.MetadataElement',

        constructor : function(level, lnum, parent)
        {
            Ext4.apply(this,level);
            this.depth = lnum;
            this.hierarchy = parent;
            this.members = null;
            if (Ext4.isArray(level.members))
            {
                this.members = [];
                for (var m=0 ; m<level.members.length ; m++)
                    this.members.push(new LABKEY.query.olap.metadata.Member(level.members[m], this));
            }
            this.hierarchy.dimension.cube.uniqueNameMap[this.uniqueName] = this;
            this.hierarchy.dimension.cube.levelMap[this.name] = this;
        },
        // @deprecated
        getLNUM : function()
        {
            return this.depth;
        },
        getDepth : function()
        {
            return this.depth;
        },
        listMembers : function(config)
        {
            var cube = this.hierarchy.dimension.cube;

            if (this.members)
            {
                if (config.success)
                    config.success.apply(config.scope||window, [this.members, this, config]);
                return;
            }
            if (this.loading)
            {
                if (!config.success)
                    return;
                var me = this;
                var listener = function(level, members)
                {
                    if (level != me)
                        return;
                    cube.removeListener(listener);
                    config.success.apply(config.scope||window, [this.members, this, config]);
                };
                cube.on('membersLoaded', listener, this);
                return;
            }
            this.loading = true;

            if (this.hierarchy.dimension.name == "Measures")
            {
                console.log("Measures.listMembers is NYI");
                return;
            }

            // NOTE: getMembers.api would be nice
            var query = "SELECT\n" +
                    "[Measures].RowCount ON COLUMNS,\n" +
                    this.getUniqueName() + ".members ON ROWS\n" +
                    "FROM " + this.hierarchy.dimension.cube.getName();

            var queryConfig =
            {
                configId: config.configId || cube.configId,
                query: query,
                success: this._loadMembers,
                scope: this,
                originalConfig: config
            };
            _private.executeMdx(queryConfig);
        },
        _loadMembers : function(cellset, config)
        {
            var cube = this.hierarchy.dimension.cube;
            this.members = [];
            var positions = cellset.axes[1].positions;
            for (var p=0 ; p<positions.length ; p++)
            {
                var member = positions[p][0];
                var level = cube.getByUniqueName(member.level.uniqueName) || member.level;
                var mdxMember = new LABKEY.query.olap.metadata.Member(member, level);
                this.members.push(mdxMember);
            }
            if (config.originalConfig.success)
                config.originalConfig.success.apply(config.originalConfig.scope||window, [this.members, this, config.originalConfig]);
            cube.fireEvent('membersLoaded', this, this.members);
        }
    });

    Ext4.define('LABKEY.query.olap.metadata.Hierarchy', {
        extend: 'LABKEY.query.olap.metadata.MetadataElement',

        constructor : function(hier, parent)
        {
            Ext4.apply(this,hier);
            this.dimension = parent;
            this.levels = [];
            this.levelMap = {};
            for (var l=0 ; l<hier.levels.length ; l++)
            {
                var level = new LABKEY.query.olap.metadata.Level(hier.levels[l], l+1, this);
                this.levels.push(level);
                this.levelMap[level.name] = level;
            }
            this.dimension.cube.uniqueNameMap[this.uniqueName] = this;
            this.dimension.cube.hierarchyMap[this.name] = this;
        },
        getLevels : function()
        {
            return this.levels;
        },
        getLevel : function(lnum)
        {
            if (Ext4.isString(lnum))
            {
                if (!this.levelMap[lnum])
                    throw "Level not found: " + lnum;
                return this.levelMap[lnum];
            }
            else
            {
                if (!this.levels[lnum-1])
                    throw "Level not found: lnum=" + lnum;
                return this.levels[lnum-1];
            }
        },
        getDimension : function()
        {
            return this.dimension;
        },
        getParent : function()
        {
            return this.dimension;
        }
    });

    Ext4.define('LABKEY.query.olap.metadata.Dimension', {
        extend: 'LABKEY.query.olap.metadata.MetadataElement',

        constructor : function(dim, parent)
        {
            Ext4.apply(this,dim);
            this.cube = parent;
            this.hierarchies = [];
            for (var h=0 ; h<dim.hierarchies.length ; h++)
                this.hierarchies.push(new LABKEY.query.olap.metadata.Hierarchy(dim.hierarchies[h], this));
            this.cube.uniqueNameMap[this.uniqueName] = this;

            this.cube.dimensionMap[this.uniqueName] = this;
            this.cube.dimensionMap[this.name] = this;
            this.cube.dimensionMap[this.name.toLowerCase()] = this;
        },
        getHierarchies : function()
        {
            return this.hierarchies;
        },
        getCube : function()
        {
            return this.cube;
        },
        getParent : function()
        {
            return this.cube;
        }
    });

    Ext4.define('LABKEY.query.olap.metadata.Cube', {

        extend: 'LABKEY.query.olap.metadata.MetadataElement',

        mixins: {
            observable: 'Ext4.util.Observable'
        },

        /**
         * By default cubes will try to load as soon as they are constructed. This can be set to true
         * and then it is up to the creator to call load()
         */
        deferLoad: false,

        /**
         * By default cubes will not attempt to use perspectives nor will they expect that they are supplied
         * at runtime (via applyContext). If perspectives are enabled, then the cube creator is responsible
         * for initializing the perspectives available.
         */
        usePerspectives: false,

        /**
         * By default cubes will not attempt to cache member sets generated by 'named' filters. When this is enabled,
         * queryParticipantList() will be called each time a member set is updated so that name can be used against
         * query APIs (e.g. selectRows || MDX).
         */
        useServerMemberCache: false,

        constructor : function(config)
        {
            Ext4.apply(this, {
                _isReady: false,
                defaultContext: {},
                dimensions: [],
                dimensionMap: {},
                hierarchyMap: {}, // names may not be unique use getByUniqueName
                levelMap: {},      // names may not be unique use getByUniqueName
                uniqueNameMap: {}
            });

            this.mixins.observable.constructor.call(this, config);
            this.initConfig = config;  // this is handed to the user in onReady

            this.callParent([config]);

            this.addEvents('membersLoaded', 'onready');

            if (!this.deferLoad)
            {
                this.load();
            }
        },

        raiseError : function(msg, fatal) {
            console.error('OLAP:', msg);
            if (fatal !== false) {
                this._isReady = false;
            }
        },

        getName : function()
        {
            return this.name;
        },

        onDefinition : function(cubeDef, context, config)
        {
            this._def = cubeDef;
            this._context = context;

            this.mdx = new LABKEY.query.olap.MDX(this);

            this._isReady = true;

            for (var d=0; d < cubeDef.dimensions.length; d++)
            {
                var dimension = new LABKEY.query.olap.metadata.Dimension(cubeDef.dimensions[d], this);
                this.dimensions.push(dimension);
            }

            var defaults = context ? context.defaults : this.defaultContext.defaults;
            var values   = context ? context.values : this.defaultContext.values;

            if (Ext4.isDefined(defaults) && Ext4.isDefined(values)) {
                this.mdx = LABKEY.query.olap.AppContext.applyContext(this.mdx, defaults, values);

                if (!this.mdx) {
                    this.raiseError('Failed to apply application context.');
                }
                else if (this.usePerspectives === true && !Ext4.isObject(this.mdx.perspectives)) {
                    this.raiseError('Failed to provide \'perspectives\' configurations. Provide a definition of \'perspectives\' or disable \'usePerspectives\'.');
                }
            }
            else if (this.usePerspectives === true) {
                this.raiseError('Failed to provide \'persepectives\'. Provide a \'defaultContext\' config to set the \'perspectives\' appropriately.');
            }

            if (this._isReady === true) {
                this.fireEvent('onready', this.getMDX());
            }
        },

        load : function()
        {
            if (!this._def)
            {
                var defConfig =
                {
                    name: this.name,
                    configId: this.configId,
                    containerPath: this.containerPath,
                    schemaName: this.schemaName,
                    contextName: this.contextName,
                    memberExclusionFields: this.memberExclusionFields,
                    success: this.onDefinition,
                    scope: this
                };

                _private.getCubeDefinition(defConfig);
            }
            else
            {
                this.onDefinition(this._def, this._context);
            }
        },

        getDimensions : function()
        {
            return this.dimensions;
        },

        getDimension : function(uniqueNameOrName)
        {
            return this.dimensionMap[uniqueNameOrName];
        },

        /**
         * Not available until after the store is loaded.
         */
        getMDX : function() {
            return this.mdx;
        },

        getByUniqueName : function(name)
        {
            return this.uniqueNameMap[name];
        },

        onReady : function(callback, scope) {
            if (this._isReady === true)  {
                callback.call(scope, this.getMDX());
            }
            else {
                this.on('onready', function(){ callback.call(scope, this.getMDX()); }, this, {single: true});
            }
        }
    });

    LABKEY.query.olap.CubeManager = new function()
    {
        var _cubes = {};

        var getCube = function(config)
        {
            if (!config)
            {
                console.error('OLAP: A cube configuration must be supplied to LABKEY.query.olap.Cube.getCube()');
            }

            var c = Ext4.apply({ scope: this, defaultCube: {} }, config);
            c.name = c.name || c.defaultCube.name;
            c.schemaName = c.schemaName || c.defaultCube.schemaName;
            c.configId = c.configId || c.defaultCube.configId;
            c.memberExclusionFields = c.memberExclusionFields || c.defaultCube.memberExclusionFields;

            if (!c.name || !c.configId || !c.schemaName)
            {
                console.error('OLAP: A \'name\', \'configId\', and \'schemaName\' must be supplied to LABKEY.query.olap.Cube.getCube() configuration');
            }

            if (!_cubes[c.name])
            {
                _cubes[c.name] = Ext4.create('LABKEY.query.olap.metadata.Cube', c);
            }
            return _cubes[c.name];
        };

        return {
            getCube: getCube,
            executeOlapQuery: _private.executeMdx
        };
    };


    /**
     * this is not like Olap4j, it is a stateful mdx query helper
     *
     */
    Ext4.define('LABKEY.query.olap.MDX', {
        _cube : null,
        _filter : null,
        _serverSets: {},

        constructor: function(cube)
        {
            this._cube = cube;
            this._filter = {};
        },

        getCube : function()
        {
            return this._cube;
        },

        getDimensions : function()
        {
            return this._cube.getDimensions();
        },

        getPerspectiveObj: function(name)
        {
            if (!Ext4.isObject(this.perspectives))
                this._cube.raiseError('Failed to provide \'persepectives\'. Provide an \'applyContext\' function to set the \'perspectives\' appropriately.');

            return this.perspectives[name];
        },

        getDefaultPerspective : function()
        {
            return this.defaultPerspective;
        },

        getDimension : function(name)
        {
            return this._cube.getDimension(name);
        },

        getHierarchy : function(uniqueName) {
            var level = this._cube.getByUniqueName(uniqueName), hierarchy;

            if (level)
            {
                if (level.hierarchy) {
                    // we found a level, so pass back the hierarchy
                    hierarchy = level.hierarchy;
                }
                else if (level.dimension) {
                    // we found the hierarchy by uniqueName
                    hierarchy = level;
                }
                else if (level.hierarchies) {
                    // if we found a dimension by the unique name, see if it has an hierarchies of the same name
                    for (var i = 0; i < level.hierarchies.length; i++) {
                        if (uniqueName == level.hierarchies[i].uniqueName) {
                            hierarchy = level.hierarchies[i];
                            break;
                        }
                    }
                }
            }

            return hierarchy;
        },

        getLevel : function(uniqueName) {
            var level = this._cube.getByUniqueName(uniqueName);

            if (level && level.dimension) {
                level = undefined;
            }

            return level;
        },

        getMember : function(uniqueName) {
            var member = this._cube.getByUniqueName(uniqueName);

            if (member && (member.dimension || member.hierarchy)) {
                member = undefined;
            }

            return member;
        },

        clearNamedFilter : function(name, callback, scope)
        {
            if (this._filter[name]) {
                delete this._filter[name];
            }

            if (Ext4.isFunction(callback)) {
                callback.call(scope || this);
            }
        },

        setNamedFilter : function(name, filter)
        {
            this._filter[name] = filter;
        },

        getNamedFilter : function(name)
        {
            return this._filter[name];
        },

        serverSaveNamedSet : function(name, members, callback, scope) {
            Ext4.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', 'saveNamedSet'),
                method: 'POST',
                jsonData: {
                    setName: name,
                    setList: members
                },
                success: function(response) {
//                console.log('saved "' + name + '" in server cache. Contains', members.length, 'members.');
                    this._serverSets[name] = members;
                    if (Ext4.isFunction(callback)) {
                        callback.call(scope || this, name);
                    }
                },
                failure: function() {
                    alert('ERROR: serverSaveNamedSet.');
                },
                scope: this
            });
        },

        allowMemberCaching : function() {
            return this._cube.useServerMemberCache === true;
        },

        serverDeleteNamedSet : function(name, callback, scope) {
            Ext4.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', 'deleteNamedSet'),
                method: 'POST',
                jsonData: {
                    setName: name
                },
                success: function() {
//                console.log('deleted "' + name + '" in server cache.');
                    if (this._serverSets[name]) {
                        delete this._serverSets[name];
                    }
                    if (Ext4.isFunction(callback)) {
                        callback.call(scope || this);
                    }
                },
                failure: function() {
                    alert('ERROR: serverDeleteNamedSet.');
                },
                scope: this
            });
        },

        serverGetNamedSets : function() {
            return Ext4.clone(this._serverSets);
        },

        serverGetNamedSet : function(name) {
            return this._serverSets[name];
        },

        serverHasNamedSet : function(name) {
            return Ext4.isArray(this._serverSets[name]);
        },

        resetNamedFilters : function()
        {
            this._filter = {};
        },

        queryAsJson : function(config)
        {
            config.useJsonQuery = true;
            this.query(config);
        },

        queryAsCountDistinct : function(config)
        {
            config.useJsonQuery = false;
            this.query(config);
        },

        query : function(config)
        {
            var copy = Ext4.apply({}, config);

            // regular mdx filter
            copy.sliceFilter = copy.sliceFilter ? copy.sliceFilter.slice() : [];

            // for countDistinct api
            copy.whereFilter = copy.whereFilter ? copy.whereFilter.slice() : [];

            if (copy.filter && copy.countFilter) {
                console.error('OLAP: Both filter and countFilter are specified. These are the same, using countFilter is recommended.');
            }

            if (copy.filter) {
                copy.countFilter = copy.filter.slice();
            }
            else {
                copy.countFilter = copy.countFilter ? copy.countFilter.slice() : [];
            }

            var namedFilters = copy.useNamedFilters || [];
            for (var f=0; f < namedFilters.length; f++)
            {
                var filters = this.getNamedFilter(namedFilters[f]);

                if (!filters)
                    continue;

                if (!Ext4.isArray(filters))
                    filters = [filters];

                // process count vs where filters
                var counts = [], wheres = [], ft;
                for (var d=0; d < filters.length; d++) {
                    if (filters[d]) {
                        ft = filters[d].filterType;
                        if (ft === 'WHERE') {
                            wheres.push(filters[d]);
                        }
                        else { // 'COUNT' by default
                            counts.push(filters[d]);
                        }
                    }
                }

                counts = this._wrapFilterPerspectives(counts, copy);

                copy.countFilter = copy.countFilter.concat(counts);
                copy.whereFilter = copy.whereFilter.concat(wheres);
            }
            copy.sql = config.sql;
            return this._executeQuery(copy);
        },

        /**
         * @paran {[Object]} configs
         * @param {function([query.SelectRowsResults], configs} success
         * @param {function() failure
         */
        queryMultiple : function(configs, success, failure, scope)
        {
            var outstandingQueries = configs.length;
            var results = new Array(configs.length);
            var failed = false;
            var checkDone = function()
            {
                if (outstandingQueries > 0)
                    return;
                if (failed)
                    failure.call(scope);
                else
                    success.call(scope, results, configs);
            };
            var innerSuccess = function(qr, mdx, config)
            {
                if (Ext4.isFunction(config.originalConfig.success))
                    config.originalConfig.success.call(config.originalConfig,scope||window, qr, config.originalConfig);
                results[config.queryIndex] = qr;
                outstandingQueries--;
                checkDone();
            };
            var innerFailure = function(a,b,c)
            {
                console.log("NYI: finish failure handling");
                if (Ext4.isFunction(config.originalConfig.failure))
                    config.originalConfig.failure.apply(config.originalConfig.scope||window, arguments.concat([config.originalConfig]));
                failed = true;
                outstandingQueries--;
                checkDone();
            };
            for (var c=0 ; c<configs.length ; c++)
            {
                var config = Ext4.apply({},configs[c]);
                config.originalConfig = configs[c];
                config.queryIndex = c;
                config.success = innerSuccess;
                config.failure = innerFailure;
                this.query(config);
            }
        },

        /**
         * @deprecated This is hard-coded wrapper to look at specific subject/patient/participant levels. Just do it yourself.
         */
        queryParticipantList : function(config)
        {
            if (config.onCols || config.onColumns)
                throw "bad config";
            var c = Ext4.apply({},config);
            if (!c.onRows)
            {
                var map = this._cube.uniqueNameMap;
                var level = map["[Subject].[Subject]"] || map["[Patient].[Patient]"] ||  map["[Participant].[Participant]"];
                if (level)
                {
                    c.onRows = {level:level.uniqueName, members:"members"};
                }
            }
            this.query(c);
        },

        hasFilter : function (filterName) {
            return !Ext4.isEmpty(this.getNamedFilter(filterName));
        },

        /**
         * @private
         */
        _executeQuery : function(config)
        {
            var queryConfig =
            {
                configId : config.configId || this._cube.configId,
                schemaName : config.schemaName || this._cube.schemaName,
                cubeName : config.cubeName || this._cube.name,
                container: config.container,
                containerPath : config.containerPath,
                query :
                {
                    // TODO: Move this declaration to a place where it can be more appropriately documented. Its own object?

                    /**
                     * A set of members to select on the rows of the returned cellset
                     * (required)
                     */
                    onRows: config.onRows,

                    /**
                     * A set of members to select on the columns of the returned cellset
                     * (optional)
                     */
                    onColumns: config.onColumns || config.onCols,


                    /**
                     * Regular mdx slice filter (not used by count distinct api)
                     */
                    sliceFilter: config.sliceFilter,


                    /**
                     * Name of the level that contains the members we are counting in the query result
                     * (required)
                     */
                    countDistinctLevel: config.countDistinctLevel,

                    /**
                     * The filter is used to specify a subset of members in the countDistinctLevel to be counted in the query result
                     * (optional)
                     */
                    countFilter: config.countFilter,

                    /**
                     * Name of the level that relates the onRows, onColumns, and whereFilter results (e.g. ParticipantVisit).
                     * If not specified, this is the same as the countDistinctLevel
                     * (optional)
                     */
                    joinLevel: config.joinLevel,

                    /**
                     * This filter is used on the data processed by the query. The result will be a set of members of the
                     * joinLevel. If joinLevel == countDistinctLevel then this will be functionally equivalent to the countFilter.
                     * e.g. ParticipantVisit
                     * (optional)
                     */
                    whereFilter: config.whereFilter,

                    /**
                     * A boolean flag for whether results containing rows with a count of 0 will be a part of the response. Defaults to true.
                     * (optional)
                     */
                    showEmpty: config.showEmpty === true,

                    /**
                     * A boolean flag for whether returned counts should include the [#null] member. Defaults to true for backward compatibility.
                     * (optional)
                     */
                    includeNullMemberInCount: !(config.includeNullMemberInCount === false)
                },
                log : config.log,
                originalConfig : config,
                scope : this,
                success : function(cellset,queryConfig)
                {
                    var config = queryConfig.originalConfig;
                    if (Ext4.isFunction(config.success))
                        config.success.apply(config.scope||window, [cellset, this, config]);
                }
            };
            _private.executeJson(queryConfig);
        },

        /**
         * @private
         * When using perspectives, each filter is wrapped with the associated perspective query. A default perspective
         * can be provided by the cube configuration and will be used if a filter does not specify its perspective.
         */
        _wrapFilterPerspectives : function(filters, copy)
        {
            if (this._cube.usePerspectives === true && !Ext4.isEmpty(filters))
            {
                var _default = this._cube.mdx.defaultPerspective;
                if (!copy.perspective)
                {
                    console.warn('Query generated without providing perspective. Using default perspective: \'' + _default + '\'');
                    copy.perspective = _default;
                }
                var perspectiveFilters = [];

                Ext4.each(filters, function(filter)
                {
                    if (!filter.perspective)
                    {
                        console.warn('Filter generated/saved without providing perspective. Using default perspective: \'' + _default + '\'');
                        filter.perspective = _default;
                    }

                    if (filter.perspective === copy.perspective)
                    {
                        perspectiveFilters.push(filter);
                    }
                    else
                    {
                        // generate a wrapped filter
                        perspectiveFilters.push({
                            operator: 'INTERSECT',
                            arguments: [{
                                level: this._cube.mdx.perspectives[copy.perspective].level,
                                membersQuery: filter
                            }]
                        });
                    }
                }, this);

                filters = perspectiveFilters;
            }

            return filters;
        }
    });

    Ext4.define('LABKEY.query.olap.AppContext', {
        singleton: true,

        applyContext : function(mdx, defaults, values) {

            var _context = Ext4.clone(values);

            this.setDefaults(defaults);
            this.applyPerspectives(mdx, _context);
            this.applyDimensions(mdx, _context);
            this.clearDefaults();

            return mdx;
        },

        applyPerspectives : function(mdx, context) {
            if (Ext4.isObject(context.perspectives)) {
                var p = context.perspectives, _defaultP;
                Ext4.iterate(p, function(name, properties) {
                    if (properties._default === true) {
                        _defaultP = name;
                    }
                });
                mdx.perspectives = p;
                mdx.defaultPerspective = _defaultP;
            }
        },

        applyDimensions : function(mdx, context) {

            var dims = mdx.getDimensions();
            var dimDefaults = this.getDimensionDefaults();

            if (Ext4.isObject(dimDefaults) && Ext4.isArray(dims)) {
                var c_dims = context.dimensions, c_idx = -1, config, dd;
                var keysStr = this.getKeyString(dimDefaults);

                for (var d=0; d < dims.length; d++) {

                    //
                    // map the dimension defaults
                    //
                    dd = this.processDefaults(dims[d], dimDefaults, undefined);

                    //
                    // lookup if there is a context definition
                    //
                    c_idx = -1;
                    if (Ext4.isArray(c_dims)) {
                        for (var c = 0; c < c_dims.length; c++) {
                            if (c_dims[c].uniqueName === dims[d].uniqueName) {
                                c_idx = c;
                                break;
                            }
                        }
                    }

                    config = Ext4.applyIf((c_idx >= 0 ? c_dims[c_idx] : {}), dd);
                    Ext4.copyTo(dims[d], config, keysStr, true);

                    //
                    // Apply this dimensions context hierarchies
                    //
                    this.applyHierarchies(mdx, dims[d], config);
                }
            }
        },

        applyHierarchies : function(mdx, dim, context) {

            var hierarchies = dim.hierarchies;
            var hierDefaults = this.getHierarchyDefaults();

            if (Ext4.isObject(hierDefaults) && Ext4.isArray(hierarchies))
            {
                var c_hierarchies = context.hierarchies, c_idx = -1, config, hh;
                var keysStr = this.getKeyString(hierDefaults);

                for (var h=0; h < hierarchies.length; h++) {

                    //
                    // map the hierarchy defaults
                    //
                    hh = this.processDefaults(hierarchies[h], hierDefaults, dim);

                    //
                    // lookup if there is a context definition
                    //
                    c_idx = -1;
                    if (Ext4.isArray(c_hierarchies)) {
                        for (var c=0; c < c_hierarchies.length; c++) {
                            if (c_hierarchies[c].uniqueName === hierarchies[h].uniqueName) {
                                c_idx = c;
                                break;
                            }
                        }
                    }

                    config = Ext4.applyIf((c_idx >= 0 ? c_hierarchies[c_idx] : {}), hh);
                    Ext4.copyTo(hierarchies[h], config, keysStr, true);

                    //
                    // Apply this hierarchy's context levels
                    //
                    this.applyLevels(mdx, hierarchies[h], config);
                }
            }
        },

        applyLevels : function(mdx, hierarchy, context) {

            var levels = hierarchy.levels;
            var levelDefaults = this.getLevelDefaults();

            if (Ext4.isObject(levelDefaults) && Ext4.isArray(levels))
            {
                var c_levels = context.levels, c_idx = -1, config, ll;
                var keysStr = this.getKeyString(levelDefaults);

                for (var l=0; l < levels.length; l++) {

                    //
                    // map the level defaults
                    //
                    ll = this.processDefaults(levels[l], levelDefaults, hierarchy);

                    //
                    // lookup if there is a context definition
                    //
                    c_idx = -1;
                    if (Ext4.isArray(c_levels)) {
                        for (var c=0; c < c_levels.length; c++) {
                            if (c_levels[c].uniqueName === levels[l].uniqueName) {
                                c_idx = c;
                                break;
                            }
                        }
                    }

                    config = Ext4.applyIf((c_idx >= 0 ? c_levels[c_idx] : {}), ll);
                    Ext4.copyTo(levels[l], config, keysStr, true);
                }
            }
        },

        processDefaults: function(object, defaults, parent) {
            var dd = Ext4.clone(defaults), k;

            Ext4.iterate(defaults, function(key, value) {
                if (Ext4.isString(value))
                {
                    if (value.indexOf('parent::') === 0)
                    {
                        if (Ext4.isObject(parent))
                        {
                            k = value.replace('parent::', '');
                            dd[key] = parent[k];
                        }
                        else if (LABKEY.devMode)
                        {
                            console.warn("Invalid 'parent::' configuration for:", key);
                        }
                    }
                    else if (value.indexOf('prop::') === 0)
                    {
                        k = value.replace('prop::', '');
                        dd[key] = object[k];
                    }
                    else if (value.indexOf('path::') === 0)
                    {
                        var path = value.replace('path::', '').split("|"), vv = undefined;
                        if (path.length == 2)
                        {
                            if (Ext4.isArray(object.hierarchies))
                            {
                                var h = object.hierarchies[path[0]];
                                if (h && Ext4.isArray(h.levels) && h.levels.length > parseInt(path[1]))
                                {
                                    var lvl = h.levels[path[1]];
                                    if (Ext4.isObject(lvl) && Ext4.isString(lvl.uniqueName)) {
                                        vv = lvl.uniqueName;
                                    }
                                }
                            }
                        }

                        dd[key] = vv;
                    }
                    else if (value.indexOf('label::') === 0 && Ext4.isString(object.name))
                    {
                        dd[key] = this.parseLabel(object);
                    }
                }
            }, this);

            return dd;
        },

        parseLabel : function(hierarchy) {
            var label = hierarchy.name.split('.');
            return label[label.length-1];
        },

        getKeyString : function(map) {
            var str = '', sep = '';
            Ext4.iterate(map, function(k) { str += sep + k; sep = ','});
            return str;
        },

        setDefaults : function(defaults) {
            this._defaults = Ext4.clone(defaults);
        },

        clearDefaults : function() {
            if (this._defaults) {
                delete this._defaults;
            }
        },

        /**
         * Requires that this.defaults be set when called
         */
        getDimensionDefaults : function() {
            return this._defaults.dimension;
        },

        /**
         * Requires that this.defaults be set when called
         */
        getHierarchyDefaults : function() {
            return this._defaults.hierarchy;
        },

        getLevelDefaults : function() {
            return this._defaults.level;
        }
    });


})();