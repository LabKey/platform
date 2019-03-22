/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.api.security;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.settings.AppProps;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

/**
 * User: adam
 * Date: Jan 14, 2010
 * Time: 1:33:38 PM
 */
public enum PasswordExpiration
{
    Never(Integer.MAX_VALUE, "Never") {
        @Override
        public boolean hasExpired(Supplier<Date> supplier)
        {
            return false;
        }},
    FiveSeconds(0, "Every five seconds -- for testing purposes only") {
        @Override
        public boolean hasExpired(Supplier<Date> supplier)
        {
            Calendar cutoff = Calendar.getInstance();
            cutoff.add(Calendar.SECOND, -5);

            return supplier.get().compareTo(cutoff.getTime()) < 0;
        }

        @Override
        protected boolean display()
        {
            return AppProps.getInstance().isDevMode();
        }},
    ThreeMonths(3, "Every three months"),
    SixMonths(6, "Every six months"),
    OneYear(12, "Every 12 months");

    private final int _months;
    private final String _description;

    // Return the options that admins can see.  Will omit the "Every five seconds" rule on production servers.
    public static Collection<PasswordExpiration> displayValues()
    {
        List<PasswordExpiration> list = new LinkedList<>();

        for (PasswordExpiration pe : values())
            if (pe.display())
                list.add(pe);

        return list;
    }

    PasswordExpiration(int months, String description)
    {
        _months = months;
        _description = description;
    }

    // All options get displayed by default.
    protected boolean display()
    {
        return true;
    }

    public int getMonths()
    {
        return _months;
    }

    public String getDescription()
    {
        return _description;
    }

    public boolean hasExpired(Supplier<Date> supplier)
    {
        Calendar cutoff = Calendar.getInstance();
        cutoff.add(Calendar.MONTH, -getMonths());

        return supplier.get().compareTo(cutoff.getTime()) < 0;
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testExpirations()
        {
            testExpiration(PasswordExpiration.Never, 60, 0, 0);
            testExpiration(PasswordExpiration.OneYear, 60, 7, 7);
            testExpiration(PasswordExpiration.SixMonths, 60, 33, 34);    // # of weeks with an expired password differs depending on if February is in the range
            testExpiration(PasswordExpiration.ThreeMonths, 60, 46, 47);  // # of weeks with an expired password differs depending on if February is in the range
            testExpiration(PasswordExpiration.FiveSeconds, 60, 59, 59);
        }

        // Test expiration every week for the specified number of weeks
        private void testExpiration(PasswordExpiration expiration, int weeks, int expectedLow, int expectedHigh)
        {
            long now = new Date().getTime();
            long milliseconds_in_week = 1000 * 60 * 60 * 24 * 7;
            int expired = 0;

            for (int i = 0; i < weeks; i++)
            {
                Date d = new Date(now - milliseconds_in_week * i);

                if (expiration.hasExpired(() -> d))
                    expired++;
            }

            if (expired < expectedLow || expired > expectedHigh)
                fail("Invalid number of expirations for " + expiration + ": expected <" + expectedLow + (expectedLow != expectedHigh ? "," + expectedHigh : "") + "> but was <" + expired + ">");
        }
    }
}
