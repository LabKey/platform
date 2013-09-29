/*
 * Copyright (c) 2007-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// NOTE labkey.js should NOT depend on any external libraries like ExtJS

if (typeof LABKEY == "undefined")
{
    /**
     * @class LABKEY
     * LabKey core API and utilities.
     * @singleton
     */
    var LABKEY = {};
    LABKEY.Internal = {};
    LABKEY.contextPath = (typeof __contextPath == "undefined") ? "UNDEFINED" : __contextPath;
    LABKEY.imagePath = (typeof __contextPath == "undefined") ? "UNDEFINED" : __contextPath + "/_images";
    LABKEY.devMode = false;
    LABKEY.demoMode = false;
    LABKEY.extJsRoot = "ext-3.4.1";
    LABKEY.extJsRoot_42 = "ext-4.2.1";
    LABKEY.extThemeRoot = "labkey-ext-theme";
    LABKEY.verbose = false;
    LABKEY.widget = {};
    LABKEY.hash = 0;
    LABKEY.dirty = false;
    LABKEY.submit = false;
    LABKEY.buttonBarMenu = false;
    LABKEY.fieldMarker = '@';

    /**
     * @namespace The DataRegion class allows you to interact with LabKey grids, including querying and modifying selection state, filters, and more.
     */
    LABKEY.DataRegions = {};

    LABKEY._requestedCssFiles = {};
    LABKEY._requestedScriptFiles = [];
    LABKEY._loadedScriptFiles = {};
    LABKEY._emptyFunction = function(){};

    var nullConsole =
    {
        assert : LABKEY._emptyFunction,
        count : LABKEY._emptyFunction,
        debug : LABKEY._emptyFunction,
        dir : LABKEY._emptyFunction,
        dirxml: LABKEY._emptyFunction,
        error : LABKEY._emptyFunction,
        info : LABKEY._emptyFunction,
        group : LABKEY._emptyFunction,
        groupEnd : LABKEY._emptyFunction,
        log : LABKEY._emptyFunction,
        profile : LABKEY._emptyFunction,
        profileEnd : LABKEY._emptyFunction,
        time : LABKEY._emptyFunction,
        timeEnd : LABKEY._emptyFunction,
        trace : LABKEY._emptyFunction,
        warn : LABKEY._emptyFunction
    };
    if (!("console" in window))
    {
        window.console = nullConsole;
    }
    else
    {
        for (var f in nullConsole)
            if (!(f in console))
                console[f] = nullConsole[f];
        if (console.debug == LABKEY._emptyFunction)
            console.debug = console.warn;
        if (console.dir == LABKEY._emptyFunction)
            console.dir = function (o) {for (var p in o) console.debug(p + ": " + o[o]);};
    }
}


LABKEY.init = function(config)
{
    for (var p in config)
    {
        //TODO: we should be trying to seal some of these objects, or at least wrap them to make them harder to manipulate
        if (config.hasOwnProperty(p)) {
            this[p] = config[p];
        }
    }
    if ("Security" in LABKEY)
        LABKEY.Security.currentUser = LABKEY.user;
};


/**
 * Loads a javascript file from the server.  See also LABKEY.onReady()
 * @param file A single file or an Array of files.
 * @param immediate True to load the script immediately; false will defer script loading until the page has been downloaded.
 * @param callback Called after the script files have been loaded.
 * @param scope Callback scope.
 * @param inOrder True to load the scripts in the order they are passed in. Default is false.
 * @example &lt;script type="text/javascript"&gt;
    LABKEY.requiresScript("myModule/myScript.js");
&lt;/script&gt;
 */
