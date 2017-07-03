/*
 * Copyright (C) 2017 pholthau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.citec.csra.allocation.cli;

import static de.citec.csra.allocation.cli.ExecutableResource.Completion.EXPIRE;
import static de.citec.csra.allocation.cli.ExecutableResource.Completion.MONITOR;
import static de.citec.csra.allocation.cli.ExecutableResource.Completion.RETAIN;
import static de.citec.csra.rst.util.IntervalUtils.currentTimeInMicros;
import java.util.concurrent.ExecutionException;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import rsb.RSBException;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy.MAXIMUM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority.NORMAL;

/**
 *
 * @author pholthau
 */
public class ContinuityTest {

	@BeforeClass
	public static void initServer() throws InterruptedException, RSBException {
		TestSetup.initServer();
	}

	@Test
	public void testImmediateRelease() throws RSBException, InterruptedException, ExecutionException {
		long duration = 200000;
		ExecutableResource<String> t = new ExecutableResource<String>("Blocker", MAXIMUM, NORMAL, SYSTEM, 0, duration, MICROSECONDS, EXPIRE, "immediate-resource") {
			@Override
			public String execute() throws ExecutionException, InterruptedException {
				return "computed result";
			}
		};
		long before = currentTimeInMicros();
		t.startup();
		t.getFuture().get();
		long after = currentTimeInMicros();
		long runtime = after - before;

		assertTrue("block time smaller than estimated duration", runtime < duration);
	}

	@Test
	public void testContinousAllocation() throws RSBException, InterruptedException, ExecutionException {
		long duration = 200000;
		ExecutableResource<String> t = new ExecutableResource<String>("Blocker", MAXIMUM, NORMAL, SYSTEM, 0, duration, MICROSECONDS, MONITOR, "monitored-resource") {
			@Override
			public String execute() throws ExecutionException, InterruptedException {
				return "computed result";
			}
		};
		long before = currentTimeInMicros();
		t.startup();
		t.getFuture().get();
		long after = currentTimeInMicros();
		long runtime = after - before;

		assertTrue("at least block for allocated time: " + runtime + ">=" + duration + "?", runtime >= duration);
	}

	@Test
	public void testLazyContinousAllocation() throws RSBException, InterruptedException, ExecutionException {
		long duration = 500000;
		ExecutableResource<String> t = new ExecutableResource<String>("Blocker", MAXIMUM, NORMAL, SYSTEM, 0, duration, MICROSECONDS, RETAIN, "retained-resource") {
			@Override
			public String execute() throws ExecutionException, InterruptedException {
				return "computed result";
			}
		};
		long before = currentTimeInMicros();
		t.startup();
		t.getFuture().get();
		long after = currentTimeInMicros();

		long remaining = t.getRemote().getRemainingTime();
		long runtime = after - before;

		assertTrue("there is time remaining", remaining > 0);
		assertTrue("remaining time smaller than allocated", remaining <= duration);
//		assertTrue("remaining time plus runtime larger than allocated time", runtime + remaining >= duration);
	}
}
