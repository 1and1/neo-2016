/*
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.neo.datareplicator;





import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;




// THIS is a very rudimentary implementation!! BOM is not considered, ... 
// TODO are the better implementations. BUT, dependency of this project should be minimal
// for this reason avoid to add additional dependencies to other projects. May code copy could be a solution.
// This depends on the amount of code and on the license of the code to copy  


class Encodings {

    private static final ImmutableList<Charset> CHARSETS_TO_TESTED = ImmutableList.of(Charset.forName("UTF-8"),
                                                                                      Charset.forName("ISO-8859-15"), 
                                                                                      Charset.forName("windows-1253"));
    
    public static Charset guessEncoding(byte[] binary) {
        for (Charset charset : CHARSETS_TO_TESTED) {
            try {
                final CharsetDecoder decoder = charset.newDecoder();
                decoder.reset();
                decoder.decode(ByteBuffer.wrap(binary));
                return charset;
            } catch (CharacterCodingException ignore) { }
        }
 
        return Charsets.UTF_8;
    }
}