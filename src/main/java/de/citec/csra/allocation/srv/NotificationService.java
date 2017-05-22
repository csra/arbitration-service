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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.Factory;
import rsb.Informer;
import rsb.ParticipantId;
import rsb.RSBException;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class NotificationService {

	private final Map<String, RemoteNotifier> notifiers = new HashMap<>();
	private final Map<String, Future> futures = new HashMap<>();
	private final static Logger LOG = Logger.getLogger(NotificationService.class.getName());
	private final ExecutorService exec = Executors.newCachedThreadPool();
	private Informer informer;
	private ParticipantId participant;

	private static NotificationService instance;

	public static NotificationService getInstance() {
		if (instance == null) {
			instance = new NotificationService();
		}
		return instance;
	}

	private NotificationService() {
		try {
			this.informer = Factory.getInstance().createInformer(AllocationServer.getScope());
			this.informer.activate();
			this.participant = this.informer.getId();
		} catch (RSBException ex) {
			LOG.log(Level.SEVERE, "RSB communication failed", ex);
			this.informer = null;
		}
	}

	public ParticipantId getID() {
		return this.participant;
	}

	private boolean functional() {
		return this.informer != null && this.informer.isActive();
	}

	public void init(String id) {
		if (functional()) {
			RemoteNotifier r = new RemoteNotifier(this.informer, id);
			this.notifiers.put(id, r);
			this.futures.put(id, this.exec.submit(r));
			r.update();
		}
	}

	public void update(String id, boolean publish) {
		if (functional()) {
			if (this.notifiers.containsKey(id)) {
				RemoteNotifier notifier = this.notifiers.get(id);
				if(publish){
					notifier.update();
				}
				if (!Allocations.getInstance().isAlive(id)) {
					if (this.futures.containsKey(id)) {

						Future f = this.futures.get(id);
						f.cancel(false);

						this.notifiers.remove(id);
						this.futures.remove(id);
					}
				}
			} else {
				LOG.log(Level.WARNING, "attempt to update notifier for allocation ''{0}'' ignored, no such allocation available", id);
			}
		} else {
			LOG.log(Level.WARNING, "attempt to update notifier for allocation ''{0}'' ignored, RSB communication not available", id);
		}
	}
}
