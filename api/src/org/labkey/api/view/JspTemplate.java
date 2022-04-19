/*
 * Copyright (c) 2010-2016 LabKey Corporation
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
package org.labkey.api.view;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.reports.report.JavaScriptReport;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.security.User;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.StringWriter;
import java.util.Objects;

/**
 * Executes a JSP and renders output to a string. Useful for JSP templating of SQL queries, etc.
 * User: adam
 * Date: Aug 10, 2010
 */
public class JspTemplate<ModelClass> extends JspView<ModelClass>
{
    public JspTemplate(String page)
    {
        this(page, null);
    }

    public JspTemplate(String page, @Nullable ModelClass model)
    {
        super(page, model);
        setFrame(WebPartView.FrameType.NOT_HTML);
    }

    public String render() throws Exception
    {
        ViewContext context = getViewContext();
        if (null == context)
        {
            context = new ViewContext();
            context.setUser(User.guest);
            context.setContainer(HttpView.currentContext().getContainer());
            context.setActionURL(new ActionURL());
            setViewContext(context);
        }

        StringWriter out = new StringWriter();

        // Tomcat 8 Jasper rejects requests other than GET, POST, or HEAD... so, make this mock request a GET. #24750
        context.setRequest(new MockHttpServletRequest("GET", null));
        var mockResponse = new MockHttpServletResponse();
        context.setResponse(mockResponse);
        try (var init = HttpView.initForRequest(context, context.getRequest(), context.getResponse()))
        {
            // Tomcat 8 Jasper rejects requests other than GET, POST, or HEAD... so, make this mock request a GET. #24750
            include(this, out, context.getRequest(), context.getResponse());
        }

        String errorMessage = mockResponse.getErrorMessage();
        if (null != errorMessage)
            throw new IllegalStateException(errorMessage);

        return out.getBuffer().toString().trim();
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test() throws Exception
        {
            String test = (new JspTemplate<>("/org/labkey/api/view/jspTemplateTest.jsp")).render();
            assertEquals("This is a JSP used by the JspTemplate.TestCase", test);

            RReport r = new RReport();
            assertEquals("", r.getDefaultScript());
            assertTrue(r.getDesignerHelpHtml().length() > 1000);

            JavaScriptReport js = new JavaScriptReport();
            assertTrue(js.getDefaultScript().length() > 500);
            assertTrue(js.getDesignerHelpHtml().length() > 1000);
        }
    }
}
