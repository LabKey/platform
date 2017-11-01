/*
 * Copyright (c) 2008-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var Timeline_urlPrefix = LABKEY.contextPath + "/similetimeline/";

LABKEY.Timeline = {
    populateEvents: function (data, config)
    {
        /**
        * Return a getter for a value. If a string is passed treat is as a field name
         * @param getter
         * @param htmlEncode True if field data should be htmlEncoded before display. Only applies if getter is a field name
         */
        function wrapGetter (getter, htmlEncode)
        {
            if (getter == null)
                return function() { return null };
            else if (LABKEY.Utils.isFunction(getter))
                return getter;
            else if (htmlEncode)
                return function(row) { return LABKEY.Utils.encodeHtml(row[getter]); };
            return function (row) { return row[getter]; };
        }

        /**
        * Return a function for a value. If a string is passed return a function that returns a constant
         * @param getter
         */
        function wrapConstant(value)
        {
            if (value == null)
                return function() { return null };
            else if (LABKEY.Utils.isFunction(value))
                return value;
            else
                return function (row) { return value };
        }


        var startFn = wrapGetter(config.start);
        var endFn = wrapGetter(config.end);
        var titleFn = wrapGetter(config.title, true);
        var descriptionFn = wrapGetter(config.description, true);
        var iconFn = wrapGetter(config.icon);
        var linkFn = wrapGetter(config.link);
        var colorFn = wrapConstant(config.color);
        var textColorFn = wrapConstant(config.textColor);

        var tlData = {events:[]};
        for (var i = 0; i < data.rows.length; i++)
        {
            var row = data.rows[i];
            if (!startFn(row))
                continue;

            var event = {
                start:startFn(row),
                end:endFn(row),
                title:titleFn(row),
                description:descriptionFn(row),
                icon:iconFn(row),
                link:linkFn(row),
                color:colorFn(row),
                textColor:textColorFn(row)
            };
            tlData.events.push(event);
        }
        config.eventSource.loadJSON(tlData, window.location.href);
    },

    addEventSet: function(eventConfig)
    {
        var ec = Ext4.apply({}, eventConfig);

        if (null == ec.query || null == ec.query.queryName || null == ec.query.schemaName)
            throw "Query specification error in addEventSet";

        if (null == ec.eventSource)
            ec.eventSource = this.getBand(0).getEventSource();

        if (ec.query.successCallback)
            throw "Unexpected callback in queryConfig";

        var queryConfig = Ext4.apply({}, ec.query, {
            success:function(data) {LABKEY.Timeline.populateEvents(data, ec)},
            failure:function() {alert("Error occurred in timeline query. schemaName: " + ec.query.schemaName + ", queryName: " + ec.query.queryName)}
        });

        LABKEY.Query.selectRows(queryConfig);
    },

    create: function (config)
    {
        if (!config.bandInfos)
        {
            if (!config.eventSource)
                config.eventSource = new Timeline.DefaultEventSource(0);

            config.bandInfos =  [
            Timeline.createBandInfo({
                width:          "90%",
                date:           "Oct 1 2007 00:00:00 GMT",
                eventSource: config.eventSource,
                intervalUnit:   Timeline.DateTime.MONTH,
                intervalPixels: 300
            }),
            Timeline.createBandInfo({
                width:          "10%",
                eventSource: config.eventSource,
                showEventText:false,
                intervalUnit:   Timeline.DateTime.YEAR,
                intervalPixels: 200
            })
          ];

          config.bandInfos[1].syncWith = 0;
          config.bandInfos[1].highlight = true;
        }
        else
            config.eventSource = config.bandInfos[0].eventSource;

        var tl = Timeline.create(document.getElementById(config.renderTo), config.bandInfos);
        //Add a function
        tl.addEventSet = LABKEY.Timeline.addEventSet;

        if (config.query)
            tl.addEventSet(config);

        return tl ;
    }

};

/*==================================================
 *  Timeline API
 *
 *  This file will load all the Javascript files
 *  necessary to make the standard timeline work.
 *  It also detects the default locale.
 *
 *  DO NOT!!! Include this file in your HTML file as follows:
 *
 *
 *==================================================
 */

var Timeline = {};
Timeline.Platform = {};
    /*
        HACK: We need these 2 things here because we cannot simply append
        a <script> element containing code that accesses Timeline.Platform
        to initialize it because IE executes that <script> code first
        before it loads timeline.js and util/platform.js.
    */

