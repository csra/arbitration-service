/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
			this.informer = Factory.getInstance().createInformer(AllocationService.SCOPE);
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
		}
	}

	public void update(String id) {
		if (functional()) {
			if (this.notifiers.containsKey(id)) {
				RemoteNotifier notifier = this.notifiers.get(id);
				notifier.update();
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
