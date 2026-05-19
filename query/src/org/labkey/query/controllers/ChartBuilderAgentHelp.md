Vega-Lite Chart Spec Generation Instructions

We are looking to create Vega-Lite chart specs with this schema only: https://vega.github.io/schema/vega-lite/v6.json
All of the specs created should be of this same version and format as the examples provided.

Any specifics about Vega-Lite chart specs should use the documentation from https://vega.github.io/vega-lite/docs/.

Example of a Box plot spec config:
```
{
"$schema": "https://vega.github.io/schema/vega-lite/v6.json",
"description": "Box plot with overlayed jittered raw data points and mean marker.",
"data": {
"values": [...]
},
"width": 600,
"encoding": {
"x": {
"field": "category",
"type": "nominal",
"axis": {
"labelAngle": 0,
"labelExpr": "split(datum.value, ' ')"
}
},
"y": {"field": "score", "type": "quantitative"}
},
"layer": [
{
"mark": {
"type": "boxplot",
"size": 100,
"ticks": {"size": 75, "color": "black"},
"box": {"fill": "transparent", "stroke": "black"},
"rule": {"color": "black"},
"outliers": false
}
},
{
"mark": {"type": "circle", "opacity": 0.5, "color": "firebrick", "size": 50},
"transform": [
{"calculate": "random()", "as": "randomJitter"}
],
"encoding": {
"xOffset": {
"field": "randomJitter",
"type": "quantitative",
"scale": {"range": [50, 150]}
},
"tooltip": [
{"field": "score", "type": "quantitative"},
{"field": "category"}
]
}
}
]
}
```

Example Scatter plot spec config:
```
{
"$schema": "https://vega.github.io/schema/vega-lite/v6.json",
"title": "Scatter Plot: Tooltips Enabled",
"width": 600,
"height": 400,
"data": {
"values": [...]
},
"mark": {"type": "point", "filled": true, "size": 100},
"encoding": {
"x": {"field": "Horsepower", "type": "quantitative"},
"y": {"field": "Miles_per_Gallon", "type": "quantitative"},
"color": {"field": "Cylinders", "type": "ordinal"},
"shape": {"field": "Origin", "type": "nominal"},

// NEW: Tooltip encoding
"tooltip": [
{"field": "Name", "type": "nominal"},
{"field": "Horsepower", "type": "quantitative"},
{"field": "Miles_per_Gallon", "type": "quantitative", "title": "MPG"},
{"field": "Cylinders", "type": "ordinal"},
{"field": "Origin", "type": "nominal"}
]
}
}
```

Example Line chart spec config:
```
{
"$schema": "https://vega.github.io/schema/vega-lite/v6.json",
"title": "Stock Prices: Dashed Lines, Solid Legend Icons",
"width": 600,
"data": {
"values": [...]
},
// GLOBAL ENCODINGS
"encoding": {
"x": {"timeUnit": "year", "field": "date"},
"y": {"aggregate": "mean", "field": "price", "type": "quantitative", "title": "Price (Mean ± SD)"},
"color": {
"field": "symbol",
"type": "nominal",
"scale": {
"domain": [
"Microsoft Corporation (Enterprise Systems Division)",
"Amazon.com Inc. Global Logistics and Web Services",
"International Business Machines (Legacy Infrastructure)",
"Google LLC (Alphabet Class A Common Stock Search)"
],
"range": ["firebrick", "#1f77b4", "#ff7f0e", "#2ca02c"]
},
"legend": {
"title": null,
"orient": "right",
"symbolOpacity": 1,
"columns": 1,
"labelLimit": 0,
}
}
},
"layer": [
{
// LAYER 1: Error Bars
"mark": {"type": "errorbar", "extent": "stderr", "ticks": true}
},
{
// LAYER 2: Lines (Dashed)
"mark": "line",
"encoding": {
"strokeDash": {
"field": "symbol",
"type": "nominal",
"scale": {
"domain": [
"Microsoft Corporation (Enterprise Systems Division)",
"Amazon.com Inc. Global Logistics and Web Services",
"International Business Machines (Legacy Infrastructure)",
"Google LLC (Alphabet Class A Common Stock Search)"
],
// Microsoft = Dashed [4,4], Others = Solid [0,0]
"range": [[4, 4], [0, 0], [2, 2], [8, 4, 2, 4]]
},
"legend": null
}
}
},
{
// LAYER 3: Points
"mark": {"type": "point", "size": 100, "filled": true},
"encoding": {
"shape": {
"field": "symbol",
"type": "nominal",
"scale": {
"domain": [
"Microsoft Corporation (Enterprise Systems Division)",
"Amazon.com Inc. Global Logistics and Web Services",
"International Business Machines (Legacy Infrastructure)",
"Google LLC (Alphabet Class A Common Stock Search)"
],
"range": ["diamond", "circle", "square", "triangle"]
},
"legend": {
"title": null,
"orient": "right",
"columns": 1,
"labelLimit": 0,
}
}
}
}
],
// This forces the 'strokeDash' legend to be processed separately.
"resolve": {
"legend": {
"strokeDash": "independent"
}
}
}
```

Example Bar chart spec config:
```
{
"$schema": "https://vega.github.io/schema/vega-lite/v6.json",
"title": "Bar Chart: Error Bars Behind Main Bars",
"width": 600,
"data": {
"values": [...]
},
"encoding": {
"x": {
"field": "a",
"type": "nominal",
"axis": {
"labelAngle": 0,
"labelExpr": "split(datum.value, ' ')"
}
}
},
"layer": [
{
// LAYER 1 (Bottom): The Error Bar
// Drawn first, so it sits behind the main bar.
"mark": {
"type": "errorbar",
"extent": "stderr",
"ticks": {
"width": 25
}
},
"encoding": {
"y": {
"field": "b",
"type": "quantitative"
// No title needed here, the top layer handles it
}
}
},
{
// LAYER 2 (Top): The Main Bar (Mean)
// Drawn second, covering the lower half of the error bar.
"mark": {
"type": "bar",
"color": "maroon"
},
"encoding": {
"y": {
"aggregate": "mean",
"field": "b",
"type": "quantitative",
"title": "Value (Mean ± SD)"
}
}
}
]
}
```

Example Pie chart spec config:
```
{
"$schema": "https://vega.github.io/schema/vega-lite/v6.json",
"title": "Pie Chart: Hiding Overlapping Labels",
"width": 600,
"height": 600,
"data": {
"values": [...]
},
"transform": [
{
"joinaggregate": [{"op": "sum", "field": "value", "as": "TotalValue"}]
},
{
"calculate": "datum.value / datum.TotalValue", "as": "Percentage"
}
],
"encoding": {
"theta": {"field": "value", "type": "quantitative", "stack": true},
"order": {"field": "value", "sort": "descending"},
// Tooltips are essential here so users can see the values for the tiny slices we hid
"tooltip": [
{"field": "category", "title": "Category"},
{"field": "value", "title": "Count"},
{"field": "Percentage", "format": ".1%", "title": "Percent"}
]
},
"layer": [
{
"mark": {"type": "arc", "outerRadius": 200},
"encoding": {
"color": {
"field": "category",
"type": "nominal",
"legend": {"title": "Categories", "orient": "right", "labelLimit": 0}
}
}
},
{
"mark": {"type": "text", "radius": 140, "fill": "black"},
"encoding": {
"text": {
"condition": {
// FIX: If percentage is less than 5% (0.05), show an empty string
"test": "datum.Percentage < 0.05",
"value": ""
},
// Otherwise, show the percentage
"field": "Percentage",
"type": "quantitative",
"format": ".1%"
}
}
}
]
}
```
    