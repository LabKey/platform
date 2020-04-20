package org.labkey.devtools.mascot;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.WebListener;

@WebListener
public class MockMascotServletContextListener implements ServletContextListener
{
    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent)
    {
        ServletRegistration.Dynamic servlet = servletContextEvent.getServletContext().addServlet("MockMascotServlet", MockMascotServlet.class);
        servlet.addMapping("/mockmascot/*");
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent)
    {
    }
}
