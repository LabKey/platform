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
"values": [
{"category": "A category with a longer label", "score": 10}, {"category": "A", "score": 20},
{"category": "A category with a longer label", "score": 40}, {"category": "A", "score": 50},
{"category": "A category with a longer label", "score": 55}, {"category": "A", "score": 100},
{"category": "B", "score": 15}, {"category": "B", "score": 25},
{"category": "B", "score": 35}, {"category": "B", "score": 60},
{"category": "B", "score": 75}, {"category": "B", "score": 90},
{"category": "A category with a longer label", "score": 10}, {"category": "A", "score": 20},
{"category": "A category with a longer label", "score": 40}, {"category": "A", "score": 50},
{"category": "A category with a longer label", "score": 55}, {"category": "A", "score": 100},
{"category": "B", "score": 15}, {"category": "B", "score": 25},
{"category": "B", "score": 35}, {"category": "B", "score": 60},
{"category": "B", "score": 75}, {"category": "B", "score": 90}
]
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
"values": [
{"Name": "chevrolet chevelle malibu", "Miles_per_Gallon": 18, "Cylinders": 8, "Horsepower": 130, "Origin": "USA"},
{"Name": "buick skylark 320", "Miles_per_Gallon": 15, "Cylinders": 8, "Horsepower": 165, "Origin": "USA"},
{"Name": "plymouth satellite", "Miles_per_Gallon": 18, "Cylinders": 8, "Horsepower": 150, "Origin": "USA"},
{"Name": "amc rebel sst", "Miles_per_Gallon": 16, "Cylinders": 8, "Horsepower": 150, "Origin": "USA"},
{"Name": "ford torino", "Miles_per_Gallon": 17, "Cylinders": 8, "Horsepower": 140, "Origin": "USA"},
{"Name": "pontiac catalina", "Miles_per_Gallon": 14, "Cylinders": 8, "Horsepower": 225, "Origin": "USA"},
{"Name": "chevrolet impala", "Miles_per_Gallon": 14, "Cylinders": 8, "Horsepower": 220, "Origin": "USA"},
{"Name": "plymouth fury iii", "Miles_per_Gallon": 14, "Cylinders": 8, "Horsepower": 215, "Origin": "USA"},
{"Name": "pontiac catalina", "Miles_per_Gallon": 14, "Cylinders": 8, "Horsepower": 225, "Origin": "USA"},
{"Name": "amc ambassador dpl", "Miles_per_Gallon": 15, "Cylinders": 8, "Horsepower": 190, "Origin": "USA"},
{"Name": "citroen ds-21 pallas", "Miles_per_Gallon": 0, "Cylinders": 4, "Horsepower": 115, "Origin": "Europe"},
{"Name": "toyota corolla mark ii", "Miles_per_Gallon": 24, "Cylinders": 4, "Horsepower": 95, "Origin": "Japan"},
{"Name": "datsun pl510", "Miles_per_Gallon": 27, "Cylinders": 4, "Horsepower": 88, "Origin": "Japan"},
{"Name": "volkswagen 1131 deluxe sedan", "Miles_per_Gallon": 26, "Cylinders": 4, "Horsepower": 46, "Origin": "Europe"},
{"Name": "peugeot 504", "Miles_per_Gallon": 25, "Cylinders": 4, "Horsepower": 87, "Origin": "Europe"},
{"Name": "audi 100 ls", "Miles_per_Gallon": 24, "Cylinders": 4, "Horsepower": 90, "Origin": "Europe"},
{"Name": "saab 99e", "Miles_per_Gallon": 25, "Cylinders": 4, "Horsepower": 95, "Origin": "Europe"},
{"Name": "bmw 2002", "Miles_per_Gallon": 26, "Cylinders": 4, "Horsepower": 113, "Origin": "Europe"},
{"Name": "honda civic", "Miles_per_Gallon": 24, "Cylinders": 4, "Horsepower": 60, "Origin": "Japan"},
{"Name": "datsun 1200", "Miles_per_Gallon": 35, "Cylinders": 4, "Horsepower": 69, "Origin": "Japan"}
]
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
"values": [
{"date": "2000-01-01", "price": 20, "symbol": "Microsoft Corporation (Enterprise Systems Division)"},
{"date": "1999-01-01", "price": 20, "symbol": "Microsoft Corporation (Enterprise Systems Division)"},
{"date": "2001-01-01", "price": 40, "symbol": "Microsoft Corporation (Enterprise Systems Division)"},
{"date": "2002-01-01", "price": 45, "symbol": "Microsoft Corporation (Enterprise Systems Division)"},
{"date": "2003-01-01", "price": 35, "symbol": "Microsoft Corporation (Enterprise Systems Division)"},
{"date": "2000-01-01", "price": 53, "symbol": "Microsoft Corporation (Enterprise Systems Division)"},
{"date": "2000-01-01", "price": 253, "symbol": "Microsoft Corporation (Enterprise Systems Division)"},
{"date": "2000-01-01", "price": 153, "symbol": "Microsoft Corporation (Enterprise Systems Division)"},
{"date": "2000-01-01", "price": 53, "symbol": "Microsoft Corporation (Enterprise Systems Division)"},
{"date": "2001-01-01", "price": 41, "symbol": "Microsoft Corporation (Enterprise Systems Division)"},
{"date": "2002-01-01", "price": 41, "symbol": "Microsoft Corporation (Enterprise Systems Division)"},
{"date": "2003-01-01", "price": 41, "symbol": "Microsoft Corporation (Enterprise Systems Division)"},

     {"date": "2000-01-01", "price": 100, "symbol": "Amazon.com Inc. Global Logistics and Web Services"},
     {"date": "2001-01-01", "price": 80, "symbol": "Amazon.com Inc. Global Logistics and Web Services"},
     {"date": "2002-01-01", "price": 90, "symbol": "Amazon.com Inc. Global Logistics and Web Services"},
     {"date": "2003-01-01", "price": 120, "symbol": "Amazon.com Inc. Global Logistics and Web Services"},


     {"date": "2000-01-01", "price": 10, "symbol": "International Business Machines (Legacy Infrastructure)"},
     {"date": "2001-01-01", "price": 12, "symbol": "International Business Machines (Legacy Infrastructure)"},
     {"date": "2002-01-01", "price": 15, "symbol": "International Business Machines (Legacy Infrastructure)"},
     {"date": "2003-01-01", "price": 14, "symbol": "International Business Machines (Legacy Infrastructure)"},


     {"date": "2000-01-01", "price": 50, "symbol": "Google LLC (Alphabet Class A Common Stock Search)"},
     {"date": "2001-01-01", "price": 60, "symbol": "Google LLC (Alphabet Class A Common Stock Search)"},
     {"date": "2002-01-01", "price": 120, "symbol": "Google LLC (Alphabet Class A Common Stock Search)"},
     {"date": "2003-01-01", "price": 160, "symbol": "Google LLC (Alphabet Class A Common Stock Search)"}
]
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
"values": [
{"a": "Category Alpha Long Label", "b": 20},
{"a": "Category Alpha Long Label", "b": 30},
{"a": "Category Alpha Long Label", "b": 34},


     {"a": "Category Beta Extended Text", "b": 50},
     {"a": "Category Beta Extended Text", "b": 60},
     {"a": "Category Beta Extended Text", "b": 55},


     {"a": "Category Gamma Detailed Desc", "b": 40},
     {"a": "Category Gamma Detailed Desc", "b": 46},
     {"a": "Category Gamma Detailed Desc", "b": 43},


     {"a": "Category Delta More Words", "b": 91},
     {"a": "Category Delta More Words", "b": 81},
    
     {"a": "Category Epsilon Maximum Length", "b": 81},
     {"a": "Category Zeta Simple", "b": 53},
     {"a": "Category Eta Tiny", "b": 19},
     {"a": "Category Theta Another One", "b": 87},
     {"a": "Category Iota Final Test", "b": 52}
]
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
"values": [
{"category": "Category Alpha Long Label", "value": 40},
{"category": "Category Beta Extended Text", "value": 60},
{"category": "Category Gamma Detailed Desc", "value": 100},
{"category": "Category Delta More Words", "value": 30},
{"category": "Category Epsilon Maximum Length", "value": 2}, // Tiny Slice
{"category": "Category Zeta Simple", "value": 1}   // Tiny Slice
]
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
    