LABKEY.requiresScript = function(file, immediate, callback, scope, inOrder)
{
    if (arguments.length < 2)
        immediate = true;

    if (Object.prototype.toString.call(file) == "[object Array]")
    {
        var requestedLength = file.length;
        var loaded = 0;

        if (inOrder)
        {
            var chain = function()
            {
                loaded++;
                if (loaded == requestedLength && typeof callback == 'function')
                {
                    callback.call(scope);
                }
                else if (loaded < requestedLength)
                    LABKEY.requiresScript(file[loaded], immediate, chain, true);
            };
            LABKEY.requiresScript(file[loaded], immediate, chain, true);
        }
        else
        {
            var allDone = function()
            {
                loaded++;
                if (loaded == requestedLength && typeof callback == 'function')
                    callback.call(scope);
            };

            for (var i = 0; i < file.length; i++)
                LABKEY.requiresScript(file[i], immediate, allDone);
        }
        return;
    }

    if (file.indexOf('/') == 0)
    {
        file = file.substring(1);
    }

    if (this._loadedScriptFiles[file])
    {
        if (typeof callback == "function")
            callback.call(scope);
        return;
    }

    function onScriptLoad()
    {
        if (typeof callback == "function")
            callback.call(scope);
    }

    if (!immediate)
        this._requestedScriptFiles.push({file: file, callback: callback, scope: scope});
    else
    {
        this._loadedScriptFiles[file] = true;

        //although FireFox and Safari allow scripts to use the DOM
        //during parse time, IE does not. So if the document is
        //closed, use the DOM to create a script element and append it
        //to the head element. Otherwise (still parsing), use document.write()

        // Support both LabKey and external JavaScript files
        var src = file.substr(0, 4) != "http" ? LABKEY.contextPath + "/" + file + '?' + LABKEY.hash : file;

        if (LABKEY.isDocumentClosed || callback)
        {
            //create a new script element and append it to the head element
            var script = LABKEY.addElemToHead("script", {
                src: src,
                type: "text/javascript"
            });

            // IE has a different way of handling <script> loads
            if (script.readyState)
            {
                script.onreadystatechange = function () {
                    if (script.readyState == "loaded" || script.readyState == "complete") {
                        script.onreadystatechange = null;
                        onScriptLoad();
                    }
                }
            }
            else
            {
                script.onload = onScriptLoad;
            }
        }
        else
            document.write('\n<script type="text/javascript" src="' + src + '"></script>\n');
    }
};


LABKEY.loadedScripts = function()
{
    var ret = (arguments.length > 0 && this._loadedScriptFiles[arguments[0]]) ? true : false
    for (var i = 0 ; i < arguments.length ; i++)
        this._loadedScriptFiles[arguments[i]] = true;
    return ret;
};

LABKEY.requestedCssFiles = function()
{
    var ret = (arguments.length > 0 && this._requestedCssFiles[arguments[0]]) ? true : false
    for (var i = 0 ; i < arguments.length ; i++)
        this._requestedCssFiles[arguments[i]] = true;
    return ret;
};

LABKEY.addElemToHead = function(elemName, attributes)
{
    var elem = document.createElement(elemName);
    for(var attr in attributes)
        elem[attr] = attributes[attr];
    return document.getElementsByTagName("head")[0].appendChild(elem);
};

// TODO: Eligible for removal after util.js is migrated
LABKEY.addMarkup = function(html)
{
    if (LABKEY.isDocumentClosed)
    {
        var elem = document.createElement("div");
        elem.innerHTML = html;
        document.body.appendChild(elem.firstChild);
    }
    else
        document.write(html);
};


LABKEY.loadScripts = function()
{
    for (var i=0 ; i<this._requestedScriptFiles.length ; i++)
    {
        var o = this._requestedScriptFiles[i];
        LABKEY.requiresScript(o.file, true, o.callback, o.scope);
    }
    LABKEY.isDocumentClosed = true;
};

/**
 * Loads a CSS file from the server.
 * @param {String} [file] The path of the CSS file to load
 * @example &lt;script type="text/javascript"&gt;
    LABKEY.requiresCss("myModule/myFile.css");
&lt;/script&gt;
 */
LABKEY.requiresCss = function(file)
{
    if (Object.prototype.toString.call(file) == "[object Array]")
    {
        for (var i=0;i<file.length;i++)
            LABKEY.requiresCss(file[i]);
        return;
    }

    if (file.indexOf('/') == 0)
        file = file.substring(1);

    var fullPath = LABKEY.contextPath + "/" + file;
    if (this._requestedCssFiles[fullPath])
        return;
    //console.debug("<link href=" + fullPath);
    LABKEY.addElemToHead("link", {
        type: "text/css",
        rel: "stylesheet",
        href: fullPath + "?" + LABKEY.hash
    });
    this._requestedCssFiles[fullPath] = 1;
};


/**
 * This can be added to any LABKEY page in order to load ExtJS 3.  This is the preferred method to declare Ext3 usage
 * from wiki pages.  For HTML or JSP pages defined in a module, see our <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=ext4Development">documentation</a> on declaration of client dependencies.
 * <p>
 * NOTE: It is important to place this line in a separate script block the your code.  For example:
 * @example
&lt;script type="text/javascript"&gt;
    LABKEY.requiresExt3();
&lt;/script&gt;
&lt;script type="text/javascript"&gt;
    Ext.onReady(function(){
    //your code here
    });
&lt;/script&gt;
 */
LABKEY.requiresExt3 = function(immediate, callback, scope)
{
    if (arguments.length < 1) immediate = true;

    // Require that these CSS files be placed first in the <head> block so that they can be overridden by user customizations
    LABKEY.requiresCss(LABKEY.extJsRoot + '/resources/css/ext-all.css', true);
//    LABKEY.requiresCss([
//        LABKEY.extJsRoot + '/resources/css/ext-all.css',
//        LABKEY.extJsRoot + '/resources/css/ext-patches.css'
//    ], true);

    LABKEY.requiresScript([
         LABKEY.extJsRoot + "/adapter/ext/ext-base.js",
         LABKEY.extJsRoot + "/ext-all" + (LABKEY.devMode ?  "-debug.js" : ".js"),
         LABKEY.extJsRoot + "/ext-patches.js"
    ], immediate, callback, scope, true);
};

