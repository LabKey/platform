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
        document.write('<script type="text/javascript" language="javascript" src="' + LABKEY.contextPath + "/" + file + '?' + LABKEY.hash + '"></script>');
    }
}


LABKEY.loadScripts = function()
{
    for (var i=0 ; i<this._requestedScriptFiles.length ; i++)
    {
        var file = this._requestedScriptFiles[i];
        LABKEY.requiresScript(file, true);
    }
}


LABKEY.requiresCss = function(file)
{
    var fullPath = LABKEY.contextPath + "/" + file;
    if (this._requestedScriptFiles[fullPath])
        return;
    //console.debug("<link href=" + fullPath);
    document.write('<link rel="stylesheet" type="text/css" href="'+fullPath+'" />');
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
    LABKEY.requiresScript(LABKEY.extJsRoot + "/adapter/ext/ext-base.js", immediate);
    LABKEY.requiresScript(LABKEY.extJsRoot + "/ext-all" + (LABKEY.devMode ?  "-debug.js" : ".js"), immediate);
}

LABKEY.requiresClientAPI = function(immediate)
{
    if (arguments.length < 2) immediate = true;
    LABKEY.requiresCss(LABKEY.extJsRoot + '/resources/css/ext-all.css');
    LABKEY.requiresExtJs(immediate);
    LABKEY.requiresScript('clientapi/clientapi' + (LABKEY.devMode ? '.js' : '.min.js'), immediate);
}

LABKEY.requiresButtonBarMenu = function()
{
    if (!LABKEY.buttonBarMenu)
    {
        LABKEY.requiresMenu();

        YAHOO.util.Event.addListener(window, "load", initButtonBarMenu);
        LABKEY.buttonBarMenu = true;
    }
}

function initButtonBarMenu() {
    var elements = YAHOO.util.Dom.getElementsByClassName('yuimenu', 'div');
    for (i = 0; i < elements.length; i++) {
        var menu = new YAHOO.widget.Menu(elements[i].id, { constraintoviewport: true });
        menu.render();
    }
}

function showMenu(parent, menuElementId) {
    var menuDiv = YAHOO.util.Dom.get(menuElementId);
    menuDiv.style.display = "";
    var menu = new YAHOO.widget.Menu(menuElementId, { constraintoviewport: true });
    var x = YAHOO.util.Dom.getX(parent);
    var y = YAHOO.util.Dom.getY(parent) + parent.offsetHeight;
    var region = YAHOO.util.Dom.getRegion(menuElementId);
    menu.render();
    menu.moveTo(x, y);
    menu.show();
    if (region.bottom > document.body.scrollTop + YAHOO.util.Dom.getViewportHeight())
    {
        document.body.scrollTop = region.bottom - YAHOO.util.Dom.getViewportHeight();
    }
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
