/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.internal.component

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.component.AmbiguousGraphVariantsException
import org.gradle.internal.component.NoMatchingGraphVariantsException
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.util.GradleVersion

/**
 * These tests demonstrate the behavior of the [SelectionFailureHandler] when a project has various
 * variant selection failures.
 */
class SelectionFailureHandlerIntegrationTest extends AbstractIntegrationSpec {
    // region resolution failures
    def "demonstrate ambiguous graph variant selection failure for project"() {
        buildKotlinFile << """
            ${setupAmbiguousVariantSelectionFailureForProject()}
            ${forceConsumerResolution()}
        """

        expect:
        fails "forceResolution", "--stacktrace"
        failure.assertHasErrorOutput("Caused by: " + AmbiguousGraphVariantsException.class.getName())
        failure.assertHasDescription("Could not determine the dependencies of task ':forceResolution'.")
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':resolveMe'.")
        failure.assertHasCause("Could not resolve project :.")
        failure.assertHasErrorOutput("The consumer was configured to find attribute 'color' with value 'blue'. However we cannot choose between the following variants of project ::")
    }

    def "demonstrate ambiguous graph variant selection failure for externalDep"() {
        buildKotlinFile << """
            ${setupAmbiguousVariantSelectionFailureForExternalDep()}
            ${forceConsumerResolution()}
        """

        expect:
        fails "forceResolution", "--stacktrace"
        failure.assertHasErrorOutput("Caused by: " + AmbiguousGraphVariantsException.class.getName())
        failure.assertHasDescription("Execution failed for task ':forceResolution'")
        failure.assertHasCause("Could not resolve all files for configuration ':resolveMe'.")
        failure.assertHasCause("Could not resolve com.squareup.okhttp3:okhttp:4.4.0.")
        failure.assertHasErrorOutput("The consumer was configured to find attribute 'org.gradle.category' with value 'documentation'. However we cannot choose between the following variants of com.squareup.okhttp3:okhttp:4.4.0:")
    }

    def "demonstrate no matching graph variants selection failure for project"() {
        buildKotlinFile << """
            ${setupNoMatchingGraphVariantsSelectionFailureForProject()}
            ${forceConsumerResolution()}
        """

        expect:
        fails "forceResolution", "--stacktrace"
        failure.assertHasErrorOutput("Caused by: " + NoMatchingGraphVariantsException.class.getName())
        failure.assertHasDescription("Could not determine the dependencies of task ':forceResolution'.")
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':resolveMe'.")
        failure.assertHasCause("Could not resolve project :.")
        failure.assertHasErrorOutput("Incompatible because this component declares attribute 'color' with value 'blue' and the consumer needed attribute 'color' with value 'green'")
    }

    def "demonstrate no matching graph variants selection failure for externalDep"() {
        buildKotlinFile << """
            ${setupNoMatchingGraphVariantsSelectionFailureForExternalDep()}
            ${forceConsumerResolution()}
        """

        expect:
        fails "forceResolution", "--stacktrace"
        failure.assertHasErrorOutput("Caused by: " + NoMatchingGraphVariantsException.class.getName())
        failure.assertHasDescription("Execution failed for task ':forceResolution'.")
        failure.assertHasCause("Could not resolve all files for configuration ':resolveMe'.")
        failure.assertHasCause("Could not resolve com.squareup.okhttp3:okhttp:4.4.0.")
        failure.assertHasErrorOutput("No matching variant of com.squareup.okhttp3:okhttp:4.4.0 was found. The consumer was configured to find attribute 'org.gradle.category' with value 'non-existent-format' but:")
    }
    // endregion resolution failures

    // region dependencyInsight failures
    /**
     * Running the dependencyInsight report can also generate a variant selection failure, but this
     * does <strong>NOT</strong> cause the task to fail.
     */
    def "demonstrate dependencyInsight report no matching capabilities failure"() {
        buildKotlinFile << """
            ${setupDependencyInsightFailure()}
        """

        expect:
        succeeds "dependencyInsight", "--configuration", "compileClasspath", "--dependency", "gson"
        outputContains("Could not resolve com.google.code.gson:gson:2.8.5.")
        outputContains("""Failures:
      - Could not resolve com.google.code.gson:gson:2.8.5.
        Review the variant matching algorithm documentation at https://docs.gradle.org/${GradleVersion.current().version}/userguide/variant_attributes.html#abm_algorithm:""")
    }
    // endregion dependencyInsight failures

    // region setup
    private String setupAmbiguousVariantSelectionFailureForProject() {
        return """
            val color = Attribute.of("color", String::class.java)
            val shape = Attribute.of("shape", String::class.java)

            configurations {
                consumable("blueRoundElements") {
                    attributes.attribute(color, "blue")
                    attributes.attribute(shape, "round")
                }
                consumable("blueSquareElements") {
                    attributes.attribute(color, "blue")
                    attributes.attribute(shape, "square")
                }

                dependencyScope("blueFilesDependencies")

                resolvable("resolveMe") {
                    extendsFrom(configurations.getByName("blueFilesDependencies"))
                    attributes.attribute(color, "blue")
                }
            }

            dependencies {
                add("blueFilesDependencies", project(":"))
            }
        """
    }

    private String setupAmbiguousVariantSelectionFailureForExternalDep() {
        return """
            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            configurations {
                dependencyScope("myLibs")

                resolvable("resolveMe") {
                    extendsFrom(configurations.getByName("myLibs"))
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.DOCUMENTATION))
                    }
                }
            }

            dependencies {
                add("myLibs", "com.squareup.okhttp3:okhttp:4.4.0")
            }
        """
    }

    private String setupNoMatchingGraphVariantsSelectionFailureForProject() {
        return """
            plugins {
                id("base")
            }

            val color = Attribute.of("color", String::class.java)

            configurations {
                val default by getting {
                    attributes.attribute(color, "blue")
                }

                dependencyScope("defaultDependencies")

                resolvable("resolveMe") {
                    extendsFrom(configurations.getByName("defaultDependencies"))
                    attributes.attribute(color, "green")
                }
            }

            dependencies {
                add("defaultDependencies", project(":"))
            }
        """
    }

    private String setupNoMatchingGraphVariantsSelectionFailureForExternalDep() {
        return """
            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            configurations {
                dependencyScope("myLibs")

                resolvable("resolveMe") {
                    extendsFrom(configurations.getByName("myLibs"))
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, "non-existent-format"))
                    }
                }
            }

            dependencies {
                add("myLibs", "com.squareup.okhttp3:okhttp:4.4.0")
            }
        """
    }

    private String setupDependencyInsightFailure() {
        return """
            plugins {
                `java-library`
                `java-test-fixtures`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            dependencies {
                // Adds a dependency on the test fixtures of Gson, however this
                // project doesn't publish such a thing
                implementation(testFixtures("com.google.code.gson:gson:2.8.5"))
            }
        """
    }

    private String forceConsumerResolution() {
        return """
            val forceResolution by tasks.registering {
                inputs.files(configurations.getByName("resolveMe"))
                doLast {
                    inputs.files.files.forEach { println(it) }
                }
            }
        """
    }
    // endregion setup
}
