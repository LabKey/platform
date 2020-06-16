/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.devtools;

import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormArrayList;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.MethodsAllowed;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Button;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DOM;
import static org.labkey.api.util.DOM.Attribute.*;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.labkey.api.util.DOM.BR;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.IFRAME;
import static org.labkey.api.util.DOM.INPUT;
import static org.labkey.api.util.DOM.TABLE;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.PageFlowUtil.filter;

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
    public static final String NAME = "test";

    public TestController()
    {
        setActionResolver(_actionResolver);
    }

    private void navTrail(NavTree root, String currentActionName)
    {
        (new BeginAction()).addNavTrail(root);
        root.addChild(currentActionName);
    }

    private ActionURL actionURL(Class<? extends Controller> actionClass)
    {
        return new ActionURL(actionClass, getContainer());
    }


    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new ActionListView(TestController.this);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Test Actions", actionURL(BeginAction.class));
        }
    }


    private static final List<ViewContext> LEAKED_VIEW_CONTEXTS = new LinkedList<>();

    /**
     * Invoking this action leaks a single ViewContext. Clear leaks by invoking ClearLeaksAction.
     */
    @RequiresPermission(AdminPermission.class)
    public class LeakAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            ViewContext ctx = getViewContext();
            LEAKED_VIEW_CONTEXTS.add(ctx);
            return new HtmlView("One ViewContext leaked: " + ctx.toString());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            navTrail(root, "Leak");
        }
    }


    /**
     * Clears all ViewContexts leaked by ClearLeaksAction.
     */
    @RequiresPermission(AdminPermission.class)
    public class ClearLeaksAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            int count = LEAKED_VIEW_CONTEXTS.size();
            LEAKED_VIEW_CONTEXTS.clear();
            return new HtmlView("Cleared " + count + " leaked ViewContexts");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            navTrail(root, "Clear Leaks");
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class SimpleFormAction extends FormViewAction<SimpleForm>
    {
        String _enctype = "application/x-www-form-urlencoded";

        @Override
        public void validateCommand(SimpleForm form, Errors errors)
        {
            form.validate(errors);
        }

        @Override
        public ModelAndView getView(SimpleForm form, boolean reshow, BindException errors)
        {
            form.encType = _enctype;
            return new JspView<>("/org/labkey/devtools/view/form.jsp", form, errors);
        }

        @Override
        public boolean handlePost(SimpleForm simpleForm, BindException errors)
        {
            return false;
        }

        @Override
        public ActionURL getSuccessURL(SimpleForm form)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            navTrail(root, "Form Test (" + _enctype + ")");
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class TagsAction extends FormViewAction<SimpleForm>
    {
        @Override
        public void validateCommand(SimpleForm target, Errors errors)
        {
            target.validate(errors);
        }

        @Override
        public ModelAndView getView(SimpleForm simpleForm, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/devtools/view/tags.jsp", simpleForm, errors);
        }

        @Override
        public boolean handlePost(SimpleForm simpleForm, BindException errors)
        {
            return false;
        }

        @Override
        public ActionURL getSuccessURL(SimpleForm simpleForm)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            navTrail(root, "Spring tags test");
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class MultipartFormAction extends SimpleFormAction
    {
        public MultipartFormAction()
        {
            _enctype = "multipart/form-data";
        }

        @Override
        public boolean handlePost(SimpleForm simpleForm, BindException errors)
        {
            return false;
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class ComplexFormAction extends FormViewAction<ComplexForm>
    {
        @Override
        public void validateCommand(ComplexForm target, Errors errors)
        {
            ArrayList<TestBean> beans = target.getBeans();
            for (int i = 0; i < beans.size(); i++)
            {
                errors.pushNestedPath("beans[" + i + "]");
                beans.get(i).validate(errors);
                errors.popNestedPath();
            }
        }

        @Override
        public ModelAndView getView(ComplexForm complexForm, boolean reshow, BindException errors)
        {
            if (complexForm.getBeans().size() == 0)
            {
                ArrayList<TestBean> a = new ArrayList<>(2);
                a.add(new TestBean());
                a.add(new TestBean());
                complexForm.setBeans(a);
                complexForm.setStrings(new String[2]);
            }
            return new JspView<>("/org/labkey/devtools/view/complex.jsp", complexForm, errors);
        }

        @Override
        public boolean handlePost(ComplexForm complexForm, BindException errors)
        {
            return false;
        }

        @Override
        public ActionURL getSuccessURL(ComplexForm complexForm)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            navTrail(root, "Form Test");
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


    /**
     * Simple action for verifying proper CSRF token handling from external scripts and programs. Referenced in the
     * HTTP Interface docs: https://www.labkey.org/Documentation/wiki-page.view?name=remoteAPIs
     */
    @SuppressWarnings("unused")
    @RequiresNoPermission
    @CSRF(CSRF.Method.ALL)
    public static class CsrfAction extends ReadOnlyApiAction
    {
        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            ApiSimpleResponse res = new ApiSimpleResponse();
            res.put("success", true);
            return res;
        }
    }


    public abstract class PermAction extends SimpleViewAction
    {
        String _action;

        PermAction(String action)
        {
            _action = action;
        }

        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new HtmlView("SUCCESS you can " + _action);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            navTrail(root, "perm " + _action + " test");
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
        @Override
        public ModelAndView getView(ExceptionForm form, BindException errors)
        {
            NullPointerException npe;
            if (null == form.getMessage())
                npe = new NullPointerException();
            else
                npe = new NullPointerException(form.getMessage());
            ExceptionUtil.decorateException(npe, ExceptionUtil.ExceptionInfo.ExtraMessage, "testing", true);
            throw npe;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    @RequiresSiteAdmin
    public class NpeOtherAction extends SimpleViewAction<ExceptionForm>
    {
        @Override
        public ModelAndView getView(ExceptionForm form, BindException errors)
        {
            NullPointerException npe;
            if (null == form.getMessage())
                npe = new NullPointerException();
            else
                npe = new NullPointerException(form.getMessage());
            ExceptionUtil.decorateException(npe, ExceptionUtil.ExceptionInfo.ExtraMessage, "testing", true);
            throw npe;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    @RequiresSiteAdmin
    public class MultiExceptionAction extends SimpleViewAction<ExceptionForm>
    {
        @Override
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

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    @RequiresSiteAdmin
    public class IllegalStateAction extends SimpleViewAction<ExceptionForm>
    {
        @Override
        public ModelAndView getView(ExceptionForm form, BindException errors)
        {
            IllegalStateException ise;
            if (null == form.getMessage())
                ise = new IllegalStateException();
            else
                ise = new IllegalStateException(form.getMessage());
            ExceptionUtil.decorateException(ise, ExceptionUtil.ExceptionInfo.ExtraMessage, "testing", true);
            throw ise;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    @RequiresSiteAdmin
    public class ConfigurationExceptionAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            throw new ConfigurationException("You have a configuration problem.", "What will make things better is if you stop visiting this action.");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    @RequiresSiteAdmin
    public class NotFoundAction extends SimpleViewAction<ExceptionForm>
    {
        @Override
        public ModelAndView getView(ExceptionForm form, BindException errors)
        {
            if (null == form.getMessage())
                throw new NotFoundException();
            else
                throw new NotFoundException(form.getMessage());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    @RequiresSiteAdmin
    public class UnauthorizedAction extends SimpleViewAction<ExceptionForm>
    {
        @Override
        public ModelAndView getView(ExceptionForm form, BindException errors)
        {
            if (null == form.getMessage())
                throw new UnauthorizedException();
            else
                throw new UnauthorizedException(form.getMessage());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    @RequiresSiteAdmin
    public class DeadlockAction extends SimpleViewAction<ExceptionForm>
    {
        @Override
        public ModelAndView getView(ExceptionForm form, BindException errors)
        {
            if (getViewContext().getRequest().getParameterMap().containsKey("_retry_"))
                return new HtmlView(HtmlString.of("Second time's a charm"));
            throw new DeadlockLoserDataAccessException("Try again", null);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    @RequiresSiteAdmin
    public class HtmlViewAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            String title = getViewContext().getActionURL().getParameter("title");
            return new HtmlView(title, "This is my HTML");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ButtonAction extends SimpleViewAction<ButtonForm>
    {
        @Override
        public ModelAndView getView(ButtonForm form, BindException errors)
        {
            if (isPost())
            {
                Button.ButtonBuilder button = PageFlowUtil.button(form.getText())
                        .href(form.getHref())
                        .enabled(form.isEnabled())
                        .disableOnClick(form.isDisableonclick())
                        .submit(form.isButtonsubmit())
                        .onClick(form.getOnclick());

                Map<String, String> map = new LinkedHashMap<>();
                // test that the attribute looks like an attribute (e.g. no special chars)
                if (form.getAttrkey1() != null && form.getAttrkey1().equals(filter(form.getAttrkey1())))
                    map.put(form.getAttrkey1(), filter(form.getAttrvalue1()));
                if (form.getAttrkey2() != null && form.getAttrkey2().equals(filter(form.getAttrkey2())))
                    map.put(form.getAttrkey2(), filter(form.getAttrvalue2()));
                if (!map.isEmpty())
                    button.attributes(map);

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

            return new JspView<>("/org/labkey/devtools/view/buttons.jsp", form);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
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

        public String getText()
        {
            return _text;
        }

        public void setText(String text)
        {
            _text = text;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DomAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object form, BindException errors)
        {
            return new JspView<>("/org/labkey/devtools/view/dom.jsp", form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            navTrail(root, "DOM Test");
        }
    }


    public static class URLForm
    {
        public URLHelper url = AppProps.getInstance().getHomePageActionURL();

        public URLHelper getUrl()
        {
            return url;
        }

        public void setUrl(URLHelper url)
        {
            this.url = url;
        }
    }

    // useful for stress testing and deadlock testing
    @RequiresNoPermission
    public class IFrameAction extends SimpleViewAction<URLForm>
    {
        @Override
        public ModelAndView getView(URLForm form, BindException errors) throws Exception
        {
            var td = TD(at(style, "height:500px;width:500px;"), IFRAME(at(style, "height:100%;width:100%;", src, form.url)));
            return new HtmlView(
                    DIV(
                            DOM.LK.FORM(INPUT(at(type, "submit")), INPUT(at(type, "text", name, "url", value, form.url, style, "width:890px;"))),
                            BR(),
                            TABLE(at(DOM.Attribute.style, "width:1000px;height:1000px;"),
                                    TR(td, td), TR(td, td))));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresNoPermission
    @MethodsAllowed("GET")
    public class GetAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new HtmlView(HtmlString.of(getViewContext().getRequest().getMethod()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {

        }
    }

    @RequiresNoPermission
    @MethodsAllowed("POST")
    public class PostAction extends GetAction
    {
    }

    @RequiresNoPermission
    @MethodsAllowed("DELETE")
    public class DeleteAction extends GetAction
    {
    }

    @RequiresNoPermission
    @MethodsAllowed("PUT")
    public class PutAction extends GetAction
    {
    }
}
