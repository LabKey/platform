/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2015 LabKey Corporation
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


(function()
{
    var CONCAT_STRING = '|\uFFFF|';


    LABKEY.Query.experimental = LABKEY.Query.experimental || {};



    var CountStarAggregator = function()
    {
    };
    CountStarAggregator.prototype =
    {
        count: 0,

        valueOf: function()
        {
            return this.count;
        },
        getCount: function()
        {
            return this.count;
        },
        addTo: function()
        {
            this.count++;
        },

        removeFrom: function()
        {
            this.count--;
        },

        supports: function()
        {
            return ["COUNT"];
        }
    };


    var UniqueValueAggregator = function()
    {
        this.valueHasBeenSet = false;
        this.isUnique = true;
        this.value = null;
    };
    UniqueValueAggregator.prototype =
    {
        valueOf: function()
        {
            return this.getValue();
        },
        getValue: function()
        {
            return this.isUnique && this.valueHasBeenSet ? this.value : undefined;
        },
        addTo: function(value, record)
        {
            if (!this.isUnique)
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
        removeFrom: function(value, record)
        {
            // not supported
        },
        supports: function()
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
    CollectNonNullValuesAggregator.prototype =
    {
        values: null,
        _isSorted: false,
        sum: null,

        valueOf: function()
        {
            return this.getMean();
        },
        getValues: function()
        {
            return this.values;
        },
        getCount: function()
        {
            return this.values.length;
        },
        getSum: function()
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
        getMean: function()
        {
            var count = this.getCount();
            if (0 == count)
                return null;
            var sum = this.getSum();
            return sum / count;
        },
        getVariance: function()
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
        getStdDev: function()
        {
            var var_ = this.getVariance();
            if (null === var_)
                return null;
            return Math.sqrt(var_);
        },
        getStdErr: function()
        {
            var stddev = this.getStdDev();
            if (null == stddev)
                return null;
            var N = this.getCount();
            return stddev / Math.sqrt(N);
        },
        getMedian: function()
        {
            this._sort();
            var values = this.values;
            var length = values.length;
            if (0 == length)
                return null;
            else if (1 == length % 2)
                return values[(length - 1) / 2];
            else
                return (values[length / 2 - 1] + values[length / 2]) / 2.0;
        },
        getMax: function()
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
        getMin: function()
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
        getCountDistinct: function()
        {
            var values = this.values;
            var length = values.length;
            if (length <= 1)
                return length;
            this._sort();
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

        addTo: function(value, record)
        {
            if (null == value)
                return;
            this.values.push(value);
            this._isSorted = false;
            return this;
        },
        removeFrom: function(value, record)
        {
            if (null == value)
                return;
            this._sort();
            var b = crossfilter.bisectRight(this.values, value, 0, this.values.length);
            if (this.values[b] != measure.values[i])
                throw "IllegalState";
            this.values.splice(b, 1);
            this.sum = null;
            return this;
        },

        _sort: function()
        {
            if (!this._isSorted && 1 < this.values.length)
                crossfilter.quicksort(this.values, 0, this.values.length);
            this._isSorted = true;
        },

        supports: function()
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
        this.sum = 0
        this.sumOfSquares = 0;
        this.min = null;
        this.max = null;
    };
    CollectPreAggregatedValues.prototype =
    {
        addTo: function(value, record)
        {
            var v;
            if (this.countColumn && null !== (v = record[this.countColumn]))
                this.count += v;
            if (this.sumColumn && null !== (v = record[this.sumColumn]))
                this.sum += v;
            if (this.sumOfSquaresColumn && null !== (v = record[this.sumOfSquaresColumn]))
                this.sumOfSquares += v;
            if (this.minColumn && null !== (v = record[this.minColumn]))
                if (this.min === null || v < this.min)
                    this.min = v;
            if (this.maxColumn && null !== (v = record[this.maxColumn]))
                if (this.max === null || v < this.max)
                    this.max = v;
            return this;
        },
        removeFrom: function(value, record)
        {
            var v;
            if (this.countColumn && null !== (v = record[this.countColumn]))
                this.count -= v;
            if (this.sumColumn && null !== (v = record[this.sumColumn]))
                this.sum -= v;
            if (this.sumOfSquaresColumn && null !== (v = record[this.sumOfSquaresColumn]))
                this.sumOfSquares -= v;
            return this;
        },
        getCount: function()
        {
            if (!this.countColumn)
                return null;
            return this.count;
        },
        getSum: function()
        {
            if (!this.sumColumn)
                return null;
            return this.sum;
        },
        getMean: function()
        {
            if (!this.countColumn || !this.sumColumn)
                return null;
            return this.count == 0 ? null : this.sum / this.count;
        },
        getVariance: function()
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
            var var_ = (N * s2 - s1 * s1) / (N * (N - 1));
            return var_;
        },
        getStdDev: function()
        {
            var var_ = this.getVariance();
            if (null === var_)
                return null;
            return Math.sqrt(var_);
        },
        getStdErr: function()
        {
            var stddev = this.getStdDev();
            if (null == stddev)
                return null;
            var N = this.getCount();
            return stddev / Math.sqrt(N);
        },
        supports: function()
        {
            var ret = [];
            if (this.countColumn)
                ret.push("COUNT");
            if (this.sumColumn)
                ret.push("SUM");
            if (this.countColumn && this.sumColumn)
                ret.push("MEAN");
            if (this.countColumn && this.sumColumn && sumOfSquaresColumn)
            {
                ret.push("STDDEV");
                ret.push("VAR");
                ret.push("STDERR");
            }
            if (this.minColumn)
                ret.push("MIN");
            if (this.maxColumn)
                ret.push("MAX");
            return ret;
        }
    };


    LABKEY.Query.experimental.MeasureStore = new (function()
    {
        function reduceInit(columns)
        {
            var accumulators = new Array(columns.length);
            for (var i = 0; i < columns.length; i++)
            {
                if (columns[i] && columns[i].aggregator)
                    accumulators[i] = new columns[i].aggregator(columns[i]);
            }
            return accumulators;
        }

        function reduceAdd(columns, accumulators, row)
        {
            for (var i = 0; i < accumulators.length; i++)
            {
                var v = row[columns[i].name];  // this might be an object depending on the response type
                if (null != v && typeof v === "object" && 'value' in v)
                    v = v.value;
                accumulators[i].addTo(v, row);
            }
            return accumulators;
        }

        function reduceRemove(columns, accumulators, row)
        {
            for (var i = 0; i < accumulators.length; i++)
            {
                var v = row[columns[i].name];  // this might be an object depending on the response type
                if (null != v && typeof v === "object" && 'value' in v)
                    v = v.value;
                accumulators[i].removeFrom(v, row);
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
            this._dimensions = {};
            this._records = config.records || [];

            var columnNames = config.columns || [];
            if (!config.columns && 0 < config.records.length)
            {
                var rec = config.records[0];
                for (var name in rec)
                {
                    if (!rec.hasOwnProperty(name))
                        continue;
                    columnNames.push(name);
                }
            }

            // consider using some sort of NamedList implementation
            this._columnMap = {};
            this._columns = [];
            me = this;
            me._columns.push({name: '*', index: 0, aggregator: CountStarAggregator});
            me._columnMap['*'] = me._columns[0];
        columnNames.forEach(function(name,index){
                var col = {name: name, index: index + 1, aggregator: UniqueValueAggregator};
                me._columnMap[name] = col.index;
                me._columns[col.index] = col;
            });

        this._measures = (config.measures || []).map(function(m){
                if (typeof m === "string")
                    m = {name: m};
                return m;
            });
        this._measures.forEach(function(m){
                var index = me._columnMap[m.name];
                if (index)
                {
                    var column = me._columns[index];
                    // consider is apply(column,measure) better or column.measure = measure
                    Ext4.apply(column, m);
                    if (m.countColumn || m.sumColumn)
                        me._columns[index].aggregator = CollectPreAggregatedValues;
                    else
                        me._columns[index].aggregator = CollectNonNullValuesAggregator;
                }
            });

            this._crossfilter = crossfilter(this._records);
            var dimensions = config.dimensions || [];
            for (var i = 0; i < dimensions.length; i++)
                this.getDimension(dimensions[i]);
        };
        MeasureStore.prototype =
        {
            _crossfilter: null,
            _dimensions: null,
            _measures: null,
            _records: null,
            _columnsMap: null,      // column name to index
            _columns: null,         // {name:foo, index:i, aggregator:constructor}

            records: function()
            {
                return this._records;
            },


            _group: function(dim, keyFn)
            {
                var fnInit = reduceInit.bind(null, this._columns);
                var fnAdd = reduceAdd.bind(null, this._columns);
                var fnRemove = reduceRemove.bind(null, this._columns);

                var group;
                if (null == dim)
                    group = this._crossfilter.groupAll();
                else if (keyFn)
                    group = dim.group(keyFn);
                else
                    group = dim.group();
                return group.reduce(fnAdd, fnRemove, fnInit);
            },


            group: function(dimName, keyFn)
            {
                var dim = null == dimName ? null : this.getDimension(dimName);
                return this._group(dim, keyFn);
            },


            filter: function(dimName, range)
            {
                var dim = this.getDimension(dimName);
                return dim.filter(range);
            },

            /**
             * Select the list of distinct member values in this dimension, will return raw concatenated keys
             */
            _members: function(dimName)
            {
                var group = this.group(dimName);
            var ret = group.reduceCount().all().map(function(entry)
                {
                    return entry.key;
                });
                group.dispose();
                return ret;
            },

            /**
             * Select the list of distinct member values in this dimension as an array
             */
            members: function(dimName)
            {
                var dim = this.getDimension(dimName);
                var group = this._group(dim);
                var ret;
                if (1 == dim._keys.length)
                {
                ret = group.reduceCount().all().map(function (entry)
                    {
                        return entry.key;
                    });
                }
                else
                {
                ret = group.reduceCount().all().map(function (entry)
                    {
                        return entry.key.split(CONCAT_STRING);
                    });
                }
                group.dispose();
                return ret;
            },

            filterAll: function(dimName)
            {
                return this.getDimension(dimName).filterAll();
            },

            getDimension: function(dimName)
            {
                var dimArray = [dimName];
                if (LABKEY.Utils.isArray(dimName))
                {
                    dimArray = dimName;
                    dimName = dimName.join(CONCAT_STRING);
                }
                if (this._dimensions.hasOwnProperty(dimName))
                    return this._dimensions[dimName];

                // validate that we can find these names
                var firstRow = this._records.length ? this._records[0] : null;
                if (firstRow)
                {
                dimArray.forEach(function(name)
                    {
                        if (!(name in firstRow))
                        {
                            console.error("Column not found in data: " + name);
                            throw "Column not found in data: " + name;
                        }
                    });
                }

                var getter;
                if (dimArray.length == 1)
                {
                    getter = (function(name, record)
                    {
                        var key = record[name];
                        if (null != key && typeof key === "object" && 'value' in key)
                            key = key.value;
                        return key;
                    }).bind(null, dimName);
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
                dim = this._crossfilter.dimension(getter);
                dim._name = dimName;
                dim._keys = dimArray;
                this._dimensions[dimName] = dim;
                return dim;
            },


            //
            // Data selecting methods
            //



            /**
             * Returns an array with one element for each element in the dimension.
             * See MeasureStore.members()
             */
            _array: function(entries, memberIndex, aggregate)
            {
                switch (aggregate)
                {
                    case "VALUES":
                    return entries.map(function(entry)
                        {
                            return null == entry ? null : entry.value[memberIndex].getValues();
                        });
                        break;
                    case "COUNT":
                    return entries.map(function(entry)
                        {
                            return null == entry ? null : entry.value[memberIndex].getCount();
                        });
                        break;
                    case "SUM":
                    return entries.map(function(entry)
                        {
                            return null == entry ? null : entry.value[memberIndex].getSum();
                        });
                        break;
                    case "MEAN":
                    return entries.map(function(entry)
                        {
                            return null == entry ? null : entry.value[memberIndex].getMean();
                        });
                        break;
                    case "VAR":
                    return entries.map(function(entry)
                        {
                            return null == entry ? null : entry.value[memberIndex].getVariance();
                        });
                        break;
                    case "STDDEV":
                    return entries.map(function(entry)
                        {
                            return null == entry ? null : entry.value[memberIndex].getStdDev();
                        });
                        break;
                    case "STDERR":
                    return entries.map(function(entry)
                        {
                            return null == entry ? null : entry.value[memberIndex].getStdErr();
                        });
                        break;
                    case "MIN":
                    return entries.map(function(entry)
                        {
                            return null == entry ? null : entry.value[memberIndex].getMin();
                        });
                        break;
                    case "MAX":
                    return entries.map(function(entry)
                        {
                            return null == entry ? null : entry.value[memberIndex].getMax();
                        });
                        break;
                    default:
                        throw "NYI";
                }
            },


            flattenGroupEntry: function(dim, entry)
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
             * Returns one object per key in dimension dimName
             */
            select: function(dimName)
            {
                var dim = this.getDimension(dimName);
                var group = this._group(dim);
                var entries = group.all();

                var ret;
                var me = this;
            ret = entries.map(function(entry)
                {
                    return me.flattenGroupEntry(dim, entry);
                });
                group.dispose();
                return ret;
            },


            selectArray: function(dimName, measureName, aggregate)
            {
                // find index of this measureName
                var index = -1;
                for (var m = 0; m < this._columns.length; m++)
                    if (measureName === this._columns[m].name)
                        index = m;
                if (index === -1)
                    throw "Column name not found: " + measureName;

                var dim = this.getDimension(dimName);
                var group = this._group(dim);
                var ret = this._array(group.all(), index, aggregate);
                group.dispose();
                return ret;
            },

            // CONSIDER: transpose by default?
            selectXYArray: function(dimName, measureXName, aggregateX, measureYName, aggregateY)
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
                var xSeries = this._array(entries, x, aggregateX);
                var ySeries = this._array(entries, y, aggregateY);
                group.dispose();
                return [xSeries, ySeries];
            },


            _selectSeries: function(rowDim, colDim)
            {
                var rowMemberKeys = this._members(rowDim);
                var colMemberKeys = this._members(colDim);
                var keyDividerIndex = rowDim.length;
                var r, c;

                // generate mapping table for rows and cols
                // TODO natural sorting instead of string sorting (maybe use crossfilter.permute())
                var rowMap = {};
                for (r = 0; r < rowMemberKeys.length; r++)
                    rowMap[rowMemberKeys[r]] = r;
                var colMap = {};
                for (c = 0; c < colMemberKeys.length; c++)
                    colMap[colMemberKeys[c]] = c;

                // preallocate arrays
                var resultArray = new Array(rowMemberKeys.length);
                for (r = 0; r < rowMemberKeys.length; r++)
                    resultArray[r] = new Array(colMemberKeys.length);

                var dim = this.getDimension(rowDim.concat(colDim));
                var group = this._group(dim);
                var entries = group.all();
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
            },

            selectSeries: function(rowDim, colDim)
            {
                rowDim = LABKEY.Utils.isArray(rowDim) ? rowDim : [rowDim];
                colDim = LABKEY.Utils.isArray(colDim) ? colDim : [colDim];
                var results = this._selectSeries(rowDim, colDim, true);
                var me = this;
                var dim = this.getDimension(rowDim.concat(colDim));
                var ret;
            ret = results.map(function(rowArray){
                return rowArray.map(function(entry){
                        return me.flattenGroupEntry(dim, entry);
                    });
                });
                return ret;
            },

            selectSeriesArray: function(rowDim, colDim, measureName, aggregate)
            {
                rowDim = LABKEY.Utils.isArray(rowDim) ? rowDim : [rowDim];
                colDim = LABKEY.Utils.isArray(colDim) ? colDim : [colDim];
                var index = -1;
                for (var m = 0; m < this._columns.length; m++)
                    if (measureName === this._columns[m].name)
                        index = m;
                if (index === -1)
                    throw "Column name not found: " + measureName;

                var me = this;
                var results = this._selectSeries(rowDim, colDim, false);
            return results.map(function(row){return me._array(row,index,aggregate)});
            }
        };




//
// static methods
//

// LABKEY.Query.selectRows() wrapper
        var _apiWrapper = function(originalConfig, apiFn, resultHanderFn)
        {
            var apiConfig = {};
            var onSuccess;
            var measures, dimensions;

            for (var p in originalConfig)
            {
                if (!originalConfig.hasOwnProperty(p))
                    continue;
                switch (p)
                {
                    case "measures":
                        measures = originalConfig[p];
                        apiConfig[p] = originalConfig[p];
                        break;
                    case "dimensions":
                        dimensions = originalConfig[p];
                        break;
                    case "success":
                        onSuccess = originalConfig[p];
                        break;
                    default:
                        apiConfig[p] = originalConfig[p];
                }
            }

            apiConfig.success = function(results)
            {
                var measureStore = resultHanderFn(results, measures, dimensions);
                if (onSuccess)
                    onSuccess(measureStore);
            };

            apiFn(apiConfig);
        };

        function _handleSelectRowsResponse(results, measures, dimensions)
        {
            if (null == measures)
            {
                measures = [];
            results.metaData.fields.forEach(function(field)
                {
                    if (field.measure)
                        measures.push(field.name);
                });
            }
            if (null == dimensions)
            {
                dimensions = [];
            results.metaData.fields.forEach(function(field)
                {
                    if (field.dimension || field.recommendedVariable)
                        dimensions.push(field.name);
                });
            }
            var measureStore;
            measureStore = new MeasureStore
            ({
                // there's not really an advantage to pre constructing the dimensions
                //dimensions: dimensions,
                measures: measures,
                records: results.rows
            });
            return measureStore;
        }

        function _handleGetDataResponse(results, measures, dimensions)
        {
            var measureSpecs = measures;
            measures = [];
            dimensions = [];

        measureSpecs.forEach(function(m)
            {
                var name = ('alias' in m.measure) ? m.measure.alias : m.measure.schemaName + "_" + m.measure.queryName + "_" + m.measure.name;
                if ('isMeasure' in m.measure && m.measure.isMeasure)
                    measures.push(name);
                else if ('isDimension' in m.measure && m.measure.isDimension)
                    dimensions.push(name);
            });
            var measureStore;
            measureStore = new MeasureStore
            ({
                // there's not really an advantage to pre constructing the dimensions
                //dimensions: dimensions,
                measures: measures,
                records: results.rows
            });
            return measureStore;
        }

        function _handleCellSetResponse(cellset, measuresConfig)
        {
            var measuresMap = {};
            if (measuresConfig)
            measuresConfig.forEach(function(m){measuresMap[m.name] = m;});

            // verify that all columns are measures
            var measures = [];
            var positions = cellset.axes[0].positions;
            for (var i = 0; i < positions.length; i++)
            {
                var posArray = positions[i];
                if (posArray.length != 1)
                    throw "MeasureStore does not support nesting on the ROWS axis";
                var m = posArray[0];
                if (!Ext4.String.startsWith(m.uniqueName, "[Measures].["))
                    throw "MeasureStore expects measures on the ROWS axis";
                var measureConfig = measuresMap[m.name];
                if (!measureConfig)
                    measureConfig = {name: m.name, sumColumn: m.name};
                measures.push(measureConfig);
            }
            var rows = cellset.cells.map(function(cellrow)
            {
                var retrow = {};
                for (var m = 0; m < cellrow.length; m++)
                {
                    var cell = cellrow[m];
                    // row properties
                    if (m == 0)
                    {
                        for (var p = 0; p < cell.positions[1].length; p++)
                        {
                            var member = cell.positions[1][p];
                            retrow[member.level.uniqueName] = member.name;
                        }
                    }
                    // measures
                    retrow[cell.positions[0][0].name] = cell.value;
                }
                return retrow;
            });

            var measureStore;
            measureStore = new MeasureStore
            ({
                // there's not really an advantage to pre constructing the dimensions
                //dimensions: dimensions,
                measures: measures,
                records: rows
            });
            return measureStore;
        }


        var MeasureStoreStatic = {};

        MeasureStoreStatic.selectRows = function(config)
        {
            _apiWrapper(config, LABKEY.Query.selectRows, _handleSelectRowsResponse);
        };


        MeasureStoreStatic.executeSql = function(config)
        {
            _apiWrapper(config, LABKEY.Query.executeSql, _handleSelectRowsResponse);
        };


        // NOTE mdx is only supported for site admin at the moment
        /**
         * NOTE: only loads results of the form
         *
         * SELECT [Measures].members ON COLUMNS,
         * CROSSJOIN([Level1].members,...,[LevelN].members) ON ROWS
         * FROM [Cube]
         *
         * Note only measures on columns, only level.members on rows
         * Any sort of ragged hierarchy or heterogeous members is NOT supported.
         */
        MeasureStoreStatic.executeOlapQuery = function(config)
        {
            _apiWrapper(config, LABKEY.query.olap.CubeManager.executeOlapQuery, _handleCellSetResponse);
        };


        // LABKEY.Query.Visualization.getData() wrapper
        // NOTE: getData() does not require that measures be marked with isMeasure and isDimension, but this
        // wrapper API does
        MeasureStoreStatic.getData = function(config)
        {
            _apiWrapper(config, LABKEY.Query.Visualization.getData, _handleGetDataResponse);
        };


        // NYI load from Ext.Store (listen to data change events?)
        MeasureStoreStatic.store = function(config)
        {
        };


        MeasureStoreStatic.create = function(config)
        {
            return new MeasureStore(config);
        };

        MeasureStoreStatic.VALUES = "VALUES";
        MeasureStoreStatic.COUNT = "COUNT";
        MeasureStoreStatic.SUM = "SUM";
        MeasureStoreStatic.MEAN = "MEAN";

        return MeasureStoreStatic;

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
     * MultiMeasureStore can be used to make all these cases look the same.
     */
    LABKEY.Query.experimental.AxisMeasureStore = new (function()
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

            setMeasure: function(index, label, store, measureName, filters)
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
            setXMeasure: function(store, measureName, filters)
            {
                this.setMeasure(0, 'x', store, measureName, filters);
            },
            setYMeasure: function(store, measureName, filters)
            {
                this.setMeasure(1, 'y', store, measureName, filters);
            },
            setZMeasure: function(store, measureName, filters)
            {
                this.setMeasure(2, 'z', store, measureName, filters);
            },

            setAxis: function(axis, dimension)
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
            flattenJoinEntry: function(dim, entry)
            {
                var measures = this.measures;
                var r = {};
                var keyNames = dim._keys;
                var keyValues = entry.key.split(CONCAT_STRING);
                for (var i = 0; i < keyNames.length; i++)
                    r[keyNames[i]] = i < keyValues.length ? keyValues[i] : null;
                for (var m = 0; m < measures.length; m++)
                {
                    var label = measures[m].label;
                    var measureName = measures[m].measureName;
                    r[label] = entry.value[m][measureName];
                }
                return r;
            },

        setJoinOption: function(){},

            _join: function(dimArray, results)
            {
                var cf = crossfilter();
            results.forEach(function(result,index){
                result.forEach(function(row){row.__index = index;});
                    cf.add(result);
                });
            var initFn = function() {return [];};
                var addFn = function(accum, row)
                {
                    accum[row.__index] = row;
                    return accum;
                };
            var xyz = cf.dimension(function(row){return row.__key;}).group().reduce(addFn, null, initFn).all();
                return xyz;
            },

        members : function(dim){},

            select: function(dimName)
            {
                var dimArray = [];
                if (!dimName)
                {
                    for (var a = 0; a < this.axes; a++)
                        if (this.axes[a])
                            dimArray = dimArray.concat(this.axes[a]);
                }
                else if (LABKEY.Utils.isArray(dimName))
                    dimArray = dimName;
                else
                    dimArray = [dimName];

                var results = this.measures.map(function(measure)
                {
                    if (null == measure)
                        return null;
                    // TODO: push/pop filters
                    if (measure.filters)
                    {
                        for (var d in measure.filters)
                        {
                            if (!axis.filters.hasOwnProperty(d))
                                continue;
                            measure.measureStore.filter(d, measure.filters[d]);
                        }
                    }
                    var measureStore = measure.measureStore;
                    //var dim = measureStore.getDimension(dimArray);
                    //var group = measureStore._group(dim);
                    //var entries = group.all();
                    //group.dispose();
                    //return entries;
                    return measureStore.select(dimArray);
                });

                if (0 == results.length)
                    return null;
                else if (1 == results.length)
                    results = results[0];
                else
                    results = this._join(dimArray, results);

                var me = this;
                var ret = results.map(function(entry)
                {
                    return me.flattenJoinEntry(dim, entry);
                });
                return ret;
            }
        };

        return {
            create: function()
            {
                return new AxisMeasureStore();
            }
        }
    });



})();

