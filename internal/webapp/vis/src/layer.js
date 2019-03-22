/*
 * Copyright (c) 2012-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * LABKEY.vis.Layer objects are used to define layers in a plot. The user should not worry about any methods on this
 * object, all methods are used internally by the {@link LABKEY.vis.Plot} object.
 *
 * @class LABKEY.vis.Layer class. Used to define layers used in {@link LABKEY.vis.Plot} objects.
 * @param config An object with the following properties.
 * @param {Object} [config.aes] (Optional) The aesthetic map object used to define aesthetic mappings such as x, y, color
 *      etc. If not present then it defaults to the aes object on the LABKEY.vis.Plot object. For an in depth description
 *      of the config.aes object please see the documentation for {@link LABKEY.vis.Plot}.
 * @param {Array} [config.data] The array of data to use when rendering this layer. If not present then it defaults to
 *      the data array on the LABKEY.vis.Plot object.
 * @param {Object} config.geom A geom object to use when rendering this layer. See {@link LABKEY.vis.Geom}.
 * @param {String} [config.name] (Optional) Name of the layer. You can give layers the same name or a different name. It
 *      Is primarily used if you have more than one layer that is using a color or shape scale and each layer has an
 *      overlap of values, but you want to differentiate each color or shape depending on the layer and aesthetic value.
 *      For example if you have a plot with two layers, each layer has a Path Geom on it, one layer could be used to
 *      plot weight over time, the other could be used to plot blood pressure over time for the same participant. If you
 *      want the lines to be different colors you could name each layer differently (i.e. Weight and Blood Pressure).

 */
LABKEY.vis.Layer = function(config){
	this.originalAes = config.aes ? config.aes : {};
    this.aes = LABKEY.vis.convertAes(this.originalAes);
	this.data = config.data ? config.data : null; // This is the data used on the layer. If not specified it will used the data from the base plot object.
	this.geom = config.geom ? config.geom : null; // This is the geom object used to render on the grid. It is currently required.
    this.name = config.name ? ' ' + config.name : '';

    for(var aesthetic in this.aes){
        LABKEY.vis.createGetter(this.aes[aesthetic]);
    }
    
    this.hasData = function(){
        return this.data && this.data.length > 0;
    };

	this.render = function(renderer, grid, scales, data, parentAes, index){
		// This function takes the data an renders it according the mappings, geoms, stats, and positions passed in.

		if(this.geom){
			this.geom.render(renderer, grid, scales, this.data ? this.data : data, this.aes, parentAes, this.name, index);
		} else {
			// Without a geom the render function will not do anything.
			console.log("Unable to render this layer. No geom present");
		}
	};

    this.setAes = function(aes){
        LABKEY.vis.mergeAes(this.aes, aes);
        if (this.plot) {
            this.plot.render();
        }
    };

	return this;
};