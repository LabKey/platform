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
        isHTML = new RegExp(['<a', '<table', '<div', '<span'].join('|'));

    // Not all message board configurations include the rendererType option
    if (renderTypeEl && renderTypeEl.value != 'HTML' && isHTML.test(text))
    {
        var currentTypeDescription = renderTypeEl.options[renderTypeEl.selectedIndex].text;
        var msg = 'The content of your message may contain HTML. Are you sure that you want to submit it as ' + currentTypeDescription + '?';
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

Ext4.onReady(function() {
    Ext4.create('Ext.resizer.Resizer', {
        el: Ext4.getBody(),
        handles: 'se',
        minWidth: 200,
        minHeight: 100,
        wrap: true
    });
});