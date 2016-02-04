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
import java.net.URI;


import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import net.oneandone.neo.datareplicator.utils.InMemoryConsumer;
import net.oneandone.neo.datareplicator.utils.TestServlet;
import net.oneandone.neo.datareplicator.utils.Utils;
import net.oneandone.neo.datareplicator.utils.WebServer;


public class ReplicatorSupportedSchemesTest {
    
    private static TestServlet servlet = new TestServlet();
    private static WebServer server;

    
    @BeforeClass
    public static void setUp() throws Exception {
        server = WebServer.withServlet(servlet)
                          .start();
    }
    
    @AfterClass
    public static void tearDown() throws Exception {
        server.close();
    }
    
    
    @Test
    public void testHttpSource() throws Exception {
        InMemoryConsumer testConsumer = new InMemoryConsumer();
        
        ReplicationJob job = ReplicationJob.source(server.getBasepath() + "hello.utf8.txt?charset=utf-8")
                                           .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "Greek");
        job.close();
    }
    
    
    @Test
    public void testFileSource() throws Exception {
        InMemoryConsumer testConsumer = new InMemoryConsumer();
        
        File file = new File("src" + File.separator + "test" + File.separator + "resources" + File.separator + "hello.utf8.txt");
        ReplicationJob job = ReplicationJob.source(file.toURI())
                                           .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "Greek");
        job.close();
    }
    
    @Test
    public void testClasspathSource() throws Exception {
        InMemoryConsumer testConsumer = new InMemoryConsumer();
        
        ReplicationJob job = ReplicationJob.source(URI.create(("classpath:hello.utf8.txt")))
                                           .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "Greek");
        job.close();
    }
    
    
    
    @Test
    public void testNonExistingHttpSource() throws Exception {
        InMemoryConsumer testConsumer = new InMemoryConsumer();
        
        try {
            ReplicationJob.source(server.getBasepath() + " notexists.txt")
                          .startConsumingText(testConsumer);
            Assert.fail("RuntimeException expected");
        } catch (RuntimeException expected) { }
    }

    //todo - add tests for http etag.
    //todo - re-check thrown exceptions.

}