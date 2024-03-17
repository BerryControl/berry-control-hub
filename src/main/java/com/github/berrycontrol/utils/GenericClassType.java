/*
 *    Copyright 2024 Thomas Bonk <thomas@meandmymac.de>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.github.berrycontrol.utils;

import com.google.common.reflect.TypeToken;

public class GenericClassType<T> {
    private final Class<T> type;

    public GenericClassType() {
        TypeToken<T> typeToken = new TypeToken<T>(getClass()) {};
        this.type = (Class<T>) typeToken.getRawType();
    }

    public Class<T> getType() {
        return this.type;
    }

    public static <T> Class<T> type() {
        GenericClassType<T> ct = new GenericClassType<>();

        return ct.getType();
    }
}
