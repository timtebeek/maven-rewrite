/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.timtebeek.maven.rewrite;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class StringUtilsToCommonsLang3Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpath("plexus-utils", "maven-shared-utils"))
                .recipe(Environment.builder()
                        .scanRuntimeClasspath()
                        .build()
                        .activateRecipes("com.github.timtebeek.maven.rewrite.StringUtilsToCommonsLang3"));
    }

    @Nested
    class AddDependencies {

        @Test
        void addMissingDependency() {
            rewriteRun(
                    mavenProject(
                            "project",
                            // language=Java
                            srcMainJava(
                                    java(
                                            """
                                        import org.apache.maven.shared.utils.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.reverse("foo");
                                            }
                                        }
                                        """,
                                            """
                                        import org.apache.commons.lang3.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.reverse("foo");
                                            }
                                        }
                                        """),
                                    // language=xml
                                    pomXml(
                                            """
                                            <project>
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>com.github.timtebeek.maven</groupId>
                                                <artifactId>project</artifactId>
                                                <version>1.0.0</version>
                                                <dependencies>
                                                    <dependency>
                                                        <groupId>org.apache.maven.shared</groupId>
                                                        <artifactId>maven-shared-utils</artifactId>
                                                        <version>3.2.1</version>
                                                    </dependency>
                                                </dependencies>
                                            </project>
                                            """,
                                            """
                                            <project>
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>com.github.timtebeek.maven</groupId>
                                                <artifactId>project</artifactId>
                                                <version>1.0.0</version>
                                                <dependencies>
                                                    <dependency>
                                                        <groupId>org.apache.commons</groupId>
                                                        <artifactId>commons-lang3</artifactId>
                                                        <version>3.12.0</version>
                                                    </dependency>
                                                    <dependency>
                                                        <groupId>org.apache.maven.shared</groupId>
                                                        <artifactId>maven-shared-utils</artifactId>
                                                        <version>3.2.1</version>
                                                    </dependency>
                                                </dependencies>
                                            </project>
                                            """))));
        }

        @Test
        void notAddedWhenPresent() {
            rewriteRun(
                    mavenProject(
                            "project",
                            // language=Java
                            srcMainJava(
                                    java(
                                            """
                                        import org.apache.maven.shared.utils.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.reverse("foo");
                                            }
                                        }
                                        """,
                                            """
                                        import org.apache.commons.lang3.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.reverse("foo");
                                            }
                                        }
                                        """)),
                            // language=xml
                            pomXml(
                                    """
                                            <project>
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>com.github.timtebeek.maven</groupId>
                                                <artifactId>project</artifactId>
                                                <version>1.0.0</version>
                                                <dependencies>
                                                    <dependency>
                                                        <groupId>org.apache.commons</groupId>
                                                        <artifactId>commons-lang3</artifactId>
                                                        <version>3.12.0</version>
                                                    </dependency>
                                                    <dependency>
                                                        <groupId>org.apache.maven.shared</groupId>
                                                        <artifactId>maven-shared-utils</artifactId>
                                                        <version>3.2.1</version>
                                                    </dependency>
                                                </dependencies>
                                            </project>
                                            """)));
        }

        @Test
        void notAddedTwice() {
            rewriteRun(
                    mavenProject(
                            "project",
                            // language=Java
                            srcMainJava(
                                    java(
                                            """
                                        import org.apache.maven.shared.utils.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.reverse("foo");
                                            }
                                        }
                                        """,
                                            """
                                        import org.apache.commons.lang3.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.reverse("foo");
                                            }
                                        }
                                        """),
                                    java(
                                            """
                                                    import org.codehaus.plexus.util.StringUtils;

                                                    class B {
                                                        public void test() {
                                                            StringUtils.reverse("foo");
                                                        }
                                                    }
                                                    """,
                                            """
                                                    import org.apache.commons.lang3.StringUtils;

                                                    class B {
                                                        public void test() {
                                                            StringUtils.reverse("foo");
                                                        }
                                                    }
                                                    """)),
                            // language=xml
                            pomXml(
                                    """
                                            <project>
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>com.github.timtebeek.maven</groupId>
                                                <artifactId>project</artifactId>
                                                <version>1.0.0</version>
                                                <dependencies>
                                                    <dependency>
                                                        <groupId>org.apache.maven.shared</groupId>
                                                        <artifactId>maven-shared-utils</artifactId>
                                                        <version>3.2.1</version>
                                                    </dependency>
                                                    <dependency>
                                                        <groupId>org.codehaus.plexus</groupId>
                                                        <artifactId>plexus-utils</artifactId>
                                                        <version>3.5.0</version>
                                                    </dependency>
                                                </dependencies>
                                            </project>
                                            """,
                                    """
                                            <project>
                                                <modelVersion>4.0.0</modelVersion>
                                                <groupId>com.github.timtebeek.maven</groupId>
                                                <artifactId>project</artifactId>
                                                <version>1.0.0</version>
                                                <dependencies>
                                                    <dependency>
                                                        <groupId>org.apache.commons</groupId>
                                                        <artifactId>commons-lang3</artifactId>
                                                        <version>3.12.0</version>
                                                    </dependency>
                                                    <dependency>
                                                        <groupId>org.apache.maven.shared</groupId>
                                                        <artifactId>maven-shared-utils</artifactId>
                                                        <version>3.2.1</version>
                                                    </dependency>
                                                    <dependency>
                                                        <groupId>org.codehaus.plexus</groupId>
                                                        <artifactId>plexus-utils</artifactId>
                                                        <version>3.5.0</version>
                                                    </dependency>
                                                </dependencies>
                                            </project>
                                            """)));
        }
    }

    @Nested
    class MavenShared {
        @Nested
        class DirectReplacements {
            @Test
            void reverse() {
                // language=java
                rewriteRun(
                        java(
                                """
                                        import org.apache.maven.shared.utils.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.reverse("foo");
                                            }
                                        }
                                        """,
                                """
                                        import org.apache.commons.lang3.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.reverse("foo");
                                            }
                                        }
                                        """));
            }
        }

        @Nested
        class ManualReplacements {
            @Test
            void capitalise() {
                // language=java
                rewriteRun(
                        java(
                                """
                                        import org.apache.maven.shared.utils.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.capitalise("foo");
                                            }
                                        }
                                        """,
                                """
                                        import org.apache.commons.lang3.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.capitalize("foo");
                                            }
                                        }
                                        """));
            }

            @Test
            void clean() {
                // language=java
                rewriteRun(
                        java(
                                """
                                        import org.apache.maven.shared.utils.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.clean("foo");
                                            }
                                        }
                                        """,
                                """
                                        import org.apache.commons.lang3.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.trimToEmpty("foo");
                                            }
                                        }
                                        """));
            }

            @Test
            void defaultString() {
                // language=java
                rewriteRun(
                        java(
                                """
                                        import org.codehaus.plexus.util.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.defaultString("foo", "bar");
                                            }
                                        }
                                        """,
                                """
                                        import java.util.Objects;

                                        class A {
                                            public void test() {
                                                Objects.toString("foo", "bar");
                                            }
                                        }
                                        """));
            }
        }
    }

    @Nested
    class Plexus {
        @Nested
        class DirectReplacements {
            @Test
            void reverse() {
                // language=java
                rewriteRun(
                        java(
                                """
                                        import org.codehaus.plexus.util.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.reverse("foo");
                                            }
                                        }
                                        """,
                                """
                                        import org.apache.commons.lang3.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.reverse("foo");
                                            }
                                        }
                                        """));
            }
        }

        @Nested
        class ManualReplacements {
            @Test
            void capitalise() {
                // language=java
                rewriteRun(
                        java(
                                """
                                        import org.codehaus.plexus.util.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.capitalise("foo");
                                            }
                                        }
                                        """,
                                """
                                        import org.apache.commons.lang3.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.capitalize("foo");
                                            }
                                        }
                                        """));
            }

            @Test
            void clean() {
                // language=java
                rewriteRun(
                        java(
                                """
                                        import org.codehaus.plexus.util.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.clean("foo");
                                            }
                                        }
                                        """,
                                """
                                        import org.apache.commons.lang3.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.trimToEmpty("foo");
                                            }
                                        }
                                        """));
            }

            @Test
            void defaultString() {
                // language=java
                rewriteRun(
                        java(
                                """
                                        import org.codehaus.plexus.util.StringUtils;

                                        class A {
                                            public void test() {
                                                StringUtils.defaultString("foo", "bar");
                                            }
                                        }
                                        """,
                                """
                                        import java.util.Objects;

                                        class A {
                                            public void test() {
                                                Objects.toString("foo", "bar");
                                            }
                                        }
                                        """));
            }
        }
    }
}
