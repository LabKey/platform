/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
if (!LABKEY.discuss) {
    LABKEY.discuss = {};
}

LABKEY.discuss.validate = function(form)
{
    var trimmedTitle = form.title.value.trim(),
        submitBtn = Ext4.get('submitButton');

    if (trimmedTitle.length == 0)
    {
        Ext4.Msg.alert('Error', 'Title must not be blank.');
        if (submitBtn) {
            submitBtn.replaceCls('labkey-disabled-button', 'labkey-button');
        }
        return false;
    }

    var text = document.getElementById('body').value.toLowerCase(),
        renderTypeEl = document.getElementById('rendererType'),
        isHTML = new RegExp(['<a', '<table', '<div', '<span'].join('|')),
        // Look for double-backslashes at the end of a line, double stars (bold) or tildes (italics) around anything,
        isWiki = new RegExp(['\\\\\\\\[\\n\\r]', '\\*\\*.*\\*\\*', '\\~\\~.*\\~\\~'].join('|')),
        // Look for underscores around anything or # at the start of a line or three slanted ticks before and after anything,
        isMarkup = new RegExp(['_.*_', '^#', '\\`\\`\\`.*\\`\\`\\`'].join('|'));

    var msg = null;
    // Not all message board configurations include the rendererType option
    // Need to keep the values in sync with the enum org.labkey.api.wiki.WikiRendererType
    if (renderTypeEl && renderTypeEl.value != 'HTML' && isHTML.test(text))
    {
        msg = 'The content of your message may contain HTML. Are you sure that you want to submit it as ' + renderTypeEl.options[renderTypeEl.selectedIndex].text + '?';
    }
    else if (renderTypeEl && renderTypeEl.value != 'RADEOX' && isWiki.test(text))
    {
        msg = 'The content of your message may contain Wiki markup. Are you sure that you want to submit it as ' + renderTypeEl.options[renderTypeEl.selectedIndex].text + '?';
    }
    else if (renderTypeEl && renderTypeEl.value != 'MARKDOWN' && isMarkup.test(text))
    {
        msg = 'The content of your message may contain Markdown markup. Are you sure that you want to submit it as ' + renderTypeEl.options[renderTypeEl.selectedIndex].text + '?';
    }

    if (msg)
    {
        Ext4.Msg.confirm('Confirm message formatting', msg,
                function (btn) {
                    if (btn == 'yes') {
                        form.submit();
                    }
                    else if (submitBtn) {
                        submitBtn.replaceCls('labkey-disabled-button', 'labkey-button');
                    }
                });
        return false;
    }

    return true;
};

LABKEY.discuss.removeAttachment = function(eid, name, xid) {
    Ext4.Msg.show({
        title: 'Remove Attachment',
        msg: 'Please confirm you would like to remove this attachment. This cannot be undone.',
        buttons: Ext4.Msg.OKCANCEL,
        icon: Ext4.Msg.QUESTION,
        fn : function(b) {
            if (b == 'ok') {
                Ext4.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('announcements', 'deleteAttachment'),
                    method: 'POST',
                    success: function() {
                        var el = document.getElementById(xid);
                        if (el) {
                            el.parentNode.removeChild(el);
                        }
                    },
                    failure: function() {
                        alert('Failed to remove attachment.');
                    },
                    params: {
                        entityId: eid,
                        name: name
                    }
                });
            }
        }
    });
};

(function($) {

    var _init = function() {
        $('a[data-toggle="tab"]').on('show.bs.tab', function (e) {
            var currentText = $("#body")[0].value;
            if (e.target.hash === '#preview') {
                _convertFormat('MARKDOWN', 'HTML', currentText, 'preview');
            }
        });

        $('#rendererType').on('change', function(selectObject) {_rendererChange(this)
        });

        _rendererChange($('#rendererType')[0]);
    };

    var _hideAllHelperText = function() {
        $('div[class="help-MARKDOWN"]').hide();
        $('div[class="help-HTML"]').hide();
        $('div[class="help-RADEOX"]').hide();
        $('div[class="help-TEXT_WITH_LINKS"]').hide();
    };

    var _hideSourceAndPreviewTabs = function() {
        $('#messageTabs').hide(); // hide the tabs
        $('#messageTabs li:first-child a').tab('show') // force select of first tab panel
    };

    var _rendererChange =  function(selectObject) {
        var selectedRenderer = selectObject ? selectObject.value : undefined;

        _hideAllHelperText();
        _hideSourceAndPreviewTabs();
        if (selectedRenderer == 'MARKDOWN') {
            $('#messageTabs').show(); // show source and preview tabs
            $('div[class="help-MARKDOWN"]').show();
        }
        else if (selectedRenderer == 'HTML') {
            $('div[class="help-HTML"]').show();
        }
        else if (selectedRenderer == 'RADEOX') {
            $('div[class="help-RADEOX"]').show();
        }
        else if (selectedRenderer == 'TEXT_WITH_LINKS') {
            $('div[class="help-TEXT_WITH_LINKS"]').show();
        }
    };

    var _convertFormat = function(fromFormat, toFormat, body, elementToUpdate) {
        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL("wiki", "transformWiki"),
            method : 'POST',
            jsonData : {
                body: body,
                fromFormat: fromFormat,
                toFormat: toFormat
            },
            success: function (response) {_onConvertSuccess(response, elementToUpdate)},
            failure: LABKEY.Utils.getCallbackWrapper(function(exceptionInfo) {
                LABKEY.Utils.alert('Error', 'Unable to convert your page to the new format for the following reason: ' + exceptionInfo.exception);
            }, this, true)
        });
    };

    var _onConvertSuccess = function(response, elementToUpdate) {
        var respJson = LABKEY.Utils.decode(response.responseText);

        if (respJson.toFormat == "HTML") {
             document.getElementById(elementToUpdate).innerHTML = respJson.body;
        }
    };

    LABKEY.Utils.onReady(_init);
})(jQuery);