/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.openejb.server.ssh;

import org.apache.openejb.OpenEjbContainer;
import org.apache.openejb.config.DeploymentFilterable;
import org.apache.sshd.ClientChannel;
import org.apache.sshd.ClientSession;
import org.apache.sshd.SshClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ejb.embeddable.EJBContainer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SSHServerTest {
    private static EJBContainer container;

    @BeforeClass
    public static void start() {
        System.setProperty("openejb.server.ssh.key", "src/test/key/ssh-key");
        System.setProperty("openejb.logger.external", "true");
        container = EJBContainer.createEJBContainer(new HashMap<Object, Object>() {{
            put(OpenEjbContainer.OPENEJB_EMBEDDED_REMOTABLE, "true");
            put(DeploymentFilterable.CLASSPATH_FILTER_SYSTEMAPPS, "false");
        }});
    }

    @AfterClass
    public static void close() {
        container.close();
        System.getProperties().remove("openejb.logger.external");
        System.getProperties().remove("openejb.server.ssh.key");
    }

    @Test(timeout = 10000L)
    public void call() throws Exception {
        final SshClient client = SshClient.setUpDefaultClient();
        client.start();
        try {
            final ClientSession session = client.connect("localhost", 4222).await().getSession();
            session.authPassword("jonathan", "secret");

            final ClientChannel channel = session.createChannel("shell");
            final ByteArrayOutputStream sent = new ByteArrayOutputStream();
            final PipedOutputStream pipedIn = new TeePipedOutputStream(sent);
            channel.setIn(new PipedInputStream(pipedIn));
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final ByteArrayOutputStream err = new ByteArrayOutputStream();
            channel.setOut(out);
            channel.setErr(err);
            channel.open();

            pipedIn.write("properties\r\n".getBytes());
            pipedIn.flush();

            pipedIn.write("exit\r\n".getBytes());
            pipedIn.flush();

            channel.waitFor(ClientChannel.CLOSED, 0);
            channel.close(false);
            client.stop();

            assertTrue(new String(sent.toByteArray()).contains("properties\r\nexit\r\n"));
            assertTrue(new String(out.toByteArray()).contains("ServerService(id=ssh)"));
        } catch (final Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    public static class TeePipedOutputStream extends PipedOutputStream {
        private OutputStream tee;

        public TeePipedOutputStream(final OutputStream tee) {
            this.tee = tee;
        }

        @Override
        public void write(final int b) throws IOException {
            super.write(b);
            tee.write(b);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            super.write(b, off, len);
            tee.write(b, off, len);
        }
    }
}
