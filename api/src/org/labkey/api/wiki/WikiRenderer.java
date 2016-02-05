/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.wiki;

/**
 * User: Tamra Myers
 * Date: Aug 16, 2006
 * Time: 11:57:19 AM
 */
public interface WikiRenderer
{
    // TODO: Pass in "wiki page", "message", "issue", etc. so we can tailor any error messages
    FormattedHtml format(String text);
}
