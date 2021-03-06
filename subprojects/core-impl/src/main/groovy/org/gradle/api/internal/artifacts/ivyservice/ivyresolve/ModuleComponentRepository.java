/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

/**
 * A repository of module components.
 *
 * The plan is to sync this with {@link org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver} and rename it
 * to have 'resolver' instead of 'repository' in its name.
 */
public interface ModuleComponentRepository {
    String getId();

    String getName();

    /**
     * @return True if the underlying repository can provide a full list of module versions for a selector.
     */
    // TODO:DAZ Remove this when we remove IvyDependencyResolverAdapater
    boolean canListModuleVersions();

    ModuleComponentRepositoryAccess getLocalAccess();

    ModuleComponentRepositoryAccess getRemoteAccess();
}
