/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2015-2017 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */


/**
 * MeasureStore is a very thin wrapper around crossfilter().  The added functionality consists of
 *
 * a) handling grouping on multiple columns
 * b) rich aggregation such as MEDIAN, COUNTDISTINCT
 * c) can start with unaggregated or pre-aggregated records
 * d) LABKEY loaders for selectRows(), executeSql(), getData(), selectOlap() etc.
 * e) series() wrapper over group, to "pivot" or "shard" your data into series
 *
 * TODO caseInsensitive option
 * TODO natural ordering for dates,numbers
 *
 * CONSIDER should I be caching group objects more aggressively? or letting the caller hang on to them?
 *
 * NOTE: not IE8 compatible (but neither is crossfilter.js, might require es5-shim.js?
 */


(function($, crossfilter)
{
    var CONCAT_STRING = '|\uFFFF|';

    var sortValues = function(values)
    {
        if (1 < values.length)
            crossfilter.quicksort(values, 0, values.length);
    };

    var CountStarAggregator = function() {
        this.values = [];
    };

    CountStarAggregator.prototype = {
        count: 0,

        valueOf : function()
        {
            return this.count;
        },

        getValues : function()
        {
            return this.values;
        },

        getCount : function()
        {
            return this.count;
        },

        addTo : function(value)
        {
            this.count++;
            this.values.push(value);
            sortValues(this.values);
        },

        removeFrom : function(value)
        {
            this.count--;
            if (null == value)
                return;
            var b = crossfilter.bisect.right(this.values, value, 0, this.values.length);
            this.values.splice(b, 1);
        },

        supports : function()
        {
            return ["COUNT"];
        }
    };

    var UniqueValueAggregator = function()
    {
        this.valueHasBeenSet = false;
        this.isUnique = true;
        this.value = null;
        this.values = [];
    };

    UniqueValueAggregator.prototype = {

        valueOf : function()
        {
            return this.getValue();
        },

        getValue : function()
        {
            return this.isUnique && this.valueHasBeenSet ? this.value : undefined;
        },

        getValues : function()
        {
            return this.values.filter(function(value, index, self) {
                return self.indexOf(value) === index;
            });
        },

        addTo : function(value, record)
        {
            if (value != undefined && value != null)
            {
                this.values.push(value);
                sortValues(this.values);
            }

            if (!this.isUnique)
                return this;
            if (null == value)
                return this;
            if (!this.valueHasBeenSet)
            {
                this.value = value;
                this.valueHasBeenSet = true;
                return this;
            }
            if (this.value === value)
                return this;
            if (typeof this.value === "string" && typeof value === "string" && this.value.toUpperCase() === value.toUpperCase())
                return this;
            // not equals
            this.isUnique = false;
            this.value = undefined;
            return this;
        },

        removeFrom : function(value, record)
        {
            if (null == value)
                return;
            var b = crossfilter.bisect.right(this.values, value, 0, this.values.length);
            this.values.splice(b, 1);
        },

        supports : function()
        {
            return ["VALUE"];
        }
    };


    /*
     * collect all the values for the measure, compute
     * mean,sum,etc lazily
     */
    var CollectNonNullValuesAggregator = function()
    {
        this.values = [];
    };

    CollectNonNullValuesAggregator.prototype = {

        values: null,
        _isSorted: true,
        sum: null,

        valueOf : function()
        {
            return this.getMean();
        },

        getValues : function()
        {
            return this.values;
        },

        getCount : function()
        {
            return this.values.length;
        },

        getSum : function()
        {
            if (null == this.sum && 0 < this.values.length)
            {
                var sum = 0.0;
                var values = this.values;
                for (var i = 0; i < values.length; i++)
                    sum += values[i];
                this.sum = sum;
            }
            return this.sum;
        },

        getMean : function()
        {
            var count = this.getCount();
            if (0 == count)
                return null;
            var sum = this.getSum();
            return sum / count;
        },

        getVariance : function()
        {
            var N = this.getCount();
            if (N < 2)
                return null;
            var mean = this.getMean();
            var sumSquareOfDifference = 0;
            var values = this.values;
            for (var i = 0; i < values.length; i++)
            {
                var d = values[i] - mean;
                sumSquareOfDifference += d * d;
            }
            return sumSquareOfDifference / (N - 1);
        },

        getStdDev : function()
        {
            var var_ = this.getVariance();
            if (null === var_)
                return null;
            return Math.sqrt(var_);
        },

        getStdErr : function()
        {
            var stddev = this.getStdDev();
            if (null == stddev)
                return null;
            var N = this.getCount();
            return stddev / Math.sqrt(N);
        },

        getMedian : function()
        {
            var values = this.values;
            var length = values.length;
            if (0 == length)
                return null;
            else if (1 == length % 2)
                return values[(length - 1) / 2];
            else
                return (values[length / 2 - 1] + values[length / 2]) / 2.0;
        },

        getMax : function()
        {
            var values = this.values;
            var length = values.length;
            if (0 == length)
                return null;
            var max = values[0];
            for (var i = 1; i < length; i++)
                if (max < values[i])
                    max = values[i];
            return max;
        },

        getMin : function()
        {
            var values = this.values;
            var length = values.length;
            if (0 == length)
                return null;
            var max = values[0];
            for (var i = 1; i < length; i++)
                if (max > values[i])
                    max = values[i];
            return max;
        },

        getCountDistinct : function()
        {
            var values = this.values;
            var length = values.length;
            if (length <= 1)
                return length;
            var count = 1;
            var v = values[0];
            for (var i = 1; i < length; i++)
            {
                if (v < values[i])
                {
                    count++;
                    v = values[i];
                }
            }
            return count;
        },

        addTo : function(value, record)
        {
            if (null == value)
                return;
            this.values.push(value);
            sortValues(this.values);
            return this;
        },

        removeFrom : function(value, record)
        {
            if (null == value)
                return;
            var b = crossfilter.bisect.right(this.values, value, 0, this.values.length);
            if (this.values[b] != value)
                throw "IllegalState";
            this.values.splice(b, 1);
            this.sum = null;
            return this;
        },

        supports : function()
        {
            if (this.values.length > 0 && typeof this.values[0] != "number")
            {
                return ["COUNT", "MIN", "MAX"];
            }
            else
            {
                return ["COUNT", "SUM", "MEAN", "MEDIAN", "MIN", "MAX", "VAR", "STDDEV", "STDERR"];
            }

        }
    };


    // config.countColumn
    // config.sumColumn
    // config.sumOfSquaresColumn
    var CollectPreAggregatedValues = function(config)
    {
        this.countColumn = config.countColumn;
        this.sumColumn = config.sumColumn;
        this.sumOfSquaresColumn = config.sumOfSquaresColumn;
        this.minColumn = config.minColumn;
        this.maxColumn = config.maxColumn;
        this.count = 0;
        this.sum = 0;
        this.sumOfSquares = 0;
        this.min = null;
        this.max = null;
        this.values = [];
    };

    CollectPreAggregatedValues.prototype = {

        getValues : function()
        {
            return this.values;
        },

        addTo : function(value, record)
        {
            var v;
            if (this.countColumn && null !== (v = record[this.countColumn]))
                this.count += v;
            if (this.sumColumn && null !== (v = record[this.sumColumn]))
                this.sum += v;
            if (this.sumOfSquaresColumn && null !== (v = record[this.sumOfSquaresColumn]))
                this.sumOfSquares += v;
            if (this.minColumn && null !== (v = record[this.minColumn]))
            {
                if (this.min === null || v < this.min)
                    this.min = v;
            }
            if (this.maxColumn && null !== (v = record[this.maxColumn]))
            {
                if (this.max === null || v < this.max)
                    this.max = v;
            }
            this.values.push(value);
            sortValues(this.values);
            return this;
        },

        removeFrom : function(value, record)
        {
            var v;
            if (this.countColumn && null !== (v = record[this.countColumn]))
                this.count -= v;
            if (this.sumColumn && null !== (v = record[this.sumColumn]))
                this.sum -= v;
            if (this.sumOfSquaresColumn && null !== (v = record[this.sumOfSquaresColumn]))
                this.sumOfSquares -= v;

            if (null != value) {
                var b = crossfilter.bisect.right(this.values, value, 0, this.values.length);
                this.values.splice(b, 1);
            }
            return this;
        },

        getCount : function()
        {
            if (!this.countColumn)
                return null;
            return this.count;
        },

        getSum : function()
        {
            if (!this.sumColumn)
                return null;
            return this.sum;
        },

        getMean : function()
        {
            if (!this.countColumn || !this.sumColumn)
                return null;
            return this.count == 0 ? null : this.sum / this.count;
        },

        getVariance : function()
        {
            if (!this.countColumn || !this.sumColumn || !this.sumOfSquaresColumn)
                return null;
            if (this.count < 2)
                return null;
            // there may be better incremental ways of computing StdDev()
            // https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance
            var N = this.count;
            var s1 = this.sum;
            var s2 = this.sumOfSquares;
            return (N * s2 - s1 * s1) / (N * (N - 1));
        },

        getStdDev : function()
        {
            var var_ = this.getVariance();
            if (null === var_)
                return null;
            return Math.sqrt(var_);
        },

        getStdErr : function()
        {
            var stddev = this.getStdDev();
            if (null == stddev)
                return null;
            var N = this.getCount();
            return stddev / Math.sqrt(N);
        },

        supports : function()
        {
            var ret = [];
            if (this.countColumn)
                ret.push("COUNT");
            if (this.sumColumn)
                ret.push("SUM");
            if (this.countColumn && this.sumColumn)
            {
                ret.push("MEAN");

                if (this.sumOfSquaresColumn)
                {
                    ret.push("STDDEV");
                    ret.push("VAR");
                    ret.push("STDERR");
                }
            }
            if (this.minColumn)
                ret.push("MIN");
            if (this.maxColumn)
                ret.push("MAX");
            return ret;
        }
    };


    LABKEY.Query.MeasureStore = new (function()
    {
        function reduceInit(columns)
        {
            var accumulators = new Array(columns.length);
            for (var i = 0; i < columns.length; i++)
            {
                var column = columns[i];
                if (column && column.aggregator)
                {
                    accumulators[i] = new column.aggregator(column);
                    accumulators[i]._columnIndex = i;
                    accumulators[i]._columnName = column.name;
                }
            }
            return accumulators;
        }

        function reduceAdd(accumulators, row)
        {
            for (var i = 0; i < accumulators.length; i++)
            {
                var accum = accumulators[i];
                var v = row[accum._columnName];  // this might be an object depending on the response type
                if (null != v && typeof v === "object" && 'value' in v)
                    v = v.value;
                accum.addTo(v, row);
            }
            return accumulators;
        }

        function reduceAddSimpleValue(accumulators, row)
        {
            for (var i = 0; i < accumulators.length; i++)
            {
                var accum = accumulators[i];
                var v = row[accum._columnName];
                accum.addTo(v, row);
            }
            return accumulators;
        }

        function reduceAddObjectValue(accumulators, row)
        {
            for (var i = 0; i < accumulators.length; i++)
            {
                var accum = accumulators[i];
                var v = row[accum._columnName];
                if (null != v)
                    v = v.value;
                accum.addTo(v, row);
            }
            return accumulators;
        }

        function reduceRemove(accumulators, row)
        {
            for (var i = 0; i < accumulators.length; i++)
            {
                var accum = accumulators[i];
                var v = row[accum._columnName];
                if (null != v && typeof v === "object" && 'value' in v)
                    v = v.value;
                accum.removeFrom(v, row);
            }
            return accumulators;
        }

        /**
         * TODO richer metadata
         * dimensions should collect more info like conceptURI, etc
         *    {name:"Visit", columnName:'ParticipantVisit/Visit/Label', conceptUri:"cpas#visit"}
         * measure should collect more info like countColumnName, etc.
         *    {name:"CellCount",columnName:'study_ICS_CellCount'}
         * or
         *    {name:"CellCount", countColumn:'study_ICS_CellCount_COUNT', sumColumn:'study_ICS_CellCount_SUM'}
         *
         *    config.dimensions is optional
         *    config.measures must specify the columns that are to be aggregated and plotted
         *    config.records should be a an array of objects, both of selectRows formats are supported
         */
        var MeasureStore = function(config)
        {
            var name, rec;
            var columnNames = config.columns || [];
            var countStar = {
                name: '*',
                index: 0,
                aggregator: CountStarAggregator
            };

            this._dimensions = {};
            this._records = config.records || [];
            for (var r=0 ; r<this._records.length ; r++)
                this._records[r]["__rownumber__"] = r;
            this._columns = [countStar];
            this._responseMetadata = config.responseMetadata;
            this._columnMap = {
                '*': countStar
            };

            if (!config.columns && 0 < config.records.length)
            {
                rec = config.records[0];
                for (name in rec)
                {
                    if (rec.hasOwnProperty(name))
                        columnNames.push(name);
                }
            }

            // infer record format
            var recordType = "SIMPLE";
            if (0 < config.records.length)
            {
                rec = config.records[0];
                for (name in rec)
                {
                    if (!rec.hasOwnProperty(name))
                        continue;
                    if ((typeof rec[name]) == "object")
                        recordType = "OBJECT";
                }
            }
            this._recordType = recordType;

            // consider using some sort of NamedList implementation
            var me = this;
            columnNames.forEach(function(name, index) {
                var col = {
                    name: name,
                    index: index + 1,
                    aggregator: UniqueValueAggregator
                };
                me._columnMap[name] = col.index;
                me._columns[col.index] = col;
            });

            this._measures = (config.measures || []).map(function(m) {
                if (LABKEY.Utils.isString(m))
                    m = {name: m};
                return m;
            });
            this._measures.forEach(function(m) {
                var index = me._columnMap[m.name];
                if (index)
                {
                    var column = me._columns[index];
                    // consider is apply(column,measure) better or column.measure = measure
                    $.extend(column, m);
                    if (m.countColumn || m.sumColumn)
                        me._columns[index].aggregator = CollectPreAggregatedValues;
                    else
                        me._columns[index].aggregator = CollectNonNullValuesAggregator;
                }
            });

            this._crossfilter = crossfilter(this._records);
            var dimensions = config.dimensions || [];
            for (var i = 0; i < dimensions.length; i++) {
                this.getDimension(dimensions[i]);
            }
        };

        MeasureStore.prototype =
        {
            _crossfilter: null,
            _dimensions: null,
            _measures: null,
            _records: null,
            _responseMetadata: null,    // schemaName, queryName, columnAliasMap, etc.
            _columnMap: null,           // column alias to index
            _columns: null,             // {name:foo, index:i, aggregator:constructor}
            // UNKNOWN: check at runtime
            // SIMPLE : {key:'abc', x:4}
            // OBJECT : {key:{value:'abc'}, x:{value:4}}
            // ARRAY : ['abc', 4] (not yet supported)
            _recordType : "UNKNOWN",

            records : function()
            {
                return this._records;
            },

            getResponseMetadata : function()
            {
                return this._responseMetadata;
            },

            getColumnMap : function()
            {
                return this._columnMap;
            },

            _group : function(dim, keyFn)
            {
                var fnInit = reduceInit.bind(null, this._columns);
                var fnAdd;
                if (this._recordType==="OBJECT")
                    fnAdd = reduceAddObjectValue;
                else if (this._recordType==="SIMPLE")
                    fnAdd = reduceAddSimpleValue;
                else
                    fnAdd = reduceAdd;
                var fnRemove = reduceRemove;

                var group;
                if (null == dim)
                    group = this._crossfilter.groupAll();
                else if (keyFn)
                    group = dim.group(keyFn);
                else
                    group = dim.group();
                return group.reduce(fnAdd, fnRemove, fnInit);
            },

            group : function(dimName, keyFn)
            {
                var dim = null == dimName ? null : this.getDimension(dimName);
                return this._group(dim, keyFn);
            },

            filter : function(dimName, range)
            {
                // issue 24008: use filterFunction if filtering by an array of values
                if ($.isArray(range)) {
                    return this.getDimension(dimName).filterFunction(function(dimVal) {
                        return range.indexOf(dimVal) > -1;
                    });
                }
                else {
                    return this.getDimension(dimName).filter(range);
                }
            },

            /**
             * Select the list of distinct member values in this dimension as an array
             */
            members : function(dimName)
            {
                var dim = this.getDimension(dimName),
                    group = this._group(dim),
                    ret;

                if (1 == dim._keys.length)
                {
                    ret = group.reduceCount().all().map(function(entry)
                    {
                        return entry.key;
                    });
                }
                else
                {
                    ret = group.reduceCount().all().map(function(entry)
                    {
                        return entry.key.split(CONCAT_STRING);
                    });
                }
                group.dispose();
                return ret;
            },

            filterAll : function(dimName)
            {
                return this.getDimension(dimName).filterAll();
            },

            getDimension : function(dimName)
            {
                var dimArray = [dimName],
                    firstRow = this._records.length ? this._records[0] : null,
                    getter;

                if (dimName === '*')
                    dimArray = ["__rownumber__"];

                if (LABKEY.Utils.isArray(dimName))
                {
                    dimArray = dimName;
                    dimName = dimName.join(CONCAT_STRING);
                }

                if (this._dimensions.hasOwnProperty(dimName))
                {
                    return this._dimensions[dimName];
                }

                // validate that we can find these names
                if (firstRow)
                {
                    dimArray.forEach(function(name)
                    {
                        if (name === undefined)
                        {
                            throw 'Column is undefined.';
                        }

                        if (!(name in firstRow))
                        {
                            throw "Column not found in data: " + name;
                        }
                    });
                }

                if (dimArray.length == 1)
                {
                    getter = (function(name, record)
                    {
                        var key = record[name];
                        if (null != key && typeof key === "object" && 'value' in key)
                            key = key.value;
                        return key;
                    }).bind(null, dimArray[0]);
                }
                else
                {
                    getter = (function(names, record)
                    {
                        var keys = [];
                        for (var i = 0; i < names.length; i++)
                        {
                            var key = record[names[i]];
                            if (null != key && typeof key === "object" && 'value' in key)
                                key = key.value;
                            keys.push(key);
                        }
                        return keys.join(CONCAT_STRING);
                    }).bind(null, dimArray);
                }

                var dim = this._crossfilter.dimension(getter);
                dim._name = dimName;
                dim._keys = dimArray;
                this._dimensions[dimName] = dim;
                return dim;
            },


            //
            // Data selecting methods
            //

            flattenGroupEntry : function(dim, entry)
            {
                var columns = this._columns;
                var r = {__key: entry.key};
                //var keyNames = dim._keys;
                //var keyValues = entry.key.split(CONCAT_STRING);
                //for (var i = 0; i < keyNames.length; i++)
                //    r[keyNames[i]] = i < keyValues.length ? keyValues[i] : null;
                for (var m = 0; m < columns.length; m++)
                    r[columns[m].name] = entry.value[m];
                return r;
            },

            /**
             * Returns one object per key in dimension dimName.
             */
            select : function(dimName)
            {
                var dim = this.getDimension(dimName),
                    group = this._group(dim),
                    entries = group.all(),
                    ret, me = this;

                ret = entries.map(function(entry)
                {
                    return me.flattenGroupEntry(dim, entry);
                });
                group.dispose();
                return ret;
            },

            selectArray : function(dimName, measureName, aggregate)
            {
                // find index of this measureName
                var index = -1;
                for (var m = 0; m < this._columns.length; m++)
                {
                    if (LABKEY.Utils.isDefined(measureName) && measureName === this._columns[m].name)
                    {
                        index = m;
                        break;
                    }
                }

                if (index === -1)
                    throw "Column name not found: " + measureName;

                var dim = this.getDimension(dimName);
                var group = this._group(dim);
                var ret = _array(this, group.all(), index, aggregate);
                group.dispose();
                return ret;
            },

            // CONSIDER: transpose by default?
            selectXYArray : function(dimName, measureXName, aggregateX, measureYName, aggregateY)
            {
                var x = -1, y = -1;
                for (var m = 0; m < this._columns.length; m++)
                {
                    if (measureXName === this._columns[m].name)
                        x = m;
                    if (measureYName === this._columns[m].name)
                        y = m;
                }
                if (x === -1)
                    throw "Column name not found: " + measureXName;
                if (y === -1)
                    throw "Column name not found: " + measureYName;

                var group = this.group(dimName);
                var entries = group.all();
                var xSeries = _array(this, entries, x, aggregateX);
                var ySeries = _array(this, entries, y, aggregateY);
                group.dispose();
                return [xSeries, ySeries];
            },

            selectSeries : function(rowDim, colDim)
            {
                if (!LABKEY.Utils.isDefined(rowDim) || !LABKEY.Utils.isDefined(colDim))
                {
                    console.error('MeasureStore.selectSeries(rowDim, colDim) requires row and column dimensions be specified.');
                    return;
                }

                rowDim = LABKEY.Utils.isArray(rowDim) ? rowDim : [rowDim];
                colDim = LABKEY.Utils.isArray(colDim) ? colDim : [colDim];

                var results = _selectSeries(this, rowDim, colDim),
                    dim = this.getDimension(rowDim.concat(colDim)),
                    me = this;

                return results.map(function(rowArray) {
                    return rowArray.map(function(entry) {
                        return me.flattenGroupEntry(dim, entry);
                    });
                });
            },

            selectSeriesArray : function(rowDim, colDim, measureName, aggregate)
            {
                rowDim = LABKEY.Utils.isArray(rowDim) ? rowDim : [rowDim];
                colDim = LABKEY.Utils.isArray(colDim) ? colDim : [colDim];
                var index = -1;
                for (var m = 0; m < this._columns.length; m++) {
                    if (measureName === this._columns[m].name)
                        index = m;
                }
                if (index === -1)
                    throw "Column name not found: " + measureName;

                var me = this;
                var results = _selectSeries(this, rowDim, colDim);
                return results.map(function(row) {
                    return _array(me, row, index, aggregate);
                });
            }
        };

        //
        // static methods
        //

        var aggregateFnMap = {
            VALUES: 'getValues',
            COUNT: 'getCount',
            SUM: 'getSum',
            MEAN: 'getMean',
            VAR: 'getVariance',
            STDDEV: 'getStdDev',
            STDERR: 'getStdErr',
            MIN: 'getMin',
            MAX: 'getMax'
        };

        /**
         * Returns an array with one element for each element in the dimension.
         * See MeasureStore.members()
         */
        function _array(measureStore, entries, memberIndex, aggregate)
        {
            var aggImpl = aggregateFnMap[aggregate];
            if (aggImpl) {
                return entries.map(function(entry)
                {
                    return null == entry ? null : entry.value[memberIndex][aggImpl]();
                });
            }

            throw 'Aggregate \"' + aggregate + '\" does not exist';
        }

        // LABKEY.Query.selectRows() wrapper
        function _apiWrapper(originalConfig, apiFn, apiScope, resultHandlerFn)
        {
            var apiConfig = {},
                onSuccess, onFailure, scope,
                measures, dimensions;

            $.each(originalConfig, function(p, value) {
                switch (p) {
                    case "measures":
                        measures = value;
                        apiConfig[p] = value;
                        break;
                    case "dimensions":
                        dimensions = value;
                        break;
                    case "success":
                        onSuccess = value;
                        break;
                    case "failure":
                        onFailure = value;
                        break;
                    case "scope":
                        scope = value;
                        break;
                    default:
                        apiConfig[p] = value;
                }
            });

            apiConfig.success = function(results) {
                var measureStore = resultHandlerFn(results, measures, dimensions);
                if ($.isFunction(onSuccess)) {
                    onSuccess.call(scope || this, measureStore, measures, dimensions);
                }
            };

            apiConfig.failure = function(response) {
                if (onFailure) {
                    onFailure.call(scope || this, response);
                }
            };

            apiFn.call(apiScope || this, apiConfig);
        }

        function _handleSelectRowsResponse(results, measures, dimensions)
        {
            if (null == measures)
            {
                measures = [];
                results.metaData.fields.forEach(function(field)
                {
                    if (field.measure)
                        measures.push(field.name || field.fieldKey.toString());
                });
            }

            var responseMetadata = $.extend
                (
                    {
                        schemaName: results.schemaName,
                        queryName: results.queryName
                    },
                    results.getMetaData()
                );

            // TODO: Remove this backwards compatibility once cds/fb_refinement is ready
            if (results.columnAliases !== undefined) {
                responseMetadata.columnAliases = results.columnAliases;
            }

            var ms = new MeasureStore({
                measures: measures,
                records: results.rows,
                responseMetadata: responseMetadata
            });

            // tack on field metadata to each column
            results.metaData.fields.forEach(function(field)
            {
                var name = field.fieldKey.toString();
                var index = ms._columnMap[name];
                if (index)
                    ms._columns[index].field = field;
            });

            return ms;
        }

        function _handleGetDataResponse(results, measures, dimensions)
        {
            var _measures = [];

            measures.forEach(function(m)
            {
                var _measure = m.measure,
                    name = ('alias' in _measure) ? _measure.alias : _generateAlias(_measure);

                if (_measure.isMeasure === true) {
                    _measures.push(name);
                }
            });

            var responseMetadata = {
                schemaName: results.schemaName,
                queryName: results.queryName
            };

            // TODO: Remove this backwards compatibility once cds/fb_refinement is ready
            if (results.columnAliasMap !== undefined) {
                responseMetadata.columnAliasMap = results.columnAliasMap;
            }
            else {
                responseMetadata.columnAliases = results.columnAliases;
            }

            // TODO add flag for when to add this and what the property name should be
            for (var i = 0; i < results.rows.length; i++)
            {
                results.rows[i]['_rowIndex'] = {value: i};
            }

            return new MeasureStore({
                measures: _measures,
                records: results.rows,
                responseMetadata: responseMetadata
            });
        }

        function _handleCellSetResponse(cellset, measuresConfig)
        {
            var measuresMap = {},
                measures = [],
                positions = cellset.axes[0].positions,
                posArray,
                measureConfig,
                rows, i, m;

            if (measuresConfig) {
                measuresConfig.forEach(function(m) { measuresMap[m.name] = m; });
            }

            // verify that all columns are measures
            for (i = 0; i < positions.length; i++)
            {
                posArray = positions[i];
                if (posArray.length != 1)
                    throw "MeasureStore does not support nesting on the ROWS axis";
                m = posArray[0];
                if (!/\[Measures]\.\[/g.test(m.uniqueName))
                    throw "MeasureStore expects measures on the ROWS axis";

                measureConfig = measuresMap[m.name];
                if (!measureConfig)
                {
                    measureConfig = {
                        name: m.name,
                        sumColumn: m.name
                    };
                }
                measures.push(measureConfig);
            }

            rows = cellset.cells.map(function(cellrow)
            {
                var retrow = {},
                    cell,
                    member,
                    m, p;

                for (m = 0; m < cellrow.length; m++)
                {
                    cell = cellrow[m];
                    // row properties
                    if (m == 0)
                    {
                        for (p = 0; p < cell.positions[1].length; p++)
                        {
                            member = cell.positions[1][p];
                            retrow[member.level.uniqueName] = member.name;
                        }
                    }
                    // measures
                    retrow[cell.positions[0][0].name] = cell.value;
                }
                return retrow;
            });

            return new MeasureStore({
                measures: measures,
                records: rows
            });
        }

        function _generateAlias(measure) {
            return [measure.schemaName, measure.queryName, measure.name].join('_');
        }

        /**
         * Select the list of distinct member values in this dimension, will return raw concatenated keys
         */
        function _members(measureStore, dimName)
        {
            var group = measureStore.group(dimName);
            var ret = group.reduceCount().all().map(function(entry)
            {
                return entry.key;
            });
            group.dispose();
            return ret;
        }

        function _naturalSort(aso, bso)
        {
            return LABKEY.internal.SortUtil.naturalSort(aso, bso);
        }

        function _selectSeries(measureStore, rowDim, colDim)
        {
            var rowMemberKeys = _members(measureStore, rowDim),
                colMemberKeys = _members(measureStore, colDim),
                keyDividerIndex = rowDim.length,
                dim = measureStore.getDimension(rowDim.concat(colDim)),
                group = measureStore._group(dim),
                entries = group.all(),
                rowMap = {},
                colMap = {},
                r, c;

            // generate mapping table for rows
            if (rowMemberKeys.length > 0)
            {
                if (LABKEY.Utils.isString(rowMemberKeys[0]))
                {
                    rowMemberKeys.sort(_naturalSort);
                }

                for (r = 0; r < rowMemberKeys.length; r++)
                {
                    rowMap[rowMemberKeys[r]] = r;
                }
            }

            // generate mapping table for cols
            if (colMemberKeys.length > 0)
            {
                if (LABKEY.Utils.isString(colMemberKeys[0]))
                {
                    colMemberKeys.sort(_naturalSort);
                }

                for (c = 0; c < colMemberKeys.length; c++)
                {
                    colMap[colMemberKeys[c]] = c;
                }
            }

            // preallocate arrays
            var resultArray = new Array(rowMemberKeys.length);
            for (r = 0; r < rowMemberKeys.length; r++)
            {
                resultArray[r] = new Array(colMemberKeys.length);
            }

            entries.forEach(function(entry)
            {
                var keyValues = entry.key.split(CONCAT_STRING);

                var rowKey = keyValues.slice(0, keyDividerIndex).join(CONCAT_STRING);
                var colKey = keyValues.slice(keyDividerIndex, keyValues.length).join(CONCAT_STRING);
                var rowIndex = rowMap[rowKey];
                var colIndex = colMap[colKey];
                resultArray[rowIndex][colIndex] = entry;
            });

            return resultArray;
        }

        // MeasureStoreStatic
        return {

            VALUES: 'VALUES',
            COUNT: 'COUNT',
            SUM: 'SUM',
            MEAN: 'MEAN',

            selectRows : function(config)
            {
                _apiWrapper(config, LABKEY.Query.selectRows, undefined, _handleSelectRowsResponse);
            },

            executeSql : function(config)
            {
                _apiWrapper(config, LABKEY.Query.executeSql, undefined, _handleSelectRowsResponse);
            },

            // NOTE mdx is only supported for site admin at the moment
            /**
             * NOTE: only loads results of the form
             *
             * SELECT [Measures].members ON COLUMNS,
             * CROSSJOIN([Level1].members,...,[LevelN].members) ON ROWS
             * FROM [Cube]
             *
             * Note only measures on columns, only level.members on rows
             * Any sort of ragged hierarchy or heterogeneous members is NOT supported.
             */
            executeOlapQuery : function(config)
            {
                _apiWrapper(config, LABKEY.query.olap.CubeManager.executeOlapQuery, undefined, _handleCellSetResponse);
            },

            // LABKEY.Query.Visualization.getData() wrapper
            // NOTE: getData() does not require that measures be marked with isMeasure and isDimension, but this
            // wrapper API does
            getData : function(config, getDataFn, getDataScope)
            {
                var getData = LABKEY.Query.Visualization.getData,
                    scope = this;

                if ($.isFunction(getDataFn)) {
                    getData = getDataFn;
                    scope = getDataScope;
                }

                _apiWrapper(config, getData, scope, _handleGetDataResponse);
            },

            // NYI load from Ext.Store (listen to data change events?)
            store : function(config)
            {
            },

            create : function(config)
            {
                return new MeasureStore(config);
            }
        };

    })();


    /**
     * A MeasureStore has a lot of functionality, and can be used directly for most charting needs.
     *
     * However, it does not handle these tricky situations.
     *
     * a) plotting a measure against it self (e.g. x-axis = CellCount where Population=CD4; y-axis = CellCount where Population=CD8)
     *    this can be partially handled by using selectSeries() or two calls two .select() with different filter() values
     * b) plotting/grouping measures with different keys, where storing the data in one-store doesn't work
     *
     * AxisMeasureStore can be used to make all these cases look the same.
     */
    LABKEY.Query.AxisMeasureStore = new (function()
    {
        var AxisMeasureStore = function()
        {
            this.axes = [];
            this.measures = [];
            this._columns = [];
        };

        AxisMeasureStore.prototype =
        {
            axes: null,
            measures: null,

            setMeasure : function(index, label, store, measureName, filters)
            {
                this.measures[index] =
                {
                    measureStore: store,
                    measureName: measureName,
                    label: label,
                    filters: filters,
                    index: index
                };
            },

            // plot axis names
            setXMeasure : function(store, measureName, filters)
            {
                this.setMeasure(0, 'x', store, measureName, filters);
            },

            setYMeasure : function(store, measureName, filters)
            {
                this.setMeasure(1, 'y', store, measureName, filters);
            },

            setZMeasure : function(store, measureName, filters)
            {
                this.setMeasure(2, 'z', store, measureName, filters);
            },

            setAxis : function(axis, dimension)
            {
            },

            setOnColumns: function(dimension)
            {
            },
            /*
             setOnRows : function(dimension)
             {
             },
             setOnPages : function(dimension)
             {
             },
             setOnSections : function(dimension)
             {
             },
             setOnChapters : function(dimension)
             {
             },
             */
            flattenJoinEntry : function(dim, entry, includeAllDimensions)
            {
                var measures = this.measures,
                    r = {},
                    keyNames = dim._keys,
                    keyValues = $.type(entry.key) === 'string' ? entry.key.split(CONCAT_STRING) : [entry.key],
                    label, measureName, i;

                for (i = 0; i < keyNames.length; i++)
                {
                    r[keyNames[i]] = i < keyValues.length ? keyValues[i] : null;
                }

                for (i = 0; i < measures.length; i++)
                {
                    if (measures[i]) {
                        label = measures[i].label;
                        measureName = measures[i].measureName;

                        // TODO: the given measure might not be defined for all axes, should we set it to null or ...?
                        if (entry.value[i]) {
                            r[label] = entry.value[i][measureName];
                            if (includeAllDimensions) {
                                r[label].rawRecord = entry.value[i];
                            }
                        }
                    }
                }

                return r;
            },

            setJoinOption : function() {},

            _join : function(dimArray, results)
            {
                var cf = crossfilter();
                results.forEach(function(result, index)
                {
                    result.forEach(function(row)
                    {
                        row.__index = index;
                    });
                    cf.add(result);
                });
                var initFn = function() {return [];};
                var addFn = function(accum, row)
                {
                    accum[row.__index] = row;
                    return accum;
                };
                return cf.dimension(function(row) { return row.__key; }).group().reduce(addFn, null, initFn).all();
            },

            members : function(dim) {},

            /*
             * Select records from this AxisMeasureStore based on grouping by the selected dimName.
             */
            select : function(dimName, includeAllDimensions)
            {
                var dimArray = [],
                    results,
                    dim;

                if (!dimName)
                {
                    for (var a = 0; a < this.axes; a++) {
                        if (this.axes[a])
                            dimArray = dimArray.concat(this.axes[a]);
                    }
                }
                else if (LABKEY.Utils.isArray(dimName))
                    dimArray = dimName;
                else
                    dimArray = [dimName];

                results = this.measures.map(function(measure)
                {
                    if (null == measure)
                        return null;
                    // TODO: push/pop filters
                    if (measure.filters)
                    {
                        for (var d in measure.filters)
                        {
                            if (measure.filters.hasOwnProperty(d))
                            {
                                measure.measureStore.filter(d, measure.filters[d]);
                            }
                        }
                    }
                    var measureStore = measure.measureStore;

                    // TODO: Have this reviewed, we're cherry picking the dim off the first measureStore.
                    // Definitely better than using a global. Should AxisMeasureStore implement getDimension? Inherit from MeasureStore?
                    if (!dim)
                        dim = measureStore.getDimension(dimArray);
                    //var group = measureStore._group(dim);
                    //var entries = group.all();
                    //group.dispose();
                    //return entries;
                    return measureStore.select(dimArray);
                });

                //
                // results is an array with one entry per input measure store
                //   each entry is the result of grouping by dimArray
                //

                if (0 == results.length || !dim)
                    return null;
                else if (1 == results.length)
                    results = results[0];
                else
                    results = this._join(dimArray, results);

                //
                // results is now one array of key-value pairs representing the joined result
                // the key is the concatenated join key and value is an array with one entry per input measure store
                //    each entry is a result from the previous grouping operation
                //

                var me = this;
                results = results.map(function(entry)
                {
                    // expand the key value, and select the x and y measure
                    return me.flattenJoinEntry(dim, entry, includeAllDimensions);
                });

                //
                // flatten un-concatenates the join key and creates a property for each component
                // it also pulls out the aggregated results for the defined x,y,z value
                //

                return results;
            }
        };

        return {
            create : function()
            {
                return new AxisMeasureStore();
            }
        }
    });

})(jQuery, crossfilter);

