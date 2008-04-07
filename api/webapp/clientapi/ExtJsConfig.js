/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:support@labkey.com">support@labkey.com</a>)
 * @version 8.1
 * @license Copyright (c) 2008 LabKey Software Foundation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */
 
<!-- Utility call to reset the location of the blank image; otherwise we connect to extjs.com by default: -->
Ext.BLANK_IMAGE_URL = LABKEY.contextPath + '/_.gif';  // 2.0