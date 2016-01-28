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




import java.io.File;
import java.io.IOException;

import org.junit.Assert;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;


class Utils {
    
    private Utils() { }
   
    public static void assertMapEntryEquals(ImmutableMap<String, String> m1, ImmutableMap<String, String> m2, String name) {
        Assert.assertEquals(m1.get(name), m2.get(name));
    }
    
    public static ImmutableMap<String, String> loadFileAsMap(String name, String charset) throws IOException {
        final String content = new String(Files.toByteArray(new File("src" + File.separator + "test" + File.separator + "resources" + File.separator + name)), charset);
        return toMap(content);
    }
    
    public static ImmutableMap<String, String> toMap(String content) {
        return ImmutableMap.copyOf(Splitter.on("\n").trimResults().withKeyValueSeparator("=").split(content));
    }

}