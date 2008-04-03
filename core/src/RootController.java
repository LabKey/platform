/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.view.ViewController;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.beehive.netui.pageflow.Forward;

/**
 * Just a placeholder file so that Beehive doesn't spit out errors on startup
 * due to not finding jpf-struts-config.xml, which is for the controller in the
 * root of the webapp.
 * <p/>
 * User: jeckels
 * Date: Oct 4, 2005
 */
@Jpf.Controller
public class RootController extends ViewController
{

    @Jpf.Action
    /**
     * This method represents the point of entry into the pageflow
     */
    protected Forward begin() throws Exception
    {
        return null;
    }
}
