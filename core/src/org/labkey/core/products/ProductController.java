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
package org.labkey.core.products;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.products.ProductRegistry;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Marshal(Marshaller.Jackson)
public class ProductController extends SpringActionController
{

    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ProductController.class);
    private static final Logger _log = Logger.getLogger(ProductController.class);

    public ProductController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public static class MenuSectionsAction extends ReadOnlyApiAction<MenuItemsForm>
    {
        private List<String> _productIds;

        @Override
        public void validateForm(MenuItemsForm menuItemsForm, Errors errors)
        {
            if (menuItemsForm.getItemLimit() != null && menuItemsForm.getItemLimit() < 0)
                errors.reject(ERROR_MSG, "'itemLimit' must be >= 0");

            ProductRegistry registry = ProductRegistry.get();
            if (!StringUtils.isEmpty(menuItemsForm.getProductIds()))
            {
                _productIds = Arrays.asList(menuItemsForm.getProductIds().split(","));
                String missingProducts = _productIds.stream().filter(productId -> !registry.containsProductId(productId)).collect(Collectors.joining(", "));
                if (!missingProducts.isEmpty())
                    _log.warn("No such products: " + missingProducts);
            }
        }

        @Override
        public Object execute(MenuItemsForm menuItemsForm, BindException errors) throws Exception
        {
            if (_productIds != null)
                return ProductRegistry.get().getProductMenuSections(getViewContext(), _productIds, menuItemsForm.getItemLimit());
            else
                return ProductRegistry.get().getProductMenuSections(getViewContext(), getContainer(), menuItemsForm.getItemLimit());
        }
    }

    public static class MenuItemsForm
    {
        private String _productIds; // comma-separated list of productIds
        private Integer _itemLimit;

        public String getProductIds()
        {
            return _productIds;
        }

        public void setProductIds(String productIds)
        {
            _productIds = productIds;
        }

        public Integer getItemLimit()
        {
            return _itemLimit;
        }

        public void setItemLimit(Integer itemLimit)
        {
            _itemLimit = itemLimit;
        }
    }
}
