package org.labkey.devtools.mascot;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.annotation.WebListener;

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
