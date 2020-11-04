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
package org.labkey.api.jsp;

import org.labkey.api.util.SafeToRender;

import javax.servlet.jsp.JspWriter;
import java.io.IOException;

public class LabKeyJspWriter extends JspWriterWrapper
{
    LabKeyJspWriter(JspWriter jspWriter)
    {
        super(jspWriter);
    }

    @Override
    public void print(char[] s)
    {
        throw new IllegalStateException("A JSP is attempting to render a character array!");
    }

    @Override
    public void print(String s) throws IOException
    {
        throw new IllegalStateException("A JSP is attempting to render a string!");
    }

    @Override
    public void print(Object obj) throws IOException
    {
        // These are the only objects we consider safe-to-render
        if (null == obj || obj instanceof SafeToRender || obj instanceof Number || obj instanceof Boolean)
        {
            super.print(obj);
        }
        else
        {
            throw new IllegalStateException("A JSP is attempting to render an object of class " + obj.getClass().getName() + "!");
        }
    }
}
