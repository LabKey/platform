/*
 * Copyright (c) 2007 LabKey Software Foundation
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

/**
 * Represents a search hit. See SimpleSearchHit for a simple implementation
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: May 1, 2008
 * Time: 1:48:06 PM
 * To change this template use File | Settings | File Templates.
 */
public interface SearchHit
{
    /**
     * Returns the domain in which this hit was found (e.g., "wiki", "study", etc.).
     * The domain name should match the domain name returned from getDomain() in
     * the Searchable interface.
     * @return The domain
     */
    public String getDomain();

    /**
     * Returns the container path in which this hit was found.
     * @return The container path
     */
    public String getContainerPath();

    /**
     * Returns the title of this search hit.
     * @return The title
     */
    public String getTitle();

    /**
     * Returns the href for this search hit. This will commonly be put into
     * the href attribute of an anchor element in a search result page, which
     * may be served from any part of the site. Therefore, this href should be
     * relative to the server root (e.g., "/labkey/wiki/....")
     * @return The href
     */
    public String getHref();

    /**
     * Returns a programmaticly-used type for the search hit.
     * For example, hits for wiki pages will return "labkey/wiki".
     * Clients may use this to offer hit-specific functionality based
     * on the type.
     * @return The type of hit
     */
    public String getType();

    /**
     * Returns the type of this search hit, suitable for display to the user.
     * This should be the type of document or object found by this hit.
     * @return A description of the type of hit
     */
    public String getTypeDescription();

    /**
     * Returns the context surrounding the hit, suitable for display to the user.
     * For textural search providers, this might include the text surrounding the
     * search term(s). For database objects, it might include other column values.
     * This is optional and implementations may return null if there is no context.
     * @return The context or null if no context is available or appropriate.
     */
    public String getDetails();

}
