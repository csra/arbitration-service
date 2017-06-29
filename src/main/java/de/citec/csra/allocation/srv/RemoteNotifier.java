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

import static de.citec.csra.rst.util.IntervalUtils.currentTimeInMicros;
import static de.citec.csra.rst.util.StringRepresentation.shortString;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.Informer;
import rsb.RSBException;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.*;
import rst.timing.IntervalType.Interval;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class RemoteNotifier implements Runnable {

	private final static Logger LOG = Logger.getLogger(RemoteNotifier.class.getName());

	private final Informer informer;
	private final String id;
	private final Object monitor = new Object();

	public RemoteNotifier(Informer informer, String id) {
		this.informer = informer;
		this.id = id;
	}

	private Interval getSlot() {
		return Allocations.getInstance().getSlot(id);
	}

	public void update() {
		synchronized (monitor) {
			publish();
			monitor.notify();
		}
	}

	private void publish() {
		ResourceAllocation allocation = Allocations.getInstance().get(id);
		try {
			LOG.log(Level.INFO, "Publish allocation: {0}", shortString(allocation));
			this.informer.publish(allocation);
		} catch (RSBException | NullPointerException ex) {
			LOG.log(Level.SEVERE, "could not publish current allocation status '" + shortString(allocation) + "'", ex);
		}
	}

	@Override
	public void run() {
		try {

			State initial = Allocations.getInstance().getState(id);
			if (initial == null) {
				return;
			}

			switch (initial) {
				case REQUESTED:
					synchronized (monitor) {
						try {
							monitor.wait(2000);
						} catch (InterruptedException ex) {
							interrupted();
						}
						if (!confirmState(SCHEDULED)) {
							Allocations.getInstance().setState(this.id, REJECTED);
							publish();
							return;
						}
					}
					break;
				case SCHEDULED:
					break;
				default:
					LOG.log(Level.WARNING, "Illegal initial state ''{0}'', aborting.", initial);
					return;
			}

			synchronized (monitor) {
				long delay;
				while ((delay = getSlot().getBegin().getTime() - currentTimeInMicros()) > 0) {
					try {
						monitor.wait(delay / 1000, (int) ((delay % 1000) * 1000));
					} catch (InterruptedException ex) {
						interrupted();
						return;
					}
					if (!confirmState(SCHEDULED)) {
						return;
					}
				}
			}

			if (!confirmState(SCHEDULED)) {
				return;
			}

			Allocations.getInstance().setState(id, ALLOCATED);
			publish();

			synchronized (monitor) {
				long remaining;
				while ((remaining = getSlot().getEnd().getTime() - currentTimeInMicros()) > 0) {
					try {
						monitor.wait(remaining / 1000, (int) ((remaining % 1000) * 1000));
					} catch (InterruptedException ex) {
						interrupted();
					}
					if (!confirmState(ALLOCATED)) {
						return;
					}
				}
			}

			if (!confirmState(ALLOCATED)) {
				return;
			}

			Allocations.getInstance().setState(id, RELEASED);
			publish();
			Allocations.getInstance().remove(id);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void interrupted() {

		State current = Allocations.getInstance().getState(id);
		if (current == null) {
			return;
		}

		State action = null;
		switch (current) {
			case REQUESTED:
				action = REJECTED;
				break;
			case SCHEDULED:
				action = CANCELLED;
				break;
			case ALLOCATED:
				action = ABORTED;
				break;
			default:
				break;
		}

		LOG.log(Level.WARNING, "''{1}'' interrupted in state ''{0}'', shutting down.", new String[]{current.name(), id});
		if (action != null) {
			LOG.log(Level.WARNING, "Setting state to ''{0}''.", action.name());
			Allocations.getInstance().setState(id, action);
			publish();
			Allocations.getInstance().remove(id);
		}
		Thread.currentThread().interrupt();
	}

	private boolean confirmState(State state) {
		State current = Allocations.getInstance().getState(id);
		return current != null && current.equals(state);
	}
}
