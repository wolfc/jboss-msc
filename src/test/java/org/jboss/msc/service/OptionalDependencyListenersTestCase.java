/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.msc.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.concurrent.Future;

import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.util.FailToStartService;
import org.jboss.msc.util.TestServiceListener;
import org.junit.Before;
import org.junit.Test;

/**
 * Test notifications sent to listeners related to dependency operations with scenarios that involve one or more
 * optional dependencies.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 * @see ServiceListener#dependencyFailed(ServiceController)
 * @see ServiceListener#dependencyFailureCleared(ServiceController)
 * @see ServiceListener#dependencyInstalled(ServiceController)
 * @see ServiceListener#dependencyUninstalled(ServiceController)
 */
public class OptionalDependencyListenersTestCase extends AbstractServiceTest {
    private static final ServiceName firstServiceName = ServiceName.of("firstService");
    private static final ServiceName secondServiceName = ServiceName.of("secondService");
    private static final ServiceName thirdServiceName = ServiceName.of("thirdService");
    private static final ServiceName fourthServiceName = ServiceName.of("fourthService");
    private static final ServiceName fifthServiceName = ServiceName.of("fifthService");
    private static final ServiceName sixthServiceName = ServiceName.of("sixthService");
    private TestServiceListener testListener;

    @Before
    public void setUpTestListener() {
        testListener = new TestServiceListener();
    }

    @Test
    public void testNotNotifiedOptionalFailedDependencyUninstalled() throws Exception {
        final Future<ServiceController<?>> firstServiceStart = testListener.expectServiceStart(firstServiceName);
        // install first service, with an optional dependency on missing second service
        serviceContainer.addService(firstServiceName, Service.NULL)
            .addListener(testListener)
            .addOptionalDependency(secondServiceName)
            .install();
        // first service is expected to start
        assertController(firstServiceName, firstServiceStart);

        final Future<StartException> secondServiceFailed = testListener.expectServiceFailure(secondServiceName);
        // install the missing second service, a fail to start service set to fail on the first attempt to start
        serviceContainer.addService(secondServiceName, new FailToStartService(true))
            .addListener(testListener)
            .install();
        // second service is expected to fail
        final ServiceController<?> secondController = assertFailure(secondServiceName, secondServiceFailed);

        final Future<ServiceController<?>> firstServiceDependencyUninstalled = testListener.expectNoDependencyUninstall(firstServiceName);
        // remove second service
        secondController.setMode(Mode.REMOVE);
        // no missing dependency notification is expected
        assertNull(firstServiceDependencyUninstalled.get());
    }

    @Test
    public void testOptionalFailedDependencyUninstalled1() throws Exception {
        testOptionalFailedDependencyUninstalled(Mode.NEVER);
    }

    @Test
    public void testOptionalFailedDependencyUninstalled2() throws Exception {
        testOptionalFailedDependencyUninstalled(Mode.ACTIVE);
    }

    public void testOptionalFailedDependencyUninstalled(ServiceController.Mode initialMode) throws Exception {
        final Future<StartException> secondServiceFailed = testListener.expectServiceFailure(secondServiceName);
        final Future<ServiceController<?>> firstServiceDependencyFailed = testListener.expectDependencyFailure(firstServiceName);

        //install second service, a fail to start service, set to fail at the first attempt to start
        serviceContainer.addService(secondServiceName, new FailToStartService(true))
            .addListener(testListener)
            .install();
        // second service is expected to fail
        assertFailure(secondServiceName, secondServiceFailed);

        // and install first service on initialMode, with a dependency on second service
        serviceContainer.addService(firstServiceName, Service.NULL)
            .addListener(testListener)
            .addOptionalDependency(secondServiceName)
            .setInitialMode(initialMode)
            .install();
        // a dep failure notification should be send by first service
        final ServiceController<?> firstController = assertController(firstServiceName, firstServiceDependencyFailed);

        final Future<ServiceController<?>> firstServiceDependencyFailureCleared = testListener.expectDependencyFailureCleared(firstServiceName);
        final Future<ServiceController<?>> firstServiceDependencyUninstalled = testListener.expectNoDependencyUninstall(firstServiceName);
        // remove second service
        serviceContainer.getService(secondServiceName).setMode(Mode.REMOVE);
        // the dep failure should be cleared
        assertSame(firstController, firstServiceDependencyFailureCleared.get());
        // and a new missing dep notification is expected
        assertNull(firstServiceDependencyUninstalled.get());
    }

    @Test
    public void testOptionalDependencyUninstalled() throws Exception {
        final Future<ServiceController<?>> secondServiceDependencyMissing = testListener.expectDependencyUninstall(secondServiceName);
        // install second service, that depends on the missing third service
        serviceContainer.addService(secondServiceName, Service.NULL)
            .addListener(testListener)
            .addDependency(thirdServiceName)
            .install();
        // a missing dependency notification is expected from second services
        final ServiceController<?> secondController = assertController(secondServiceName, secondServiceDependencyMissing);

        final Future<ServiceController<?>> firstServiceDependencyMissing = testListener.expectDependencyUninstall(firstServiceName);
        // install first service, which has an optional dependency on second service
        serviceContainer.addService(firstServiceName, Service.NULL)
            .addListener(testListener)
            .addOptionalDependency(secondServiceName)
            .install();
        // a missing dependency notification is also expected from first service
        final ServiceController<?> firstController = assertController(firstServiceName, firstServiceDependencyMissing);

        final Future<ServiceController<?>> firstServiceDependencyInstall = testListener.expectDependencyInstall(firstServiceName);
        final Future<ServiceController<?>> firstServiceStart = testListener.expectServiceStart(firstServiceName);
        // removing second service is expected to cause its disconnection from the optional dependent first service
        secondController.setMode(Mode.REMOVE);
        // thus resulting in a dependency install notification, and in first service starting
        assertController(firstController, firstServiceDependencyInstall);
        assertController(firstController, firstServiceStart);
    }

