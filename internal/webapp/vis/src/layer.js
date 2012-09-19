/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.vis.Layer = function(config){

	/*
		The aes object contains all of the aesthetic maps used for the layer. E.g. your x axis is days, left axis is CD4 values.
		Current possible aesthetic mapping options: x, left, right, color, group, and hovertext.
		An aes object will looks something like this:
		{
			x: 'NAME_OF_X_VALUE', // A string of the value name can be passed
			left: function(row){
				// Or a function that shows you how to access the value in the row object.
				return row.NAME_OF_LEFT_AXIS_VALUE.value // This is going to be used for most Labkey stuff. Ex: row.study_LabResults_Hemoglobin.value
			},
			hovertext: function(row){
				return (row.subjectId + row.hemoglobin).toString(); // Returns a string for hovertext label.
			}
		}
	*/
	this.originalAes = config.aes ? config.aes : {};
    this.aes = LABKEY.vis.convertAes(this.originalAes);
	this.data = config.data ? config.data : null; // This is the data used on the layer. If not specified it will used the data from the base plot object.
	this.geom = config.geom ? config.geom : null; // This is the geom object used to render on the grid. It is currently required.
	this.stat = config.stat ? config.stat : null; // This is the stat object used to format the data, it is optional and not currently implemented.
	this.position = config.position ? config.position : null; // This is the position type used to control how overlapping is handled (e.g. jittering, stacking, dodging). It is not currently implemented.
    this.name = config.name ? ' ' + config.name : '';

    for(var aesthetic in this.aes){
        LABKEY.vis.createGetter(this.aes[aesthetic]);
    }

    this.hasData = function(){
        return this.data && this.data.length > 0;
    }

	this.render = function(paper, grid, scales, data, parentAes){
		// This function takes the data an renders it according the mappings, geoms, stats, and positions passed in.

		if(this.geom){
			// console.log("Rendering a layer!");
			this.geom.render(paper, grid, scales, this.data ? this.data : data, this.aes, parentAes, this.name);
		} else {
			// Without a geom the render function will not do anything.
			console.log("Unable to render this layer. No geom present");
		}
	}

	return this;
};