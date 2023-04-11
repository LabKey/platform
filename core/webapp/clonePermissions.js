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

function createCloneUserField(disabled, includeInactive)
{
    const tagConfig = {
        tag: 'input',
        id: 'cloneUser',
        type: 'text',
        name: 'cloneUser',
        style: 'width: 303px;',
        autocomplete: 'off'
    };

    if (disabled)
        tagConfig.disabled = true;

    Ext4.create('LABKEY.element.AutoCompletionField', {
        renderTo: 'auto-completion-div',
        completionUrl: LABKEY.ActionURL.buildURL('security', 'completeUser.api', null, {includeInactive: includeInactive}),
        tagConfig: tagConfig
    });
}