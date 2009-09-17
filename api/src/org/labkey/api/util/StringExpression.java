package org.labkey.api.util;

import java.util.Map;
import java.io.Writer;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
* User: matthewb
* Date: Sep 16, 2009
* Time: 12:14:49 PM
*/
public interface StringExpression
{
    public String eval(Map ctx);

    public String getSource();

    public void addParameter(String key, String value);

    public void render(Writer out, Map ctx) throws IOException;
}
