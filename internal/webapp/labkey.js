/*
 * Copyright (c) 2007-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// NOTE labkey.js should not depend on Ext

if (typeof LABKEY == "undefined")
{
    /**
     * @class LABKEY
     * LabKey core API and utilities.
     * @singleton
     */
    var LABKEY = {};
    LABKEY.contextPath = (typeof __contextPath == "undefined") ? "UNDEFINED" : __contextPath;
    LABKEY.imagePath = (typeof __contextPath == "undefined") ? "UNDEFINED" : __contextPath + "/_images";
    LABKEY.devMode = false;
    LABKEY.demoMode = false;
    LABKEY.extJsRoot = "ext-3.4.0";
    LABKEY.extJsRoot_40 = "ext-4.0.7";
    LABKEY.extThemeRoot = "labkey-ext-theme";
    LABKEY.verbose = false;
    LABKEY.widget = {};
    LABKEY.hash = 0;
    LABKEY.dirty = false;
    LABKEY.submit = false;
    LABKEY.buttonBarMenu = false;
    LABKEY.fieldMarker = '@';

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
        this[p] = config[p];
    }
    if ("Security" in LABKEY)
        LABKEY.Security.currentUser = LABKEY.user;
};


/**
 * Adds new listener to be executed when all required scripts are fully loaded.
 * @param {Mixed} config Either a callback function, or an object with the following properties:
 *
 * <li>callback (required) A function that will be called when required scripts are loaded.</li>
 * <li>scope (optional) The scope to be used for the callback function.  Defaults to the current scope.</li>
 * <li>scripts (optional) A string with a single script or an array of script names to load.  This will be passed to LABKEY.requiresScript().</li>
 * @example &lt;script type="text/javascript"&gt;
    //simple usage
    LABKEY.onReady(function(){
        //your code here.  will be executed once scripts have loaded
    });

    //
    LABKEY.onReady({
        scope: this,
        scripts: ['/myModule/myScript.js', 'AnotherScript.js]
        callback: function(){
            //your code here.  will be executed once scripts have loaded
        });
    });
&lt;/script&gt;
 */
LABKEY.onReady = function(config)
{
    var scope;
    var callback;
    var scripts;

    if(Ext.isFunction(config)){
        scope = this;
        callback = config;
        scripts = null;
    }
    else if (Ext.isObject(config) && Ext.isFunction(config.callback))
    {
        scope = config.scope || this;
        callback = config.callback;
        scripts = config.scripts;
    }
    else
    {
        alert("Improper configuration for LABKEY.onReady()");
        return;
    }

    if(scripts)
    {
        LABKEY.requiresScript(scripts, true, callback, scope, true);
    }
    else
    {
        Ext.onReady(callback, scope);
    }
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
            function chain()
            {
                loaded++;
                if (loaded == requestedLength && typeof callback == 'function')
                {
                    callback.call(scope);
                }
                else if (loaded < requestedLength)
                    LABKEY.requiresScript(file[loaded], immediate, chain, true);
            }
            LABKEY.requiresScript(file[loaded], immediate, chain, true);
        }
        else
        {
            function allDone()
            {
                loaded++;
                if (loaded == requestedLength && typeof callback == 'function')
                    callback.call(scope);
            }

            for (var i = 0; i < file.length; i++)
                LABKEY.requiresScript(file[i], immediate, allDone);
        }
        return;
    }

//    console.log("requiresScript( " + file + " , " + immediate + " )");

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
//        console.log("<script href=" + file + ">");

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


LABKEY.addElemToHead = function(elemName, attributes)
{
    var elem = document.createElement(elemName);
    for(var attr in attributes)
        elem[attr] = attributes[attr];
    return document.getElementsByTagName("head")[0].appendChild(elem);
};


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


LABKEY.requiresExtJs = function(immediate)
{
    if (arguments.length < 1) immediate = true;

    // Require that these CSS files be placed first in the <head> block so that they can be overridden by user customizations
    LABKEY.requiresCss(LABKEY.extJsRoot + '/resources/css/ext-all.css', true);
//    LABKEY.requiresCss(LABKEY.extJsRoot + '/resources/css/ext-patches.css', true);
    LABKEY.requiresScript(LABKEY.extJsRoot + "/adapter/ext/ext-base.js", immediate);
    LABKEY.requiresScript(LABKEY.extJsRoot + "/ext-all" + (LABKEY.devMode ?  "-debug.js" : ".js"), immediate);
    LABKEY.requiresScript(LABKEY.extJsRoot + "/ext-patches.js", immediate);
};

