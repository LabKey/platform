/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */


/*
 * These apis are fairly closely modeled on olap4j (except for being async, of course).
 */

Ext4.ns("LABKEY.query.olap");

// TODO: Actually make this API private
LABKEY.query.olap._private = new function() {

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

        return Ext4.Ajax.request(
        {
//            url : LABKEY.ActionURL.buildURL("olap", "jsonQuery.api", config.containerPath),
            url : LABKEY.ActionURL.buildURL("olap", "countDistinctQuery.api", config.containerPath),
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
        if (!('includeMembers' in config))
            config.includeMembers = true;
        Ext4.Ajax.request({
            url : LABKEY.ActionURL.buildURL("olap", "getCubeDefinition", config.containerPath),
            method : 'POST',
            jsonData: { cubeName:config.name, configId:config.configId, schemaName:config.schemaName, includeMembers:config.includeMembers },
            success: function(r){postProcessGetCubeDefinition(r,config);},
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
        });
    };

    var postProcessCellSet = function(cs)
    {
        var idMap = postProcessCube(cs.metadata.cube);

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
        var cube = Ext4.decode(response.responseText);
        postProcessCube(cube);
        if (Ext4.isFunction(config.success))
            config.success.apply(config.scope||window, [cube, config]);
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
            this.uniqueName = LABKEY.query.olap._private.createUniqueName(this.uname);
        return this.uniqueName;
    },

    getUniqueNameArray : function()
    {
        if (!this.uname && this.uniqueName)
            this.uname = LABKEY.query.olap._private.parseUniqueName(this.uniqueName);
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
        LABKEY.query.olap._private.executeMdx(queryConfig);
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
     * query API's (e.g. selectRows || MDX).
     */
    useServerMemberCache: false,

    applyContext: undefined,

    dimensions: [],

    dimensionMap: {},

    hierarchyMap: {}, // names may not be unique use getByUniqueName

    levelMap: {}, // names may not be unique use getByUniqueName

    uniqueNameMap: {},

    _isReady: false,

    constructor : function(config)
    {
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

    onDefinition : function(cubeDef)
    {
        this._def = cubeDef;
        this.mdx = new LABKEY.query.olap.MDX(this);

        this._isReady = true;

        for (var d=0; d < cubeDef.dimensions.length; d++)
        {
            var dimension = new LABKEY.query.olap.metadata.Dimension(cubeDef.dimensions[d], this);
            this.dimensions.push(dimension);
        }

        if (Ext4.isFunction(this.applyContext)) {
            this.mdx = this.applyContext.call(this, this.mdx);
            if (!this.mdx) {
                this.raiseError('Failed to apply application context.');
            }
            else if (this.usePerspectives === true && !Ext4.isObject(this.mdx.perspectives)) {
                this.raiseError('Failed to provide \'perspectives\' configurations. Provide a definition of \'perspectives\' or disable \'usePerspectives\'.');
            }
        }
        else if (this.usePerspectives === true) {
            this.raiseError('Failed to provide \'persepectives\'. Provide an \'applyContext\' function to set the \'perspectives\' appropriately.');
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
                schemaName: this.schemaName,
                success: this.onDefinition,
                scope: this
            };

            LABKEY.query.olap._private.getCubeDefinition(defConfig);
        }
        else
        {
            this.onDefinition(this._def);
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
        var c = Ext4.apply({ scope: this }, config);

        if (!c)
        {
            console.error('OLAP: A cube configuration must be supplied to LABKEY.query.olap.Cube.getCube()');
        }
        else if (!c.name)
        {
            console.error('OLAP: A cube \'name\' must be supplied to LABKEY.query.olap.Cube.getCube() configuration');
        }

        var cube;

        if (_cubes[c.name])
        {
            cube = _cubes[c.name];
        }
        else
        {
            cube = Ext4.create('LABKEY.query.olap.metadata.Cube', config);
            _cubes[c.name] = cube;
        }

        return cube;
    };

    return {
        getCube: getCube,
        executeOlapQuery: LABKEY.query.olap._private.executeMdx
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

    getDimension : function(name)
    {
        return this._cube.getDimension(name);
    },

    getHierarchy : function(uniqueName) {
        var level = this._cube.getByUniqueName(uniqueName), hierarchy;

        if (level && level.hierarchy) {
            hierarchy = level.hierarchy;
        }
        else if (level && level.dimension) {
            hierarchy = level;
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
        var found = false;
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

    // this is for testing
    translateQuery : function(config)
    {
        var c = Ext4.apply({}, config, {filter:[], useNamedFilters:[]});
        for (var f=0 ; f<c.useNamedFilters.length ; f++)
        {
            var filter = this._filter[c.useNamedFilters[f]];
            if (!filter)
                continue;
            if (!Ext4.isArray(filter))
                filter = [filter];
            c.filter = c.filter.concat(filter);
        }

        return this._generateMdx(c);
    },


    _queryJS : function(config)
    {
        var mdx = this;
        var query = this._generateMdx(config);

        var queryConfig =
        {
            configId : config.configId || this._cube.configId,
            query : query,
            log : config.log,
            originalConfig : config,
            scope : this,
            success : function(cellset,queryConfig)
            {
                var config = queryConfig.originalConfig;
                if (Ext4.isFunction(config.success))
                    config.success.apply(config.scope||window, [cellset, mdx, config]);
            }
        };
        LABKEY.query.olap._private.executeMdx(queryConfig);
    },


    _queryJava : function(config)
    {
        var queryConfig =
        {
            configId : config.configId || this._cube.configId,
            schemaName : config.schemaName || this._cube.schemaName,
            cubeName : config.cubeName || this._cube.name,
            query :
                {
                    showEmpty:config.showEmpty,
                    onRows:config.onRows,
                    onColumns:(config.onColumns||config.onCols),
                    filter:config.filter,
                    countDistinctLevel:config.countDistinctLevel
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
        LABKEY.query.olap._private.executeJson(queryConfig);
    },


    query : function(config)
    {
        var copy = Ext4.apply({},config,{filter:[], useNamedFilters:[]});
        copy.filter = copy.filter ? copy.filter.slice() : [];
        var namedFilters = copy.useNamedFilters || [];
        for (var f=0; f < namedFilters.length; f++)
        {
            var filters = this._filter[namedFilters[f]];

            if (!filters)
                continue;

            if (!Ext4.isArray(filters))
                filters = [filters];

            if (this._cube.usePerspectives === true && filters.length > 0) {
                var _default = this._cube.mdx.defaultPerspective;
                if (!copy.perspective) {
                    console.warn('Query generated without providing perspective. Using default perspective: \'' + _default + '\'');
                    copy.perspective = _default;
                }
                var perspectiveFilters = [];

                Ext4.each(filters, function(filter) {

                    if (!filter.perspective) {
                        console.warn('Filter generated/saved without providing perspective. Using default perspective: \'' + _default + '\'');
                        filter.perspective = _default;
                    }

                    if (filter.perspective !== copy.perspective) {

                        // generate a wrapped filter
                        var wrapped = {
                            operator: 'INTERSECT',
                            arguments: [{
                                level: this._cube.mdx.perspectives[copy.perspective].level,
                                membersQuery: filter
                            }]
                        };
                        perspectiveFilters.push(wrapped);
                    }
                    else {
                        perspectiveFilters.push(filter);
                    }
                }, this);

                filters = perspectiveFilters;
            }

            copy.filter = copy.filter.concat(filters);
        }
//        console.debug(JSON.stringify({showEmpty:copy.showEmpty, onRows:copy.onRows, onCols:copy.onCols, filter:copy.filter}));
        return this._queryJava(copy);
    },


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
        return this._filter[filterName] && this._filter[filterName].length > 0;
    },


    _callOperator : function(op, expr)
    {
    },


    _processExpr : function(expr, defaultOperator, defaultArrayOperator)
    {
        if (Ext4.isArray(expr))
            expr = {operator:(defaultArrayOperator || "UNION"), arguments:expr};
        var op;
        if (expr.operator)
            op = expr.operator;
        else if (expr.membersQuery || expr.members)
            op = "MEMBERS";
        else
            op = defaultOperator || "MEMBERS";

        if (op == "UNION")
            return this._toUnionExpr(expr);
        else if (op == "MEMBERS")
            return this._toMembersExpr(expr);
        else if (op == "INTERSECT")
            return this._toIntersectExpr(expr);
        else if (op == "CROSSJOIN")
            return this._toCrossJoinExpr(expr);
        else if (op == "XINTERSECT")
            return this._toSmartCrossJoinExpr(expr);
        throw "unexpected operator: " + op;
    },


    // smart cross-join: intersect within level, crossjoin across levels
    _toSmartCrossJoinExpr : function(expr)
    {
        var arguments = Ext4.isArray(expr) ? expr : expr.arguments;     // handle array for old olap_test.js tests
        if (arguments.length==0)
            return null;
        var setsByLevel = {};
        for (var e=0 ; e<arguments.length ; e++)
        {
            var set = this._processExpr(arguments[e]);
            var key = set.level ? set.level.uniqueName : '-';
            if (!setsByLevel[key]) setsByLevel[key] = [];
            setsByLevel[key].push(set);
        }
        var sets = [];
        for (var k in setsByLevel)
        {
            var arr = setsByLevel[k];
            if (arr.length == 1)
                sets.push(arr[0]);
            else
                sets.push({fn:'Intersect', type:'set', level:arr[0].level, arguments:arr});
        }
        if (sets.length == 1)
            return sets[0];
        else
            return {fn:'CrossJoin', type:"set", arguments:sets};
    },


    _toCrossJoinExpr : function(expr)
    {
        var arguments = expr.arguments;
        var sets = [];
        for (var e=0 ; e<arguments.length ; e++)
        {
            var set = this._processExpr(arguments[e]);
            sets.push(set);
        }
        if (sets.length == 1)
            return sets[0];
        else
            return {fn:'CrossJoin', type:"set", arguments:sets};
    },


    _toUnionExpr : function(expr)
    {
        var arguments = expr.arguments;
        var sets = [];
        for (var e=0 ; e<arguments.length ; e++)
        {
            var set = this._processExpr(arguments[e]);
            if (set.fn=='{')
            {   // flatten nested unions
                for (var i=0 ; i<set.arguments.length ; i++)
                    sets.push(set.arguments[i]);
            }
            else
            {
                sets.push(set);
            }
        }
        if (sets.length == 1)
            return sets[0];
        else
        {
            var level = sets[0].level;
            for (var s=0 ; s<sets.length ; s++)
                if (level != sets[s].level) level = null;
            return {fn:'Union', type:"set", level:level, arguments:sets};
        }
    },


    _toIntersectExpr : function(expr)
    {
        var arguments = expr.arguments;
        var sets = [];
        for (var e=0 ; e<arguments.length ; e++)
        {
            var set = this._processExpr(arguments[e]);
            sets.push(set);
        }
        if (sets.length == 1)
            return sets[0];
        else
        {
            var level = sets[0].level;
            for (var s=0 ; s<sets.length ; s++)
                if (level != sets[s].level) level = null;
            return {fn:'Intersect', type:"set", level:level, arguments:sets};
        }
    },

    _resolveUniqueName : function(md)
    {
        if (md.uniqueName)
            return md.uniqueName;
        if (md.uname)
            return LABKEY.query.olap._private.createUniqueName(md.uname);
        return null;
    },


    _resolveLevelOrHierarchy : function(membersDef)
    {
        var level;
        if (!membersDef.level && !membersDef.hierarchy)
            throw "filter should specify 'level' or 'hierarchy'";
        if (membersDef.level)
        {
            if (Ext4.isString(membersDef.level))
                level = this._cube.getByUniqueName(membersDef.level) || this._cube.levelMap[membersDef.level];
            else
                level = membersDef.level;
            if (!level)
                throw "level not found: " + membersDef.level;
            return level;
        }
        var hier;
        if (membersDef.hierarchy)
        {
            if (Ext4.isString(membersDef.hierarchy))
                hier = this._cube.getByUniqueName(membersDef.hierarchy) || this._cube.hierarchyMap[membersDef.hierarchy];
            else
                hier = membersDef.hierarchy;
            if (!hier)
                throw "hierarchy not found: " + membersDef.hierarchy;
            var depth = hier.levels.length-1;
            if (Ext4.isNumber(membersDef.depth))
                depth = membersDef.depth;
            else if (Ext4.isNumber(membersDef.lnum))
                depth = membersDef.lnum;
            else if (Ext4.isString(membersDef.members))
                return hier;
            if (depth >= hier.levels.length)
                throw "hierarchy only has " + hier.levels.length + " levels";
            return hier.levels[depth];
        }
        throw "level not found: " + (membersDef.level ||membersDef.hierarchy);
    },


    _toMembersExpr : function(membersDef)
    {
        var hierarchy, level;
        var set = this._resolveLevelOrHierarchy(membersDef);
        if ('levels' in set)
            hierarchy = set;
        else
            level = set;

        if (hierarchy)
        {
            if (membersDef.members && !(membersDef.members == 'members' || membersDef.members == 'children'))
                throw "only 'children' or 'members' function is supported for hierarchy";
            var fnName = membersDef.members || 'members';
            return {fn:"{", level:null, type:"set", arguments:[hierarchy.uniqueName + "." + fnName]};
        }
        else if (Ext4.isArray(membersDef.members))
        {
            var members = [];
            for (var m=0 ; m<membersDef.members.length ; m++)
                members.push(this._toMemberExpr(level, membersDef.members[m]));
            return {fn:"{", level:level, type:"set", arguments:members};
        }
        else
        {
            if (membersDef.members && membersDef.members != 'members')
                throw "only 'members' is supported for level";
            var levelExpr = {fn:"", type:"set", level:level, arguments:[level.uniqueName + ".members"]};
            if (!membersDef.membersQuery)
                return levelExpr;
            var membersExpr = this._processExpr(membersDef.membersQuery,"MEMBERS","XINTERSECT");
            return this._toFilterExistsExpr(levelExpr, membersExpr.arguments, "OR", "[Measures].[RowCount]");
        }
    },


    // there might be a way to write this filter without a measure, but this is my current attempt
    _toFilterExistsExpr : function(levelExpr, members, op, measure)
    {
        op = " " + op + " ";
        var opConnector = "";
        var filterExpr = "";
        for (var m=0 ; m<members.length ; m++)
        {
            var term = "NOT ISEMPTY(" + this._toSetString(members[m]) + ")";
//            var term = "((" + this._toSetString(measure) + "," + this._toSetString(members[m]) + ") > 0)";
            filterExpr += opConnector + term;
            opConnector = op;
        }
        return {fn:"Filter", type:"set", level:levelExpr.level, arguments:[levelExpr, filterExpr]};
    },


    _toMemberExpr : function(level, member)
    {
        var uniqueName = Ext4.isString(member) ? member :this._resolveUniqueName(member);
        return {fn:"", type:"member", level:level, arguments:[uniqueName]};
    },


    _toSetString : function (expr)
    {
        if (Ext4.isString(expr))
            return expr;

        var binarySetFn = true;
        var start = expr.fn + "(", end = ")";
        if (expr.fn == "(")
        {
            start = "(";
            binarySetFn = false;
        }
        else if (expr.fn=="")
        {
            start = end = "";
            binarySetFn = false;
        }
        else if (expr.fn == "{")
        {
            start = "{"; end = "}";
            binarySetFn = false;
        }
        else if (expr.fn == "Intersect")
            binarySetFn = true;
        else if (expr.fn == "Union")
            binarySetFn = true;
        else if (expr.fn == "CrossJoin")
            binarySetFn = true;

        if (binarySetFn)
        {
            var args = expr.arguments;
            while (args.length > 2)
            {
                args[0] = {fn:expr.fn, arguments:[args[0], args[1]]};
                args.splice(1,1);
            }
        }

        var s = start;
        var comma = "";
        for (var a=0 ; a<expr.arguments.length ; a++)
        {
            s += comma; comma=",";
            var arg = expr.arguments[a];
            if (Ext4.isString(arg))
                s += arg;
            else
                s += this._toSetString(arg);
        }
        s += end;
        return s;
    },


    // @private
    _generateMdx : function(config)
    {
        var rowset, columnset;
        if (config.onCols)
            columnset = this._toSetString(this._processExpr(config.onCols,"XINTERSECT","XINTERSECT"));
        if (config.onRows)
            rowset = this._toSetString(this._processExpr(config.onRows,"XINTERSECT","XINTERSECT"));
        var filterset = null;
        if (config.filter && config.filter.length > 0)
            filterset = this._toSetString(this._processExpr(config.filter,"XINTERSECT","XINTERSECT"));

        var countMeasure = "[Measures].DefaultMember";
        var withDefinition = "";
        if (filterset)
        {
            countMeasure = "[Measures].ParticipantCount";
            withDefinition = "WITH SET ptids AS " + filterset + "\n" +
                    "MEMBER " + countMeasure + " AS " + "COUNT(ptids,EXCLUDEEMPTY)\n";
        }
        if (!columnset)
            columnset = countMeasure;
        else
            columnset = "(" + columnset + " , " + countMeasure + ")";

        var query = withDefinition + "SELECT\n" + "  "  + columnset + " ON COLUMNS\n";
        if (rowset)
            query += ", " + (config.showEmpty ? "" : " NON EMPTY ") + rowset + " ON ROWS\n";
        query += "FROM [" + this._cube.getName() + "]\n";
        return query;
    },

    // @private
    _generateParticipantMdx : function(config)
    {
        var columnset = "[Measures].members",
                countMeasure = "ParticipantList",
                filterset,
                withDefinition = "";

        if (config.filter && config.filter.length > 0)
            filterset = this._toSetString(this._processExpr(config.filter,"XINTERSECT","XINTERSECT"));
        else
            filterset = "[Subject].[Subject].members";

        if (filterset)
            withDefinition = "WITH SET " + countMeasure + " AS " + filterset + "\n";

        var query = withDefinition + "SELECT\n" + "  "  + columnset + " ON COLUMNS\n";
        if (countMeasure)
            query += ", " + (config.showEmpty ? "" : " NON EMPTY ")  + countMeasure + " ON ROWS\n";
        query += "FROM [" + this._cube.getName() + "]\n";

        return query;
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
    }
});
