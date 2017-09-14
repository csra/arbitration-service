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
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;
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
		publish();
		synchronized (monitor) {
			monitor.notify();
		}
	}

	private void publish() {
		ResourceAllocation allocation = Allocations.getInstance().get(id);
		try {
			if (allocation != null) {
				LOG.log(Level.INFO, "Publish allocation: {0}", shortString(allocation));
				this.informer.publish(allocation);
			} else {
				LOG.log(Level.WARNING, "Publish allocation with id ''{0}'' ignored, no such allocation available", id);
			}
		} catch (RSBException ex) {
			LOG.log(Level.SEVERE, "Could not publish current allocation '" + shortString(allocation) + "'", ex);
		}
	}

	@Override
	public void run() {
		try {

			State initial = Allocations.getInstance().getState(id);
			if (initial == null) {
				LOG.log(Level.WARNING, "No initial state found: ''{0}'', discarding  id ''{1}''", new Object[]{initial, id});
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
					}
					if (!confirmState(SCHEDULED, WARNING)) {
						Allocations.getInstance().setState(this.id, REJECTED);
						publish();
						return;
					}
					break;
				case SCHEDULED:
					break;
				default:
					LOG.log(Level.WARNING, "Illegal initial state ''{0}'', discarding id ''{1}'': already monitored?", initial);
					return;
			}

//			wait for slot to begin
			long wait;
			Interval slot;
			while ((slot = getSlot()) != null && ((wait = slot.getBegin().getTime()) - currentTimeInMicros()) > 0) {
				try {
					synchronized (monitor) {
						monitor.wait(wait / 1000, (int) ((wait % 1000) * 1000));
					}
					if (!confirmState(SCHEDULED, FINE)) {
						return;
					}
				} catch (InterruptedException ex) {
					ex.printStackTrace();
					interrupted();
					return;
				}
			}

			Allocations.getInstance().setState(id, ALLOCATED);
			publish();

//			wait for slot to end
			while ((slot = getSlot()) != null && ((wait = slot.getEnd().getTime()) - currentTimeInMicros()) > 0) {
				try {
					synchronized (monitor) {
						monitor.wait(wait / 1000, (int) ((wait % 1000) * 1000));
					}
					if (!confirmState(ALLOCATED, FINE)) {
						return;
					}
				} catch (InterruptedException ex) {
					ex.printStackTrace();
					interrupted();
				}
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

	private boolean confirmState(State state, Level level) {
		State current = Allocations.getInstance().getState(id);
		boolean confirmed = current != null && current.equals(state);
		if (!confirmed) {
			LOG.log(level, "Could not confirm state ''{0}'' for id ''{1}'': Current state is ''{2}''.", new Object[]{state, id, current});
		}
		return confirmed;
	}
}
