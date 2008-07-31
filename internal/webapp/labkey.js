/*
 * Copyright (c) 2007-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
if (typeof LABKEY == "undefined")
{
    var LABKEY = {};
    LABKEY.contextPath = "must call init()";
    LABKEY.imagePath = "must call init()";
    LABKEY.devMode = false;
    LABKEY.yahooRoot = "_yui/build";
    LABKEY.extJsRoot = "ext-2.0.2";
    LABKEY.verbose = false;
    LABKEY.widget = {};
    LABKEY.hash = 0;
    LABKEY.dirty = false;
    LABKEY.submit = false;
    LABKEY.buttonBarMenu = false;

    LABKEY._requestedScriptFiles = [];
    LABKEY._loadedScriptFiles = {};
    LABKEY._emptyFunction = function(){};

    // FireBug console
    if (!("console" in window))
    {
        window.console =
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
    }
}


LABKEY.init = function(config)
{
    for (var p in config)
    {
        this[p] = config[p];
    }
};


LABKEY.requiresScript = function(file, immediate)
{
    if (arguments.length < 2)
        immediate = true;

//    console.log("requiresScript( " + file + " , " + immediate + " )");

    if (file.indexOf('/') == 0)
    {
        file = file.substring(1);
    }

    if (this._loadedScriptFiles[file])
        return;

    if (!immediate)
        this._requestedScriptFiles.push(file);
    else
    {
        this._loadedScriptFiles[file] = true;
//        console.log("<script href=" + file + ">");

        //although FireFox and Safari allow scripts to use the DOM
        //during parse time, IE does not. So if the document is
        //closed, use the DOM to create a script element and append it
        //to the head element. Otherwise (still parsing), use document.write()
        if(LABKEY.isDocumentClosed)
        {
            //create a new script element and append it to the head element
            LABKEY.addElemToHead("script", {
                src: LABKEY.contextPath + "/" + file + "?" + LABKEY.hash,
                type: "text/javascript"
            });
        }
        else
            document.write('<script type="text/javascript" language="javascript" src="' + LABKEY.contextPath + "/" + file + '?' + LABKEY.hash + '"></script>');
    }
}

LABKEY.addElemToHead = function(elemName, attributes)
{
    var elem = document.createElement(elemName);
    for(var attr in attributes)
        elem[attr] = attributes[attr];
    document.getElementsByTagName("head")[0].appendChild(elem);
}

LABKEY.addMarkup = function(html)
{
    if(LABKEY.isDocumentClosed)
    {
        var elem = document.createElement("div");
        elem.innerHTML = html;
        document.body.appendChild(elem.firstChild);
    }
    else
        document.write(html);
}


LABKEY.loadScripts = function()
{
    for (var i=0 ; i<this._requestedScriptFiles.length ; i++)
    {
        var file = this._requestedScriptFiles[i];
        LABKEY.requiresScript(file, true);
    }
    LABKEY.isDocumentClosed = true;
}


LABKEY.requiresCss = function(file)
{
    var fullPath = LABKEY.contextPath + "/" + file;
    if (this._requestedScriptFiles[fullPath])
        return;
    //console.debug("<link href=" + fullPath);
    LABKEY.addElemToHead("link", {
        type: "text/css",
        rel: "stylesheet",
        href: fullPath
    });
    this._requestedScriptFiles[fullPath] = 1;
}


LABKEY.requiresYahoo = function(script, immediate)
{
    if (arguments.length < 2) immediate = true;

    var dir = script == "container_core" ? "container" : script;
    var base=LABKEY.yahooRoot + "/" + dir + "/" + script;
    var expanded = LABKEY.devMode ? (LABKEY.verbose ? base+"-debug.js" : base+".js") : base+"-min.js";
    LABKEY.requiresScript(expanded, immediate);
}

LABKEY.requiresExtJs = function(immediate)
{
    if (arguments.length < 2) immediate = true;
    LABKEY.requiresCss(LABKEY.extJsRoot + '/resources/css/ext-all.css');
    LABKEY.requiresScript(LABKEY.extJsRoot + "/adapter/ext/ext-base.js", immediate);
    LABKEY.requiresScript(LABKEY.extJsRoot + "/ext-all" + (LABKEY.devMode ?  "-debug.js" : ".js"), immediate);
}

LABKEY.requiresClientAPI = function(immediate)
{
    if (arguments.length < 2) immediate = true;
    LABKEY.requiresCss(LABKEY.extJsRoot + '/resources/css/ext-all.css');
    LABKEY.requiresExtJs(immediate);
    if(LABKEY.devMode)
    {
        //load individual scripts so that they get loaded from source tree
        LABKEY.requiresScript("clientapi/ActionURL.js");
        LABKEY.requiresScript("clientapi/Assay.js");
        LABKEY.requiresScript("clientapi/Chart.js");
        LABKEY.requiresScript("clientapi/EditorGridPanel.js");
        LABKEY.requiresScript("clientapi/ExtJsConfig.js");
        LABKEY.requiresScript("clientapi/Filter.js");
        LABKEY.requiresScript("clientapi/GridView.js");
        LABKEY.requiresScript("clientapi/NavTrail.js");
        LABKEY.requiresScript("clientapi/Query.js");
        LABKEY.requiresScript("clientapi/Store.js");
        LABKEY.requiresScript("clientapi/Utils.js");
        LABKEY.requiresScript("clientapi/WebPart.js");
        LABKEY.requiresScript("clientapi/Security.js");
    }
    else
        LABKEY.requiresScript('clientapi/clientapi' + (LABKEY.devMode ? '.js' : '.min.js'), immediate);
}


function showMenu(parent, menuElementId, align) {
    if (!align)
        align = "tl-bl?";
    Ext.menu.MenuMgr.get(menuElementId).show(parent, align);
}

LABKEY.requiresMenu = function()
{
    //LABKEY.requiresCss(LABKEY.yahooRoot + '/menu/assets/menu.css');
    LABKEY.requiresYahoo('yahoo', true);
    LABKEY.requiresYahoo('event', true);
    if (LABKEY.devMode)
        LABKEY.requiresYahoo('logger', true);
    LABKEY.requiresYahoo('dom', true);
    LABKEY.requiresYahoo('container', true);
    LABKEY.requiresYahoo('menu', true);
}

LABKEY.setSubmit = function (submit)
{
    this.submit = submit;
}

LABKEY.setDirty = function (dirty)
{
    this.dirty = dirty;
}

LABKEY.isDirty = function () { return this.dirty; }

LABKEY.beforeunload = function (dirtyCallback)
{
    return function () {
        if (!LABKEY.submit &&
            (LABKEY.isDirty() || (dirtyCallback && dirtyCallback()))) {
            return "You will lose any changes made to this page.";
        }
    }
}

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
        for (var att in attributes)
            e[att] = attributes[att];
    return e;
}

LABKEY.toHTML = function(elem)
{
    if ('htmlText' in elem)
        return elem.htmlText;
    var y = document.createElement("SPAN");
    y.appendChild(elem);
    return y.innerHTML;
}