    @Test
    public void testOptionalDependencyInstalledFirst2() throws Exception {
        Future<StartException> secondServiceFailed = testListener.expectServiceFailure(secondServiceName);
        Future<ServiceController<?>> thirdServiceStart = testListener.expectServiceStart(thirdServiceName);
        // install thirdService, a failToStart service, set to fail on the first attempt to start,
        // and dependent on third service
        serviceContainer.addService(secondServiceName, new FailToStartService(true))
            .addDependency(thirdServiceName)
            .addListener(testListener)
            .install();
        serviceContainer.addService(thirdServiceName, Service.NULL).addListener(testListener).install();
        final ServiceController<?> thirdController = assertController(thirdServiceName, thirdServiceStart);
        // secondService fails
        assertFailure(secondServiceName, secondServiceFailed);

        final Future<ServiceController<?>> thirdServiceStop = testListener.expectServiceStop(thirdServiceName);
        final Future<ServiceController<?>> secondServiceDependencyMissing = testListener.expectDependencyUninstall(secondServiceName);
        // remove thirdService
        thirdController.setMode(Mode.REMOVE);
        // third service should stop, and second service should send a missing dep notification
        assertSame(thirdController, thirdServiceStop.get());
        final ServiceController<?> secondController = assertController(secondServiceName, secondServiceDependencyMissing);

        final Future<ServiceController<?>> firstServiceDependencyMissing = testListener.expectDependencyUninstall(firstServiceName);
        // install firstService, a dependent on second service
        serviceContainer.addService(firstServiceName, Service.NULL)
            .addListener(testListener)
            .addOptionalDependency(secondServiceName)
            .install();
        // first service is expected to send a notification of missing dependency
        final ServiceController<?> firstController = assertController(firstServiceName, firstServiceDependencyMissing);

        thirdServiceStart = testListener.expectServiceStart(thirdServiceName);
        final Future<ServiceController<?>> secondServiceDependencyInstalled = testListener.expectDependencyInstall(secondServiceName);
        final Future<ServiceController<?>> firstServiceDependencyInstalled = testListener.expectDependencyInstall(firstServiceName);
        final Future<ServiceController<?>> firstServiceStart = testListener.expectServiceStart(firstServiceName);
        final Future<ServiceController<?>> secondServiceStart = testListener.expectServiceStart(secondServiceName);
        // install third service
        serviceContainer.addService(thirdServiceName, Service.NULL)
            .addListener(testListener)
            .install();
        // third service is expected to start without issues
        assertController(thirdServiceName, thirdServiceStart);
        // second and first service should send a dep installed notification...
        assertController(secondController, secondServiceDependencyInstalled);
        assertController(firstController, firstServiceDependencyInstalled);
        // and start
        assertController(secondController, secondServiceStart);
        assertController(firstController, firstServiceStart);

        final Future<ServiceController<?>> firstServiceStopped = testListener.expectServiceRemoval(firstServiceName);
        firstController.setMode(Mode.REMOVE);
        assertSame(firstController, firstServiceStopped.get());
    }

    @Test
    public void testOptionalDependencyWithFailuresAndMissingDeps() throws Exception {
        Future<ServiceController<?>> firstServiceStart = testListener.expectServiceStart(firstServiceName);
        // install first service with an optional dependency on second service
        serviceContainer.addService(firstServiceName, Service.NULL)
            .addListener(testListener)
            .addOptionalDependency(secondServiceName)
            .install();
        // first service is expected to start
        final ServiceController<?> firstController = assertController(firstServiceName, firstServiceStart);

        final Future<ServiceController<?>> secondServiceDependencyMissing = testListener.expectDependencyUninstall(secondServiceName);
        // install second service with dependencies on the missing third and fourth services
        serviceContainer.addService(secondServiceName, Service.NULL)
            .addListener(testListener)
            .addDependencies(thirdServiceName, fourthServiceName)
            .install();
        // a dep missing notification is expected from second service
        ServiceController<?> secondController = assertController(secondServiceName, secondServiceDependencyMissing);
        // while first controller continues disconnected from second service, and hence is still in the up state
        assertSame(State.UP, firstController.getState());

        Future<ServiceController<?>> firstServiceStop = testListener.expectServiceStop(firstServiceName);
        Future<ServiceController<?>> firstServiceDependencyMissing = testListener.expectDependencyUninstall(firstServiceName);
        // disable first controller
        firstController.setMode(Mode.NEVER);
        assertController(firstController, firstServiceStop);
        // this should enable its connection with the optional dependency on second service, resulting in the
        // dependency missing notification
        assertController(firstController, firstServiceDependencyMissing);

        final Future<ServiceController<?>> secondServiceRemoved = testListener.expectServiceRemoval(secondServiceName);
        final Future<ServiceController<?>> firstServiceDependencyInstall = testListener.expectDependencyInstall(firstServiceName);
        // remove second service
        secondController.setMode(Mode.REMOVE);
        assertController(secondController, secondServiceRemoved);
        // the result is that first service is disconnected from its optional dependency and sends a dependency install
        // notification
        assertController(firstController, firstServiceDependencyInstall);

        firstServiceStart = testListener.expectServiceStart(firstServiceName);
        // enable first service
        firstController.setMode(Mode.ACTIVE);
        // which results in the service start
        assertController(firstController, firstServiceStart);

        final Future<StartException> fourthServiceFailure = testListener.expectServiceFailure(fourthServiceName);
        final Future<StartException> fifthServiceFailure = testListener.expectServiceFailure(fifthServiceName);
        // install the missing fourth and fifth services
        // both set to fail on the first attempt to start
        serviceContainer.addService(fourthServiceName, new FailToStartService(true))
            .addListener(testListener)
            .install();
        serviceContainer.addService(fifthServiceName, new FailToStartService(true))
            .addListener(testListener)
            .install();
        assertFailure(fourthServiceName, fourthServiceFailure);
        assertFailure(fifthServiceName, fifthServiceFailure);

        final Future<ServiceController<?>> secondServiceDependencyFailure = testListener.expectDependencyFailure(secondServiceName);
        // reinstall second service
        serviceContainer.addService(secondServiceName, Service.NULL)
            .addListener(testListener)
            .addDependencies(thirdServiceName, fourthServiceName)
            .install();
        // a dependency failure notification is expected now from second service
        secondController = assertController(secondServiceName, secondServiceDependencyFailure);

        firstServiceStop = testListener.expectServiceStop(firstServiceName);
        final Future<ServiceController<?>> firstServiceDependencyFailure = testListener.expectDependencyUninstall(firstServiceName);
        // disable first service
        firstController.setMode(Mode.NEVER);
        assertController(firstController, firstServiceStop);
        // thus causing it to connect with its optional dependency...
        // the result is a dependency failure notification, regarding fourth and fifth service start failures
        assertController(firstController, firstServiceDependencyFailure);
    }

