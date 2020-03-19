package org.labkey.api.jsp.taglib;

import org.labkey.api.jsp.JspBase;
import org.labkey.api.view.template.ClientDependency;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;

/**
 * This "tag" allows for client dependencies to be loaded inline to a JavaScript <script> tag
 * contained in a JSP. This ensures the JSPs dependencies are loaded in both sync and async
 * use cases.
 *
 * Example in a JSP:
 * <%!
 *     public void addClientDependencies(ClientDependencies dependencies)
 *     {
 *         dependencies.add("someLib");
 *         dependencies.add("myFile.js");
 *     }
 * %>
 * <script type="application/javascript">
 *     <labkey:loadClientDependencies>
 *         // Script here can assume async/sync safe loading of dependencies declared above
 *     </labkey:loadClientDependencies>
 * </script>
 */
public class LoadClientDependenciesTag extends BodyTagSupport
{
    @Override
    public int doStartTag() throws JspException
    {
        StringBuilder sb = new StringBuilder();
        StringBuilder files = new StringBuilder("[");

        Object page = this.pageContext.getPage();

        if (page instanceof JspBase)
        {
            String delim = "";
            for (ClientDependency dependency : ((JspBase)page).getClientDependencies())
            {
                files.append(delim).append("'").append(dependency.getScriptString()).append("'");
                delim = ",";
            }
        }

        files.append("]");

        // OK if "files" is empty array -- requireScript will call the handler
        sb.append("LABKEY.requiresScript(").append(files).append(", function(){\n");

        print(sb);
        return BodyTagSupport.EVAL_BODY_INCLUDE;
    }

    @Override
    public int doEndTag() throws JspException
    {
        StringBuilder sb = new StringBuilder();

        sb.append("}, true);\n");

        print(sb);
        return BodyTagSupport.EVAL_PAGE;
    }

    private void print(StringBuilder sb) throws JspException
    {
        try
        {
            pageContext.getOut().print(sb.toString());
        }
        catch (IOException e)
        {
            throw new JspException(e);
        }
    }
}
