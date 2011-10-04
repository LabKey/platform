Ext.namespace('LABKEY', 'LABKEY.ext');

LABKEY.ext.Timeline = Ext.extend(Ext.Panel, {

    constructor : function(config) {

        if (!config.eventStore)
            throw 'eventStore required for LABKEY.ext.Timeline';

        Ext.apply(this, config, {
            /* Ext Config */
            border : false,
            frame  : false,

            /* Chart Config */
            tl : null,
            tp : null,
            format          : 'iso8601',
            seriesNames     : [],
            bands           : [],
            eventSourceLine : null,
            eventSourcePlot : null,
            seriesSource    : null
        });

        // total count of stores
        this.storeCount = this.bands.length + 1; // event store

        this.eventStore.on('load', this.eventStoreLoad, this);

        for (var i=0; i < this.bands.length; i++) {
            this.bands[i].store.on('load', this.seriesStoreLoad, this);
        }

        LABKEY.ext.Timeline.superclass.constructor.call(this);
    },

    initComponent : function() {

        this.initSeries();
        this.eventStore.load();

        for (var i=0; i < this.bands.length; i++) {
            this.bands[i].store.load();
        }

        LABKEY.ext.Timeline.superclass.initComponent.apply(this, arguments);
    },

    initSeries : function() {
        // TODO: Figure out what we're trying to do here
//        for (var i=0; i < this.seriesConfig.length; i++)
//        {
//            this.seriesNames.push(this.seriesConfig[i].id);
//        }
    },

    eventStoreLoad : function(store, recs, opts) {
        this.populateEventSources();
    },

    seriesStoreLoad : function(store, recs, opts) {
        this.populateEventSources();
    },

    populateEventSources : function() {

        if (!this.callCount)
            this.callCount = 1;

        if (this.storeCount == this.callCount)
        {
            /* Initialize Timeline and Timeplot sources */
            Timeline.DateTime = SimileAjax.DateTime;
            this.eventSourceLine = new Timeline.DefaultEventSource();
            this.seriesSource    = new Timeplot.DefaultEventSource();

            this.loadJsonAsSeries();

            var json = this.getStoreAsJson(this.eventStore);
            var eventData = {events : json};
            this.eventSourceLine.loadJSON(eventData, LABKEY.ActionURL.getContextPath()+LABKEY.timeline.directory);

            this.renderCharts();

            this.tl.getBand(0).setMaxVisibleDate(json[json.length-1].start);
        }
        else
        {
            this.callCount++;
        }
    },

    renderCharts : function()
    {
        this.createTimeline();

        var band = this.tl.getBand(0);
        for (var i = 0; i < this.bands.length ; i++)
        {
            band = this.tl.getBand(i+1);
            this.renderSeries(band, this.bands[i]);
        }
    },

    createTimeline : function()
    {
        var countSeries = this.bands.length + 1; // why?
        var widthPct    = "" + Math.floor(100/countSeries) + '%';

        var bandInfos = [];

        // push event
        bandInfos.push(
                Timeline.createBandInfo({
                    eventSource    : this.eventSourceLine,
                    width          : widthPct,
                    intervalUnit   : Timeline.DateTime.MONTH,
                    intervalPixels : 30,
                    color          :'#eeeeee'
                })
        );

        // push series(s)
        for (var i = 0; i < this.bands.length; i++)
        {
            var bandInfo = Timeline.createBandInfo({
                width          : widthPct,
                intervalUnit   : Timeline.DateTime.MONTH,
                intervalPixels : 30,
                color          : '#dddddd'
            });
            bandInfo.syncWith = 0;
            bandInfos.push(bandInfo);
        }

        this.tl = Timeline.create(this.getEl().dom.parentNode, bandInfos, Timeline.HORIZONTAL);
    },

    renderSeries : function(band, bandConfig)
    {
        var events = this.getStoreAsJson(bandConfig.store);
        if (events.length == 0)
            return;

        var i, s, series;

        for (s = 0; s < bandConfig.series.length; s++)
        {
            series = [];
            for (i = 0; i < events.length; i++)
            {
                var e = events[i];
                if (!e.title || !e.start || !e.value)
                    continue;
                if (e.title != bandConfig.series[s].id)
                    continue;
                series.push([new Date(e.start), e.value]);
            }
            if (series.length > 0)
                this.renderLineChart(band, series, bandConfig.series[s]);
        }
    },

    renderLineChart : function(band, series, seriesConfig)
    {
        var startDate = series[0][0];
        var endDate = series[series.length-1][0];

        var xStart = Math.round(band.dateToPixelOffset(startDate));
        var xEnd   = Math.round(band.dateToPixelOffset(endDate));
        var width  = Math.max(xEnd - xStart,10);
        var height = band.getViewWidth()-4;

        if (!band.spklLayer)
        {
            band.spklLayer = band.createLayerDiv(100);
        }
        if (!band.spklDiv)
        {
            div = LABKEY.createElement("div",null,{});
            Ext.apply(div.style, {
                position : 'absolute',
                top      : '5px',
                left     : xStart + 'px',
                width    : width,
                height   : height
            });
            band.spklDiv = div;
            band.spklLayer.appendChild(div);

            band.addOnScrollListener(function(band)
            {
                var xStart = Math.round(band.dateToPixelOffset(startDate));
                band.spklDiv.style.left = xStart + 'px';
            });
        }
        if (!band.labelDiv)
        {
            var labelDivStyle = 'font-weight:bold; position:absolute; left:0; top:0; z-index:1000;';
            band.labelDiv = LABKEY.createElement("div", '', {style:labelDivStyle});
            band.labelDiv.style.position='absolute';
            band.labelDiv.style.top = '5px';
            band.labelDiv.style.left = (xStart+xEnd)/2 + 'px';
            band.spklLayer.appendChild(band.labelDiv);

            band.addOnScrollListener(function(band)
            {
                var xStart = Math.round(band.dateToPixelOffset(startDate));
                var xEnd   = Math.round(band.dateToPixelOffset(endDate));
                band.labelDiv.style.left = (xStart+xEnd)/2 + 'px';
            });
        }

        var chartRangeMax = seriesConfig.yRange || 1;
        for (var i=0 ; i<series.length ; i++)
        {
            var y = series[i][1];
            if (y > chartRangeMax)
                chartRangeMax = y;
        }

        var options = Ext.apply({}, seriesConfig);
        options.chartRangeMax = chartRangeMax;

        this._renderLineChartImpl(band.spklDiv, height, width, series, options);

        var label         = LABKEY.createElement("span", seriesConfig.caption||seriesConfig.id);
        label.style.color = seriesConfig.lineColor || "#000000";

        band.labelDiv.appendChild(label);
        band.labelDiv.appendChild(LABKEY.createElement("br"));
    },

    _renderLineChartImpl : function(el, h, w, data, options)
    {
        options.spotRadius = 0;
        options.composite = true;
        $(el).sparkline(data, Ext.apply({height:h, width:w, fillColor:false}, options));
    },

    loadJsonAsSeries : function()
    {
        this.seriesSource._events.maxValues = [];

        var parseDateTime = function(v) {return v?new Date(v):null;};

        var added = false;
        var array, i, j;

        for (j=0; j < this.bands.length; j++)
        {
            for (i=0; i < this.bands[j].store.getCount(); i++)
            {
                var rec  = this.bands[j].store.getAt(i);
                var date = parseDateTime(rec.data.start);

                if (date)
                {
                    //TODO : Examine how this works with seriesName -- they are all returning 'undefined'
                    array = [];
                    var evt = new Timeplot.DefaultEventSource.NumericEvent(date, array);
                    this.seriesSource._events.add(evt);
                    added = true;
                }
            }
        }

        if (added)
            this.seriesSource._fire('onAddMany', []);
    },

    getStoreAsJson : function(store)
    {
        var json = [];
        for (var i=0; i < store.getCount(); i++)
        {
            json.push(store.getAt(i).data);
        }
        return json;
    }
});

LABKEY.timeline.isLoaded = true;
