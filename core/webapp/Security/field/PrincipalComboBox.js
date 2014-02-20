/*
 * Copyright (c) 2012-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('Security.field.PrincipalComboBox', {

    extend: 'Ext.form.field.ComboBox',

    alias: 'widget.labkey-principalcombo',

    excludedPrincipals : null,
    queryMode: 'local',

    groupsOnly : false,
    usersOnly : false,
    unfilteredStore : null,

    tpl : new Ext4.XTemplate(
        '<ul>',
        '<tpl for=".">',
            '<li role="option" class="x4-boundlist-item {[this.typeCls(values)]}">{[this.prefixSite(values)]}</li>',
        '</tpl>',
        '</ul>',
        {
            typeCls : function(values) {
                var c = 'pGroup';
                if (values.Type == 'u')
                    c = 'pUser';
                else if (!values.Container)
                    c = 'pSite';
                return c;
            },

            prefixSite : function(values) {
                var value = Ext4.String.htmlEncode(values.Name);
                if (values.Type == 'g' && !values.Container)
                {
                    value = "Site: " + value;
                }
                else if (values.Type == 'u' && values.DisplayName && values.DisplayName.length > 0 && values.Name != values.DisplayName)
                {
                    // issue 17704, add display name to users
                    value += " (" + Ext4.String.htmlEncode(values.DisplayName) + ")";
                }

                return value;
            }
        }
    ),

    constructor : function(config)
    {
        var a = Ext4.isArray(config.excludedPrincipals) ? config.excludedPrincipals : [], i;
        a.push(Security.util.SecurityCache.groupDevelopers);
        a.push(Security.util.SecurityCache.groupAdministrators);

        delete config.excludedPrincipals;
        this.excludedPrincipals = {};

        for (i=0 ; i<a.length ; i++) {
            this.excludedPrincipals[a[i]] = true;
        }

        config = Ext4.apply({}, config, {
            store          : config.cache.principalsStore,
            minListWidth   : 200,
            width          : 250, // without width won't render correctly if PolicyEditor is not showing initially
            triggerAction  : 'all',
            forceSelection : true,
            typeAhead      : true,
            displayField   : 'Name',
            emptyText : config.groupsOnly ? 'Add group...' : config.usersOnly ? 'Add user...' : 'Add user or group...',
            itemId    : 'Users_dropdownMenu'
        });

        this.callParent([config]);
    },

    onDataChanged : function(store)
    {
        if (store) {
            this.callParent([store, false]);
            this.bindStore(store);
        }
    },

    bindStore : function(store, initial)
    {
        // issue 19328: don't bind store on typeAhead
        if (store && store.filters.length > 0)
            return;

        if (this.unfilteredStore)
        {
            this.unfilteredStore.removeListener("add",         this.onDataChanged, this);
            this.unfilteredStore.removeListener("datachanged", this.onDataChanged, this);
            this.unfilteredStore.removeListener("remove",      this.onDataChanged, this);
        }
        // UNDONE: ComboBox does not lend itself to filtering, but this is expensive!
        // UNDONE: would be nice to share a filtered DataView like object across the PrincipalComboBoxes
        // CONSIDER only store UserId and lookup record in XTemplate
        if (store)
        {
            this.unfilteredStore = store;
            this.unfilteredStore.addListener("add",         this.onDataChanged, this);
            this.unfilteredStore.addListener("datachanged", this.onDataChanged, this);
            this.unfilteredStore.addListener("remove",      this.onDataChanged, this);
            var type = this.groupsOnly ? 'groups' : this.usersOnly ? 'users' : null;
            store = Security.store.SecurityCache.filterPrincipalsStore(store, type, null, this.excludedPrincipals);
        }

        this.callParent(arguments);
    }
});
