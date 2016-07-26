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
package de.citec.csra.allocation.srv;

import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.Informer;
import rsb.RSBException;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.*;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class RemoteNotifier implements Runnable {

	private final static Logger LOG = Logger.getLogger(RemoteNotifier.class.getName());

	private final Informer informer;
	private final long interval = 10;
	private final String id;

	public RemoteNotifier(Informer informer, String id) {
		this.informer = informer;
		this.id = id;
	}
	
	private ResourceAllocation get(){
		return Allocations.getInstance().get(id);
	}
	
	private boolean isAlive(){
		return Allocations.getInstance().isAlive(id);
	}

	public void update() {
		switch (get().getState()) {
			case REQUESTED:
				break;
			case REJECTED:
			case SCHEDULED:
			case ALLOCATED:
			case ABORTED:
			case CANCELLED:
			case RELEASED:
				publish();
				break;
			default:
				break;
		}
	}

	private void publish() {
		ResourceAllocation allocation = get();
		try {
			LOG.log(Level.INFO, "Publish allocation: {0}", allocation.toString().replaceAll("\n", " "));
			this.informer.publish(allocation);
		} catch (RSBException | NullPointerException ex) {
			LOG.log(Level.SEVERE, "could not publish current allocation status '" + allocation.toString().replaceAll("\n", " ") + "'", ex);
		}
	}


	@Override
	public void run() {
		try{
		if (!isAlive()) {
			return;
		}

		long max = System.currentTimeMillis() + 2000;
		boolean scheduled = false;
		while (System.currentTimeMillis() < max && !scheduled) {
			if (!isAlive()) {
				return;
			}
			scheduled = get().getState().equals(SCHEDULED);
			try {
				Thread.sleep(this.interval);
			} catch (InterruptedException ex) {
				LOG.log(Level.WARNING, "Interrupted");
				Thread.currentThread().interrupt();
			}
		}
		if (!scheduled) {
			Allocations.getInstance().setState(this.id, REJECTED);
			publish();
			return;
		}

		try {
			while ((scheduled = isAlive()) && System.currentTimeMillis() < get().getSlot().getBegin().getTime()) {
				Thread.sleep(this.interval);
			}
			if (!scheduled) {
				return;
			}
			Allocations.getInstance().setState(this.id, ALLOCATED);
			publish();

			try {
				while (isAlive() && System.currentTimeMillis() < get().getSlot().getEnd().getTime()) {
					Thread.sleep(this.interval);
				}
				if (!isAlive()) {
					return;
				}
				Allocations.getInstance().setState(this.id, RELEASED);
				publish();

			} catch (InterruptedException interex) {
				LOG.log(Level.WARNING, "Interrupted in ''{0}'' state, aborting: ", new String[]{get().getState().name(), get().toString().replaceAll("\n", " ")});
				Allocations.getInstance().setState(this.id, ABORTED);
				publish();
				Thread.currentThread().interrupt();
			}
		} catch (InterruptedException interex) {
			LOG.log(Level.WARNING, "Interrupted in ''{0}'' state, aborting: ", new String[]{get().getState().name(), get().toString().replaceAll("\n", " ")});
			Allocations.getInstance().setState(this.id, CANCELLED);
				publish();
			Thread.currentThread().interrupt();
		}
		}catch(Exception e){
			e.printStackTrace();
		}
	}

}
