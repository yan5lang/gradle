/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r112

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException
import org.gradle.tooling.model.*
import org.gradle.tooling.model.gradle.BuildInvocations

@ToolingApiVersion(">=1.12")
@TargetGradleVersion(">=1.0-milestone-8")
class BuildInvocationsCrossVersionSpec extends ToolingApiSpecification {
    def implicitTaskNames = ['dependencies', 'dependencyInsight', 'help', 'init', 'projects', 'properties', 'tasks', 'wrapper']
    def nonRootImplicitTaskNames = ['dependencies', 'dependencyInsight', 'help', 'projects', 'properties', 'tasks']

    def setup() {
        settingsFile << '''
include 'a'
include 'b'
include 'b:c'
rootProject.name = 'test'
'''
        buildFile << '''
task t1 << {
    println "t1 in $project.name"
}

project(':b') {
    task t3 << {
        println "t3 in $project.name"
    }
    task t2 << {
        println "t2 in $project.name"
    }
}

project(':b:c') {
    task t1 << {
        println "t1 in $project.name"
    }
    task t2 << {
        println "t2 in $project.name"
    }
}'''
    }

    @TargetGradleVersion(">=1.8 <=1.11")
    def "no task selectors when running action in older container"() {
        when:
        withConnection { connection -> connection.action(new FetchAllTaskSelectorsBuildAction()).run() }

        then:
        Exception e = thrown()
        e.cause.message.startsWith('No model of type \'BuildInvocations\' is available in this build.')
    }

    @TargetGradleVersion(">=1.12")
    def "can request task selectors in action"() {
        when:
        Map<String, Set<String>> result = withConnection { connection ->
            connection.action(new FetchAllTaskSelectorsBuildAction()).run() }

        then:
        result != null
        result.keySet() == ['test', 'a', 'b', 'c'] as Set
        result['test'] == ['t1', 't2', 't3'] as Set
        result['b'] == ['t1', 't2', 't3'] as Set
        result['c'] == ['t1', 't2'] as Set
        result['a'].isEmpty()
    }

    @TargetGradleVersion(">=1.12")
    def "build task selectors from action"() {
        given:
        toolingApi.isEmbedded = false // to load launchables using correct classloader in integTest
        when:
        BuildInvocations projectSelectors = withConnection { connection ->
            connection.action(new FetchTaskSelectorsBuildAction('b')).run() }
        TaskSelector selector = projectSelectors.taskSelectors.find { it -> it.name == 't1'}
        def result = withBuild { BuildLauncher it ->
            it.forLaunchables(selector)
        }

        then:
        result.result.assertTasksExecuted(':b:c:t1')
    }

    @TargetGradleVersion(">=1.0-milestone-8")
    def "build task selectors from connection"() {
        when:
        toolingApi.isEmbedded = false // to load launchables using correct classloader in integTest
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }
        TaskSelector selector = model.taskSelectors.find { TaskSelector it ->
            it.name == 't1'
        }
        def result = withBuild { BuildLauncher it ->
            it.forLaunchables(selector)
        }