    @Test
    public void testOptionalDependencyInstalledFirst() throws Exception {
        final Future<ServiceController<?>> thirdServiceDependencyFailed = testListener.expectDependencyFailure(thirdServiceName);
        final Future<StartException> fourthServiceFailed = testListener.expectServiceFailure(fourthServiceName);
        // install thirdService and fourthService
        // thirdService depends on fourthService...
        serviceContainer.addService(thirdServiceName, Service.NULL)
            .addDependency(fourthServiceName)
            .setInitialMode(Mode.ACTIVE)
            .addListener(testListener)
            .install();
        // ... a failToStart service, set to fail on the first attempt to start it
        serviceContainer.addService(fourthServiceName, new FailToStartService(true)).addListener(testListener).setInitialMode(Mode.ON_DEMAND).install();
        // fourthService fails
        assertNotNull(fourthServiceFailed.get());
        // a dependencyFailure notification is expected by thirdService
        ServiceController<?> thirdController = assertController(thirdServiceName, thirdServiceDependencyFailed);

        final Future<ServiceController<?>> secondServiceDependencyFailed = testListener.expectDependencyFailure(secondServiceName);
        final Future<ServiceController<?>> firstServiceDependencyFailed = testListener.expectDependencyFailure(firstServiceName);

        // add secondService with an optional dependency on thirdService

        // add firstService with a dependency on secondService
        serviceContainer.addService(firstServiceName, Service.NULL)
            .addDependency(secondServiceName).setInitialMode(Mode.NEVER)
            .addListener(testListener)
            .install();
        serviceContainer.addService(secondServiceName, Service.NULL)
        .addOptionalDependency(thirdServiceName)
        .setInitialMode(Mode.NEVER)
        .addListener(testListener)
        .install();
        // and the first dependency failed message is expected to reach the entire dependent chain, including firstService
        final ServiceController<?> secondController = assertController(secondServiceName, secondServiceDependencyFailed);
        final ServiceController<?> firstController = assertController(firstServiceName, firstServiceDependencyFailed);

        final Future<ServiceController<?>> thirdServiceDependencyFailureCleared = testListener.expectDependencyFailureCleared(thirdServiceName);
        final Future<ServiceController<?>> secondServiceDependencyFailureCleared = testListener.expectDependencyFailureCleared(secondServiceName);
        final Future<ServiceController<?>> firstServiceDependencyFailureCleared = testListener.expectDependencyFailureCleared(firstServiceName);
        // set third service mode to never
        thirdController.setMode(Mode.NEVER);
        // the failure is expected to be cleared
        assertController(thirdController, thirdServiceDependencyFailureCleared);
        assertController(secondController, secondServiceDependencyFailureCleared);
        assertController(firstController, firstServiceDependencyFailureCleared);

        Future<ServiceController<?>> thirdServiceStart = testListener.expectServiceStart(thirdServiceName);
        Future<ServiceController<?>> fourthServiceStart = testListener.expectServiceStart(fourthServiceName);
        // set third service to active mode
        thirdController.setMode(Mode.ACTIVE);
        // third and fourth service must start at this point
        ServiceController<?> fourthController = assertController(fourthServiceName, fourthServiceStart);
        assertController(thirdController, thirdServiceStart);

        Future<ServiceController<?>> secondServiceStart = testListener.expectServiceStart(secondServiceName);
        // set second service mode to active
        secondController.setMode(Mode.ACTIVE);
        // second service is expected to start
        assertController(secondController, secondServiceStart);

        Future<ServiceController<?>> thirdServiceMissingDependency = testListener.expectDependencyUninstall(thirdServiceName);
        Future<ServiceController<?>> secondServiceStop = testListener.expectServiceStop(secondServiceName);
        Future<ServiceController<?>> secondServiceMissingDependency = testListener.expectDependencyUninstall(secondServiceName);
        Future<ServiceController<?>> firstServiceMissingDependency = testListener.expectDependencyUninstall(firstServiceName);
        // remove fourth service
        fourthController.setMode(Mode.REMOVE);
        // the missing dependency is expecte dto reach the entire dependency chain
        assertController(thirdController, thirdServiceMissingDependency);
        assertController(secondController, secondServiceMissingDependency);
        assertController(firstController, firstServiceMissingDependency);
        // and second service should stop
        assertController(secondController, secondServiceStop);

        Future<ServiceController<?>> firstServiceDependencyInstalled = testListener.expectDependencyInstall(firstServiceName);
        Future<ServiceController<?>> secondServiceDependencyInstalled = testListener.expectDependencyInstall(secondServiceName);
        Future<ServiceController<?>> thirdServiceDependencyInstalled = testListener.expectDependencyInstall(thirdServiceName);
        fourthServiceStart = testListener.expectServiceStart(fourthServiceName);
        thirdServiceStart = testListener.expectServiceStart(thirdServiceName);
        secondServiceStart = testListener.expectServiceStart(secondServiceName);
        // install fourth service
        serviceContainer.addService(fourthServiceName, Service.NULL).addListener(testListener).install();
        fourthController = assertController(fourthServiceName, fourthServiceStart);
        // all dep chain must send the dependency installed notification
        assertController(thirdController, thirdServiceDependencyInstalled);
        assertController(secondController, secondServiceDependencyInstalled);
        assertController(firstController, firstServiceDependencyInstalled);
        // and second and third service should both start
        assertController(thirdController, thirdServiceStart);
        assertController(secondController, secondServiceStart);

        secondServiceStop = testListener.expectServiceStop(secondServiceName);
        secondServiceStart = testListener.expectServiceStart(secondServiceName);
        // remove third service
        thirdController.setMode(Mode.REMOVE);
        // second service is expected to stop as the result of third service being stopped
        assertController(secondController, secondServiceStop);
        // and expected to start again as the third service removal results in the second service disconnection from
        // the optional dependency
        assertController(secondController, secondServiceStart);

        final Future<ServiceController<?>> fourthServiceRemoval = testListener.expectServiceRemoval(fourthServiceName);
        // remove fourth service
        fourthController.setMode(Mode.REMOVE);
        assertController(fourthController, fourthServiceRemoval);

        final Future<ServiceController<?>> thirdServiceDependencyMissing = testListener.expectDependencyUninstall(thirdServiceName);
        final Future<ServiceController<?>> secondServiceDependencyMissing = testListener.expectNoDependencyUninstall(secondServiceName);
        // reinstall third service with dependency on missing fourth service
        serviceContainer.addService(thirdServiceName, Service.NULL)
            .addDependency(fourthServiceName)
            .setInitialMode(Mode.ACTIVE)
            .addListener(testListener)
            .install();
        // dependency missing notification expected
        thirdController = assertController(thirdServiceName, thirdServiceDependencyMissing);
        // dependency missing notification is not expected from second service, as it is still disconnected from its
        // optional dependency
        assertNull(secondServiceDependencyMissing.get());

        thirdServiceDependencyInstalled = testListener.expectDependencyInstall(thirdServiceName);
        fourthServiceStart = testListener.expectServiceStart(fourthServiceName);
        // reinstall fourth service
        serviceContainer.addService(fourthServiceName, Service.NULL)
            .addListener(testListener)
            .install();
        // fourth service is expected to start
        fourthController = assertController(fourthServiceName, fourthServiceStart);
        // and third service should send a notification that the missing deps are now installed
        assertController(thirdController, thirdServiceDependencyInstalled);
    }

