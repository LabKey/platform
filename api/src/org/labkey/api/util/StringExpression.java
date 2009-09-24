/*
 * Copyright (c) 2009 LabKey Corporation
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
public interface StringExpression extends Cloneable
{
    public String eval(Map ctx);

    public String getSource();

    public void render(Writer out, Map ctx) throws IOException;

    public StringExpression copy(); // clone without the Exception
}
