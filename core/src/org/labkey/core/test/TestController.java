/*
 * Copyright (c) 2007-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.core.test;

import org.labkey.api.action.FormArrayList;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.Button;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * User: matthewb
 * Date: Sep 6, 2007
 * Time: 8:55:54 AM
 *
 * This controller is for testing the controller framework including
 *
 *  actions
 *  jsp
 *  tags
 *  error handling
 *  binding
 *  exceptions 
 */
public class TestController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(TestController.class);

    public TestController() throws Exception
    {
        setActionResolver(_actionResolver);
    }


    private NavTree navTrail(NavTree root, String currentActionName)
    {
        (new BeginAction()).appendNavTrail(root);
        root.addChild(currentActionName);

        return root;
    }

    // Test action
    @RequiresPermission(ReadPermission.class)
    public class TestAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            // Add code here

            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    private ActionURL actionURL(Class<? extends Controller> actionClass)
    {
        return new ActionURL(actionClass, getContainer());
    }


    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new ActionListView();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Test Actions", actionURL(BeginAction.class));
            return root;
        }
    }


    private static final List<ViewContext> LEAKED_VIEW_CONTEXTS = new LinkedList<>();

    /**
     * Invoking this action leaks a single ViewContext. Clear leaks by invoking ClearLeaksAction.
     */
    @RequiresPermission(AdminPermission.class)
    public class LeakAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ViewContext ctx = getViewContext();
            LEAKED_VIEW_CONTEXTS.add(ctx);
            return new HtmlView("One ViewContext leaked: " + ctx.toString());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return navTrail(root, "Leak");
        }
    }


    /**
     * Clears all ViewContexts leaked by ClearLeaksAction.
     */
    @RequiresPermission(AdminPermission.class)
    public class ClearLeaksAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            int count = LEAKED_VIEW_CONTEXTS.size();
            LEAKED_VIEW_CONTEXTS.clear();
            return new HtmlView("Cleared " + count + " leaked ViewContexts");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return navTrail(root, "Clear Leaks");
        }
    }


    public class ActionListView extends HttpView
    {
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            List<ActionDescriptor> descriptors = new ArrayList<>(_actionResolver.getActionDescriptors());
            descriptors.sort(Comparator.comparing(ActionDescriptor::getPrimaryName));

            for (ActionDescriptor ad : descriptors)
            {
                out.print("<a href=\"");
                out.print(PageFlowUtil.filter(actionURL(ad.getActionClass())));
                out.print("\">");
                out.print(PageFlowUtil.filter(ad.getPrimaryName()));
                out.print("</a><br>");
            }
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class SimpleFormAction extends FormViewAction<SimpleForm>
    {
        String _enctype = "application/x-www-form-urlencoded";
        
        public void validateCommand(SimpleForm form, Errors errors)
        {
            form.validate(errors);
        }

        public ModelAndView getView(SimpleForm form, boolean reshow, BindException errors) throws Exception
        {
            form.encType = _enctype;
            return jspView("form.jsp", form, errors);
        }

        public boolean handlePost(SimpleForm simpleForm, BindException errors) throws Exception
        {
            return false;
        }

        public ActionURL getSuccessURL(SimpleForm form)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return navTrail(root, "Form Test (" + _enctype + ")");
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class TagsAction extends FormViewAction<SimpleForm>
    {
        public void validateCommand(SimpleForm target, Errors errors)
        {
            target.validate(errors);
        }

        public ModelAndView getView(SimpleForm simpleForm, boolean reshow, BindException errors) throws Exception
        {
            ModelAndView mv = jspView("tags.jsp", simpleForm, errors);
            return mv;
        }

        public boolean handlePost(SimpleForm simpleForm, BindException errors) throws Exception
        {
            return false;
        }

        public ActionURL getSuccessURL(SimpleForm simpleForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return navTrail(root, "Spring tags test");
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class MultipartFormAction extends SimpleFormAction
    {
        public MultipartFormAction()
        {
            _enctype ="multipart/form-data";
        }

        public boolean handlePost(SimpleForm simpleForm, BindException errors) throws Exception
        {
            return false;
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class ComplexFormAction extends FormViewAction<ComplexForm>
    {
        public void validateCommand(ComplexForm target, Errors errors)
        {
            ArrayList<TestBean> beans = target.getBeans();
            for (int i=0 ; i<beans.size(); i++)
            {
                errors.pushNestedPath("beans["+i+"]");
                beans.get(i).validate(errors);
                errors.popNestedPath();
            }
        }

        public ModelAndView getView(ComplexForm complexForm, boolean reshow, BindException errors) throws Exception
        {
            if (complexForm.getBeans().size() == 0)
            {
                ArrayList<TestBean> a = new ArrayList<>(2);
                a.add(new TestBean());
                a.add(new TestBean());
                complexForm.setBeans(a);
                complexForm.setStrings(new String[2]);
            }
            return jspView("complex.jsp", complexForm, errors);
        }

        public boolean handlePost(ComplexForm complexForm, BindException errors) throws Exception
        {
            return false;
        }

        public ActionURL getSuccessURL(ComplexForm complexForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return navTrail(root, "Form Test");
        }
    }


    public static class TestBean
    {
        public boolean getA()
        {
            return _a;
        }

        public void setA(boolean a)
        {
            _a = a;
        }

        public String getB()
        {
            return _b;
        }

        public void setB(String b)
        {
            _b = b;
        }

        public String getC()
        {
            return _c;
        }

        public void setC(String c)
        {
            _c = c;
        }

        public int getInt()
        {
            return _int;
        }

        public void setInt(int anInt)
        {
            _int = anInt;
        }

        public int getPositive()
        {
            return _positive;
        }

        public void setPositive(int positive)
        {
            _positive = positive;
        }

        public Integer getInteger()
        {
            return _integer;
        }

        public void setInteger(Integer integer)
        {
            _integer = integer;
        }

        public String getString()
        {
            return _string;
        }

        public void setString(String string)
        {
            _string = string;
        }

        public String getRequired()
        {
            return _required;
        }

        public void setRequired(String required)
        {
            _required = required;
        }

        public String getText()
        {
            return _text;
        }

        public void setText(String text)
        {
            _text = text;
        }

        public String getX()
        {
            return _x;
        }

        public void setX(String x)
        {
            _x = x;
        }

        public String getY()
        {
            return _y;
        }

        public void setY(String y)
        {
            _y = y;
        }

        public String getZ()
        {
            return _z;
        }

        public void setZ(String z)
        {
            _z = z;
        }

        private boolean _a = true;  // default to true to test clearing
        private String _b;
        private String _c;
        private int _int;
        private int _positive;
        private Integer _integer;
        private String _string;
        private String _required;
        private String _text;
        private String _x;
        private String _y;
        private String _z;

        void validate(Errors errors)
        {
            if (_positive < 0)
                    errors.rejectValue("positive", ERROR_MSG, "Value must not be less than zero");
            if (null == _required)
                errors.rejectValue("required", ERROR_REQUIRED);
        }
    }


    public static class SimpleForm extends TestBean
    {
        public String encType = null;
    }


    public static class ComplexForm
    {
        private String[] strings = new String[3];

        private ArrayList<TestBean> beans = new FormArrayList<>(TestBean.class);

        public String[] getStrings()
        {
            return strings;
        }

        public void setStrings(String[] strings)
        {
            this.strings = strings;
        }

        public ArrayList<TestBean> getBeans()
        {
            return beans;
        }

        public void setBeans(ArrayList<TestBean> beans)
        {
            this.beans = beans;
        }
    }


    @CSRF @RequiresNoPermission
    public class CSRFAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new HtmlView("SUCCESS - CSRF");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return navTrail(root, "CSRF test");
        }
    }


    public abstract class PermAction extends SimpleViewAction
    {
        String _action;

        PermAction(String action)
        {
            _action = action;
        }

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new HtmlView("SUCCESS you can " + _action);
        }


        public NavTree appendNavTrail(NavTree root)
        {
            return navTrail(root, "perm " + _action + " test");
        }
    }


    @RequiresNoPermission
    public class PermNoneAction extends PermAction
    {
        public PermNoneAction()
        {
            super("none");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class PermReadAction extends PermAction
    {
        public PermReadAction()
        {
            super("read");
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class PermUpdateAction extends PermAction
    {
        public PermUpdateAction()
        {
            super("update");
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class PermInsertAction extends PermAction
    {
        public PermInsertAction()
        {
            super("insert");
        }
    }

    @RequiresPermission(DeletePermission.class)
    public class PermDeleteAction extends PermAction
    {
        public PermDeleteAction()
        {
            super("delete");
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class PermAdminAction extends PermAction
    {
        public PermAdminAction()
        {
            super("admin");
        }
    }


    JspView jspView(String name, Object model, Errors errors)
    {
        //noinspection unchecked
        return new JspView(TestController.class, name, model, errors);
    }


    public static class ExceptionForm
    {
        private String _message;

        public String getMessage()
        {
            return _message;
        }

        public void setMessage(String message)
        {
            _message = message;
        }
    }


    // NpeAction, IllegalStateAction, ConfigurationExceptionAction, NotFoundAction, and UnauthorizedAction
    // allow simple testing of exception logging and display, both with and without a message

    @RequiresSiteAdmin
    public class NpeAction extends SimpleViewAction<ExceptionForm>
    {
        public ModelAndView getView(ExceptionForm form, BindException errors) throws Exception
        {
            NullPointerException npe;
            if (null == form.getMessage())
                npe = new NullPointerException();
            else
                npe = new NullPointerException(form.getMessage());
            ExceptionUtil.decorateException(npe, ExceptionUtil.ExceptionInfo.ExtraMessage, "testing", true);
            throw npe;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class NpeOtherAction extends SimpleViewAction<ExceptionForm>
    {
        public ModelAndView getView(ExceptionForm form, BindException errors) throws Exception
        {
            NullPointerException npe;
            if (null == form.getMessage())
                npe = new NullPointerException();
            else
                npe = new NullPointerException(form.getMessage());
            ExceptionUtil.decorateException(npe, ExceptionUtil.ExceptionInfo.ExtraMessage, "testing", true);
            throw npe;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class MultiExceptionAction extends SimpleViewAction<ExceptionForm>
    {
        public ModelAndView getView(ExceptionForm form, BindException errors) throws Exception
        {
            Exception exception;
            switch (form.getMessage())
            {
                case "ISE":
                    exception = new IllegalStateException();
                    break;
                case "NPE":
                    exception = new NullPointerException();
                    break;
                case "NPE2":
                    exception = new NullPointerException();
                    break;
                default:
                    throw new IllegalArgumentException(form.getMessage());
            }
            ExceptionUtil.decorateException(exception, ExceptionUtil.ExceptionInfo.ExtraMessage, "testing", true);
            throw exception;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class IllegalStateAction extends SimpleViewAction<ExceptionForm>
    {
        public ModelAndView getView(ExceptionForm form, BindException errors) throws Exception
        {
            IllegalStateException ise;
            if (null == form.getMessage())
                ise = new IllegalStateException();
            else
                ise = new IllegalStateException(form.getMessage());
            ExceptionUtil.decorateException(ise, ExceptionUtil.ExceptionInfo.ExtraMessage, "testing", true);
            throw ise;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class ConfigurationExceptionAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            throw new ConfigurationException("You have a configuration problem.", "What will make things better is if you stop visiting this action.");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class NotFoundAction extends SimpleViewAction<ExceptionForm>
    {
        public ModelAndView getView(ExceptionForm form, BindException errors) throws Exception
        {
            if (null == form.getMessage())
                throw new NotFoundException();
            else
                throw new NotFoundException(form.getMessage());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class UnauthorizedAction extends SimpleViewAction<ExceptionForm>
    {
        public ModelAndView getView(ExceptionForm form, BindException errors) throws Exception
        {
            if (null == form.getMessage())
                throw new UnauthorizedException();
            else
                throw new UnauthorizedException(form.getMessage());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class HtmlViewAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String title = getViewContext().getActionURL().getParameter("title");
            return new HtmlView(title, "This is my HTML");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ButtonAction extends SimpleViewAction<ButtonForm>
    {
        @Override
        public ModelAndView getView(ButtonForm form, BindException errors) throws Exception
        {
            if (isPost())
            {
                Button.ButtonBuilder button = PageFlowUtil.button(form.getText())
                        .textAsHTML(form.isHtml())
                        .href(form.getHref())
                        .enabled(form.isEnabled())
                        .disableOnClick(form.isDisableonclick())
                        .submit(form.isButtonsubmit())
                        .onClick(form.getOnclick());

                String attr = "";
                if (form.getAttrkey1() != null)
                    attr += form.getAttrkey1() + "=" + form.getAttrvalue1();
                if (form.getAttrkey2() != null)
                    attr += form.getAttrkey2() + "=" + form.getAttrvalue2();
                if (!"".equals(attr))
                    button.attributes(attr);

                form.setBuiltButton(button);
            }
            else
            {
                // default values
                form.setText("button text");
                form.setEnabled(true);
                form.setOnclick("alert('Button Testing');");
                form.setAttrkey1("target");
                form.setAttrvalue1("_blank");
            }

            return new JspView<>("/org/labkey/core/test/buttons.jsp", form);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class ButtonForm
    {
        private String _text;
        private String _href;
        private String _onclick;
        private String _attrkey1;
        private String _attrkey2;
        private String _attrvalue1;
        private String _attrvalue2;
        private boolean _disableonclick;
        private boolean _html;
        private boolean _enabled;
        private boolean _buttonsubmit;
        private Button.ButtonBuilder builtButton;


        public Button.ButtonBuilder getBuiltButton()
        {
            return builtButton;
        }

        public void setBuiltButton(Button.ButtonBuilder builtButton)
        {
            this.builtButton = builtButton;
        }

        public String getAttrkey1()
        {
            return _attrkey1;
        }

        public void setAttrkey1(String attrkey1)
        {
            _attrkey1 = attrkey1;
        }

        public String getAttrkey2()
        {
            return _attrkey2;
        }

        public void setAttrkey2(String attrkey2)
        {
            _attrkey2 = attrkey2;
        }

        public String getAttrvalue1()
        {
            return _attrvalue1;
        }

        public void setAttrvalue1(String attrvalue1)
        {
            _attrvalue1 = attrvalue1;
        }

        public String getAttrvalue2()
        {
            return _attrvalue2;
        }

        public void setAttrvalue2(String attrvalue2)
        {
            _attrvalue2 = attrvalue2;
        }

        public String getOnclick()
        {
            return _onclick;
        }

        public void setOnclick(String onclick)
        {
            _onclick = onclick;
        }

        public boolean isButtonsubmit()
        {
            return _buttonsubmit;
        }

        public void setButtonsubmit(boolean buttonsubmit)
        {
            _buttonsubmit = buttonsubmit;
        }

        public boolean isEnabled()
        {
            return _enabled;
        }

        public void setEnabled(boolean enabled)
        {
            _enabled = enabled;
        }

        public boolean isDisableonclick()
        {
            return _disableonclick;
        }

        public void setDisableonclick(boolean disableonclick)
        {
            _disableonclick = disableonclick;
        }

        public String getHref()
        {
            return _href;
        }

        public void setHref(String href)
        {
            _href = href;
        }

        public boolean isHtml()
        {
            return _html;
        }

        public void setHtml(boolean html)
        {
            _html = html;
        }

        public String getText()
        {
            return _text;
        }

        public void setText(String text)
        {
            _text = text;
        }
    }
}
