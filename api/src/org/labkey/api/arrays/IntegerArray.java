/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.arrays;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;


/**
 * Alternative to ArrayList<Integer>
 * User: mbellew
 * Date: May 24, 2004
 */
public class IntegerArray
{
    private static final int ARRAY_LEN = 1024;
    ArrayList<int[]> list = new ArrayList<>();
    int[] arrayLast;
    int lenLast = 0;
    int size = 0;


    public IntegerArray()
    {
        list = new ArrayList<>();
        arrayLast = new int[ARRAY_LEN];
        list.add(arrayLast);
    }


    public void add(int i)
    {
        if (arrayLast.length <= lenLast)
        {
            arrayLast = new int[ARRAY_LEN];
            list.add(arrayLast);
            lenLast = 0;
        }
        arrayLast[lenLast++] = i;
        size++;
    }


    public int get(int i)
    {
        return list.get(i / ARRAY_LEN)[i % ARRAY_LEN];
    }


    public int size()
    {
        return size;
    }


	public int[] toArray(@Nullable int[] dst)
    {
    	if (null == dst || dst.length < size)
			dst = new int[size];
		int end=0, i=0;
		int[] src;
		for (; i<list.size()-1 ; i++)
        {
			src = list.get(i);
			System.arraycopy(src, 0, dst, end, src.length);
			end += src.length;
        }
		src = list.get(i);
		System.arraycopy(src, 0, dst, end, lenLast);
		return dst;
    }
}
