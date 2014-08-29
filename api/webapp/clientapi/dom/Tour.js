/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if (!('help' in LABKEY))
    LABKEY.help = {};

LABKEY.help.Tour =
{
    _tours : {},

    register : function(config)
    {
        this._tours[config.id] = config;
    },

    show : function(idOrConfig)
    {
        var config = idOrConfig;
        if (typeof idOrConfig == "string")
            config = this._tours[idOrConfig];

        var hopscotchSrc = "/hopscotch/js/hopscotch.js";
        LABKEY.requiresScript(hopscotchSrc, true, function(){
            hopscotch.startTour(config);
            this.markSeen(config.id);
        }, this);
    },

    autoShow : function(idOrConfig)
    {
        var id = idOrConfig;
        var config = idOrConfig;
        if (typeof idOrConfig == "string")
            config = this._tours[idOrConfig];
        else
            id = config.id;

        if (!config || !id)
            return;

        if (this.seen(id))
            return;
        this.show(config);
    },

    seen : function(id)
    {
        // use one item for all tours, this is a little more complicated, but makes it easier to reset state
        var state = {};
        var v = localStorage.getItem("tours");
        if (v)
            state = LABKEY.Utils.decode(v);
        return "seen" == state[id];
    },

    markSeen : function(id)
    {
        var state = {};
        var v = localStorage.getItem("tours");
        if (v)
            state = LABKEY.Utils.decode(v);
        state[id] = "seen";
        localStorage.setItem("tours", LABKEY.Utils.encode(state));
    }
};