/**
 * Deprecated.  Use LABKEY.requiresExt3 instead.
 * @private
 */
LABKEY.requiresExtJs = LABKEY.requiresExt3;

/**
 * This can be added to any LABKEY page in order to load ExtJS 4.  This is the preferred method to declare Ext4 usage
 * from wiki pages.  For HTML or JSP pages defined in a module, see our <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=ext4Development">documentation</a> on declaration of client dependencies.
 * <p>
 * NOTE: It is important to place this line in a separate script block in your code.  For example:
 * @example
&lt;script type="text/javascript"&gt;
    LABKEY.requiresExt4Sandbox();
&lt;/script&gt;
&lt;script type="text/javascript"&gt;
    Ext4.onReady(function(){
    //your code here
    });
&lt;/script&gt;
 */
LABKEY.requiresExt4Sandbox = function(immediate)
{
    if (arguments.length < 1) immediate = true;

    LABKEY.requiresScript(LABKEY.extJsRoot_42 + "/ext-all-sandbox" + (LABKEY.devMode ?  "-debug.js" : ".js"), immediate);
    LABKEY.requiresScript(LABKEY.extJsRoot_42 + "/ext-patches.js", immediate);
};

LABKEY.requiresClientAPI = function(immediate)
{
    if (arguments.length < 1) immediate = true;
    if (LABKEY.devMode)
    {
        var scripts = [
            "clientapi/core/ExtAdapter.js",
            "clientapi/core/Utils.js",
            "clientapi/core/Ajax.js",
            "clientapi/core/ActionURL.js",
            "clientapi/core/Assay.js",
            "clientapi/core/Chart.js",
            "clientapi/core/Domain.js",
            "clientapi/core/Experiment.js",
            "clientapi/core/FieldKey.js",
            "clientapi/core/Filter.js",
            "clientapi/core/NavTrail.js",
            "clientapi/core/Query.js",
            "clientapi/core/WebPart.js",
            "clientapi/core/Security.js",
            "clientapi/core/Specimen.js",
            "clientapi/core/MultiRequest.js",
            "clientapi/core/Form.js",
            "clientapi/core/Message.js",
            "clientapi/core/Pipeline.js",
            "clientapi/core/Portal.js",
            "clientapi/core/Visualization.js"
        ];
        LABKEY.requiresScript(scripts, immediate);
    }
    else
    {
        LABKEY.requiresExt4Sandbox(immediate);
        LABKEY.requiresScript('clientapi.min.js', immediate);
    }
    LABKEY.requiresExt3ClientAPI(immediate);
};

