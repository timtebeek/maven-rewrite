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

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateMigrationRecipeTest {

    public static void main(String[] args) throws Exception {
        // Usage: GenerateMigrationRecipeTest <fully qualified class name source> <fully qualified class name target>
        // <output director>
        String fullyQualifiedClassNameSource = args[0];
        String fullyQualifiedClassNameTarget = args[1];
        Path outputFolder = Path.of(args[2]);
        generateRecipe(
                Class.forName(fullyQualifiedClassNameSource),
                Class.forName(fullyQualifiedClassNameTarget),
                outputFolder);
    }

    private static void generateRecipe(Class<?> source, Class<?> target, Path outputFolder) throws IOException {
        Set<String> sourceMethodPatterns = getMethodNamesAndParameters(source.getMethods());
        Set<String> targetMethodPatterns = getMethodNamesAndParameters(target.getMethods());

        // Determine which methods are present in both classes.
        SortedSet<String> methodsWithDirectReplacement = new TreeSet<>(sourceMethodPatterns);
        methodsWithDirectReplacement.retainAll(targetMethodPatterns);
        // Determine which methods are present in the source class but not in the target class.
        SortedSet<String> methodsWithIndirectReplacement = new TreeSet<>(sourceMethodPatterns);
        methodsWithIndirectReplacement.removeAll(targetMethodPatterns);

        // Write recipes for direct replacements
        writeDirectReplacements(source, target, outputFolder, methodsWithDirectReplacement);
        writeIndirectReplacements(source, target, outputFolder, methodsWithIndirectReplacement);
    }

    private static Set<String> getMethodNamesAndParameters(Method[] sourceMethods) {
        return Stream.of(sourceMethods)
                // Only look at static methods
                .filter(m -> (m.getModifiers() & 8) == 8)
                .map(m -> m.toString()
                        .split(m.getDeclaringClass().getSimpleName())[1]
                        .substring(1))
                .collect(Collectors.toSet());
    }

    private static void writeDirectReplacements(
            Class<?> source, Class<?> target, Path outputFolder, Set<String> methodsPatterns) throws IOException {
        // Write recipes for direct replacements
        Path yamlFile = outputFolder.resolve("META-INF/rewrite/%sDirectReplacements.yml".formatted(source.getName()));
        Files.write(
                yamlFile,
                """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: %1$sDirectReplacements
                displayName: Replace `%1$s` with `%2$s`
                description: Replace `%1$s` method calls with calls to `%2$s`.
                recipeList:
                """
                        .formatted(source.getName(), target.getName())
                        .getBytes());
        // Append recipe list
        Files.write(
                yamlFile,
                methodsPatterns.stream()
                        .map(m ->
                                """
                                - org.openrewrite.java.ChangeMethodTargetToStatic:
                                    methodPattern: %s %s
                                    fullyQualifiedTargetTypeName: %s
                                """
                                        .formatted(source.getName(), m, target.getName()))
                        .collect(Collectors.joining())
                        .getBytes(),
                StandardOpenOption.APPEND);
    }

    private static void writeIndirectReplacements(
            Class<?> source, Class<?> target, Path outputFolder, Set<String> methodsPatterns) throws IOException {
        // Write recipes for methods without direct replacement
        Path directReplacementFile =
                outputFolder.resolve("META-INF/rewrite/%sIndirectReplacements.yml".formatted(source.getName()));
        Files.write(
                directReplacementFile,
                """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: %1$sIndirectReplacements
                displayName: Replace `%1$s` with `%2$s`
                description: Replace `%1$s` method calls with calls to `%2$s`.
                recipeList:
                """
                        .formatted(source.getName(), target.getName())
                        .getBytes());
        // Append recipe list
        Files.write(
                directReplacementFile,
                methodsPatterns.stream()
                        .map(m ->
                                """
                                - org.openrewrite.java.ChangeMethodTargetToStatic:
                                    methodPattern: %s %s
                                    fullyQualifiedTargetTypeName: %s
                                """
                                        .formatted(source.getName(), m, target.getName()))
                        .collect(Collectors.joining())
                        .getBytes(),
                StandardOpenOption.APPEND);
    }

    @Test
    void assertGeneratedRecipes(@TempDir Path tempDir) throws Exception {
        Path rewriteDir = tempDir.resolve("META-INF/rewrite");
        Files.createDirectories(rewriteDir);
        generateRecipe(org.codehaus.plexus.util.StringUtils.class, org.apache.commons.lang3.StringUtils.class, tempDir);
        Path directReplacements = rewriteDir.resolve("org.codehaus.plexus.util.StringUtilsDirectReplacements.yml");
        assertThat(Files.readString(directReplacements))
                .startsWith(
                        """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: org.codehaus.plexus.util.StringUtilsDirectReplacements
                displayName: Replace `org.codehaus.plexus.util.StringUtils` with `org.apache.commons.lang3.StringUtils`
                description: Replace `org.codehaus.plexus.util.StringUtils` method calls with calls to `org.apache.commons.lang3.StringUtils`.
                recipeList:
                - org.openrewrite.java.ChangeMethodTargetToStatic:
                    methodPattern: org.codehaus.plexus.util.StringUtils abbreviate(java.lang.String,int)
                    fullyQualifiedTargetTypeName: org.apache.commons.lang3.StringUtils
                - org.openrewrite.java.ChangeMethodTargetToStatic:
                    methodPattern: org.codehaus.plexus.util.StringUtils abbreviate(java.lang.String,int,int)
                    fullyQualifiedTargetTypeName: org.apache.commons.lang3.StringUtils
                """);
        Path indirectReplacements = rewriteDir.resolve("org.codehaus.plexus.util.StringUtilsIndirectReplacements.yml");
        assertThat(Files.readString(indirectReplacements))
                .startsWith(
                        """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: org.codehaus.plexus.util.StringUtilsIndirectReplacements
                displayName: Replace `org.codehaus.plexus.util.StringUtils` with `org.apache.commons.lang3.StringUtils`
                description: Replace `org.codehaus.plexus.util.StringUtils` method calls with calls to `org.apache.commons.lang3.StringUtils`.
                recipeList:
                - org.openrewrite.java.ChangeMethodTargetToStatic:
                    methodPattern: org.codehaus.plexus.util.StringUtils addAndDeHump(java.lang.String)
                    fullyQualifiedTargetTypeName: org.apache.commons.lang3.StringUtils
                - org.openrewrite.java.ChangeMethodTargetToStatic:
                    methodPattern: org.codehaus.plexus.util.StringUtils capitalise(java.lang.String)
                    fullyQualifiedTargetTypeName: org.apache.commons.lang3.StringUtils
                """);
    }

    @Test
    void generatePlexusToCommons() throws Exception {
        Path metaInfRewrite = Path.of("src/main/resources");
        generateRecipe(
                org.codehaus.plexus.util.StringUtils.class, org.apache.commons.lang3.StringUtils.class, metaInfRewrite);
    }

    @Test
    void generateMavenSharedToCommons() throws Exception {
        Path metaInfRewrite = Path.of("src/main/resources");
        generateRecipe(
                org.apache.maven.shared.utils.StringUtils.class,
                org.apache.commons.lang3.StringUtils.class,
                metaInfRewrite);
    }
}