(function() {
    var bundle = true;

    // ISO-639 language codes, ISO-3166 country codes (2 characters)
    var supportedLocales = [
        "cs",       // Czech
        "de",       // German
        "en",       // English
        "es",       // Spanish
        "fr",       // French
        "it",       // Italian
        "ru",       // Russian
        "se",       // Swedish
        "vi",       // Vietnamese
        "zh"        // Chinese
    ];

    try {
        var desiredLocales = [ "en" ];
        var defaultServerLocale = "en";

        var parseURLParameters = function(parameters) {
            var params = parameters.split("&");
            for (var p = 0; p < params.length; p++) {
                var pair = params[p].split("=");
                if (pair[0] == "locales") {
                    desiredLocales = desiredLocales.concat(pair[1].split(","));
                } else if (pair[0] == "defaultLocale") {
                    defaultServerLocale = pair[1];
                } else if (pair[0] == "bundle") {
                    bundle = pair[1] != "false";
                }
            }
        };

        (function() {
            if (typeof Timeline_urlPrefix == "string") {
                Timeline.urlPrefix = Timeline_urlPrefix;
                if (typeof Timeline_parameters == "string") {
                    parseURLParameters(Timeline_parameters);
                }
            } else {
                var heads = document.documentElement.getElementsByTagName("head");
                for (var h = 0; h < heads.length; h++) {
                    var scripts = heads[h].getElementsByTagName("script");
                    for (var s = 0; s < scripts.length; s++) {
                        var url = scripts[s].src;
                        var i = url.indexOf("timeline-api.js");
                        if (i >= 0) {
                            Timeline.urlPrefix = url.substr(0, i);
                            var q = url.indexOf("?");
                            if (q > 0) {
                                parseURLParameters(url.substr(q + 1));
                            }
                            return;
                        }
                    }
                }
                throw new Error("Failed to derive URL prefix for Timeline API code files");
            }
        })();

        var includeCssFiles;
        if ("SimileAjax" in window) {
            includeJavascriptFiles = function(urlPrefix, filenames) {
                SimileAjax.includeJavascriptFiles(document, urlPrefix, filenames);
            };
            includeCssFiles = function(urlPrefix, filenames) {
                SimileAjax.includeCssFiles(document, urlPrefix, filenames);
            }
        } else {
            var getHead = function() {
                return document.getElementsByTagName("head")[0];
            };
            var includeCssFile = function(url) {
                if (document.body == null) {
                    try {
                        document.write("<link rel='stylesheet' href='" + url + "' type='text/css'/>");
                        return;
                    } catch (e) {
                        // fall through
                    }
                }

                var link = document.createElement("link");
                link.setAttribute("rel", "stylesheet");
                link.setAttribute("type", "text/css");
                link.setAttribute("href", url);
                getHead().appendChild(link);
            };

            includeCssFiles = function(urlPrefix, filenames) {
                for (var i = 0; i < filenames.length; i++) {
                    includeCssFile(urlPrefix + filenames[i]);
                }
            };
        }

        /*
         *  Include non-localized files
         */
        if (bundle) {
            includeCssFiles(Timeline.urlPrefix, [ "bundle.css" ]);
        } else {
            includeCssFiles(Timeline.urlPrefix + "styles/", cssFiles);
        }

        /*
         *  Include localized files
         */
        var loadLocale = [];
        loadLocale[defaultServerLocale] = true;

        var tryExactLocale = function(locale) {
            for (var l = 0; l < supportedLocales.length; l++) {
                if (locale == supportedLocales[l]) {
                    loadLocale[locale] = true;
                    return true;
                }
            }
            return false;
        };
        var tryLocale = function(locale) {
            if (tryExactLocale(locale)) {
                return locale;
            }

            var dash = locale.indexOf("-");
            if (dash > 0 && tryExactLocale(locale.substr(0, dash))) {
                return locale.substr(0, dash);
            }

            return null;
        };

        for (var l = 0; l < desiredLocales.length; l++) {
            tryLocale(desiredLocales[l]);
        }

        var defaultClientLocale = defaultServerLocale;
        var defaultClientLocales = ("language" in navigator ? navigator.language : navigator.browserLanguage).split(";");
        for (var l = 0; l < defaultClientLocales.length; l++) {
            var locale = tryLocale(defaultClientLocales[l]);
            if (locale != null) {
                defaultClientLocale = locale;
                break;
            }
        }

        Timeline.Platform.serverLocale = defaultServerLocale;
        Timeline.Platform.clientLocale = defaultClientLocale;
    } catch (e) {
        alert(e);
    }
})();
