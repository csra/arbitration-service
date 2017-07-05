/*
 * Copyright (C) 2017 Patrick Holthaus
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

import de.citec.csra.rst.util.IntervalUtils;
import de.citec.csra.rst.util.StringRepresentation;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.RSBException;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy.MAXIMUM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority.NORMAL;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.ALLOCATED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.RELEASED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.REQUESTED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.SCHEDULED;

/**
 *
 * @author Patrick Holthaus
 */
public class StressTest {

	private static final long TIMEOUT = 2000;
	private final static int RESOURCES = 15;
	private final static int LENGTH = 100;

	public static void main(String[] args) {

		TestSetup.initServer();

		StringRepresentation.setIntervalOrigin(-1);
		boolean success = true;
		int nr = 0;
		while (success) {
			try {
				AllocatableResource res = null;
				for (int i = 0; i < RESOURCES; i++) {
					res = new AllocatableResource("Single#" + i, MAXIMUM, NORMAL, SYSTEM, LENGTH * i, LENGTH * RESOURCES, MILLISECONDS, i + "-single-resource-" + i);
					try {
						res.startup();
//					long begin = IntervalUtils.currentTimeInMicros();
//					long now = IntervalUtils.currentTimeInMicros();
//					System.out.println("after str: " + (now - begin));
//					begin = now;
//					res.await(TIMEOUT, MILLISECONDS, REQUESTED);
//					now = IntervalUtils.currentTimeInMicros();
//					System.out.println("after req: " + (now - begin));
//					begin = now;
//					res.await(TIMEOUT, MILLISECONDS, SCHEDULED);
//					now = IntervalUtils.currentTimeInMicros();
//					System.out.println("after sch: " + (now - begin));
//					begin = now;
//					res.await(TIMEOUT, MILLISECONDS, ALLOCATED);
//					now = IntervalUtils.currentTimeInMicros();
//					System.out.println("after all: " + (now - begin));
//					begin = now;
//					res.await(TIMEOUT, MILLISECONDS, RELEASED);
//					now = IntervalUtils.currentTimeInMicros();
////				System.out.println("after rel: " + (now - begin));
//					begin = now;
//
//					System.out.println("done\n\n\n\n\n\n\n\n\n");
//					Thread.sleep(500);

					} catch (Exception ex) {
						Logger.getLogger(StressTest.class.getName()).log(Level.SEVERE, "an error occurred: " + res, ex);
						success = false;
					}
				}
				long begin = IntervalUtils.currentTimeInMicros();
				long now = IntervalUtils.currentTimeInMicros();
				System.out.println("after str: " + (now - begin));
				begin = now;
				res.await(TIMEOUT, MILLISECONDS, REQUESTED);
				now = IntervalUtils.currentTimeInMicros();
				System.out.println("after req: " + (now - begin));
				begin = now;
				res.await(TIMEOUT, MILLISECONDS, SCHEDULED);
				now = IntervalUtils.currentTimeInMicros();
				System.out.println("after sch: " + (now - begin));
				begin = now;
				res.await(TIMEOUT, MILLISECONDS, ALLOCATED);
				now = IntervalUtils.currentTimeInMicros();
				System.out.println("after all: " + (now - begin));
				begin = now;
				res.await(TIMEOUT, MILLISECONDS, RELEASED);
				now = IntervalUtils.currentTimeInMicros();
//				System.out.println("after rel: " + (now - begin));
				begin = now;

				System.out.println("done\n\n\n\n\n\n\n\n\n");
//				Thread.sleep(500);
			} catch (InterruptedException | TimeoutException ex) {
				Logger.getLogger(StressTest.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	}

}