LABKEY.requiresExt4Sandbox = function(immediate)
{
    if (arguments.length < 1) immediate = true;

    LABKEY.requiresScript(LABKEY.extJsRoot_40 + "/ext-all-sandbox" + (LABKEY.devMode ?  "-debug.js" : ".js"), immediate);
    LABKEY.requiresScript(LABKEY.extJsRoot_40 + "/ext-patches.js", immediate);
};

// adds the compatibility layer to be used on the Ext4 sandbox components
LABKEY.requiresExtSandboxCompat = function(immediate)
{
    if (arguments.length < 1) immediate = true;

    // compatibility layer
    LABKEY.requiresScript(LABKEY.extJsRoot_40 + "/ext3-sb-core-compat.js", immediate);
    LABKEY.requiresScript(LABKEY.extJsRoot_40 + "/ext3-sb-compat.js", immediate);
};

LABKEY.requiresClientAPI = function(immediate)
{
    if (arguments.length < 1) immediate = true;
    if (LABKEY.devMode)
    {
        //load individual scripts so that they get loaded from source tree
        LABKEY.requiresScript("clientapi/ExtJsConfig.js", immediate);
        LABKEY.requiresScript("clientapi/ActionURL.js", immediate);
        LABKEY.requiresScript("clientapi/Ajax.js", immediate);
        LABKEY.requiresScript("clientapi/Assay.js", immediate);
        LABKEY.requiresScript("clientapi/Chart.js", immediate);
        LABKEY.requiresScript("clientapi/DataRegion.js", immediate);
        LABKEY.requiresScript("clientapi/Domain.js", immediate);
        LABKEY.requiresScript("clientapi/Experiment.js", immediate);
        LABKEY.requiresScript("clientapi/LongTextEditor.js", immediate);
        LABKEY.requiresScript("clientapi/EditorGridPanel.js", immediate);
        LABKEY.requiresScript("clientapi/Filter.js", immediate);
        LABKEY.requiresScript("clientapi/GridView.js", immediate);
        LABKEY.requiresScript("clientapi/NavTrail.js", immediate);
        LABKEY.requiresScript("clientapi/Query.js", immediate);
        LABKEY.requiresScript("clientapi/ExtendedJsonReader.js", immediate);
        LABKEY.requiresScript("clientapi/Store.js", immediate);
        LABKEY.requiresScript("clientapi/Utils.js", immediate);
        LABKEY.requiresScript("clientapi/WebPart.js", immediate);
        LABKEY.requiresScript("clientapi/QueryWebPart.js", immediate);
        LABKEY.requiresScript("clientapi/Security.js", immediate);
        LABKEY.requiresScript("clientapi/SecurityPolicy.js", immediate);
        LABKEY.requiresScript("clientapi/Specimen.js", immediate);
        LABKEY.requiresScript("clientapi/MultiRequest.js", immediate);
        LABKEY.requiresScript("clientapi/HoverPopup.js", immediate);
        LABKEY.requiresScript("clientapi/Form.js", immediate);
        LABKEY.requiresScript("clientapi/PersistentToolTip.js", immediate);
        LABKEY.requiresScript("clientapi/Message.js", immediate);
        LABKEY.requiresScript("clientapi/FormPanel.js", immediate);
        LABKEY.requiresScript("clientapi/Pipeline.js", immediate);
        LABKEY.requiresScript("clientapi/Portal.js", immediate);
        LABKEY.requiresScript("clientapi/Visualization.js", immediate);
    }
    else
        LABKEY.requiresScript('clientapi/clientapi' + (LABKEY.devMode ? '.js' : '.min.js'), immediate);
};


LABKEY.requiresExt4ClientAPI = function(immediate)
{
    if (arguments.length < 1) immediate = true;

    //for now we assume this is only dev mode
    LABKEY.requiresExt4Sandbox(immediate);

    //load individual scripts so that they get loaded from source tree
    LABKEY.requiresScript("extWidgets/MetaHelper.js", immediate);
    LABKEY.requiresScript("extWidgets/Ext4Store.js", immediate);
    LABKEY.requiresScript("extWidgets/ExtComponents.js", immediate);
    LABKEY.requiresScript("extWidgets/Ext4FormPanel.js", immediate);
    LABKEY.requiresScript("extWidgets/Ext4GridPanel.js", immediate);
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

//
// language extensions, global functions
//

function byId(id)
{
    return document.getElementById(id);
}


function trim(s)
{
  return s.replace(/^\s+/, '').replace(/\s+$/, '');
}

String.prototype.trim = function () {return trim(this);};


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
    if (LABKEY.devMode)
        LABKEY.requiresScript("protovis/protovis-d3.2.js");
    else
        LABKEY.requiresScript("protovis/protovis-r3.2.js");

    LABKEY.requiresScript("vis/ChartComponent.js");
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