/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.data;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.util.DateUtil;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * User: adam
 * Date: 9/25/12
 * Time: 11:08 AM
 */

// Simple framework we can use to test libraries that map JSON <-> Java objects. First up is Jackson (see below), but we could
// plug in others for quick comparisons.
public class JsonTest extends Assert
{
    private static Customer c1;
    private static Customer c2;

    @BeforeClass
    public static void initialize()
    {
        Product p1 = new Product(1, "A book", 9.99f);
        Product p2 = new Product(2, "A compact disc", 15.99f);
        Product p3 = new Product(3, "A new laptop", 1000f);

        c1 = new Customer("Joe", "Smith", 1);
        c2 = new Customer("Jane", "Doe", 2);

        Order o1 = new Order(1, c1);
        o1.setProducts(Arrays.asList(p1, p2));
        Order o2 = new Order(2, c1);
        o2.setProducts(Arrays.asList(p2, p3));
        Order o3 = new Order(3, c2);
        o3.setProducts(Arrays.asList(p1, p2, p3));

        c1.setOrders(Arrays.asList(o1, o2));
        c2.setOrders(Arrays.asList(o3));
    }


    @Test
    public void jacksonTest()
    {
        try
        {
            ObjectMapper mapper = new ObjectMapper().setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
            Customer roundTripC1 = mapper.readValue(mapper.writeValueAsString(c1), Customer.class);
            assertEquals(c1, roundTripC1);
            Customer roundTripC2 = mapper.readValue(mapper.writeValueAsString(c2), Customer.class);
            assertEquals(c2, roundTripC2);
        }
        catch (IOException e)
        {
            // In previous usage of GSON, there was potential for unhandled exception from gson.toJson() and fromJson().
            // Jackson explicitly throws exceptions, but there's still nothing we can do about it if one happens here.
            throw new RuntimeException(e);
        }
    }

    @Test
    public void jsonOrgViaJackson() throws IOException
    {
        // Test serializing org.json JSON classes via Jackson
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JsonOrgModule());
        mapper.setDateFormat(new SimpleDateFormat(DateUtil.getJsonDateTimeFormatString()));

        final Date d = new GregorianCalendar(2011, 11, 3).getTime();

        JSONObject obj = new JSONObject();
        obj.put("str", "hello");
        obj.put("arr", new JSONArray(Arrays.asList("one", null, 3, new JSONObject(Collections.singletonMap("four", 4)))));
        obj.put("nul", (Object)null);
        obj.put("d", d);

        // Verify serializing org.json.JSONObject via Jackson is equivalent
        String jacksonToString = mapper.writeValueAsString(obj);
        String jsonOrgToString = obj.toString();
        assertEquals(jsonOrgToString, jacksonToString);

        // Verify deserializing org.json.JSONObject via Jackson is equivalent
        // NOTE: In both cases, the date value is deserialized as a string because JSON sucks
        JSONObject jsonOrgRoundTrip =  new JSONObject(jacksonToString);
        JSONObject jacksonRoundTrip = mapper.readValue(jacksonToString, JSONObject.class);
        assertEquals(jsonOrgRoundTrip, jacksonRoundTrip);
    }

    private static class Customer
    {
        private String _first;
        private String _last;
        private int _custId;
        private List<Order> _orders;

        private Customer() {}    // default constructor needed for jackson deserialization

        private Customer(String first, String last, int custId)
        {
            _first = first;
            _last = last;
            _custId = custId;
        }

        private void setOrders(List<Order> orders)
        {
            _orders = orders;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Customer customer = (Customer) o;

            if (_custId != customer._custId) return false;
            if (_first != null ? !_first.equals(customer._first) : customer._first != null) return false;
            if (_last != null ? !_last.equals(customer._last) : customer._last != null) return false;
            if (_orders != null ? !_orders.equals(customer._orders) : customer._orders != null) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = _first != null ? _first.hashCode() : 0;
            result = 31 * result + (_last != null ? _last.hashCode() : 0);
            result = 31 * result + _custId;
            result = 31 * result + (_orders != null ? _orders.hashCode() : 0);
            return result;
        }
    }

    private static class Order
    {
        private int _orderId;
        private int _custId;
        private List<Product> _products;

        private Order() {}  // default constructor needed for jackson deserialization

        private Order(int orderId, Customer customer)
        {
            _orderId = orderId;
            _custId = customer._custId;
        }

        private void setProducts(List<Product> products)
        {
            _products = products;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Order order = (Order) o;

            if (_custId != order._custId) return false;
            if (_orderId != order._orderId) return false;
            if (_products != null ? !_products.equals(order._products) : order._products != null) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = _orderId;
            result = 31 * result + _custId;
            result = 31 * result + (_products != null ? _products.hashCode() : 0);
            return result;
        }
    }

    private static class Product
    {
        private int _productId;
        private String _description;
        private float _price;

        private Product() {} // default constructor needed for jackson deserialization

        private Product(int productId, String description, float price)
        {
            _productId = productId;
            _description = description;
            _price = price;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Product product = (Product) o;

            if (Float.compare(product._price, _price) != 0) return false;
            if (_productId != product._productId) return false;
            if (_description != null ? !_description.equals(product._description) : product._description != null)
                return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = _productId;
            result = 31 * result + (_description != null ? _description.hashCode() : 0);
            result = 31 * result + (_price != +0.0f ? Float.floatToIntBits(_price) : 0);
            return result;
        }
    }
}
