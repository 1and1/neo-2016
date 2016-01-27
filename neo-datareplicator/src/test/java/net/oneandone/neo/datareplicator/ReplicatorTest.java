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
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;


public class ReplicatorTest {
    
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
    
    
    public static final class TestServlet extends HttpServlet {
        private static final long serialVersionUID = -1136798776740561582L;
        
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String resource = req.getRequestURI().substring(1);
            
            String charset = req.getParameter("charset"); 
            if (charset == null) {
                resp.setContentType("text/plain");
            } else {
                resp.setContentType("text/plain; charset=" + charset);
            }
            
            resp.getOutputStream().write(Files.toByteArray(new File("src" + File.separator + "test" + File.separator + "resources" + File.separator + resource)));
        }
    }

    
    @Test
    public void testHttp() throws Exception {
        InMemoryConsumer testConsumer = new InMemoryConsumer();
        
        // utf-8 with charset
        ReplicationJob job = ReplicationJob.source(server.getBasepath() + "hello.utf8.txt?charset=utf-8")
                                           .startConsumingText(testConsumer);
        assertMapEntryEquals(loadFileAsMap("hello.utf8.txt", "UTF-8"), toMap(testConsumer.waitForText()), "Greek");
        job.close();
        
        // utf-8 without charset
        job = ReplicationJob.source(server.getBasepath() + "hello.utf8.txt")
                            .startConsumingText(testConsumer);
        assertMapEntryEquals(loadFileAsMap("hello.utf8.txt", "UTF-8"), toMap(testConsumer.waitForText()), "Greek");
        job.close();


        
        // utf-8 with BOM with charset
        job = ReplicationJob.source(server.getBasepath() + "hello.utf8.withbom.txt?charset=utf-8")
                            .startConsumingText(testConsumer);
        assertMapEntryEquals(loadFileAsMap("hello.utf8.txt", "UTF-8"), toMap(testConsumer.waitForText()), "Greek");
        job.close();
        
        // utf-8 with BOM without charset
        job = ReplicationJob.source(server.getBasepath() + "hello.utf8.withbom.txt")
                            .startConsumingText(testConsumer);
        assertMapEntryEquals(loadFileAsMap("hello.utf8.txt", "UTF-8"), toMap(testConsumer.waitForText()), "Greek");
        job.close();

        
        // ISO-8859-15 with charset
        job = ReplicationJob.source(server.getBasepath() + "hello.ISO_8859_15.txt?charset=ISO-8859-15")
                            .startConsumingText(testConsumer);
        assertMapEntryEquals(loadFileAsMap("hello.utf8.txt", "UTF-8"), toMap(testConsumer.waitForText()), "German");
        job.close();
        
        // ISO-8859-15 without charset
        job = ReplicationJob.source(server.getBasepath() + "hello.ISO_8859_15.txt")
                            .startConsumingText(testConsumer);
        assertMapEntryEquals(loadFileAsMap("hello.utf8.txt", "UTF-8"), toMap(testConsumer.waitForText()), "German");
        job.close();

        
        // ISO-8859-1 with charset
        job = ReplicationJob.source(server.getBasepath() + "hello.ISO_8859_1.txt?charset=ISO-8859-1")
                            .startConsumingText(testConsumer);
        assertMapEntryEquals(loadFileAsMap("hello.utf8.txt", "UTF-8"), toMap(testConsumer.waitForText()), "German");
        job.close();
        
        // ISO-8859-1 without charset
        job = ReplicationJob.source(server.getBasepath() + "hello.ISO_8859_1.txt")
                            .startConsumingText(testConsumer);
        assertMapEntryEquals(loadFileAsMap("hello.utf8.txt", "UTF-8"), toMap(testConsumer.waitForText()), "German");
        job.close();
        
        
        
        // cp 1252 with charset
        job = ReplicationJob.source(server.getBasepath() + "hello.cp_1252.txt?charset=windows-1252")
                            .startConsumingText(testConsumer);
        assertMapEntryEquals(loadFileAsMap("hello.utf8.txt", "UTF-8"), toMap(testConsumer.waitForText()), "German");
        job.close();
        
        // cp 1252 without charset
        job = ReplicationJob.source(server.getBasepath() + "hello.cp_1252.txt")
                            .startConsumingText(testConsumer);
        assertMapEntryEquals(loadFileAsMap("hello.utf8.txt", "UTF-8"), toMap(testConsumer.waitForText()), "German");
        job.close();
    }
    
    
    
    
    
    
    private static final class InMemoryConsumer implements Consumer<String> {
        private final AtomicReference<Optional<String>> textRef = new AtomicReference<>(Optional.empty());
        
        @Override
        public void accept(String text) {
            this.textRef.set(Optional.of(text));
        }
        
        
        public String waitForText() {
            return waitForText(Duration.ofSeconds(3));
        }
        
        public String waitForText(final Duration maxWaitTime) {
            for (int i = 0; i < 100; i++) {
                if (textRef.get().isPresent()) {
                    return textRef.get().get();
                }
                
                try {
                    Thread.sleep(maxWaitTime.toMillis() / 100);
                } catch (InterruptedException ignore) { }
            }
            
            throw new RuntimeException("no data received");
        }
    }
    
    private void assertMapEntryEquals(ImmutableMap<String, String> m1, ImmutableMap<String, String> m2, String name) {
        Assert.assertEquals(m1.get(name), m2.get(name));
    }
    
    private static ImmutableMap<String, String> loadFileAsMap(String name, String charset) throws IOException {
        final String content = new String(Files.toByteArray(new File("src" + File.separator + "test" + File.separator + "resources" + File.separator + name)), charset);
        return toMap(content);
    }
    
    private static ImmutableMap<String, String> toMap(String content) {
        return ImmutableMap.copyOf(Splitter.on("\n").trimResults().withKeyValueSeparator("=").split(content));
    }
}