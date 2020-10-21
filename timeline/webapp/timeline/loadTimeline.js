Ext.namespace('LABKEY', 'LABKEY.ext', 'LABKEY.timeline');

LABKEY.timeline.isLoaded = false;


LABKEY.requiresTimeline = function(fn)
{
    var prefix  = "timeline/";
    var context = LABKEY.ActionURL.getContextPath();
    var timeline_prefix      = prefix + "timeline_2.3.0/";
    var jquery_urlPrefix    = "internal/" + "jQuery/";
    var sparkline_urlPrefix     = prefix + "sparkline/";
    var SimileAjax_urlPrefix = timeline_prefix + "timeline_ajax/";
    var Timeline_urlPrefix   = timeline_prefix + "timeline_js/";
    var Timeplot_urlPrefix   = timeline_prefix + "timeplot_js/";
    var Timeline_parameters  = 'bundle=true';
    var ctx_timeline_url     = context + "/" + Timeline_urlPrefix;
    var ctx_timeplot_url     = context + "/" + Timeplot_urlPrefix;
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
    LABKEY.requiresCss(sparkline_urlPrefix + 'ui.core.css', true);
    LABKEY.requiresCss(sparkline_urlPrefix + 'ui.tabs.css', true);

    var dependencies = [
        /* Simile */
        SimileAjax_urlPrefix + "simile-ajax-bundle.js",
        SimileAjax_urlPrefix + "scripts/signal.js",
        Timeline_urlPrefix   + "timeline-bundle.js",
        Timeplot_urlPrefix   + "timeplot-bundle.js",
        Timeline_urlPrefix   + "scripts/l10n/en/timeline.js",
        Timeline_urlPrefix   + "scripts/l10n/en/labellers.js",

        /* jQuery Sparkline */
        jquery_urlPrefix + 'jquery-3.5.1.min.js',

        /* jQuery Sparkline */
        sparkline_urlPrefix + 'ui.core.js',
        sparkline_urlPrefix + 'jquery.sparkline.js',

        /* API */
        prefix + 'Timeline.js'
    ];

    LABKEY.requiresScript(dependencies, fn, null, true);
};

LABKEY.onTimelineReady = function(fn)
{
    LABKEY.Utils.onTrue({
        testCallback : function() {return LABKEY.timeline.isLoaded;},
        success : fn,
        maxTests : 1000000
    });
};
