Ext.namespace('LABKEY', 'LABKEY.timeline');


/*** for 11.2 only remove before 11.3 final ***/
/**
 * Loads a javascript file from the server.
 * @param file A single file or an Array of files.
 * @param immediate True to load the script immediately; false will defer script loading until the page has been downloaded.
 * @param callback Called after the script files have been loaded.
 * @param scope Callback scope.
 * @param inOrder True to load the scripts in the order they are passed in. Default is false.
 */
LABKEY._requiresScript = function(file, immediate, callback, scope, inOrder)
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
                    LABKEY._requiresScript(file[loaded], immediate, chain, true);
            }
            LABKEY._requiresScript(file[loaded], immediate, chain, true);
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
                LABKEY._requiresScript(file[i], immediate, allDone);
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
/**********************************/




LABKEY.requiresTimeline = function(fn)
{
    var prefix  = "/timeline/";
    var context = LABKEY.ActionURL.getContextPath();
    var timeline_prefix      = prefix + "timeline_2.3.0/";
    var jquery_urlPrefix     = prefix + "jquery/";
    var SimileAjax_urlPrefix = timeline_prefix + "timeline_ajax/";
    var Timeline_urlPrefix   = timeline_prefix + "timeline_js/";
    var Timeplot_urlPrefix   = timeline_prefix + "timeplot_js/";
    var Timeline_parameters  = 'bundle=true';
    var ctx_timeline_url     = context + Timeline_urlPrefix;
    var ctx_timeplot_url     = context + Timeplot_urlPrefix;
    LABKEY.timeline.directory= timeline_prefix; // so others can use

    // avoid crazy timeline-api loading
    window.SimileAjax =
    {
        urlPrefix          :SimileAjax_urlPrefix,
        Platform           :{},
        params             :{},
        parseURLParameters :function(){return{};}
    };

    window.Timeline = {urlPrefix:ctx_timeline_url, serverLocale:"en", clientLocale:"en"};
    window.Timeplot = {urlPrefix:ctx_timeplot_url, importers:{}};

    LABKEY.requiresCss(SimileAjax_urlPrefix + "styles/graphics.css", true);
    LABKEY.requiresCss(Timeline_urlPrefix   + "timeline-bundle.css", true);
    LABKEY.requiresCss(Timeplot_urlPrefix   + "timeplot-bundle.css", true);
    LABKEY.requiresCss(jquery_urlPrefix + 'ui.core.css', true);
    LABKEY.requiresCss(jquery_urlPrefix + 'ui.tabs.css', true);

    var dependencies = [
        /* Simile */
        SimileAjax_urlPrefix + "simile-ajax-bundle.js",
        SimileAjax_urlPrefix + "scripts/signal.js",
        Timeline_urlPrefix   + "timeline-bundle.js",
        Timeplot_urlPrefix   + "timeplot-bundle.js",
        Timeline_urlPrefix   + "scripts/l10n/en/timeline.js",
        Timeline_urlPrefix   + "scripts/l10n/en/labellers.js",

        /* jQuery Sparkline */
        jquery_urlPrefix + 'jquery-1.6.1.min.js',
        jquery_urlPrefix + 'ui.core.js',
        jquery_urlPrefix + 'jquery.sparkline.js',

        /* API */
        prefix + 'Timeline.js'
    ];

    LABKEY._requiresScript(dependencies, true, fn, null, true);
};
