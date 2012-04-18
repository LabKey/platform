/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if(!LABKEY){
	var LABKEY = {};
}

if(!LABKEY.vis){
	LABKEY.vis = {};
}

/********** Scales **********/

if(!LABKEY.vis.Scale){
	LABKEY.vis.Scale = {};
}

LABKEY.vis.Scale.Discrete = function(config){
	// This is a discrete scale, used for categorical data (e.g. visits).
	this.type = "Discrete";
	return this;
};

LABKEY.vis.Scale.Continuous = function(trans, data, value, domain, range){
	// This is a continuous scale (e.g. dates, numbers).
	var scale = null;
	if(!domain){
		var max = d3.max(data, value);
		var min = d3.min(data, value);
		domain = [min, max]
	}
	
	if(trans == 'linear'){
		scale = d3.scale.linear().domain(domain).range(range);
	} else {
		domain[0] == 0 ? domain[0] = .00001 : domain[0] = domain[0];
		scale = d3.scale.log().domain(domain).range(range);
	}

	return scale;
};

LABKEY.vis.Scale.ColorContinuous = function(config){
	// This is a scale used for continuous color scales. (e.g. a  heatmap)

	return this;
};

LABKEY.vis.Scale.ColorDiscrete = function(){
	// Used for discrete color scales (color assigned to categorical data)
	return d3.scale.category10();
};


LABKEY.vis.Scale.PointType = function(paper, x, y, r){
	var triangle = paper.path('M ' + x + ' ' + (y + (r)) + ' L ' + (x + (r)) + ' ' + (y-(r/2)) + ' L ' + (x - (r)) + ' ' + (y - (r/2)) + ' Z');
	// var square = paper.path('M' + (x + (r/2)) + ' '  + (y + (r / 2)) + 'L' + () + () + );
	var circle = paper.circle(x, y, r);
}