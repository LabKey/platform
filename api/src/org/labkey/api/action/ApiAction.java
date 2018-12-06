package org.labkey.api.action;


/**
 * @deprecated extend ReadOnlyApiAction or MutatingApiAction instead
 */
@Deprecated
public abstract class ApiAction<FORM> extends ReadOnlyApiAction<FORM>
{
    @Deprecated
    public ApiAction()
    {
        super();
    }

    @Deprecated
    public ApiAction(Class<? extends FORM> formClass)
    {
        super(formClass);
    }
}