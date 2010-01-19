/*
 * Copyright (c) 2010 LabKey Corporation
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

import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.Calendar;
import java.util.Date;

/**
 * User: adam
 * Date: Jan 14, 2010
 * Time: 1:33:38 PM
 */
public enum PasswordExpiration
{
    Never(Integer.MAX_VALUE, "Never") {
        @Override
        public boolean hasExpired(Date lastChanged)
        {
            return false;
        }},
    ThreeMonths(3, "Every three months"),
    SixMonths(6, "Every six months"),
    OneYear(12, "Every 12 months");

    private final int _months;
    private final String _description;

    private PasswordExpiration(int months, String description)
    {
        _months = months;
        _description = description;
    }

    public int getMonths()
    {
        return _months;
    }

    public String getDescription()
    {
        return _description;
    }

    public boolean hasExpired(Date lastChanged)
    {
        Calendar cutoff = Calendar.getInstance();
        cutoff.add(Calendar.MONTH, -getMonths());

        return lastChanged.compareTo(cutoff.getTime()) < 0;
    }


    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }

        public TestCase(String name)
        {
            super(name);
        }

        public void testExpirations()
        {
            testExpiration(PasswordExpiration.Never, 60, 0);
            testExpiration(PasswordExpiration.OneYear, 60, 7);
            testExpiration(PasswordExpiration.SixMonths, 60, 33);
            testExpiration(PasswordExpiration.ThreeMonths, 60, 46);
        }

        // Test expiration every week for the specified number of weeks
        private void testExpiration(PasswordExpiration expiration, int weeks, int expectedExpirations)
        {
            long now = new Date().getTime();
            long milliseconds_in_week = 1000 * 60 * 60 * 24 * 7;
            int expired = 0;

            for (int i = 0; i < weeks; i++)
            {
                Date d = new Date(now - milliseconds_in_week * i);

                if (expiration.hasExpired(d))
                    expired++;
            }

            assertEquals("Invalid number of expirations for " + expiration, expectedExpirations, expired);
        }

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
