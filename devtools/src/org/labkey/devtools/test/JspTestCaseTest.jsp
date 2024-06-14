<%@ page import="org.junit.Test" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page import="org.labkey.api.util.JunitUtil" %>
<%@ page import="org.labkey.api.util.TestContext" %>
<%@ page import="java.util.Collection" %>
<%@ page extends="org.labkey.api.jsp.JspTest.DRT" %>

<%!
    @Test
    public void test1()
    {
        Collection<User> users = UserManager.getUsers(true);
        ContainerManager.getAllChildren(ContainerManager.getRoot())
            .forEach(c -> {
                for (User user : users)
                {
                    SecurityManager.getEffectiveRoles(c, user);
                }
            });
    }

    @Test
    public void test2()
    {
        User user = TestContext.get().getUser();
        Container c = JunitUtil.getTestContainer();

        UserSchema schema = QueryService.get().getUserSchema(user, c, "Protein");
        System.out.println(schema.getTableNames());
        schema = QueryService.get().getUserSchema(user, c, "prot");
        System.out.println(schema.getTableNames());
    }
%>