LABKEY.requiresExt3ClientAPI = function(immediate, callback, scope)
{
    if (arguments.length < 1) immediate = true;

    if (LABKEY.devMode)
    {
        var scripts = [
            "clientapi/ext3/DataRegion.js",
            "clientapi/ext3/EditorGridPanel.js",
            "clientapi/ext3/ExtendedJsonReader.js",
            "clientapi/ext3/FieldKey.js",
            "clientapi/ext3/FileSystem.js",
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

        if (!window.Ext)
        {
            LABKEY.requiresExt3(immediate, function()
            {
                //load individual scripts so that they get loaded from source tree
                LABKEY.requiresScript(scripts, immediate, callback, scope);
            });
        }
        else
        {
            LABKEY.requiresScript(scripts, immediate, callback, scope);
        }
    }
    else
    {
        LABKEY.requiresScript('clientapi/ext3.min.js', immediate, callback, scope);
    }
};

LABKEY.requiresExt4ClientAPI = function(immediate)
{
    if (arguments.length < 1) immediate = true;

    //for now we assume this is only dev mode
    LABKEY.requiresExt4Sandbox(immediate);

    LABKEY.requiresScript("clientapi/ext4/Util.js", immediate);
    LABKEY.requiresScript("clientapi/ext4/data/Reader.js", immediate);
    LABKEY.requiresScript("clientapi/ext4/data/Proxy.js", immediate);
    LABKEY.requiresScript("clientapi/ext4/data/Store.js", immediate);

    //load individual scripts so that they get loaded from source tree
    LABKEY.requiresScript("extWidgets/Ext4Helper.js", immediate);
    LABKEY.requiresScript("extWidgets/LabkeyCombo.js", immediate);
    LABKEY.requiresScript("extWidgets/ExtComponents.js", immediate);
    LABKEY.requiresScript("extWidgets/Ext4FormPanel.js", immediate);
    LABKEY.requiresScript("extWidgets/Ext4GridPanel.js", immediate);
    LABKEY.requiresScript("extWidgets/DetailsPanel.js", immediate);
};


//private
//Pull in the required JS files and invoke the callback when they're loaded
LABKEY.initializeViewDesigner = function(dependenciesCallback, scope)
{
    var dependencies = [
        "query/queryDesigner.js",
        "groupTabPanel/GroupTabPanel.js",
        "groupTabPanel/GroupTab.js",
        "ComponentDataView.js",
        "Ext.ux.dd.GridDragDropRowOrder.js"
    ];

    LABKEY.requiresCss("groupTabPanel/GroupTab.css", true);
    LABKEY.requiresCss("groupTabPanel/UngroupedTab.css", true);
    LABKEY.requiresScript(dependencies, true, function () {
        LABKEY.requiresScript("designer/designer2.js", true, dependenciesCallback, scope)
    });
};

LABKEY.setSubmit = function (submit)
{
    this.submit = submit;
};

LABKEY.setDirty = function (dirty)
{
    this.dirty = dirty;
};

LABKEY.isDirty = function () { return this.dirty; };
LABKEY.unloadMessage = "You will lose any changes made to this page.";

LABKEY.beforeunload = function (dirtyCallback, scope)
{
    return function () {
        if (!LABKEY.submit &&
            (LABKEY.isDirty() || (dirtyCallback && dirtyCallback.call(scope)))) {
            return LABKEY.unloadMessage;
        }
    };
};

LABKEY.createElement = function(tag, innerHTML, attributes)
{
    var e = document.createElement(tag);
    if (innerHTML)
        e.innerHTML = innerHTML;
    if (attributes)
    {
        for (var att in attributes)
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
    return e;
};


LABKEY.toHTML = function(elem)
{
    if ('htmlText' in elem)
        return elem.htmlText;
    var y = document.createElement("SPAN");
    y.appendChild(elem);
    return y.innerHTML;
};

LABKEY.showNavTrail = function()
{
    var elem = document.getElementById("navTrailAncestors");
    if(elem)
        elem.style.visibility = "visible";
    elem = document.getElementById("labkey-nav-trail-current-page");
    if(elem)
        elem.style.visibility = "visible";
};

LABKEY.requiresVisualization = function ()
{
    if(!LABKEY.vis){
        LABKEY.vis = {};
    }

    if (LABKEY.devMode)
    {
        LABKEY.requiresScript('vis/lib/raphael-2.1.0.js');
        LABKEY.requiresScript('vis/lib/d3-2.0.4.js');

        LABKEY.requiresScript('vis/lib/patches.js');
        LABKEY.requiresScript('vis/src/utils.js');
        LABKEY.requiresScript('vis/src/geom.js');
        LABKEY.requiresScript('vis/src/stat.js');
        LABKEY.requiresScript('vis/src/scale.js');
        LABKEY.requiresScript('vis/src/layer.js');
        LABKEY.requiresScript('vis/src/plot.js');
        LABKEY.requiresScript("vis/SVGConverter.js");

        // NOTE: If adding a required file you must add to vis.lib.xml for proper packaging
    }
    else
    {
        LABKEY.requiresScript('vis/lib/raphael-min-2.1.0.js');
        LABKEY.requiresScript('vis/lib/d3-2.0.4.min.js');
        LABKEY.requiresScript("vis/SVGConverter.js");
        LABKEY.requiresScript('vis/vis.min.js');
    }
};

// If we're in demo mode, replace each ID with an equal length string of "*".  This code should match DemoMode.id().
LABKEY.id = function(id)
{
    if (LABKEY.demoMode)
    {
        return new Array(id.length + 1 ).join("*");
    }
    else
    {
        return id;
    }
};

/**
 * @param {String} moduleName The name of the module
 * @returns {Object} The context object for this module.  The current view must have specifically requested
 * the context for this module in its view XML
 */
LABKEY.getModuleContext = function(moduleName) {
    return LABKEY.moduleContext[moduleName.toLowerCase()];
};

/**
 * @param {String} moduleName The name of the module
 * @param {String} property The property name to return
 * @returns {String} The value of the module property.  Will return null if the property has not been set.
 */
LABKEY.getModuleProperty = function(moduleName, property) {
    var ctx = LABKEY.getModuleContext(moduleName);
    if (!ctx) {
        return null;
    }
    return ctx[property];
};


//private.  used to append additional module context objects for AJAXd views
LABKEY.applyModuleContext = function(ctx) {
    for (var mn in ctx) {
        if (ctx.hasOwnProperty(mn)) {
            LABKEY.moduleContext[mn.toLowerCase()] = ctx[mn];
        }
    }
};
