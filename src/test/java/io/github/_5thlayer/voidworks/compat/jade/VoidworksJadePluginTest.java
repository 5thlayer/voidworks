// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.compat.jade;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import org.junit.jupiter.api.Test;

/**
 * The plugin class loads on a dedicated server, where the client's classes aren't there: Jade loads
 * it there to register its server data, and drops it whole if it fails (#137).
 */
class VoidworksJadePluginTest {

    @Test
    void itLoadsWithoutTheClient() {
        var server = new ServerLoader(getClass().getClassLoader());
        assertDoesNotThrow(() -> Class.forName(VoidworksJadePlugin.class.getName(), true, server));
    }

    /** The test's classes, loaded afresh and without {@code net.minecraft.client}, as on a dedicated server. */
    private static final class ServerLoader extends ClassLoader {

        private final ClassLoader classes;

        ServerLoader(ClassLoader classes) {
            super(ClassLoader.getPlatformClassLoader());
            this.classes = classes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.startsWith("net.minecraft.client.")) {
                throw new ClassNotFoundException(name);
            }
            try (var bytes = classes.getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                var read = bytes.readAllBytes();
                return defineClass(name, read, 0, read.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        @Override
        protected URL findResource(String name) {
            return classes.getResource(name);
        }

        @Override
        protected Enumeration<URL> findResources(String name) throws IOException {
            return classes.getResources(name);
        }
    }
}
