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

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.concurrent.Future;

import org.jboss.msc.service.ServiceBuilder.DependencyType;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.util.FailToStartService;
import org.jboss.msc.util.TestServiceListener;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests scenarios with dependency cycles
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class DependencyCycleTestCase extends AbstractServiceTest {

    private final ServiceName serviceAName = ServiceName.of("A");
    private final ServiceName serviceBName = ServiceName.of("B");
    private final ServiceName serviceCName = ServiceName.of("C");
    private final ServiceName serviceDName = ServiceName.of("D");
    private final ServiceName serviceEName = ServiceName.of("E");
    private final ServiceName serviceFName = ServiceName.of("F");
    private final ServiceName serviceGName = ServiceName.of("G");
    private final ServiceName serviceHName = ServiceName.of("H");
    private final ServiceName serviceIName = ServiceName.of("I");
    private final ServiceName serviceJName = ServiceName.of("J");
    private final ServiceName serviceKName = ServiceName.of("K");
    private final ServiceName serviceLName = ServiceName.of("L");
    private final ServiceName serviceMName = ServiceName.of("M");
    private final ServiceName serviceNName = ServiceName.of("N");
    private final ServiceName serviceOName = ServiceName.of("O");
    private final ServiceName servicePName = ServiceName.of("P");
    private final ServiceName serviceQName = ServiceName.of("Q");
    private final ServiceName serviceRName = ServiceName.of("R");
    private final ServiceName serviceSName = ServiceName.of("S");
    private final ServiceName serviceTName = ServiceName.of("T");
    private final ServiceName serviceUName = ServiceName.of("U");
    private final ServiceName serviceVName = ServiceName.of("V");
    private final ServiceName serviceWName = ServiceName.of("W");
    TestServiceListener testListener;

    @Before
    public void initializeTestListener() {
        testListener = new TestServiceListener();
        serviceContainer.addListener(testListener);
    }

    @Test
    public void simpleCycle() throws Exception {
        final Future<ServiceController<?>> serviceAListenerAdded = testListener.expectListenerAdded(serviceAName);
        final Future<ServiceController<?>> serviceBListenerAdded = testListener.expectListenerAdded(serviceBName);

        serviceContainer.addService(serviceAName, Service.NULL).addDependency(serviceBName).install();
        serviceContainer.addService(serviceBName, Service.NULL).addDependency(serviceCName).install();
        try {
            serviceContainer.addService(serviceCName, Service.NULL).addDependency(serviceAName).install();
            fail ("CircularDependencyException expected");
        } catch (CircularDependencyException e) {}

        final ServiceController<?> serviceAController = assertController(serviceAName, serviceAListenerAdded);
        assertSame(State.DOWN, serviceAController.getState());
        final ServiceController<?> serviceBController = assertController(serviceBName, serviceBListenerAdded);
        assertSame(State.DOWN, serviceBController.getState());
    }

    @Test
    public void cycleOnRunning() throws Exception {
        final Future<ServiceController<?>> serviceADepMissing = testListener.expectDependencyUninstall(serviceAName);
        final Future<ServiceController<?>> serviceBDepMissing = testListener.expectDependencyUninstall(serviceBName);

        final ServiceController<?> serviceAController = serviceContainer.addService(serviceAName, Service.NULL).addDependency(serviceBName).install();
        final ServiceController<?> serviceBController = serviceContainer.addService(serviceBName, Service.NULL).addDependency(serviceCName).install();

        assertController(serviceAName, serviceAController);
        assertController(serviceAController, serviceADepMissing);
        assertController(serviceBName, serviceBController);
        assertController(serviceBController, serviceBDepMissing);

        final Future<ServiceController<?>> serviceADepInstall = testListener.expectDependencyInstall(serviceAName);
        final Future<ServiceController<?>> serviceBDepInstall = testListener.expectDependencyInstall(serviceBName);
        try {
            serviceContainer.addService(serviceCName, Service.NULL).addDependency(serviceAName).install();
            fail ("CircularDependencyException expected");
        } catch (CircularDependencyException e) {}
        assertController(serviceAController, serviceADepInstall);
        assertController(serviceBController, serviceBDepInstall);
    }

    // full scenario:
    // A->B,F; B->C; C->D; D->E; E->C; F->G; G->H; H->I,W; I->H,J; J->K; K->G,H;
    // L->M; M->N; N->H,O; O->L;
    // P->Q; Q->R; R->S; S->T; T->P
    // U->V (no cycle here)
    @Test
    public void multipleCycles() throws Exception {
        // first install A, B, C, D, L, M, O
        final Future<ServiceController<?>> serviceADepMissing = testListener.expectDependencyUninstall(serviceAName);
        final Future<ServiceController<?>> serviceBDepMissing = testListener.expectDependencyUninstall(serviceBName);
        final Future<ServiceController<?>> serviceCDepMissing = testListener.expectDependencyUninstall(serviceCName);
        final Future<ServiceController<?>> serviceDDepMissing = testListener.expectDependencyUninstall(serviceDName);
        final Future<ServiceController<?>> serviceLDepMissing = testListener.expectDependencyUninstall(serviceLName);
        final Future<ServiceController<?>> serviceMDepMissing = testListener.expectDependencyUninstall(serviceMName);
        final Future<ServiceController<?>> serviceODepMissing = testListener.expectDependencyUninstall(serviceOName);

        serviceContainer.addService(serviceAName, Service.NULL).addDependencies(serviceBName, serviceFName).install();
        serviceContainer.addService(serviceBName, Service.NULL).addDependency(serviceCName).install();
        serviceContainer.addService(serviceCName, Service.NULL).addDependency(serviceDName).install();
        serviceContainer.addService(serviceDName, Service.NULL).addDependency(serviceEName).install();
        serviceContainer.addService(serviceLName, Service.NULL).addDependency(serviceMName).install();
        serviceContainer.addService(serviceMName, Service.NULL).addDependency(serviceNName).install();
        serviceContainer.addService(serviceOName, Service.NULL).addDependency(serviceLName).install();

        final ServiceController<?> serviceAController = assertController(serviceAName, serviceADepMissing);
        final ServiceController<?> serviceBController = assertController(serviceBName, serviceBDepMissing);
        final ServiceController<?> serviceCController = assertController(serviceCName, serviceCDepMissing);
        final ServiceController<?> serviceDController = assertController(serviceDName, serviceDDepMissing);
        final ServiceController<?> serviceLController = assertController(serviceLName, serviceLDepMissing);
        final ServiceController<?> serviceMController = assertController(serviceMName, serviceMDepMissing);
        final ServiceController<?> serviceOController = assertController(serviceOName, serviceODepMissing);

        // install service N
        try {
            serviceContainer.addService(serviceNName, Service.NULL).addDependencies(serviceHName, serviceOName).install();
            fail ("CircularDependencyException expected");
        } catch (CircularDependencyException e) {}

        // install E, F, G, H, I, V
        final Future<ServiceController<?>> serviceBDepInstall = testListener.expectDependencyInstall(serviceBName);
        final Future<ServiceController<?>> serviceCDepInstall = testListener.expectDependencyInstall(serviceCName);
        final Future<ServiceController<?>> serviceDDepInstall = testListener.expectDependencyInstall(serviceDName);
        final Future<ServiceController<?>> serviceFDepMissing = testListener.expectDependencyUninstall(serviceFName);
        final Future<ServiceController<?>> serviceGDepMissing = testListener.expectDependencyUninstall(serviceGName);
        final Future<ServiceController<?>> serviceHDepMissing = testListener.expectDependencyUninstall(serviceHName);
        final Future<ServiceController<?>> serviceVStart = testListener.expectServiceStart(serviceVName);

        try {
            serviceContainer.addService(serviceEName, Service.NULL).addDependency(serviceCName).install();
            fail("CircularDependencyException expected");
        } catch (CircularDependencyException e) {}
        serviceContainer.addService(serviceFName, Service.NULL).addDependency(serviceGName).install();
        serviceContainer.addService(serviceGName, Service.NULL).addDependency(serviceHName).install();
        serviceContainer.addService(serviceHName, Service.NULL).addDependencies(serviceIName, serviceWName).install();
        try {
            serviceContainer.addService(serviceIName, Service.NULL).addDependencies(serviceHName, serviceJName).install();
            fail("CirculardependencyException expected");
        } catch (CircularDependencyException e) {}
        serviceContainer.addService(serviceVName, Service.NULL).install();

        assertSame(State.DOWN, serviceAController.getState());
        assertController(serviceBController, serviceBDepInstall);
        assertSame(State.DOWN, serviceBController.getState());
        assertController(serviceCController, serviceCDepInstall);
        assertSame(State.DOWN, serviceCController.getState());
        assertController(serviceDController, serviceDDepInstall);
        assertSame(State.DOWN, serviceDController.getState());
        final ServiceController<?> serviceFController = assertController(serviceFName, serviceFDepMissing);
        assertSame(State.DOWN, serviceFController.getState());
        final ServiceController<?> serviceGController = assertController(serviceGName, serviceGDepMissing);
        assertSame(State.DOWN, serviceFController.getState());
        final ServiceController<?> serviceHController = assertController(serviceHName, serviceHDepMissing);
        assertSame(State.DOWN, serviceFController.getState());
        assertController(serviceVName, serviceVStart);

        // install J, P, Q, R, S, T, U
        final Future<ServiceController<?>> serviceJDepMissing = testListener.expectDependencyUninstall(serviceJName);
        final Future<ServiceController<?>> servicePListenerAdded = testListener.expectListenerAdded(servicePName);
        final Future<ServiceController<?>> serviceQListenerAdded = testListener.expectListenerAdded(serviceQName);
        final Future<ServiceController<?>> serviceRListenerAdded = testListener.expectListenerAdded(serviceRName);
        final Future<ServiceController<?>> serviceSListenerAdded = testListener.expectListenerAdded(serviceSName);
        final Future<ServiceController<?>> serviceUStart = testListener.expectServiceStart(serviceUName);

        serviceContainer.addService(serviceJName, Service.NULL).addDependency(serviceKName).install();
        serviceContainer.addService(servicePName, Service.NULL).addDependency(serviceQName).install();
        serviceContainer.addService(serviceQName, Service.NULL).addDependency(serviceRName).install();
        serviceContainer.addService(serviceRName, Service.NULL).addDependency(serviceSName).install();
        serviceContainer.addService(serviceSName, Service.NULL).addDependency(serviceTName).install();
        try {
            serviceContainer.addService(serviceTName, Service.NULL).addDependency(servicePName).install();
            fail("CircularDependencyException expected");
        } catch (CircularDependencyException e) {}
        serviceContainer.addService(serviceUName, Service.NULL).addDependency(serviceVName).install();

        final ServiceController<?> serviceJController = assertController(serviceJName, serviceJDepMissing);
        final ServiceController<?> servicePController = assertController(servicePName, servicePListenerAdded);
        assertSame(State.DOWN, servicePController.getState());
        final ServiceController<?> serviceQController = assertController(serviceQName, serviceQListenerAdded);
        assertSame(State.DOWN, serviceQController.getState());
        final ServiceController<?> serviceRController = assertController(serviceRName, serviceRListenerAdded);
        assertSame(State.DOWN, serviceRController.getState());
        final ServiceController<?> serviceSController = assertController(serviceSName, serviceSListenerAdded);
        assertSame(State.DOWN, serviceSController.getState());
        assertController(serviceUName, serviceUStart);

        // install service K
        final Future<ServiceController<?>> serviceKDepMissing = testListener.expectDependencyUninstall(serviceKName);
        serviceContainer.addService(serviceKName, Service.NULL).addDependencies(serviceGName, serviceHName).install();
        final ServiceController<?> serviceKController = assertController(serviceKName, serviceKDepMissing);

        // install service W
        final Future<ServiceController<?>> serviceWStart = testListener.expectServiceStart(serviceWName);
        serviceContainer.addService(serviceWName, Service.NULL).install();
        assertController(serviceWName, serviceWStart);
        assertSame(State.DOWN, serviceAController.getState());
        assertSame(State.DOWN, serviceFController.getState());
        assertSame(State.DOWN, serviceGController.getState());
        assertSame(State.DOWN, serviceHController.getState());
        assertSame(State.DOWN, serviceJController.getState());
        assertSame(State.DOWN, serviceKController.getState());
        assertSame(State.DOWN, serviceLController.getState());
        assertSame(State.DOWN, serviceMController.getState());
        assertSame(State.DOWN, serviceOController.getState());
    }

    // full scenario:
    // A->B,H,I,K,L; B->C; C->D; D->E; E->A,F; F->G; G->D,E; I->J; J->A; M->A,L,N,O; O->M
    // the dependencies F->G; M->O; A->I; A->L and M->N are optional
    @Test
    public void multipleCyclesWithOptionalDependencies() throws Exception {
        // install G
        Future<ServiceController<?>> serviceGMissingDep = testListener.expectDependencyUninstall(serviceGName);
        serviceContainer.addService(serviceGName, Service.NULL).addDependencies(serviceDName, serviceEName).install();
        assertController(serviceGName, serviceGMissingDep);

        // install L
        final FailToStartService serviceL = new FailToStartService(true);
        Future<StartException> serviceLFailure = testListener.expectServiceFailure(serviceLName);
        serviceContainer.addService(serviceLName, serviceL).install();
        ServiceController<?> serviceLController = assertFailure(serviceLName, serviceLFailure);

        // install A, B, C, D, E, F, M, O
        Future<ServiceController<?>> serviceAMissingDep = testListener.expectDependencyUninstall(serviceAName);
        Future<ServiceController<?>> serviceAFailedDep = testListener.expectDependencyFailure(serviceAName);

        serviceContainer.addService(serviceAName, Service.NULL).addDependencies(serviceBName, serviceHName, serviceIName, serviceKName)
            .addDependency(DependencyType.OPTIONAL, serviceLName).install();
        serviceContainer.addService(serviceBName, Service.NULL).addDependency(serviceCName).install();
        serviceContainer.addService(serviceCName, Service.NULL).addDependency(serviceDName).install();
        serviceContainer.addService(serviceDName, Service.NULL).addDependency(serviceEName).install();
        try {
            serviceContainer.addService(serviceEName, Service.NULL).addDependencies(serviceAName, serviceFName).install();
            fail("CircularDependencyException expected");
        } catch (CircularDependencyException e) {}
        serviceContainer.addService(serviceFName, Service.NULL).addDependency(DependencyType.OPTIONAL, serviceGName)
            .install();
        serviceContainer.addService(serviceOName, Service.NULL).addDependency(serviceMName).install();
        try {
            serviceContainer.addService(serviceMName, Service.NULL).addDependencies(serviceAName, serviceNName)
                .addDependencies(DependencyType.OPTIONAL, serviceLName, serviceOName).install();
            fail("CircularDependencyException expected");
        } catch (CircularDependencyException e) {}
        ServiceController<?> serviceAController = assertController(serviceAName, serviceAMissingDep);
        assertController(serviceAController, serviceAFailedDep);

        // install N
        final Future<StartException> serviceNFailed = testListener.expectServiceFailure(serviceNName);
        serviceContainer.addService(serviceNName, new FailToStartService(true)).install();
        final ServiceController<?> serviceNController = assertFailure(serviceNName, serviceNFailed);

        // install H, I, J
        final Future<ServiceController<?>> serviceHStart = testListener.expectServiceStart(serviceHName);

        serviceContainer.addService(serviceHName, Service.NULL).install();
        serviceContainer.addService(serviceIName, Service.NULL).addDependency(serviceJName).install();
        try {
            serviceContainer.addService(serviceJName, Service.NULL).addDependency(serviceAName).install();
            fail ("CircularDependencyException expected");
        } catch (CircularDependencyException e) {}

        assertController(serviceHName, serviceHStart);

        // install K
        final Future<StartException> serviceKFailure = testListener.expectServiceFailure(serviceKName);
        serviceContainer.addService(serviceKName, new FailToStartService(true)).install();
        final ServiceController<?> serviceKController = assertFailure(serviceKName, serviceKFailure);

        // remove service L
        final Future<ServiceController<?>> serviceLRemoval = testListener.expectServiceRemoval(serviceLName);
        serviceLController.setMode(Mode.REMOVE);
        assertController(serviceLController, serviceLRemoval);

        // restart K, this time without errors
        final Future<ServiceController<?>> serviceADepFailureCleared = testListener.expectDependencyFailureCleared(serviceAName);
        serviceKController.setMode(Mode.NEVER);
        assertController(serviceAController, serviceADepFailureCleared);

        final Future<ServiceController<?>> serviceKStart = testListener.expectServiceStart(serviceKName);
        serviceKController.setMode(Mode.ACTIVE);
        assertController(serviceKController, serviceKStart);

        serviceNController.setMode(Mode.NEVER);
        Thread.sleep(100);

        final Future<ServiceController<?>> serviceNStart = testListener.expectServiceStart(serviceNName);
        serviceNController.setMode(Mode.ACTIVE);
        assertController(serviceNController, serviceNStart);

        final Future<ServiceController<?>> serviceLStart = testListener.expectServiceStart(serviceLName);
        serviceContainer.addService(serviceLName, serviceL).setInitialMode(Mode.ACTIVE).install();
        serviceLController = assertController(serviceLName, serviceLStart);

        final Future<ServiceController<?>> serviceLStop = testListener.expectServiceStop(serviceLName);
        serviceLController.setMode(Mode.NEVER);
        assertController(serviceLController, serviceLStop);

        serviceLFailure = testListener.expectServiceFailure(serviceLName);
        serviceL.failNextTime();
        serviceLController.setMode(Mode.PASSIVE);
        assertFailure(serviceLController, serviceLFailure);

        final Future<ServiceController<?>> serviceARemoval = testListener.expectServiceRemoval(serviceAName);
        serviceAController.setMode(Mode.REMOVE);
        assertController(serviceAController, serviceARemoval);
    }

    // cycle involving aliases
    // full scenario:
    // A->B; B->C,E; C->D; E->G; F->I,J
    // whereas B->E and E->G are optional dependencies
    // A has alias D, F has alias G and B has alias H, I, J
    // first services are installed as described above. Then, the services with alias are uninstalled
    // and replaced by services without aliases, thus breaking the cycles.
    // this test will assert that cycles can be recovered, resulting in all services in UP state
    @Test
    public void cycleRecovery() throws Exception {
        // install B, C, E
        Future<ServiceController<?>> serviceBMissingDep = testListener.expectDependencyUninstall(serviceBName);
        Future<ServiceController<?>> serviceCMissingDep = testListener.expectDependencyUninstall(serviceCName);
        Future<ServiceController<?>> serviceEStart = testListener.expectServiceStart(serviceEName);

        final ServiceController<?> serviceCController = serviceContainer.addService(serviceCName, Service.NULL).addDependency(serviceDName).install();
        final ServiceController<?> serviceEController = serviceContainer.addService(serviceEName, Service.NULL).addDependency(DependencyType.OPTIONAL, serviceGName).install();
        final ServiceController<?> serviceBController = serviceContainer.addService(serviceBName, Service.NULL).addAliases(serviceHName, serviceIName, serviceJName)
            .addDependency(serviceCName).addDependency(DependencyType.OPTIONAL, serviceEName).install();

        assertController(serviceBName, serviceBController);
        assertController(serviceBController, serviceBMissingDep);
        assertController(serviceCName, serviceCController);
        assertController(serviceCController, serviceCMissingDep);
        assertController(serviceEName, serviceEController);
        assertController(serviceEController, serviceEStart);

        // try to install service A
        try {
            serviceContainer.addService(serviceAName, Service.NULL).addAliases(serviceDName).addDependency(serviceBName).install();
            fail ("CircularDependencyException expected");
        } catch (CircularDependencyException e) {}

        // try to install service F
        try {
            serviceContainer.addService(serviceFName, Service.NULL).addAliases(serviceGName).addDependencies(serviceIName, serviceJName).install();
            fail ("CircularDependencyException expected");
        } catch (CircularDependencyException e) {}

        // stop service E
        final Future<ServiceController<?>> serviceEStop = testListener.expectServiceStop(serviceEName);
        serviceEController.setMode(Mode.NEVER);
        assertController(serviceEController, serviceEStop);
        // the cycle B, E, F is found two times, because service F depends twice on service B
        // (by depending both on I and J)

        // reactivate E
        serviceEStart = testListener.expectServiceStart(serviceEName);
        serviceEController.setMode(Mode.ACTIVE);
        // serviceE can still  start now as it is not yet connected to its optional dependency G, creating a 
        // circularity in the dependencies
        assertController(serviceEController, serviceEStart);

        // install service D, without aliases
        final FailToStartService serviceD = new FailToStartService(true);
        Future<ServiceController<?>> serviceBInstalledDep = testListener.expectDependencyInstall(serviceBName);
        Future<ServiceController<?>> serviceCInstalledDep = testListener.expectDependencyInstall(serviceCName);
        final Future<StartException> serviceDFailure = testListener.expectServiceFailure(serviceDName);
        final Future<ServiceController<?>> serviceBFailedDep = testListener.expectDependencyFailure(serviceBName);
        final Future<ServiceController<?>> serviceCFailedDep = testListener.expectDependencyFailure(serviceCName);
        final ServiceController<?> serviceDController = serviceContainer.addService(serviceDName, serviceD).install();
        assertController(serviceDName, serviceDController);
        assertController(serviceBController, serviceBInstalledDep);
        assertController(serviceCController, serviceCInstalledDep);
        assertFailure(serviceDController, serviceDFailure);
        assertController(serviceBController, serviceBFailedDep);
        assertController(serviceCController, serviceCFailedDep);

        final Future<ServiceController<?>> serviceBClearedDepFailure = testListener.expectDependencyFailureCleared(serviceBName);
        final Future<ServiceController<?>> serviceCClearedDepFailure = testListener.expectDependencyFailureCleared(serviceCName);
        serviceDController.setMode(Mode.NEVER);
        assertController(serviceBController, serviceBClearedDepFailure);
        assertController(serviceCController, serviceCClearedDepFailure);

        final Future<ServiceController<?>> serviceDStart = testListener.expectServiceStart(serviceDName);
        final Future<ServiceController<?>> serviceCStart = testListener.expectServiceStart(serviceCName);
        serviceDController.setMode(Mode.ACTIVE);
        assertController(serviceDController, serviceDStart);
        assertController(serviceCController, serviceCStart);

        final Future<ServiceController<?>> serviceBRemoval = testListener.expectServiceRemoval(serviceBName);
        serviceBController.setMode(Mode.REMOVE);
        assertController(serviceBController, serviceBRemoval);

        // install services I and J
        serviceEStart = testListener.expectServiceStart(serviceEName);
        final ServiceController<?> serviceIController = serviceContainer.addService(serviceIName, Service.NULL).addDependency(serviceJName).install();
        assertController(serviceIName, serviceIController);
        final ServiceController<?> serviceJController = serviceContainer.addService(serviceJName, Service.NULL).install();
        assertController(serviceJName, serviceJController);
    }
}
