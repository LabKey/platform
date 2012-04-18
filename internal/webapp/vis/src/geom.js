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

/********** Geoms **********/

if(!LABKEY.vis.Geom){
	LABKEY.vis.Geom = {};
}

LABKEY.vis.Geom.Point = function(){
	// This is a geom that is used to plot points on a grid.
	this.type = "Point";
	
	this.render = function(paper, grid, data, layerAes, parentAes){
		var yMap = null;
		var xMap = null;
		var colorMap = null;

		if(!layerAes.x && !parentAes.x){
			console.error('x aesthetic required for point geom to render.')
			return;
		}

		xMap = layerAes.x ? layerAes.x : parentAes.x;

		if(!layerAes.left && !layerAes.right && !parentAes.left && !parentAes.right){
			console.error('left or right aesthetic required for point geom to render.')
			return;
		}

		if(layerAes.left){
			yMap = layerAes.left;
		} else if(layerAes.right){
			yMap = layerAes.right;
		} else if(parentAes.left){
			yMap = parentAes.left;
		} else if(parentAes.right){
			yMap = parentAes.right;
		}
		
		if(layerAes.color){
			colorMap = layerAes.color;
		} else if(parentAes.color){
			colorMap = parentAes.color;
		}

		for(var i = 0; i < data.length; i++){
			var x = null;
			var y = null;
			var color = null;

			if(typeof xMap.value === "function"){
				x = xMap.scale(xMap.value(data[i]));
			} else {
				x = xMap.scale(data[i][xMap.value]);
			}

			if(typeof yMap.value === "function"){
				y = yMap.scale(yMap.value(data[i]));
			} else {
				y = yMap.scale(data[i][yMap.value]);
			}
			
			if(colorMap){
				if(typeof colorMap.value === 'function'){
					color = colorMap.scale(colorMap.value(data[i]));
				} else {
					color = colorMap.scale(data[i][colorMap.value]);
				}
			}

			if(!color){
				color = function(){return "#000"};
			}

			var circle = paper.circle(x, -y, 5).attr('fill', color).attr('stroke', color).transform("t0," + grid.height);
			
			if(layerAes.hoverText){
				console.log(layerAes.hoverText.value(data[i]));
				circle.attr('title', layerAes.hoverText.value(data[i]));
			}
		}
	}

	return this;
};

LABKEY.vis.Geom.Path = function(){
	// This is a geom that is used to plot a path on a grid.
	this.type = "Path";

	this.render = function(paper, grid, data, layerAes, parentAes){
		var yMap = null;
		var xMap = null;
		var colorMap = null;
		var group = layerAes.group ? layerAes.group : parentAes.group;

		if(!layerAes.x && !parentAes.x){
			console.error('x aesthetic required for point geom to render.')
			return;
		}

		xMap = layerAes.x ? layerAes.x : parentAes.x;

		if(!layerAes.left && !layerAes.right && !parentAes.left && !parentAes.right){
			console.error('left or right aesthetic required for point geom to render.')
			return;
		}

		if(layerAes.left){
			yMap = layerAes.left;
		} else if(layerAes.right){
			yMap = layerAes.right;
		} else if(parentAes.left){
			yMap = parentAes.left;
		} else if(parentAes.right){
			yMap = parentAes.right;
		}

		if(layerAes.color){
			colorMap = layerAes.color;
		} else if(parentAes.color){
			colorMap = parentAes.color;
		}


		var line = d3.svg.line().x(function(d){
			if(typeof xMap.value === 'function'){
				return xMap.scale(xMap.value(d));
			} else {
				return d[xMap.value];
			}

		}).y(function(d){
			if(typeof yMap.value === 'function'){
				return -yMap.scale(yMap.value(d));
			} else {
				return -yMap.scale(d[yMap.value]);
			}
		});

		if(group){
			//Split data into groups so we can render 1 path per group.
			var groups = {};
			for(var i = 0; i < data.length; i++){
				// Split the data into groups.
				var value = null;
				if(typeof group.value === 'function'){
					value = group.value(data[i]);
				} else {
					value = data[i][group.value];
				}

				if(!groups[value]){
					groups[value] = [];
				}
				groups[value].push(data[i]);

			}

			for(groupTitle in groups){
				var color = "#000";
				if(colorMap){
					color = colorMap.scale(groupTitle);
				}
				groupData = groups[groupTitle];
				paper.path(line(groupData)).attr('stroke', color).transform("t0," + grid.height);
			}

		} else {
			// No groups, connect all the points.
			paper.path(line(data)).attr('shape-rendering', 'crispEdges').transform("t0," + grid.height);
		}
	}

	return this;
};

LABKEY.vis.Geom.ErrorBar = function(){
	this.type = "ErrorBar";

	this.render = function(paper, grid, data, layerAes, parentAes){

	}

	return this;
}

LABKEY.vis.Geom.Text = function(){
	// This geom is used to render text at points on the grid.
	this.type = "Text";

	return this;
}
