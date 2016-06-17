/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
	private final Listener listener;
	private final Informer informer;
	private final BlockingQueue<Event> queue;
	public static final String SCOPE = "/arbitration";

	public ArbitrationServer() throws InterruptedException, RSBException {
		EventQueueAdapter qa = new EventQueueAdapter();

		this.informer = Factory.getInstance().createInformer(SCOPE);
		this.listener = Factory.getInstance().createListener(SCOPE);
		this.listener.addHandler(qa, true);
		this.listener.addFilter(new OriginFilter(this.informer.getId(), true));
		this.queue = qa.getQueue();
	}

	public void waitForShutdown() throws InterruptedException {
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
		LOG.log(Level.INFO, "RSB communication activated at ''{0}''.", SCOPE);
	}

	public void deactivate() throws RSBException, InterruptedException {
		this.listener.deactivate();
		this.informer.deactivate();
		LOG.log(Level.INFO, "RSB communication deactivated ''{0}''.", SCOPE);
	}
}
