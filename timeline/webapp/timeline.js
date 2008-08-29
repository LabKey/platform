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
            else if (typeof getter == "function")
                return getter;
            else if (htmlEncode)
                return function (row) { return Ext.util.Format.htmlEncode(row[getter]); };
            else
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
            else if (typeof value == "function")
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
            }
            tlData.events.push(event);
        }
        config.eventSource.loadJSON(tlData, window.location.href);
    },

    addEventSet: function(eventConfig)
    {
        var ec = Ext.apply({}, eventConfig);

        if (null == ec.query || null == ec.query.queryName || null == ec.query.schemaName)
            throw "Query specification error in addEventSet";

        if (null == ec.eventSource)
            ec.eventSource = this.getBand(0).getEventSource();

        if (ec.query.successCallback)
            throw "Unexpected callback in queryConfig";

        var queryConfig = Ext.apply({}, ec.query, {
            successCallback:function(data) {LABKEY.Timeline.populateEvents(data, ec)},
            failureCallback:function() {alert("Error occured in timeline query. schemaName: " + config.query.schemaName + ", queryName: " + config.query.queryName)}
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

}

LABKEY.requiresClientAPI();
LABKEY.requiresScript("similetimeline/timeline-api.js", true);