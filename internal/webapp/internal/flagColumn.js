/*
 * Copyright (c) 2015-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.internal.FlagColumn', {

    extend: 'Ext.Component',

    statics: {
        _showDialog : function(config) {
            var flagColumn = Ext4.create('LABKEY.internal.FlagColumn', config);
            return Ext4.bind(flagColumn.setFlag, flagColumn);
        }
    },

    dataRegionName: undefined,

    defaultComment: 'Flagged for review',

    dialogTitle: 'Review',

    imgClassFlagged: '',

    imgClassUnflagged: '',

    imgSrcFlagged: LABKEY.contextPath + '/Experiment/flagDefault.gif',

    imgSrcUnflagged: LABKEY.contextPath + '/Experiment/unflagDefault.gif',

    imgTitle: 'Flag for review',

    translatePrimaryKey: undefined,

    url: LABKEY.ActionURL.buildURL('experiment', 'setFlag.api'),

    getDataRegion : function() {
        var region;
        if (LABKEY.DataRegions && LABKEY.Utils.isString(this.dataRegionName)) {
            region = LABKEY.DataRegions[this.dataRegionName];
        }
        return region;
    },

    setFlag : function(flagId) {
        Ext4.QuickTips.init();

        var clickedComment,
            flagImages = Ext4.DomQuery.select('img[flagId="' + flagId + '"');

        if (Ext4.isEmpty(flagImages)) {
            return;
        }

        var img = flagImages[0];

        if (img.title != this.imgTitle) {
            clickedComment = img.title;
        }

        var checkedLsids = [],
            dr = this.getDataRegion(),
            msg = 'Enter a comment',
            comment = clickedComment || this.defaultComment,
            lsids;

        if (dr && Ext4.isFunction(this.translatePrimaryKey)) {
            var keys = dr.getChecked();
            for (var i=0; i < keys.length; i++) {
                checkedLsids.push(this.translatePrimaryKey(keys[i]));
            }
        }

        if (Ext4.isEmpty(checkedLsids)) {
            lsids = [flagId];
        }
        else {
            msg = 'Enter comment for ' + checkedLsids.length + ' selected ' + (checkedLsids.length === 1 ? 'row' : 'rows');
            comment = this.defaultComment; // consider inspect all for equal comments
            lsids = checkedLsids;
        }

        Ext4.Msg.show({
            title: this.dialogTitle,
            prompt: true,
            msg: msg,
            value: comment,
            width: 300,
            buttons: Ext4.Msg.OKCANCEL,
            fn : function(btnId, value) {
                if (btnId === 'ok') {
                    Ext4.Ajax.request({
                        url: this.url,
                        params: {
                            lsid: lsids,
                            comment: value,
                            unique: new Date().getTime()
                        },
                        success: function(response, options) {
                            var comment = options.params.comment,
                                lsid,
                                flagImages,
                                el;

                            for (var i=0; i < lsids.length; i++) {
                                lsid = lsids[i];
                                flagImages = Ext4.DomQuery.select("img[flagId='" + lsid + "']");
                                if (!Ext4.isEmpty(flagImages)) {
                                    el = Ext4.get(flagImages[0]);
                                    if (comment) {
                                        el.set({
                                            src: this.imgSrcFlagged,
                                            title: comment
                                        });
                                        if (this.imgClassUnflagged) {
                                            el.removeCls(this.imgClassUnflagged);
                                        }
                                        el.addCls(this.imgClassFlagged);
                                    }
                                    else {
                                        el.set({
                                            src: this.imgSrcUnflagged,
                                            title: this.imgTitle
                                        });
                                        if (this.imgClassFlagged) {
                                            el.removeCls(this.imgClassFlagged);
                                        }
                                        el.addCls(this.imgClassUnflagged);
                                    }
                                }
                            }
                        },
                        failure: function() {
                            alert('Failure!'); // TODO: We can do better than this
                        },
                        scope: this
                    });
                }
            },
            scope: this
        });

        return false;
    }
});