    @Test
    public void testFailedOptionalDependency() throws Exception {
        Future<ServiceController<?>> firstServiceStart = testListener.expectServiceStart(firstServiceName);
        Future<ServiceController<?>> firstServiceUninstalled = testListener.expectNoDependencyUninstall(firstServiceName);
        // add firstService with an optional dependency on missing secondService
        serviceContainer.addService(firstServiceName, Service.NULL)
            .addOptionalDependency(secondServiceName)
            .addListener(testListener)
            .install();
        // firstService is expected to start
        final ServiceController<?> firstController = assertController(firstServiceName, firstServiceStart);

        Future<ServiceController<?>> secondServiceListenerAdded = testListener.expectListenerAdded(secondServiceName);
        // install missing secondService on ON_DEMAND mode
        serviceContainer.addService(secondServiceName, Service.NULL)
            .addListener(testListener)
            .setInitialMode(Mode.ON_DEMAND)
            .install();
        ServiceController<?> secondController =  assertController(secondServiceName, secondServiceListenerAdded);
        // secondService is not expected to start at this point
        assertSame(State.DOWN, secondController.getState());

        // move fistService to NEVER mode, and wait till it is stops
        Future<ServiceController<?>> firstServiceStop = testListener.expectServiceStop(firstServiceName);
        firstController.setMode(Mode.NEVER);
        assertController(firstController, firstServiceStop);

        // now it is expected that that firstService is connected with its optional dependency
        Future<ServiceController<?>> secondServiceStart = testListener.expectServiceStart(secondServiceName);
        firstServiceStart = testListener.expectServiceStart(firstServiceName);
        // check this by moving firstService to ACTIVE mode
        firstController.setMode(Mode.ACTIVE);
        assertController(firstController, firstServiceStart);
        // secondService is expected to start
        assertController(secondController, secondServiceStart);

        firstServiceStop = testListener.expectServiceStop(firstServiceName);
        Future<ServiceController<?>> secondServiceStop = testListener.expectServiceStop(secondServiceName);
        // move secondService to NEVER mode
        secondController.setMode(Mode.NEVER);
        assertController(secondController, secondServiceStop);
        // firstService is expected to stop
        assertController(firstController, firstServiceStop);

        firstServiceStart = testListener.expectServiceStart(firstServiceName);
        Future<ServiceController<?>> secondServiceRemoval = testListener.expectServiceRemoval(secondServiceName);
        // uninstall secondService, thus disconnecting it from firstService as an optionalDependency
        secondController.setMode(Mode.REMOVE);
        // wait for removal to be complete
        assertController(secondController, secondServiceRemoval);
        // first service expected to start
        assertController(firstController, firstServiceStart);

        secondServiceListenerAdded = testListener.expectListenerAdded(secondServiceName);
        // add a FailToStartService as a secondService, ON_DEMAND mode
        final FailToStartService failService = new FailToStartService(true);
        serviceContainer.addService(secondServiceName, failService)
            .setInitialMode(Mode.ON_DEMAND).addListener(testListener).install();
        secondController = assertController(secondServiceName, secondServiceListenerAdded);
        assertSame(State.DOWN, secondController.getState());

        firstServiceStop = testListener.expectServiceStop(firstServiceName);
        // move firstService to NEVER mode, thus connecting it with secondService as an optionalDependency
        firstController.setMode(Mode.NEVER);
        assertController(firstController, firstServiceStop);

        Future<StartException> secondServiceFailed = testListener.expectServiceFailure(secondServiceName);
        Future<ServiceController<?>> firstServiceDependencyFailed = testListener.expectDependencyFailure(firstServiceName);
        // move firstService to ACTIVE mode, triggering the start process of secondService
        firstController.setMode(Mode.ACTIVE);
        // secondService is expected to fail
        assertFailure(secondServiceName, secondServiceFailed);
        // dependency failure notification is expected
        assertController(firstController, firstServiceDependencyFailed);

        Future<ServiceController<?>> firstServiceDependencyFailureCleared = testListener.expectDependencyFailureCleared(firstServiceName);
        // retry firstService start
        firstController.setMode(Mode.NEVER);
        // firstService is supposed to receive a dependencyFailureCleared notification
        assertController(firstController, firstServiceDependencyFailureCleared);

        firstServiceStart = testListener.expectServiceStart(firstServiceName);
        secondServiceStart = testListener.expectServiceStart(secondServiceName);
        firstController.setMode(Mode.ACTIVE);
        // no failures this time; firstService is expected to start now
        assertController(firstController, firstServiceStart);
        // as is secondService
        secondController = assertController(secondServiceName, secondServiceStart);

        secondServiceStop = testListener.expectServiceStop(secondServiceName);
        firstServiceStop = testListener.expectServiceStop(firstServiceName);
        // move secondService to NEVER mode
        secondController.setMode(Mode.NEVER);
        assertController(secondController, secondServiceStop);
        assertController(firstController, firstServiceStop);

        // mark failService to fail again next time it is started
        failService.failNextTime();
        secondServiceFailed = testListener.expectServiceFailure(secondServiceName);
        firstServiceDependencyFailed = testListener.expectDependencyFailure(firstServiceName);
        // change its mode back to ON_DEMAND
        secondController.setMode(Mode.ON_DEMAND);
        assertNotNull(secondServiceFailed.get());
        // dependencyFailure notification is expected
        assertController(firstController, firstServiceDependencyFailed);

        firstServiceStart = testListener.expectServiceStart(firstServiceName);
        firstServiceDependencyFailureCleared = testListener.expectDependencyFailureCleared(firstServiceName);
        secondServiceRemoval = testListener.expectServiceRemoval(secondServiceName);
        // remove secondService after the failure
        secondController.setMode(Mode.REMOVE);
        // wait for the removal
        assertController(secondController, secondServiceRemoval);
        // dependencyFailure is expected to be cleared
        assertController(firstController, firstServiceDependencyFailureCleared);
        // firstService is expected to start
        assertController(firstController, firstServiceStart);

        firstServiceStop = testListener.expectServiceStop(firstServiceName);
        // move firstService to NEVER mode
        firstController.setMode(Mode.NEVER);
        // wait for it to go down
        assertController(firstController, firstServiceStop);

        final Future<ServiceController<?>> secondServiceDependencyFailed = testListener.expectDependencyFailure(secondServiceName);
        firstServiceDependencyFailed = testListener.expectDependencyFailure(firstServiceName);
        // install thridService, a failToStart service, set to fail on the first attempt to start it
        serviceContainer.addService(thirdServiceName, new FailToStartService(true)).install();
        // install secondService, that depends on thirdService...
        serviceContainer.addService(secondServiceName, Service.NULL)
            .addDependency(thirdServiceName)
            .setInitialMode(Mode.ON_DEMAND)
            .addListener(testListener)
            .install();
        // at this point, firstService is connected to the new secondService as an optional dependency
        // move it to active mode, to make all three services start
        firstController.setMode(Mode.ACTIVE);
        // a dependencyFailure notification is expected by secondService
        secondController = assertController(secondServiceName, secondServiceDependencyFailed);
        // and it is expected to reach the entire dependent chain, including firstService
        assertController(firstController, firstServiceDependencyFailed);

        firstServiceDependencyFailureCleared = testListener.expectDependencyFailureCleared(firstServiceName);
        firstServiceStart = testListener.expectServiceStart(firstServiceName);
        // remove secondService
        secondController.setMode(Mode.REMOVE);
        // a dependency retrying is expected
        assertController(firstController, firstServiceDependencyFailureCleared);
        // firstService is expected to start
        assertController(firstController, firstServiceStart);

        // during the entire test, firstService is not expected to notify of an uninstalled dependency
        assertNull(firstServiceUninstalled.get());
    }

