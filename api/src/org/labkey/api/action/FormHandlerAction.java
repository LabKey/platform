package org.labkey.api.action;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.labkey.api.view.NavTree;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jun 21, 2007
 * Time: 11:14:21 AM
 *
 * Like FormViewAction but does not show a form.  Only handles post and redirects.
 */
public abstract class FormHandlerAction<FORM> extends FormViewAction<FORM>
{
    public final ModelAndView getView(FORM form, boolean reshow, BindException errors) throws Exception
    {
        return new SimpleErrorView(errors);
    }

    public final NavTree appendNavTrail(NavTree root)
    {
        return root;
    }
}
