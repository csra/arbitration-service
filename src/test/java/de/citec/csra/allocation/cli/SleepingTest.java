/*
 * Copyright (C) 2016 Bielefeld University, Patrick Holthaus
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

import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.InitializeException;
import rsb.RSBException;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority;


/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class SleepingTest {

	public static void main(String[] args) throws InitializeException, InterruptedException, RSBException, ExecutionException {
		ExecutableResource<Void> sl = new ExecutableResource("descr", Policy.PRESERVE, Priority.NORMAL, Initiator.SYSTEM, 0, 2000, "/dev/urandom") {
			@Override
			public Object execute() throws ExecutionException, InterruptedException {
				try {
					System.out.println("Starting with " + remaining() + "ms.");
					long start = System.currentTimeMillis();
					long amount = 0;
					for(int i = 0; i < 20; i++){
						Thread.sleep(500);
						long now = System.currentTimeMillis();
						shiftTo(now);
						amount = now - start;
						System.out.println("Already slept for " + amount +  "ms. Time remaining: " + remaining() + "ms.");
					}
					Thread.sleep(remaining());
					return "Slept for " + (System.currentTimeMillis() - start) +  "ms in total.";
				} catch (InterruptedException ex) {
					Logger.getLogger(SleepingTest.class.getName()).log(Level.SEVERE, "client interrupted", ex);
					return "What a night 0_o";
				} catch (RSBException ex) {
					Logger.getLogger(SleepingTest.class.getName()).log(Level.SEVERE, "rsb communication failed", ex);
					return "Could not refresh allocation";
				}
			}
			
			@Override
			public void timeChanged(long remaining) {
				System.out.println("slice changed: " + remaining);
			}
		};
		sl.startup();
		System.out.println(sl.getFuture().get());
		sl.shutdown();
		RemoteAllocationService.getInstance().shutdown();
	}

}
