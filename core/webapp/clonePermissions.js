function showUserAccess()
{
    const textElem = document.getElementById("cloneUser");
    if (textElem != null)
    {
        if (textElem.value != null && textElem.value.length > 0)
        {
            const target = LABKEY.ActionURL.buildURL('user', 'userAccess.api', null, {renderInHomeTemplate: false, newEmail: textElem.value});
            window.open(target, "permissions", "height=450,width=500,scrollbars=yes,status=yes,toolbar=no,menubar=no,location=no,resizable=yes");
        }
    }
}

function createCloneUserField(disabled, includeInactive, excludeSiteAdmins, excludeUserId)
{
    const tagConfig = {
        tag: 'input',
        id: 'sourceUser',
        type: 'text',
        name: 'sourceUser',
        style: 'width: 303px;',
        autocomplete: 'off'
    };

    // Note: Any mention of "disabled" in the config (even disabled: false) results in a disabled element
    if (disabled)
        tagConfig.disabled = true;

    const params = {
        includeInactive: includeInactive
    }

    if (excludeSiteAdmins)
        params.excludeSiteAdmins = 1;

    if (excludeUserId)
        params.excludeUsers = excludeUserId;

    Ext4.create('LABKEY.element.AutoCompletionField', {
        renderTo: 'auto-completion-div',
        completionUrl: LABKEY.ActionURL.buildURL('security', 'completeUser.api', null, params),
        tagConfig: tagConfig
    });
}