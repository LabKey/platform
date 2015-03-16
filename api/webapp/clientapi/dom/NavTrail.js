/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2008-2015 LabKey Corporation
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

/**
 * @namespace NavTrail static class to adjust the text in LabKey's
 *		navigation trail. The navigation trail is the list of links across the top of
 *      the main body area, as well as the title for the current page.
 */
LABKEY.NavTrail = new function()
{
    /** @scope LABKEY.NavTrail */
    return {
        /**
         * Set the nav trail's elements.
         * @param {string} currentPageTitle The title for the current page
         * @param {Object[]} [ancestors] An array of objects that describe the ancestor pages. Each
         * object in this array can have two properties: url (which contains the URL for the page);
         * and title (which contains the title for the page). These will be assembled into the full list of ancestor pages at the top of the nav trail.
         * @param {string} [documentTitle] Document title
         * @example
<pre name="code" class="xml">
var ancestorURL = LABKEY.ActionURL.buildURL('project', 'begin');
LABKEY.NavTrail.setTrail("People View", [{url: ancestorURL, title: "API Example"}]);
</pre>
         */
        setTrail: function (currentPageTitle, ancestors, documentTitle)
        {
            var elem = document.getElementById("labkey-nav-trail-current-page");
            if (elem)
            {
                elem.innerHTML = currentPageTitle;
                elem.style.visibility = "visible";
            }

            elem = document.getElementById("navTrailAncestors");
            if (elem && ancestors)
            {
                var html = "",
                    sep = "";
                for (var idx = 0; idx < ancestors.length; ++idx)
                {
                    html += sep;
                    if (ancestors[idx].url && ancestors[idx].title)
                    {
                        html += "<a href='" + ancestors[idx].url + "'>" + ancestors[idx].title + "</a>";
                    }
                    else if (ancestors[idx].title)
                    {
                        html += ancestors[idx].title;
                    }
                    sep = ' &gt; ';
                }
                elem.innerHTML = html;
                elem.visibility = "visible";
            }

            //set document title:
            //<currentPageTitle>: <container path>
            document.title = (documentTitle || currentPageTitle) + ": " +  decodeURI(LABKEY.ActionURL.getContainer());
        }
    };
};

