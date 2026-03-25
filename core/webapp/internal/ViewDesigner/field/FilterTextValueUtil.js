Ext4.define('LABKEY.internal.ViewDesigner.field.FilterTextValueUtil', {

    setRecord : function (filterRecord, clauseIndex) {
        this.record = filterRecord;
        this.clauseIndex = clauseIndex;

        var value = this.getRecordValue();
        var filter = this.createFilter(value);
        var filterType = filter.getFilterType();
        var urlSuffix = filterType.getURLSuffix();

        // UGH: get the op value to set visibility on init
        this.setVisibleField(filterType);

        // The record value may be an Array for filters that support multiple values.
        // convert the filter value into a user-editable string using filter.getURLParameterValue()
        var valueString = filter.getURLParameterValue();

        // Display multi-valued filters with one value per line in the textarea.
        // getURLParameterValue() returns {json:[...]} when values contain the ';' separator,
        // so we need to parse that format before converting to newline-separated display.
        if (filterType.isMultiValued() && (urlSuffix !== 'notbetween' && urlSuffix !== 'between')) {
            if (typeof valueString === 'string') {
                var parsed = this._parseJsonFilterValue(valueString);
                if (parsed !== null) {
                    valueString = parsed.join('\n');
                } else if (valueString.indexOf('\n') === -1 && valueString.indexOf(';') > 0) {
                    valueString = valueString.replaceAll(';', '\n');
                }
            }
        }

        this.setValue(valueString);
        this.on('blur', function (f) {
            var v = f.getValue();
            this.setRecordValue(v);
        }, this);
    },

    // UGH: get the op value to set visibility on init
    setVisibleField : function (filterType) {
        this.setVisible(filterType != null && filterType.isDataValueRequired());
    },

    createFilter : function (value) {
        var fieldKey = this.record.get('fieldKey');
        var op = this.record.get("items")[this.clauseIndex].op;
        var filterType = LABKEY.Filter.getFilterTypeForURLSuffix(op);
        var filter = LABKEY.Filter.create(fieldKey, value, filterType);
        return filter;
    },

    getRecordValue : function () {
        return this.record.get("items")[this.clauseIndex].value;
    },

    setRecordValue : function (valueString) {
        // parse the value string into parts for multi-value filters
        var filterValue;
        try {
            // For multi-valued filters (excluding between/notbetween), values are displayed
            // one per line in the textarea. When saving, split by newline only and encode
            // using {json:[...]} if any value contains the separator character (e.g. ';')
            // to prevent parseValue from incorrectly splitting those values.
            var op = this.record.get("items")[this.clauseIndex].op;
            var filterType = LABKEY.Filter.getFilterTypeForURLSuffix(op);
            var urlSuffix = filterType ? filterType.getURLSuffix() : null;

            if (filterType && filterType.isMultiValued() && typeof valueString === 'string'
                    && urlSuffix !== 'notbetween' && urlSuffix !== 'between') {
                var sep = filterType.getMultiValueSeparator();
                var values = valueString.split('\n');
                if (sep && values.some(function(v) { return v.indexOf(sep) !== -1; })) {
                    valueString = '{json:' + JSON.stringify(values) + '}';
                } else {
                    valueString = values.join(sep);
                }
            }

            var filter = this.createFilter(valueString);
            filterValue = filter.getValue();
        }
        catch (e) {
            console.warn("Error parsing filter value: " + valueString);
            filterValue = valueString;
        }

        this.record.get("items")[this.clauseIndex].value = filterValue;
    },

    /**
     * GitHub Issue 947: Multi value text choice values with semicolon mangled in LKS grid view editor
     * Parse a {json:[...]} encoded filter value string.
     * Returns the parsed array, or null if the string is not in {json:...} format.
     */
    _parseJsonFilterValue : function (valueString) {
        if (typeof valueString === 'string'
                && valueString.indexOf('{json:') === 0
                && valueString.lastIndexOf('}') === valueString.length - 1) {
            try {
                var parsed = JSON.parse(valueString.substring('{json:'.length, valueString.length - 1));
                if (Array.isArray(parsed))
                    return parsed;
            } catch (e) {
                // Not valid JSON, return null to fall through to default handling
            }
        }
        return null;
    }
});