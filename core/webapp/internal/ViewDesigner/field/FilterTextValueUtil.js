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

        // replace ; with \n on UI
        if (filterType.isMultiValued() && (urlSuffix !== 'notbetween' && urlSuffix !== 'between')) {
            if (typeof valueString === 'string' && valueString.indexOf('\n') === -1 && valueString.indexOf(';') > 0)
                valueString = valueString.replaceAll(';', '\n');
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
        try {
            var filter = this.createFilter(valueString);
            var filterValue = filter.getValue();
        }
        catch (e) {
            console.warn("Error parsing filter value: " + valueString);
            filterValue = valueString;
        }

        this.record.get("items")[this.clauseIndex].value = filterValue;
    }
});