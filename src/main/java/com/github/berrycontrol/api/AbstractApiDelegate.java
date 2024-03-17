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
package com.github.berrycontrol.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.Optional;

public abstract class AbstractApiDelegate {
    @Autowired
    private NativeWebRequest request;

    public Optional<NativeWebRequest> getRequest() {
        return Optional.ofNullable(request);
    }

    protected boolean acceptsApplicationJson(NativeWebRequest request) {
        return MediaType
            .parseMediaTypes(request.getHeader("Accept"))
            .stream()
            .anyMatch(mediaType -> mediaType.includes(MediaType.APPLICATION_JSON));
    }
}