    @Test
    public void testDisconnectedOptionalDependencyWithFailedAndMissingDependencies() throws Exception {
        final Future<ServiceController<?>> firstServiceStart = testListener.expectServiceStart(firstServiceName);
        final Future<ServiceController<?>> secondServiceStart = testListener.expectServiceStart(secondServiceName);
        final Future<ServiceController<?>> secondServiceDependencyFailed = testListener.expectNoDependencyFailure(secondServiceName);
        final Future<ServiceController<?>> secondServiceDependencyMissing = testListener.expectNoDependencyUninstall(secondServiceName);
        // install first and second services; first service depends on second service...
        serviceContainer.addService(firstServiceName, Service.NULL)
            .addListener(testListener)
            .addDependency(secondServiceName)
            .install();
        // .. and second service has an optional dependency on third service
        serviceContainer.addService(secondServiceName, Service.NULL)
            .addListener(testListener)
            .addOptionalDependency(thirdServiceName)
            .install();
        // both services should start normally
        final ServiceController<?> firstController = assertController(firstServiceName, firstServiceStart);
        final ServiceController<?> secondController = assertController(secondServiceName, secondServiceStart);

        Future<StartException> fourthServiceFailed = testListener.expectServiceFailure(fourthServiceName);
        Future<ServiceController<?>> fifthServiceDependencyMissing = testListener.expectDependencyUninstall(fifthServiceName);
        Future<ServiceController<?>> thirdServiceDependencyFailure = testListener.expectDependencyFailure(thirdServiceName);
        Future<ServiceController<?>> thirdServiceDependencyMissing = testListener.expectDependencyUninstall(thirdServiceName);
        // install third service, with a dependency on fourth and fifth services
        serviceContainer.addService(thirdServiceName, Service.NULL)
            .addListener(testListener)
            .addDependencies(fourthServiceName, fifthServiceName)
            .install();
        // ... fourth service is a fail to start service, set to fail on the first attempt to start
        serviceContainer.addService(fourthServiceName, new FailToStartService(true))
            .addListener(testListener)
            .install();
        // ... fifth service has a missing dependency on sixth service
        serviceContainer.addService(fifthServiceName, Service.NULL)
            .addListener(testListener)
            .addDependencies(sixthServiceName)
            .install();
        // fourth service should start
        assertNotNull(fourthServiceFailed.get());
        // the missing dependency notification (sixth service) should be sent by both fifth and third services 
        final ServiceController<?> fifthController = assertController(fifthServiceName, fifthServiceDependencyMissing);
        final ServiceController<?> thirdController = assertController(thirdServiceName, thirdServiceDependencyMissing);
        // and the dependency failure notification should be send by third service
        assertController(thirdController, thirdServiceDependencyFailure);
        // both first and second services must be still in the up state, as second service is currently disconnected
        // from its optional dependency on third service
        assertSame(State.UP, firstController.getState());
        assertSame(State.UP, secondController.getState());

        Future<ServiceController<?>> sixthServiceStart = testListener.expectServiceStart(sixthServiceName);
        Future<ServiceController<?>> fifthServiceDependencyInstall = testListener.expectDependencyInstall(fifthServiceName);
        Future<ServiceController<?>> fifthServiceStart = testListener.expectServiceStart(fifthServiceName);
        Future<ServiceController<?>> thirdServiceDependencyInstall = testListener.expectDependencyInstall(thirdServiceName);
        // install sixth service
        serviceContainer.addService(sixthServiceName, Service.NULL).
            addListener(testListener).
            install();
        final ServiceController<?> sixthController = assertController(sixthServiceName, sixthServiceStart);
        // the dependency install notification is expected from fifth and third service
        assertController(fifthController, fifthServiceDependencyInstall);
        assertController(thirdController, thirdServiceDependencyInstall);
        // fifth service can now start
        assertController(fifthController, fifthServiceStart);
        // and first and second services must be in the up state, isolated from the other services
        assertSame(State.UP, firstController.getState());
        assertSame(State.UP, secondController.getState());

        final Future<ServiceController<?>> sixthServiceRemoval = testListener.expectServiceRemoval(sixthServiceName);
        fifthServiceDependencyMissing = testListener.expectDependencyUninstall(fifthServiceName);
        thirdServiceDependencyMissing = testListener.expectDependencyUninstall(thirdServiceName);
        // remove sixth service
        sixthController.setMode(Mode.REMOVE);
        assertController(sixthController, sixthServiceRemoval);
        // the dependency missing notification is expected from both third and fifth services
        assertController(fifthController, fifthServiceDependencyMissing);
        assertController(thirdController, thirdServiceDependencyMissing);
        // meanwhile, first and second services are in the up state
        assertSame(State.UP, firstController.getState());
        assertSame(State.UP, secondController.getState());

        final Future<ServiceController<?>> thirdServiceDependencyFailureCleared = testListener.expectDependencyFailureCleared(thirdServiceName);
        ServiceController<?> fourthController = serviceContainer.getService(fourthServiceName);
        // move fourth service to never mode, thus clearing the failure to start
        fourthController.setMode(Mode.NEVER);
        // dependency failure cleared expected from third service 
        assertController(thirdController, thirdServiceDependencyFailureCleared);
        // first and second services are still in the up state
        assertSame(State.UP, firstController.getState());
        assertSame(State.UP, secondController.getState());

        final Future<ServiceController<?>> fourthServiceStart = testListener.expectServiceStart(fourthServiceName);
        // renable fourth service, thus making it start
        fourthController.setMode(Mode.ACTIVE);
        assertController(fourthController, fourthServiceStart);
        // first and second services still in the up state
        assertSame(State.UP, firstController.getState());
        assertSame(State.UP, secondController.getState());

        thirdServiceDependencyInstall = testListener.expectNoDependencyInstall(thirdServiceName);
        thirdServiceDependencyMissing = testListener.expectNoDependencyUninstall(thirdServiceName);
        final Future<ServiceController<?>> fifthServiceRemoved = testListener.expectServiceRemoval(fifthServiceName);
        // remove fifth service
        fifthController.setMode(Mode.REMOVE);
        assertController(fifthController, fifthServiceRemoved);
        // now, the missing dependency on sixth service no longer exists, but we have a new missing dependency, on fifth service
        assertOppositeNotifications(thirdController, thirdServiceDependencyInstall, thirdServiceDependencyMissing);
        // first and second services still in the up state
        assertSame(State.UP, firstController.getState());
        assertSame(State.UP, secondController.getState());

        final Future<ServiceController<?>> thirdServiceStart = testListener.expectServiceStart(thirdServiceName);
        fifthServiceStart = testListener.expectServiceStart(fifthServiceName);
        sixthServiceStart = testListener.expectServiceStart(sixthServiceName);
        thirdServiceDependencyInstall = testListener.expectDependencyInstall(thirdServiceName);
        // reinstall fifth and sixth services
        serviceContainer.addService(fifthServiceName, Service.NULL)
            .addListener(testListener)
            .addDependency(sixthServiceName)
            .install();
        serviceContainer.addService(sixthServiceName, Service.NULL)
            .addListener(testListener)
            .install();
        assertController(sixthServiceName, sixthServiceStart);
        assertController(fifthServiceName, fifthServiceStart);
        // a dependency install notification is expected from third service
        assertController(thirdController, thirdServiceDependencyInstall);
        // which can now start
        assertController(thirdController, thirdServiceStart);
        // depite that, nothing has changed with the state of first and second services
        assertSame(State.UP, firstController.getState());
        assertSame(State.UP, secondController.getState());

        final Future<ServiceController<?>> thirdControllerStop = testListener.expectServiceStop(thirdServiceName);
        // disable third service
        thirdController.setMode(Mode.NEVER);
        assertController(thirdController, thirdControllerStop);
        // first and second services still running
        assertSame(State.UP, firstController.getState());
        assertSame(State.UP, secondController.getState());

        // during the entire test, as  second service has been disconnected from its optional dependency all the time,
        // no notification of dependency failures or missing dependencies should have reached second service
        assertNull(secondServiceDependencyFailed.get());
        assertNull(secondServiceDependencyMissing.get());
    }

