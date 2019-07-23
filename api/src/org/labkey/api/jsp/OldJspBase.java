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

import org.labkey.api.annotations.RemoveIn19_3;
import org.labkey.api.util.PageFlowUtil;

/**
 * Restores the previous JspBase behavior where {@code h(String)} returned a String. Use this on a <b>temporary</b> basis to keep JSPs
 * compiling, without requiring immediate updates due to this change. Switch JSPs to extend this class as follows:
 * <p>
 * {@code     <%@ page extends="org.labkey.api.jsp.OldJspBase" %>}
 * </p>
 * This class will be removed early in the 19.3 development cycle, so JSPs will need to be updated before compiling against 19.3.
 */
@Deprecated
@RemoveIn19_3
public abstract class OldJspBase extends AbstractJspBase
{
    /**
     * Html escape a string.
     * The name comes from Embedded Ruby.
     */
    public String h(String str)
    {
        return PageFlowUtil.filter(str);
    }
}
