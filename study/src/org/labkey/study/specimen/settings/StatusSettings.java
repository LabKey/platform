/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.study.specimen.settings;

import java.util.Map;

/*
 * User: brittp
 * Date: May 8, 2009
 * Time: 2:50:55 PM
 */

public class StatusSettings
{
    public static final String KEY_USE_SHOPPING_CART = "UseShoppingCart";
    private boolean _useShoppingCart;

    public StatusSettings()
    {
        // no-arg constructor for struts reflection
    }

    public StatusSettings(Map<String, String> map)
    {
        String boolString = map.get(KEY_USE_SHOPPING_CART);
        _useShoppingCart = Boolean.parseBoolean(boolString);
    }

    public void populateMap(Map<String, String> map)
    {
        map.put(KEY_USE_SHOPPING_CART, String.valueOf(_useShoppingCart));
    }

    public static StatusSettings getDefaultSettings()
    {
        StatusSettings defaults = new StatusSettings();
        defaults.setUseShoppingCart(true);
        return defaults;
    }

    public boolean isUseShoppingCart()
    {
        return _useShoppingCart;
    }

    public void setUseShoppingCart(boolean useShoppingCart)
    {
        _useShoppingCart = useShoppingCart;
    }
}