/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/********** Scales **********/

if(!LABKEY.vis.Scale){
	LABKEY.vis.Scale = {};
}

LABKEY.vis.Scale.Discrete = function(domain, range){
	// This is a discrete scale, used for categorical data (e.g. visits).
	return d3.scale.ordinal().domain(domain).rangeBands(range, 1);
};

LABKEY.vis.Scale.Continuous = function(trans, data, value, domain, range){
	// This is a continuous scale (e.g. dates, numbers).
	var scale = null;
    
	if(!domain){
		var max = d3.max(data, value);
		var min = d3.min(data, value);
		domain = [min, max];
	}
	
	if(trans == 'linear'){
		scale = d3.scale.linear().domain(domain).range(range);
	} else {
		domain[0] == 0 ? domain[0] = .00001 : domain[0] = domain[0];
		scale = d3.scale.log().domain(domain).range(range);
	}

	return scale;
};

LABKEY.vis.Scale.ColorDiscrete = function(){
	// Used for discrete color scales (color assigned to categorical data)
    return d3.scale.ordinal().range([ "#66C2A5", "#FC8D62", "#8DA0CB", "#E78AC3", "#A6D854", "#FFD92F", "#E5C494", "#B3B3B3"]);
};

LABKEY.vis.Scale.DarkColorDiscrete = function(){
	// Used for discrete color scales (color assigned to categorical data)
    return d3.scale.ordinal().range(["#378a70", "#f34704", "#4b67a6", "#d53597", "#72a124", "#c8a300", "#d19641", "#808080"]);
};

LABKEY.vis.Scale.Shape = function(){
    // Used in pointType geom.
    var circle = function(paper, x, y, r){ return paper.circle(x, y, r)};
    var square = function(paper, x, y, r){ return paper.rect(x-r, y-r, r*2, r*2)};
    var diamond = function(paper, x, y, r){r = (Math.sqrt(2*Math.pow(r*2, 2)))/2; return paper.path('M' + x + ' ' + (y+r) + ' L ' + (x+r) + ' ' + y + ' L ' + x + ' ' + (y-r) + ' L ' + (x-r) + ' ' + y + ' Z')};
    var triangle = function(paper, x, y, r){return paper.path('M ' + x + ' ' + (y + (r)) + ' L ' + (x + (r)) + ' ' + (y-(r)) + ' L ' + (x - (r)) + ' ' + (y - (r)) + ' Z')};
    var x = function(paper, x, y, r){ return paper.path('M' + (x-r) + ' ' + (y+r) + ' L '  + (x+r) + ' ' + (y-r) + 'M' + (x-r) + ' ' + (y-r) + ' L '  + (x+r) + ' ' + (y+r)).attr('stroke-width', 3)};
    
    return d3.scale.ordinal().range([circle, triangle, square, diamond, x]);
};