        then:
        result.result.assertTasksExecuted(':t1', ':b:c:t1')
    }

    @TargetGradleVersion(">=1.0-milestone-8")
    def "build task selectors from connection in specified order"() {
        when:
        toolingApi.isEmbedded = false // to load launchables using correct classloader in integTest
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }
        TaskSelector selectorT1 = model.taskSelectors.find {  it.name == 't1' }
        TaskSelector selectorT2 = model.taskSelectors.find { it.name == 't2' }
        def result = withBuild { BuildLauncher it ->
            it.forLaunchables(selectorT1, selectorT2)
        }
        then:
        result.result.assertTasksExecuted(':t1', ':b:c:t1', ':b:t2', ':b:c:t2')

        when:
        result = withBuild { BuildLauncher it ->
            it.forLaunchables(selectorT2, selectorT1)
        }
        then:
        result.result.assertTasksExecuted(':b:t2', ':b:c:t2', ':t1', ':b:c:t1')
    }

    @TargetGradleVersion(">=1.0-milestone-8")
    def "can request task selectors for project"() {
        given:
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }

        when:
        def selectors = model.taskSelectors.findAll { TaskSelector it ->
            !it.description.startsWith(':') && it.name != 'setupBuild' // synthetic task in 1.6
        }
        then:
        selectors*.name as Set == ['t1', 't2', 't3'] as Set
    }

    @TargetGradleVersion("=1.12")
    def "get tasks for projects"() {
        when:
        List<Task> tasks = withConnection { connection ->
            connection.action(new FetchTasksBuildAction(':b')).run()
        }

        then:
        tasks.size() == 2
        tasks*.name as Set == ['t2', 't3'] as Set

        when:
        tasks[0].project
        then:
        UnsupportedMethodException e = thrown()
        e != null
    }

    @TargetGradleVersion(">=2.0")
    def "get tasks including implicit for projects"() {
        when:
        List<Task> tasks = withConnection { connection ->
            connection.action(new FetchTasksBuildAction(':b')).run()
        }

        then:
        tasks.size() == 2 + nonRootImplicitTaskNames.size()
        tasks*.name as Set == (['t2', 't3'] + nonRootImplicitTaskNames) as Set

        when:
        tasks[0].project
        then:
        UnsupportedMethodException e = thrown()
        e != null

        when:
        tasks = withConnection { connection ->
            connection.action(new FetchTasksBuildAction(':')).run()
        }

        then:
        tasks.size() == 1 + implicitTaskNames.size()
        tasks*.name as Set == (['t1'] + implicitTaskNames) as Set

        when:
        tasks[0].project
        then:
        e = thrown()
        e != null
    }

    @TargetGradleVersion(">=1.12")
    def "build tasks from BuildInvocations model as Launchable"() {
        when:
        toolingApi.isEmbedded = false // to load launchables using correct classloader in integTest
        List<Task> tasks = withConnection { connection ->
            connection.action(new FetchTasksBuildAction(':b')).run()
        }
        Launchable task = tasks.find { it -> it.name == 't2'}
        def result = withBuild { BuildLauncher it ->
            it.forLaunchables(task)
        }

        then:
        result.result.assertTasksExecuted(':b:t2')
        result.result.assertTaskNotExecuted(':b:c:t2')
    }

    @TargetGradleVersion(">=1.0-milestone-8")
    def "build task from connection as Launchable"() {
        when:
        toolingApi.isEmbedded = false // to load launchables using correct classloader in integTest
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }
        Task task = model.tasks.find { Task it ->
            it.name == 't1'
        }
        def result = withBuild { BuildLauncher it ->
            it.forLaunchables(task)
        }

        then:
        result.result.assertTasksExecuted(':t1')
    }

    @TargetGradleVersion(">=1.0-milestone-8")
    def "build tasks Launchables in order"() {
        when:
        toolingApi.isEmbedded = false // to load launchables using correct classloader in integTest
        GradleProject model = withConnection { connection ->
            connection.getModel(GradleProject)
        }
        GradleTask taskT1 = model.tasks.find { it.name == 't1' }
        GradleTask taskBT2 = model.findByPath(':b').tasks.find { it.name == 't2' }
        GradleTask taskBCT1 = model.findByPath(':b:c').tasks.find { it.name == 't1' }
        def result = withBuild { BuildLauncher it ->
            it.forLaunchables(taskT1, taskBT2, taskBCT1)
        }
        then:
        result.result.assertTasksExecuted(':t1', ':b:t2', ':b:c:t1')

        when:
        result = withBuild { BuildLauncher it ->
            it.forLaunchables(taskBCT1, taskBT2, taskT1)
        }
        then:
        result.result.assertTasksExecuted(':b:c:t1', ':b:t2', ':t1')
    }

    @TargetGradleVersion(">=1.12")
    def "build tasks and selectors in order"() {
        when:
        toolingApi.isEmbedded = false // to load launchables using correct classloader in integTest
        GradleProject model = withConnection { connection ->
            connection.getModel(GradleProject)
        }
        GradleTask taskT1 = model.tasks.find { it.name == 't1' }
        BuildInvocations bSelectors = withConnection { connection ->
            connection.action(new FetchTaskSelectorsBuildAction('b')).run()
        }
        TaskSelector selectorBT1 = bSelectors.taskSelectors.find { it.name == 't1' }
        TaskSelector selectorBT3 = bSelectors.taskSelectors.find { it.name == 't3' }
        def result = withBuild { BuildLauncher it ->
            it.forLaunchables(selectorBT1, selectorBT3, taskT1)
        }
        then:
        result.result.assertTasksExecuted(':b:c:t1', ':b:t3', ':t1')
    }

    @TargetGradleVersion(">=1.0-milestone-8")
    def "build tasks and selectors in order cross version"() {
        when:
        toolingApi.isEmbedded = false // to load launchables using correct classloader in integTest
        GradleProject model = withConnection { connection ->
            connection.getModel(GradleProject)
        }
        GradleTask taskT1 = model.tasks.find { it.name == 't1' }
        GradleTask taskBT2 = model.findByPath(':b').tasks.find { it.name == 't2' }
        BuildInvocations rootSelectors = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }
        TaskSelector selectorT1 = rootSelectors.taskSelectors.find { it.name == 't1' }
        TaskSelector selectorT2 = rootSelectors.taskSelectors.find { it.name == 't2' }
        def result = withBuild { BuildLauncher it ->
            it.forLaunchables(taskT1, selectorT1, selectorT2, taskBT2)
        }
        then:
        result.result.assertTasksExecuted(':t1', ':b:c:t1', ':b:t2', ':b:c:t2')
    }

    @TargetGradleVersion("=1.12")
    def "build fails with selectors from different projects"() {
        when:
        toolingApi.isEmbedded = false // to load launchables using correct classloader in integTest
        BuildInvocations rootSelectors = withConnection { connection ->
            connection.action(new FetchTaskSelectorsBuildAction('test')).run()
        }
        BuildInvocations bSelectors = withConnection { connection ->
            connection.action(new FetchTaskSelectorsBuildAction('b')).run()
        }
        TaskSelector selectorT1 = rootSelectors.taskSelectors.find { it.name == 't1' }
        TaskSelector selectorBT1 = bSelectors.taskSelectors.find { it.name == 't1' }
        TaskSelector selectorBT3 = bSelectors.taskSelectors.find { it.name == 't3' }
        withBuild { BuildLauncher it ->
            it.forLaunchables(selectorBT1, selectorBT3, selectorT1)
        }
        then:
        UnsupportedBuildArgumentException e = thrown()
        e.message.contains 'Only selector from the same Gradle project can be built.'
    }

    @TargetGradleVersion(">=2.0")
    def "builds selectors from different projects"() {
        when:
        toolingApi.isEmbedded = false // to load launchables using correct classloader in integTest
        BuildInvocations rootSelectors = withConnection { connection ->
            connection.action(new FetchTaskSelectorsBuildAction('test')).run()
        }
        BuildInvocations bSelectors = withConnection { connection ->
            connection.action(new FetchTaskSelectorsBuildAction('b')).run()
        }
        TaskSelector selectorT1 = rootSelectors.taskSelectors.find { it.name == 't1' }
        TaskSelector selectorBT1 = bSelectors.taskSelectors.find { it.name == 't1' }
        TaskSelector selectorBT3 = bSelectors.taskSelectors.find { it.name == 't3' }
        def result = withBuild { BuildLauncher it ->
            it.forLaunchables(selectorBT1, selectorBT3, selectorT1)
        }
        then:
        result.result.assertTasksExecuted(':b:c:t1', ':b:t3', ':t1')
    }

    @TargetGradleVersion(">=1.0-milestone-8")
    def "can request tasks for root project"() {
        // TODO make sure it is for root project if default project is different

        given:
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }

        expect:
        model.tasks.count { it.name != 'setupBuild' && !implicitTaskNames.contains(it.name) } == 1

        when:
        def task = model.tasks.find { Task it -> it.name != 'setupBuild' && !implicitTaskNames.contains(it.name) }

        then:
        task.name == 't1'
        task.path == ':t1'

        when:
        task.project

        then:
        UnsupportedMethodException e = thrown()
    }
}
