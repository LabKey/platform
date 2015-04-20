/*
 * Copyright (c) 2007-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// NOTE labkey.js should NOT depend on any external libraries like ExtJS

if (typeof LABKEY == "undefined")
{
    /**
     * @namespace Namespace used to encapsulate LabKey core API and utilities.
     */
    LABKEY = new function()
    {
        var configs = {
            contextPath: "",
            DataRegions: {},
            devMode: false,
            demoMode: false,
            dirty: false,
            isDocumentClosed: false,
            extJsRoot: "ext-3.4.1",
            extJsRoot_42: "ext-4.2.1",
            extThemeRoot: "labkey-ext-theme",
            fieldMarker: '@',
            hash: 0,
            imagePath: "",
            requestedCssFiles: {},
            requestedScriptFiles: [],
            submit: false,
            unloadMessage: "You will lose any changes made to this page.",
            verbose: false,
            widget: {}
        };

        // private variables not configurable
        var _requestedCssFiles = {};

        // prepare null console to avoid errors in old versions of IE
        (function(){
            var method;
            var noop = function () {};
            var methods = [
                'assert', 'clear', 'count', 'debug', 'dir', 'dirxml', 'error',
                'exception', 'group', 'groupCollapsed', 'groupEnd', 'info', 'log',
                'markTimeline', 'profile', 'profileEnd', 'table', 'time', 'timeEnd',
                'timeStamp', 'trace', 'warn'
            ];
            var length = methods.length;
            var console = (window.console = window.console || {});

            while (length--) {
                method = methods[length];

                // Only stub undefined methods.
                if (!console[method]) {
                    console[method] = noop;
                }
            }
        })();

        // private caching mechanism for script loading
        var ScriptCache = function()
        {
            var cache = {};

            var callbacksOnCache = function(key)
            {
                // console.log('calling --', key);
                var cbs = cache[key];

                // set the cache to hit
                cache[key] = true;

                // Tell mothership.js to hook event callbacks
                if (LABKEY.Mothership)
                {
                    if (key.indexOf(configs.extJsRoot + "/ext-all") == 0)
                        LABKEY.Mothership.hookExt3();

                    if (key.indexOf(configs.extJsRoot_42 + "/ext-all") == 0)
                        LABKEY.Mothership.hookExt4();
                }

                // call on the callbacks who have been waiting for this resource
                if (isArray(cbs))
                {
                    var cb;
                    for (var c=0; c < cbs.length; c++)
                    {
                        cb = cbs[c];
                        handle(cb.fn, cb.scope);
                    }
                }
            };

            var inCache = function(key)
            {
                // console.log('hit --', key);
                return cache[key] === true;
            };

            var inFlightCache = function(key)
            {
                return isArray(cache[key]);
            };

            var loadCache = function(key, cb, s)
            {
                // console.log('miss --', key);
                // The value as an array denotes the cache resource is in flight
                if (!cache[key])
                    cache[key] = [];

                if (isFunction(cb))
                    cache[key].push({fn: cb, scope: s});
            };

            return {
                callbacksOnCache: callbacksOnCache,
                inCache: inCache,
                inFlightCache: inFlightCache,
                loadCache: loadCache
            };
        };

        // instance of scripting cache used by public methods
        var scriptCache = new ScriptCache();

        // Public Method Definitions

        var addElemToHead = function(elemName, attributes)
        {
            var elem = document.createElement(elemName);
            for (var a in attributes) {
                if (attributes.hasOwnProperty(a)) {
                    elem[a] = attributes[a];
                }
            }
            return document.getElementsByTagName("head")[0].appendChild(elem);
        };

        var addMarkup = function(html)
        {
            if (configs.isDocumentClosed)
            {
                var elem = document.createElement("div");
                elem.innerHTML = html;
                document.body.appendChild(elem.firstChild);
            }
            else
                document.write(html);
        };

        //private. used to append additional module context objects for AJAXd views
        var applyModuleContext = function(ctx) {
            for (var mn in ctx) {
                if (ctx.hasOwnProperty(mn)) {
                    LABKEY.moduleContext[mn.toLowerCase()] = ctx[mn];
                }
            }
        };

        var beforeunload = function (dirtyCallback, scope, msg)
        {
            return function () {
                if (!getSubmit() && (isDirty() || (dirtyCallback && dirtyCallback.call(scope)))) {
                    return msg || configs.unloadMessage;
                }
            };
        };

        var createElement = function(tag, innerHTML, attributes)
        {
            var e = document.createElement(tag);
            if (innerHTML)
                e.innerHTML = innerHTML;
            if (attributes)
            {
                for (var att in attributes)
                {
                    if (attributes.hasOwnProperty(att))
                    {
                        try
                        {
                            e[att] = attributes[att];
                        }
                        catch (x)
                        {
                            console.log(x); // e['style'] is read-only in old firefox
                        }
                    }
                }
            }
            return e;
        };

        var getModuleContext = function(moduleName) {
            return LABKEY.moduleContext[moduleName.toLowerCase()];
        };

        var getModuleProperty = function(moduleName, property) {
            var ctx = getModuleContext(moduleName);
            if (!ctx) {
                return null;
            }
            return ctx[property];
        };

        var getSubmit = function()
        {
            return configs.submit;
        };

        // simple callback handler that will type check then call with scope
        var handle = function(callback, scope)
        {
            if (isFunction(callback))
            {
                callback.call(scope || this);
            }
        };

        // If we're in demo mode, replace each ID with an equal length string of "*".  This code should match DemoMode.id().
        var id = function(id)
        {
            if (configs.demoMode)
            {
                return new Array(id.length + 1 ).join("*");
            }
            else
            {
                return id;
            }
        };

        var init = function(config)
        {
            for (var p in config)
            {
                //TODO: we should be trying to seal some of these objects, or at least wrap them to make them harder to manipulate
                if (config.hasOwnProperty(p)) {
                    configs[p] = config[p];
                    LABKEY[p] = config[p];
                }
            }
            if ("Security" in LABKEY)
                LABKEY.Security.currentUser = LABKEY.user;
        };

        //private (Depricated)
        //Pull in the required JS files and invoke the callback when they're loaded
        var initializeExt3ViewDesigner = function(cb, scope)
        {
            var scripts = [
                'query/queryDesigner.js',
                'ComponentDataView.js',
                'Ext.ux.dd.GridDragDropRowOrder.js'
            ];

            requiresExt3ClientAPI(function() {
                requiresScript(scripts, function() {
                    requiresScript('designer/designer2.js', cb, scope)
                });
            });

            requiresCss("groupTabPanel/GroupTab.css");
            requiresCss("groupTabPanel/UngroupedTab.css");
        };

        var isArray = function(value)
        {
            return Object.prototype.toString.call(value) === "[object Array]";
        };

        var isBoolean = function(value)
        {
            return typeof value === "boolean";
        };

        var isDirty = function()
        {
            return configs.dirty;
        };

        var isFunction = function(value)
        {
            return typeof value === "function";
        };

        var loadScripts = function()
        {
            configs.isDocumentClosed = true;
        };

        var loadedScripts = function()
        {
            for (var i=0; i < arguments.length; i++)
            {
                if (isArray(arguments[i]))
                {
                    for (var j=0; j < arguments[i].length; j++)
                    {
                        scriptCache.callbacksOnCache(arguments[i][j]);
                    }
                }
                else
                {
                    scriptCache.callbacksOnCache(arguments[i]);
                }
            }
            return true;
        };

        var requiresCss = function(file)
        {
            if (isArray(file))
            {
                for (var i=0;i<file.length;i++)
                    requiresCss(file[i]);
                return;
            }

            if (file.indexOf('/') == 0)
                file = file.substring(1);

            // Support both LabKey and external CSS files
            var fullPath = file.substr(0, 4) != "http" ? configs.contextPath + "/" + file + '?' + configs.hash : file;

            if (_requestedCssFiles[fullPath])
                return;

            addElemToHead("link", {
                type: "text/css",
                rel: "stylesheet",
                href: fullPath
            });

            _requestedCssFiles[fullPath] = true;
        };

        var requestedCssFiles = function()
        {
            var ret = (arguments.length > 0 && _requestedCssFiles[arguments[0]]) ? true : false;
            for (var i=0; i < arguments.length ; i++)
                _requestedCssFiles[arguments[i]] = true;
            return ret;
        };

        var requiresClientAPI = function(callback, scope)
        {
            // backwards compat for 'immediate'
            if (arguments.length > 0 && isBoolean(arguments[0]))
            {
                callback = arguments[1];
                scope = arguments[2];
            }

            var coreDone = function() {
                requiresExt3ClientAPI(callback, scope);
            };

            if (configs.devMode)
            {
                var scripts = [
                    // clientapi_core.lib.xml
                    "clientapi/core/Utils.js",
                    "clientapi/core/ActionURL.js",
                    "clientapi/core/Ajax.js",
                    "clientapi/core/Assay.js",
                    "clientapi/core/Domain.js",
                    "clientapi/core/Experiment.js",
                    "clientapi/core/FieldKey.js",
                    "clientapi/core/Filter.js",
                    "clientapi/core/Message.js",
                    "clientapi/core/MultiRequest.js",
                    "clientapi/core/ParticipantGroup.js",
                    "clientapi/core/Pipeline.js",
                    "clientapi/core/Specimen.js",
                    "clientapi/core/Query.js",
                    "clientapi/core/GetData.js",
                    "clientapi/core/Report.js",
                    "clientapi/core/Security.js",
                    "clientapi/core/Visualization.js",

                    // clientapi.lib.xml
                    "clientapi/dom/Utils.js",
                    "clientapi/dom/Tour.js",
                    "clientapi/dom/Chart.js",
                    "clientapi/dom/Form.js",
                    "clientapi/dom/NavTrail.js",
                    "clientapi/dom/Portal.js",
                    "clientapi/dom/Query.js",
                    "clientapi/dom/Security.js",
                    "clientapi/dom/GetData.js",
                    "clientapi/dom/WebPart.js"
                ];
                requiresScript(scripts, coreDone);
            }
            else
            {
                requiresExt4Sandbox(function() {
                    requiresScript("clientapi.min.js", coreDone);
                });
            }
        };

        var requiresExt3 = function(callback, scope)
        {
            // Mimic the results handed down by Ext3.lib.xml

            // backwards compat for 'immediate'
            if (arguments.length > 0 && isBoolean(arguments[0]))
            {
                callback = arguments[1];
                scope = arguments[2];
            }

            if (window.Ext)
            {
                handle(callback, scope);
            }
            else
            {
                // Require that these CSS files be placed first in the <head> block so that they can be overridden by user customizations
                requiresCss(configs.extJsRoot + '/resources/css/ext-all.css');

                requiresScript([
                    configs.extJsRoot + "/adapter/ext/ext-base" + (configs.devMode ?  "-debug.js" : ".js"),
                    configs.extJsRoot + "/ext-all" + (configs.devMode ?  "-debug.js" : ".js"),
                    configs.extJsRoot + "/ext-patches.js"
                ], callback, scope, true);
            }
        };

        var requiresExt3ClientAPI = function(callback, scope)
        {
            // Mimic the results handed down by clientapi/ext3.lib.xml

            // backwards compat for 'immediate'
            if (arguments.length > 0 && isBoolean(arguments[0]))
            {
                callback = arguments[1];
                scope = arguments[2];
            }

            var scripts;

            if (configs.devMode)
            {
                scripts = [
                    // groupTabPanel/groupTab.lib.xml
                    "groupTabPanel/GroupTabPanel.js",
                    "groupTabPanel/GroupTab.js",

                    // GuidedTip
                    "GuidedTip.js",

                    // clientapi/ext3.lib.xml
                    "clientapi/ext3/DataRegion.js",
                    "clientapi/ext3/EditorGridPanel.js",
                    "clientapi/ext3/ExtendedJsonReader.js",
                    "clientapi/ext3/FieldKey.js",
                    "clientapi/ext3/FileSystem.js",
                    "clientapi/ext3/FilterDialog.js",
                    "clientapi/ext3/FormPanel.js",
                    "clientapi/ext3/GridView.js",
                    "clientapi/ext3/HoverPopup.js",
                    "clientapi/ext3/LongTextEditor.js",
                    "clientapi/ext3/PersistentToolTip.js",
                    "clientapi/ext3/QueryWebPart.js",
                    "clientapi/ext3/SecurityPolicy.js",
                    "clientapi/ext3/Store.js",
                    "clientapi/ext3/Utils.js"
                ];
            }
            else
            {
                scripts = [
                    "groupTabPanel/groupTab.min.js",
                    "GuidedTip.js",
                    "clientapi/ext3.min.js"
                ];
            }

            LABKEY.requiresCss('groupTabPanel/GroupTab.css');
            LABKEY.requiresCss('groupTabPanel/UngroupedTab.css');
            LABKEY.requiresCss('GuidedTip.css');

            requiresExt3(function()
            {
                //load individual scripts so that they get loaded from source tree
                requiresScript(scripts, callback, scope);
            });
        };

        var requiresExt4ClientAPI = function(callback, scope)
        {
            // backwards compat for 'immediate'
            if (arguments.length > 0 && isBoolean(arguments[0]))
            {
                callback = arguments[1];
                scope = arguments[2];
            }

            var scripts;
            if (configs.devMode)
            {
                scripts = [
                    // clientapi/ext4.lib.xml
                    "clientapi/ext4/Util.js",
                    "clientapi/ext4/data/Reader.js",
                    "clientapi/ext4/data/Proxy.js",
                    "clientapi/ext4/data/Store.js",

                    // Ext4ClientApi.lib.xml
                    "extWidgets/LabkeyCombo.js",
                    "extWidgets/ExtComponents.js",
                    "extWidgets/Ext4FormPanel.js",
                    "extWidgets/Ext4GridPanel.js",
                    "extWidgets/DetailsPanel.js"
                ];
            }
            else
            {
                scripts = [
                    "clientapi/ext4.min.js",
                    "Ext4ClientApi.min.js"
                ];
            }

            requiresExt4Sandbox(function()
            {
                requiresScript(scripts, callback, scope, true);
            });
        };

        var requiresExt4Sandbox = function(callback, scope)
        {
            // backwards compat for 'immediate'
            if (arguments.length > 0 && isBoolean(arguments[0]))
            {
                callback = arguments[1];
                scope = arguments[2];
            }

            if (window.Ext4)
            {
                handle(callback, scope);
            }
            else
            {
                var scripts = [
                    configs.extJsRoot_42 + "/ext-all-sandbox" + (configs.devMode ?  "-debug.js" : ".js"),
                    configs.extJsRoot_42 + "/ext-patches.js"
                ];

                requiresScript(scripts, callback, scope);
            }
        };

        var requiresScript = function(file, callback, scope, inOrder)
        {
            if (arguments.length === 0)
            {
                throw "LABKEY.requiresScript() requires the 'file' parameter.";
            }

            // backwards compat for 'immediate'
            if (arguments.length > 1 && isBoolean(arguments[1]))
            {
                callback = arguments[2];
                scope = arguments[3];
                inOrder = arguments[4];
            }

            if (isArray(file))
            {
                var requestedLength = file.length;
                var loaded = 0;

                if (inOrder)
                {
                    var chain = function()
                    {
                        loaded++;
                        if (loaded == requestedLength)
                        {
                            handle(callback, scope);
                        }
                        else if (loaded < requestedLength)
                            requiresScript(file[loaded], chain, undefined, true);
                    };

                    if (scriptCache.inCache(file[loaded]))
                    {
                        chain();
                    }
                    else
                        requiresScript(file[loaded], chain, undefined, true);
                }
                else
                {
                    // request all the scripts (order does not matter)
                    var allDone = function()
                    {
                        loaded++;
                        if (loaded == requestedLength)
                        {
                            handle(callback, scope);
                        }
                    };

                    for (var i = 0; i < file.length; i++)
                    {
                        if (scriptCache.inCache(file[i]))
                        {
                            allDone();
                        }
                        else
                            requiresScript(file[i], allDone);
                    }
                }
                return;
            }

            if (file.indexOf('/') == 0)
            {
                file = file.substring(1);
            }

            if (scriptCache.inCache(file))
            {
                // cache hit -- script is loaded and ready to go
                handle(callback, scope);
                return;
            }
            else if (scriptCache.inFlightCache(file))
            {
                // cache miss -- in flight
                scriptCache.loadCache(file, callback, scope);
                return;
            }
            else
            {
                // cache miss
                scriptCache.loadCache(file, callback, scope);
            }

            // although FireFox and Safari allow scripts to use the DOM
            // during parse time, IE does not. So if the document is
            // closed, use the DOM to create a script element and append it
            // to the head element. Otherwise (still parsing), use document.write()

            // Support both LabKey and external JavaScript files
            var src = file.substr(0, 4) != "http" ? configs.contextPath + "/" + file + '?' + configs.hash : file;

            var cacheLoader = function()
            {
                scriptCache.callbacksOnCache(file);
            };

            if (configs.isDocumentClosed || callback)
            {
                //create a new script element and append it to the head element
                var script = addElemToHead("script", {
                    src: src,
                    type: "text/javascript"
                });

                // IE has a different way of handling <script> loads
                if (script.readyState)
                {
                    script.onreadystatechange = function() {
                        if (script.readyState == "loaded" || script.readyState == "complete") {
                            script.onreadystatechange = null;
                            cacheLoader();
                        }
                    };
                }
                else
                {
                    script.onload = cacheLoader;
                }
            }
            else
            {
                document.write('\n<script type="text/javascript" src="' + src + '"></script>\n');
                cacheLoader();
            }
        };

        var requiresVisualization = function(callback, scope)
        {
            // namespace check
            if (!LABKEY.vis)
            {
                LABKEY.vis = {};
            }

            var scripts = [
                '/vis/lib/patches.js',
                '/vis/lib/d3-3.3.9.min.js',
                '/vis/lib/d3pie.min.js',
                '/vis/lib/hexbin.min.js',
                '/vis/lib/sqbin.min.js',
                '/vis/lib/raphael-min-2.1.0.js'
            ];

            if (configs.devMode)
            {
                scripts = scripts.concat([
                    '/vis/src/utils.js',
                    '/vis/src/geom.js',
                    '/vis/src/stat.js',
                    '/vis/src/scale.js',
                    '/vis/src/layer.js',
                    '/vis/src/internal/RaphaelRenderer.js',
                    '/vis/src/internal/D3Renderer.js',
                    '/vis/src/plot.js'
                ]);

                // NOTE: If adding a required file you must add to vis.lib.xml for proper packaging
            }
            else
            {
                scripts = scripts.concat([
                    '/vis/vis.min.js'
                ]);
            }

            requiresScript(scripts, callback, scope, true);
        };

        var setDirty = function (dirty)
        {
            configs.dirty = (dirty ? true : false); // only set to boolean
        };

        var setSubmit = function (submit)
        {
            configs.submit = (submit ? true : false); // only set to boolean
        };

        var showNavTrail = function()
        {
            var elem = document.getElementById("navTrailAncestors");
            if(elem)
                elem.style.visibility = "visible";
            elem = document.getElementById("labkey-nav-trail-current-page");
            if(elem)
                elem.style.visibility = "visible";
        };

        return {

            /**
             * This callback type is called 'requireCallback' and is displayed as a global symbol
             *
             * @callback requireCallback
             */

            /**
             * The DataRegion class allows you to interact with LabKey grids,
             * including querying and modifying selection state, filters, and more.
             * @field
             */
            DataRegions: configs.DataRegions,

            demoMode: configs.demoMode,
            devMode: configs.devMode,
            dirty: configs.dirty,
            extJsRoot: configs.extJsRoot,
            extJsRoot_42: configs.extJsRoot_42,
            extThemeRoot: configs.extThemeRoot,
            fieldMarker: configs.fieldMarker,
            hash: configs.hash,
            imagePath: configs.imagePath,
            submit: configs.submit,
            unloadMessage: configs.unloadMessage,
            verbose: configs.verbose,
            widget: configs.widget,

            /** @field */
            contextPath: configs.contextPath,

            /**
             * Appends an element to the head of the document
             * @private
             * @param {String} elemName First argument for docoument.createElement
             * @param {Object} [attributes]
             * @returns {*}
             */
            addElemToHead: addElemToHead,

            // TODO: Eligible for removal after util.js is migrated
            addMarkup: addMarkup,
            applyModuleContext: applyModuleContext,
            beforeunload: beforeunload,
            createElement: createElement,

            /**
             * @function
             * @param {String} moduleName The name of the module
             * @returns {Object} The context object for this module.  The current view must have specifically requested
             * the context for this module in its view XML
             */
            getModuleContext: getModuleContext,

            /**
             * @function
             * @param {String} moduleName The name of the module
             * @param {String} property The property name to return
             * @returns {String} The value of the module property.  Will return null if the property has not been set.
             */
            getModuleProperty: getModuleProperty,
            getSubmit: getSubmit,
            id: id,
            init: init,
            initializeExt3ViewDesigner: initializeExt3ViewDesigner,
            isDirty: isDirty,
            loadScripts: loadScripts,
            loadedScripts: loadedScripts,

            /**
             * Loads a CSS file from the server.
             * @function
             * @param {(string|string[])} file - The path of the CSS file to load
             * @example
             &lt;script type="text/javascript"&gt;
                LABKEY.requiresCss("myModule/myFile.css");
             &lt;/script&gt;
             */
            requiresCss: requiresCss,
            requestedCssFiles: requestedCssFiles,
            requiresClientAPI: requiresClientAPI,

            /**
             * This can be added to any LABKEY page in order to load ExtJS 3.  This is the preferred method to declare Ext3 usage
             * from wiki pages.  For HTML or JSP pages defined in a module, see our <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=ext4Development">documentation</a> on declaration of client dependencies.
             * @function
             * @param {boolean} [immediate=true] - True to load the script immediately; false will defer script loading until the page has been downloaded.
             * @param {requireCallback} [callback] - Callback for when all dependencies are loaded.
             * @param {Object} [scope] - Scope of callback.
             * @example
             &lt;script type="text/javascript"&gt;
                LABKEY.requiresExt3(true, function() {
                    Ext.onReady(function() {
                        // Ext 3 is loaded and ready
                    });
                });
             &lt;/script&gt;
             */
            requiresExt3: requiresExt3,

            /**
             * This can be added to any LABKEY page in order to load the LabKey ExtJS 3 Client API.
             * @function
             * @param {boolean} [immediate=true] - True to load the script immediately; false will defer script loading until the page has been downloaded.
             * @param {requireCallback} [callback] - Callback for when all dependencies are loaded.
             * @param {Object} [scope] - Scope of callback.
             * @example
             &lt;script type="text/javascript"&gt;
                 LABKEY.requiresExt3ClientAPI(true, function() {
                    // your code here
                 });
             &lt;/script&gt;
             */
            requiresExt3ClientAPI: requiresExt3ClientAPI,

            /**
             * This can be added to any LABKEY page in order to load the LabKey ExtJS 4 Client API. This primarily
             * consists of a set of utility methods {@link LABKEY.ext4.Util} and an extended Ext.data.Store {@link LABKEY.ext4.data.Store}.
             * It will load ExtJS 4 as a dependency.
             * @function
             * @param {boolean} [immediate=true] - True to load the script immediately; false will defer script loading until the page has been downloaded.
             * @param {requireCallback} [callback] - Callback for when all dependencies are loaded.
             * @param {Object} [scope] - Scope of callback.
             * @example
             &lt;script type="text/javascript"&gt;
                 LABKEY.requiresExt4ClientAPI(true, function() {
                    // your code here
                 });
             &lt;/script&gt;
             */
            requiresExt4ClientAPI: requiresExt4ClientAPI,

            /**
             * This can be added to any LABKEY page in order to load ExtJS 4.  This is the preferred method to declare Ext4 usage
             * from wiki pages.  For HTML or JSP pages defined in a module, see our <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=ext4Development">documentation</a> on declaration of client dependencies.
             * @function
             * @param {boolean} [immediate=true] - True to load the script immediately; false will defer script loading until the page has been downloaded.
             * @param {requireCallback} [callback] - Callback for when all dependencies are loaded.
             * @param {Object} [scope] - Scope of callback.
             * @example
             &lt;script type="text/javascript"&gt;
                 LABKEY.requiresExt4Sandbox(true, function() {
                    Ext4.onReady(function(){
                        // Ext4 is loaded and ready
                    });
                 });
             &lt;/script&gt;
             */
            requiresExt4Sandbox: requiresExt4Sandbox,

            /**
             * Deprecated.  Use LABKEY.requiresExt3 instead.
             * @function
             * @private
             */
            requiresExtJs: requiresExt3,

            /**
             * Loads JavaScript file(s) from the server.
             * @function
             * @param {(string|string[])} file - A file or Array of files to load.
             * @param {boolean} [immediate=true] - True to load the script immediately; false will defer script loading until the page has been downloaded.
             * @param {requireCallback} [callback] - Callback for when all dependencies are loaded.
             * @param {Object} [scope] - Scope of callback.
             * @param {boolean} [inOrder=false] - True to load the scripts in the order they are passed in. Default is false.
             * @example
             &lt;script type="text/javascript"&gt;
                LABKEY.requiresScript("myModule/myScript.js", true, function() {
                    // your script is loaded
                });
             &lt;/script&gt;
             */
            requiresScript: requiresScript,
            requiresVisualization: requiresVisualization,
            setDirty: setDirty,
            setSubmit: setSubmit,
            showNavTrail: showNavTrail
        }
    };

}
