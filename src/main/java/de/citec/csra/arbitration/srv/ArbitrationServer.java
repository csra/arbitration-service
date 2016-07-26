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
package de.citec.csra.arbitration.srv;

import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.Event;
import rsb.Factory;
import rsb.Informer;
import rsb.Listener;
import rsb.RSBException;
import rsb.converter.DefaultConverterRepository;
import rsb.converter.ProtocolBufferConverter;
import rsb.filter.OriginFilter;
import rsb.util.EventQueueAdapter;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.communicationpatterns.TaskStateType.TaskState;
import static rst.communicationpatterns.TaskStateType.TaskState.Origin.SUBMITTER;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class ArbitrationServer {

	static {
		DefaultConverterRepository.getDefaultConverterRepository()
				.addConverter(new ProtocolBufferConverter<>(TaskState.getDefaultInstance()));
		DefaultConverterRepository.getDefaultConverterRepository()
				.addConverter(new ProtocolBufferConverter<>(ResourceAllocation.getDefaultInstance()));
	}

	private final static Logger LOG = Logger.getLogger(ArbitrationServer.class.getName());
	private final static String SCOPEVAR = "SCOPE_ARBITRATION";
	private final static String FALLBACK = "/coordination/arbitration/";

	private static ArbitrationServer instance;
	private static String scope;

	private final Listener listener;
	private final Informer informer;
	private final BlockingQueue<Event> queue;

	private ArbitrationServer() throws InterruptedException, RSBException {
		EventQueueAdapter qa = new EventQueueAdapter();

		this.informer = Factory.getInstance().createInformer(getScope());
		this.listener = Factory.getInstance().createListener(getScope());
		this.listener.addHandler(qa, true);
		this.listener.addFilter(new OriginFilter(this.informer.getId(), true));
		this.queue = qa.getQueue();
	}

	public static String getScope() {
		if (scope == null) {
			if (System.getenv().containsKey(SCOPEVAR)) {
				scope = System.getenv(SCOPEVAR);
			} else {
				LOG.log(Level.WARNING, "using fallback scope ''{0}'', consider exporting ${1}", new String[]{FALLBACK, SCOPEVAR});
				scope = FALLBACK;
			}
		}
		return scope;
	}

	public static ArbitrationServer getInstance() throws InterruptedException, RSBException {
		if (instance == null) {
			instance = new ArbitrationServer();
		}
		return instance;
	}

	public void waitForShutdown() throws InterruptedException {
		LOG.log(Level.INFO, "Arbitration service listening at ''{0}''.", this.listener.getScope());
		while (this.listener.isActive()) {
			Event e = this.queue.take();
			if (e.getData() instanceof TaskState) {
				TaskState task = (TaskState) e.getData();
				if (task.getOrigin().equals(SUBMITTER)) {
					switch (task.getState()) {
						case INITIATED:
							try {
								TaskMonitor m = new TaskMonitor(task, e.getScope(), e.getId(), this.informer);
								m.activate();
							} catch (RSBException ex) {
								LOG.log(Level.SEVERE, "Could not allocate resources", ex);
							}
							break;
						default:
							//ignore, maintained by monitor
							break;
					}
				}
			}
		}
	}

	public void activate() throws RSBException {
		this.informer.activate();
		this.listener.activate();
	}

	public void deactivate() throws RSBException, InterruptedException {
		this.listener.deactivate();
		this.informer.deactivate();
	}
}
