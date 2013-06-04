(function(){
    /**
     * @private
     */
    var validateFilter = function(filter){
        var filterObj = {};
        if(filter instanceof LABKEY.Query.Filter || filter.getColumnName){
            filterObj.fieldKey = LABKEY.FieldKey.fromString(filter.getColumnName()).getParts();
            filterObj.value = filter.getValue();
            filterObj.type = filter.getFilterType().getURLSuffix();
            return filterObj;
        }

        //If filter isn't a LABKEY.Query.Filter or LABKEY.Filter, then it's probably a raw object.
        if(filter.fieldKey){
            filter.fieldKey = validateFieldKey(filter.fieldKey);
        } else {
            throw new Error('All filters must have a "fieldKey" attribute.');
        }

        if(!filter.fieldKey){
            throw new Error("Filter fieldKeys must be valid FieldKeys");
        }

        if(!filter.type){
            throw new Error('All filters must have a "type" attribute.');
        }
        return filter;
    };

    /**
     * @private
     */
    var validateFieldKey = function(fieldKey){
        if(fieldKey instanceof LABKEY.FieldKey){
            return fieldKey.getParts();
        }

        if(fieldKey instanceof Array){
            return fieldKey;
        }

        if(typeof fieldKey === 'string'){
            return LABKEY.FieldKey.fromString(fieldKey).getParts();
        }

        return false;
    };

    /**
     * @private
     */
    var validateSource = function(source){
        if(!source || source == null){
            throw new Error('A source is required for a GetData request.');
        }

        if(!source.type){
            source.type = 'query';
        }

        if(!source.schemaName){
            throw new Error('A schemaName is required.');
        }

        source.schemaName = validateFieldKey(source.schemaName);

        if(!source.schemaName){
            throw new Error('schemaName must be a FieldKey');
        }

        if(source.type === 'query'){
            if(!source.queryName || source.queryName == null){
                throw new Error('A queryName is required for getData requests with type = "query"');
            }

            if(source.columns){
                if(!source.columns instanceof Array){
                    throw new Error('columns must be an array of FieldKeys.');
                }

                for(var i = 0; i < source.columns.length; i++){
                    source.columns[i] = validateFieldKey(source.columns[i]);

                    if(!source.columns[i]){
                        throw new Error('columns must be an array of FieldKeys.');
                    }
                }
            }
        } else if(source.type === 'sql') {
            if(!source.sql){
                throw new Error('sql is required if source.type = "sql"');
            }
        } else {
            throw new Error('Unsupported source type.');
        }
    };

    /**
     * @private
     */
    var validatePivot = function(pivot){
        if(!pivot.columns || pivot.columns == null){
            throw new Error('pivot.columns is required.');
        }

        if(!pivot.columns instanceof Array){
            throw new Error('pivot.columns must be an array of fieldKeys.');
        }

        for(var i = 0; i < pivot.columns.length; i++){
            pivot.columns[i] = validateFieldKey(pivot.columns[i]);

            if(!pivot.columns[i]){
                throw new Error('pivot.columns must be an array of fieldKeys.');
            }
        }

        if(!pivot.by || pivot.by ==  null){
            throw new Error('pivot.by is required');
        }

        pivot.by = validateFieldKey(pivot.by);

        if(!pivot.by === false){
            throw new Error('pivot.by must be a fieldKey.');
        }
    };

    /**
     * @private
     */
    var validateTransform = function(transform){
        var i;

        if(!transform.type || transform.type === null || transform.type === undefined){
            throw new Error('Transformer type is required.');
        }

        if(transform.groupBy && transform.groupBy != null){
            if(!transform.groupBy instanceof Array){
                throw new Error('groupBy must be an array.');
            }
        }


        if(transform.aggregates && transform.aggregates != null){
            if(!transform.aggregates instanceof Array){
                throw new Error('aggregates must be an array.');
            }

            for(i = 0; i < transform.aggregates.length; i++){
                if(!transform.aggregates[i].fieldKey){
                    throw new Error('All aggregates must include a fieldKey.');
                }

                transform.aggregates[i].fieldKey = validateFieldKey(transform.aggregates[i].fieldKey);

                if(!transform.aggregates[i].fieldKey){
                    throw new Error('Aggregate fieldKeys must be valid fieldKeys');
                }

                if(!transform.aggregates[i].type){
                    throw new Error('All aggregates must include a type.');
                }
            }
        }

        if(transform.filters && transform.filters != null){
            if(!transform.filters instanceof Array){
                throw new Error('The filters of a transform must be an array.');
            }

            for(i = 0; i < transform.filters.length; i++){
                transform.filters[i] = validateFilter(transform.filters[i]);
            }
        }
    };

    LABKEY.Query.GetData = {
        RawData: function(config){
            if(!config || config === null || config === undefined){
                throw new Error('A config object is required for GetData');
            }

            var i;
            var jsonData = {
                renderer: {type: 'json'}
            };
            var requestConfig = {
                method: 'POST',
                url: LABKEY.ActionURL.buildURL('query', 'getData'),
                jsonData: jsonData
            };

            validateSource(config.source);
            jsonData.source = config.source;

            if(config.transforms){
                if(!(config.transforms instanceof Array)){
                    throw new Error("transforms must be an array.");
                }

                jsonData.transforms = config.transforms;
                for(i = 0; i < jsonData.transforms.length; i++){
                    validateTransform(jsonData.transforms[i]);
                }
            }

            if(config.pivot){
                validatePivot(config.pivot);
            }

            if(!config.failure){
                requestConfig.failure = function(response, options){
                    var json = LABKEY.ExtAdapter.decode(response.responseText);
                    console.error('Failure occurred during getData', json);
                };
            } else {
                requestConfig.failure = function(response, options){
                    var json = LABKEY.ExtAdapter.decode(response.responseText);
                    config.failure(json);
                };
            }

            if(!config.success){
                throw new Error("A success callback is required.");
            }

            if(!config.scope){
                config.scope = this;
            }

            requestConfig.success = function(response){
                var json = LABKEY.ExtAdapter.decode(response.responseText);
                var wrappedResponse = new LABKEY.Query.GetDataResponse(json);
                config.success.call(config.scope, wrappedResponse);
            };

            return new LABKEY.Ajax.request(requestConfig);
        }
    };

    /**
     * @private
     */
    var generateColumnModel = function(fields){
        var i, columns = [];

        for(i = 0; i < fields.length; i++){
            columns.push({
                scale: fields[i].scale,
                hidden: fields[i].hidden,
                sortable: fields[i].sortable,
                align: fields[i].align,
                width: fields[i].width,
                dataIndex: fields[i].fieldKey.toString(),
                required: fields[i].nullable, // Not sure if this is correct.
                editable: fields[i].userEditable,
                header: fields[i].shortCaption
            })
        }

        return columns;
    };

    /**
     * @private
     */
    var generateGetDisplayField = function(fieldKeyToFind, fields){
        return function(){
            var fieldString = fieldKeyToFind.toString();
            for(var i = 0; i < fields.length; i++){
                if(fieldString == fields[i].fieldKey.toString()){
                    return fields[i];
                }
            }
            return null;
        };
    };

    LABKEY.Query.GetDataResponse = function(response){
        // response = response;
        var i, attr;

        // Shallow copy the response.
        for(attr in response){
            if(response.hasOwnProperty(attr)){
                this[attr] = response[attr];
            }
        }

        // Wrap the Schema, Lookup, and Field Keys.
        this.schemaKey = LABKEY.SchemaKey.fromParts(response.schemaName);

        for(i = 0; i < response.metaData.fields.length; i++){
            // response.metaData.fields[i] = new LABKEY.Query.Field(response.metaData.fields[i]);
            var field = response.metaData.fields[i],
                    lookup = field.lookup;

            field.fieldKey = LABKEY.FieldKey.fromParts(field.fieldKey);

            if(lookup && lookup.schemaName){
                lookup.schemaName = LABKEY.SchemaKey.fromParts(lookup.schemaName);
            }

            if(field.displayField){
                field.displayField = LABKEY.FieldKey.fromParts(field.displayField);
                field.getDisplayField = generateGetDisplayField(field.displayField, response.metaData.fields);
            }
        }

        // Generate Column Model
        this.columnModel = generateColumnModel(this.metaData.fields);

        return this;
    };

    LABKEY.Query.GetDataResponse.prototype.getMetaData = function(){
        return this.metaData;
    };

    LABKEY.Query.GetDataResponse.prototype.getSchemaName = function(asString){
        return asString ? this.schemaKey.toString() : this.schemaName;
    };

    LABKEY.Query.GetDataResponse.prototype.getQueryName = function(){
        return this.queryName;
    };

    LABKEY.Query.GetDataResponse.prototype.getColumnModel = function(){
        return this.columnModel;
    };

    LABKEY.Query.GetDataResponse.prototype.getRows = function(){
        return this.rows;
    };

    LABKEY.Query.GetDataResponse.prototype.getRow = function(idx){
        return this.rows[idx];
    };

    LABKEY.Query.GetDataResponse.prototype.getRowCount = function(){
        return this.rowCount;
    };

    /**
     * getExt4Store is currently commented out. I believe we are going to need to create a new Ext4 class for use with
     * the GetData API. The current LabKey Ext4 Store relies on Select Rows and Execute SQL and it also allows you to
     * add and edit rows as well. Currently we have no way to save data in a way that is compatible with GetData.
     */
//    LABKEY.Query.GetDataResponse.prototype.getExt4Store = function(userConfig){
//        var storeCfg = {
//            schemaName: this.getSchemaName(true),
//            queryName: this.getQueryName(),
//            requiredVersion: 9.1,
//            autoLoad: false,
//            data: this.getRows()
//        };
//
//        return storeCfg;
//    };
})();