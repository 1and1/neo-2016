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
        
        // utf-8 with charset
        ReplicationJob job = ReplicationJob.source(server.getBasepath() + "hello.utf8.txt?charset=utf-8")
                                           .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "Greek");
        job.close();
        
        // utf-8 without charset
        job = ReplicationJob.source(server.getBasepath() + "hello.utf8.txt")
                            .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "Greek");
        job.close();


        
        // utf-8 with BOM with charset
        job = ReplicationJob.source(server.getBasepath() + "hello.utf8.withbom.txt?charset=utf-8")
                            .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "Greek");
        job.close();
        
        // utf-8 with BOM without charset
        job = ReplicationJob.source(server.getBasepath() + "hello.utf8.withbom.txt")
                            .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "Greek");
        job.close();

        
        // ISO-8859-15 with charset
        job = ReplicationJob.source(server.getBasepath() + "hello.ISO_8859_15.txt?charset=ISO-8859-15")
                            .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "German");
        job.close();
        
        // ISO-8859-15 without charset
        job = ReplicationJob.source(server.getBasepath() + "hello.ISO_8859_15.txt")
                            .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "German");
        job.close();

        
        // ISO-8859-1 with charset
        job = ReplicationJob.source(server.getBasepath() + "hello.ISO_8859_1.txt?charset=ISO-8859-1")
                            .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "German");
        job.close();
        
        // ISO-8859-1 without charset
        job = ReplicationJob.source(server.getBasepath() + "hello.ISO_8859_1.txt")
                            .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "German");
        job.close();
        
        
        
        // cp 1252 with charset
        job = ReplicationJob.source(server.getBasepath() + "hello.cp_1252.txt?charset=windows-1252")
                            .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "German");
        job.close();
        
        // cp 1252 without charset
        job = ReplicationJob.source(server.getBasepath() + "hello.cp_1252.txt")
                            .startConsumingText(testConsumer);
        Utils.assertMapEntryEquals(Utils.loadFileAsMap("hello.utf8.txt", "UTF-8"), Utils.toMap(testConsumer.waitForText()), "German");
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
}