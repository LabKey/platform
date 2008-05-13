/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
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
 */
public class TestController extends SpringActionController
{
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(TestController.class);

    public TestController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    private ActionURL actionURL(Class<? extends Controller> actionClass)
    {
        return new ActionURL(actionClass, getViewContext().getContainer());
    }


    @RequiresPermission(ACL.PERM_READ)
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


    public class ActionListView extends HttpView
    {
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            for (ActionDescriptor ad : _actionResolver.getActionDescriptors())
            {
                out.print("<a href=\"");
                out.print(PageFlowUtil.filter(actionURL(ad.getActionClass())));
                out.print("\">");
                out.print(PageFlowUtil.filter(ad.getPrimaryName()));
                out.print("</a><br>");
            }
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class SimpleFormAction extends FormViewAction<SimpleForm>
    {
        String _enctype = "application/x-www-form-urlencoded";
        
        public void validateCommand(SimpleForm target, Errors errors)
        {
            target.validate(errors);
        }

        public ModelAndView getView(SimpleForm simpleForm, boolean reshow, BindException errors) throws Exception
        {
            ModelAndView mv = jspView("form.jsp", simpleForm, errors);
            mv.addObject("enctype", _enctype); // request attribute
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
            (new BeginAction()).appendNavTrail(root);
            root.addChild("Form Test (" + _enctype + ")");
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
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
            (new BeginAction()).appendNavTrail(root);
            root.addChild("Spring tags test");
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
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


    @RequiresPermission(ACL.PERM_READ)
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
                ArrayList<TestBean> a = new ArrayList<TestBean>(2);
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
            (new BeginAction()).appendNavTrail(root);
            root.addChild("Form Test");
            return root;
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

    }


    public static class ComplexForm
    {
        private String[] strings = new String[3];

        private ArrayList<TestBean> beans = new FormArrayList<TestBean>(TestBean.class);

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
            (new BeginAction()).appendNavTrail(root)
                    .addChild("perm " + _action + " test");
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class PermNoneAction extends PermAction
    {
        public PermNoneAction()
        {
            super("none");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class PermReadAction extends PermAction
    {
        public PermReadAction()
        {
            super("read");
        }
    }

    @RequiresPermission(ACL.PERM_UPDATE)
    public class PermUpdateAction extends PermAction
    {
        public PermUpdateAction()
        {
            super("update");
        }
    }

    @RequiresPermission(ACL.PERM_INSERT)
    public class PermInsertAction extends PermAction
    {
        public PermInsertAction()
        {
            super("insert");
        }
    }

    @RequiresPermission(ACL.PERM_DELETE)
    public class PermDeleteAction extends PermAction
    {
        public PermDeleteAction()
        {
            super("delete");
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
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
}