    @Test
    public void testOptionalDependencyWithFailedAndMissingDependencies() throws Exception {
        Future<StartException> fourthServiceFailed = testListener.expectServiceFailure(fourthServiceName);
        Future<ServiceController<?>> fifthServiceDependencyMissing = testListener.expectDependencyUninstall(fifthServiceName);
        Future<ServiceController<?>> thirdServiceDependencyFailure = testListener.expectDependencyFailure(thirdServiceName);
        Future<ServiceController<?>> thirdServiceDependencyMissing = testListener.expectDependencyUninstall(thirdServiceName);
        // install third service with dependency on fourth and fifth services
        serviceContainer.addService(thirdServiceName, Service.NULL)
            .addListener(testListener)
            .addDependencies(fourthServiceName, fifthServiceName)
            .install();
        // fourth service is a fail to start service, set to fail on the first attempt to start
        serviceContainer.addService(fourthServiceName, new FailToStartService(true))
            .addListener(testListener)
            .install();
        // fifth service has a missing dependency on sixth service
        serviceContainer.addService(fifthServiceName, Service.NULL)
            .addListener(testListener)
            .addDependencies(sixthServiceName)
            .install();
        // fourth service is expected to fail
        assertFailure(fourthServiceName, fourthServiceFailed);
        // fifth service should notify of a missing dependency
        final ServiceController<?> fifthController = assertController(fifthServiceName, fifthServiceDependencyMissing);
        // and third service should notify of the dependency failure (fourth service)
        final ServiceController<?> thirdController = assertController(thirdServiceName, thirdServiceDependencyFailure);
        // plus, of the missing dependency (fifth service -> sixth service)
        assertController(thirdController, thirdServiceDependencyMissing);

        Future<ServiceController<?>> firstServiceDependencyFailed = testListener.expectDependencyFailure(firstServiceName);
        Future<ServiceController<?>> firstServiceDependencyMissing = testListener.expectDependencyUninstall(firstServiceName);
        Future<ServiceController<?>> secondServiceDependencyFailed = testListener.expectDependencyFailure(secondServiceName);
        Future<ServiceController<?>> secondServiceDependencyMissing = testListener.expectDependencyUninstall(secondServiceName);
        // install second service with a dependency on third service...
        serviceContainer.addService(secondServiceName, Service.NULL)
            .addListener(testListener)
            .addOptionalDependency(thirdServiceName)
            .install();
        // second service should notify the dependency failure and the missing dependency
        final ServiceController<?> secondController = assertController(secondServiceName, secondServiceDependencyFailed);
        assertController(secondController, secondServiceDependencyMissing);

        // ... and install first service with a dependency on second service
        serviceContainer.addService(firstServiceName, Service.NULL)
            .addListener(testListener)
            .addDependency(secondServiceName)
            .install();
        // first service should also notify the missing/failed dependencies
        final ServiceController<?> firstController = assertController(firstServiceName, firstServiceDependencyFailed);
        assertController(firstController, firstServiceDependencyMissing);

        Future<ServiceController<?>> sixthServiceStart = testListener.expectServiceStart(sixthServiceName);
        Future<ServiceController<?>> fifthServiceDependencyInstall = testListener.expectDependencyInstall(fifthServiceName);
        Future<ServiceController<?>> fifthServiceStart = testListener.expectServiceStart(fifthServiceName);
        Future<ServiceController<?>> thirdServiceDependencyInstall = testListener.expectDependencyInstall(thirdServiceName);
        Future<ServiceController<?>> secondServiceDependencyInstall = testListener.expectDependencyInstall(secondServiceName);
        Future<ServiceController<?>> firstServiceDependencyInstall = testListener.expectDependencyInstall(firstServiceName);
        // install sixth service
        serviceContainer.addService(sixthServiceName, Service.NULL).
            addListener(testListener).
            install();
        final ServiceController<?> sixthController = assertController(sixthServiceName, sixthServiceStart);
        // the dependency install notification is supposed to reach the entire dependent chain
        assertController(fifthController, fifthServiceDependencyInstall);
        assertController(thirdController, thirdServiceDependencyInstall);
        assertController(secondController, secondServiceDependencyInstall);
        assertController(firstController, firstServiceDependencyInstall);
        // and fifth service can finally start
        assertController(fifthController, fifthServiceStart);

        final Future<ServiceController<?>> sixthServiceRemoval = testListener.expectServiceRemoval(sixthServiceName);
        final Future<ServiceController<?>> fifthServiceStop = testListener.expectServiceStop(fifthServiceName);
        fifthServiceDependencyMissing = testListener.expectDependencyUninstall(fifthServiceName);
        thirdServiceDependencyMissing = testListener.expectDependencyUninstall(thirdServiceName);
        secondServiceDependencyMissing = testListener.expectDependencyUninstall(secondServiceName);
        firstServiceDependencyMissing = testListener.expectDependencyUninstall(firstServiceName);
        // remove sixth service
        sixthController.setMode(Mode.REMOVE);
        assertController(sixthController, sixthServiceRemoval);
        // the entire chain should send the dependency uninstalled notification
        assertController(fifthController, fifthServiceDependencyMissing);
        assertController(thirdController, thirdServiceDependencyMissing);
        assertController(secondController, secondServiceDependencyMissing);
        assertController(firstController, firstServiceDependencyMissing);
        // plus, fifth service should stop because of the uninstalled dep
        assertController(fifthController, fifthServiceStop);

        final Future<ServiceController<?>> thirdServiceDependencyFailureCleared = testListener.expectDependencyFailureCleared(thirdServiceName);
        final Future<ServiceController<?>> secondServiceDependencyFailureCleared = testListener.expectDependencyFailureCleared(secondServiceName);
        final Future<ServiceController<?>> firstServiceDependencyFailureCleared = testListener.expectDependencyFailureCleared(firstServiceName);
        final ServiceController<?> fourthController = serviceContainer.getService(fourthServiceName);
        // move fourth controller to never mode
        fourthController.setMode(Mode.NEVER);
        // the failure should be cleared
        assertController(thirdController, thirdServiceDependencyFailureCleared);
        assertController(secondController, secondServiceDependencyFailureCleared);
        assertController(firstController, firstServiceDependencyFailureCleared);

        final Future<ServiceController<?>> fourthServiceStart = testListener.expectServiceStart(fourthServiceName);
        // move fourth service back to active mode triggers a new attempt to start
        fourthController.setMode(Mode.ACTIVE);
        // .. that is successful this time
        assertController(fourthController, fourthServiceStart);

        thirdServiceDependencyInstall = testListener.expectNoDependencyInstall(thirdServiceName);
        //secondServiceDependencyInstall = testListener.expectNoDependencyInstall(secondServiceName);
        //firstServiceDependencyInstall = testListener.expectNoDependencyInstall(firstServiceName);
        thirdServiceDependencyMissing = testListener.expectNoDependencyUninstall(thirdServiceName);
        //secondServiceDependencyMissing = testListener.expectNoDependencyUninstall(secondServiceName);
        //firstServiceDependencyMissing = testListener.expectNoDependencyUninstall(firstServiceName);
        final Future<ServiceController<?>> fifthServiceRemoved = testListener.expectServiceRemoval(fifthServiceName);
        // remove fifth service
        fifthController.setMode(Mode.REMOVE);
        assertController(fifthController, fifthServiceRemoved);
        // the dependent chain should notify both of the no more uninstalled dependency on sixth service (the service
        // is still uninstalled, but with the removal of fifth service, the dependency no longer exists)
        // and also a notification of the new missing dependency is expected
        assertOppositeNotifications(thirdController, thirdServiceDependencyInstall, thirdServiceDependencyMissing);
        //assertOppositeNotifications(secondController, secondServiceDependencyInstall, secondServiceDependencyMissing);
        //assertOppositeNotifications(firstController, firstServiceDependencyInstall, firstServiceDependencyMissing);

        final Future<ServiceController<?>> firstServiceStart = testListener.expectServiceStart(firstServiceName);
        final Future<ServiceController<?>> secondServiceStart = testListener.expectServiceStart(secondServiceName);
        final Future<ServiceController<?>> thirdServiceStart = testListener.expectServiceStart(thirdServiceName);
        fifthServiceStart = testListener.expectServiceStart(fifthServiceName);
        sixthServiceStart = testListener.expectServiceStart(sixthServiceName);
        thirdServiceDependencyInstall = testListener.expectDependencyInstall(thirdServiceName);
        secondServiceDependencyInstall = testListener.expectDependencyInstall(secondServiceName);
        firstServiceDependencyInstall = testListener.expectDependencyInstall(firstServiceName);
        // reinstall fifth and sixth services
        serviceContainer.addService(fifthServiceName, Service.NULL)
            .addListener(testListener)
            .addDependency(sixthServiceName)
            .install();
        serviceContainer.addService(sixthServiceName, Service.NULL)
            .addListener(testListener)
            .install();
        // both services are expected to start
        assertController(sixthServiceName, sixthServiceStart);
        assertController(fifthServiceName, fifthServiceStart);
        // the same goes for the rest of the chain, that should also notify of the dependency install
        assertController(thirdController, thirdServiceDependencyInstall);
        assertController(secondController, secondServiceDependencyInstall);
        assertController(firstController, firstServiceDependencyInstall);
        assertController(thirdController, thirdServiceStart);
        assertController(secondController, secondServiceStart);
        assertController(firstController, firstServiceStart);

        final Future<ServiceController<?>> thirdServiceStop = testListener.expectServiceStop(thirdServiceName);
        final Future<ServiceController<?>> secondServiceStop = testListener.expectServiceStop(secondServiceName);
        final Future<ServiceController<?>> firstServiceStop = testListener.expectServiceStop(firstServiceName);
        // move third service to never mode
        thirdController.setMode(Mode.NEVER);
        // the dependents of third service are also expected to stop
        assertController(thirdController, thirdServiceStop);
        assertController(secondController, secondServiceStop);
        assertController(firstController, firstServiceStop);
    }
}
