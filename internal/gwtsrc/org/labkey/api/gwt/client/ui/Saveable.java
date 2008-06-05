/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.gwt.client.ui;

/**
 * Interface for widgets that need a Save Finish Cancel button bar
 *
 * User: jgarms
 * Date: Jun 2, 2008
 * Time: 11:01:14 AM
 */
public interface Saveable
{
    /**
     * Save button clicked
     */
    public void save();

    /**
     * Cancel button clicked
     */
    public void cancel();

    /**
     * Finish button clicked
     */
    public void finish();
}
