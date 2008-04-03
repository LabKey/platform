package org.labkey.api.action;

import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 17, 2007
 * Time: 12:52:55 PM
 *
 * Is this better than BaseCommandController?  Probably not, but it understands TableViewForm.
 *
 * CONSIDER: make a subclass of BaseCommandController
 */
public abstract class FormViewAction<FORM> extends BaseViewAction<FORM> implements NavTrailAction
{
    boolean _reshow = false;

    public FormViewAction()
    {
    }

    public FormViewAction(Class<? extends FORM> commandClass)
    {
        super(commandClass);
    }

    protected void setReshow(boolean reshow)
    {
        _reshow = reshow;
    }

    protected boolean getReshow()
    {
        return _reshow;
    }

    public final ModelAndView handleRequest() throws Exception
    {
        FORM form = null;
        BindException errors = null;
        if (null != getCommandClass())
        {
            errors = bindParameters(getPropertyValues());
            form = (FORM)errors.getTarget();
        }

        return handleRequest(form, errors);
    }

    public ModelAndView handleRequest(FORM form, BindException errors) throws Exception
    {
        boolean success = errors == null || !errors.hasErrors();

        if ("POST".equals(getViewContext().getRequest().getMethod()))
        {
            setReshow(true);

            if (success && null != form)
                validate(form, errors);
            success = errors == null || !errors.hasErrors();

            if (success)
                success = handlePost(form, errors);

            if (success)
            {
                ActionURL url = getSuccessURL(form);
                if (null != url)
                    return HttpView.redirect(url);
                ModelAndView successView = getSuccessView(form);
                if (null != successView)
                    return successView;
            }
        }

        return getView(form, getReshow(), errors);
    }


    protected String getCommandClassMethodName()
    {
        return "handlePost";
    }

    public BindException bindParameters(PropertyValues m) throws Exception
    {
        return defaultBindParameters(getCommand(), m);
    }


    public void validate(Object target, Errors errors)
    {
        if (target instanceof HasValidator)
            ((HasValidator)target).validateSpring(errors);
        validateCommand((FORM)target, errors);
    }

    /* Generic version of validate */
    public abstract void validateCommand(FORM target, Errors errors);
    
    public abstract ModelAndView getView(FORM form, boolean reshow, BindException errors) throws Exception;


    /**
     * may call throwRedirect() on success
     * 
     * handlePost() can call setReshow(false) to force record to be reselected
     * return a view to display or null to call getView(form,true);
     */
    public abstract boolean handlePost(FORM form, BindException errors) throws Exception;

    public abstract ActionURL getSuccessURL(FORM form);

    // not usually used but some actions return views that close the current window etc...
    public ModelAndView getSuccessView(FORM form)
    {
        return null;
    }

    protected Map<String, MultipartFile> getFileMap()
    {
        if (getViewContext().getRequest() instanceof MultipartHttpServletRequest)
            return (Map<String, MultipartFile>)((MultipartHttpServletRequest)getViewContext().getRequest()).getFileMap();
        return Collections.emptyMap();
    }

    protected List<AttachmentFile> getAttachmentFileList()
    {
        return SpringAttachmentFile.createList(getFileMap());
    }
}
