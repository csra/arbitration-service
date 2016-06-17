/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.allocation.srv;

import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.Factory;
import rsb.Listener;
import rsb.RSBException;
import rsb.converter.DefaultConverterRepository;
import rsb.converter.ProtocolBufferConverter;
import rsb.filter.OriginFilter;
import rsb.util.QueueAdapter;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.communicationpatterns.TaskStateType;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class AllocationServer {

	static {
		DefaultConverterRepository.getDefaultConverterRepository()
				.addConverter(new ProtocolBufferConverter<>(ResourceAllocation.getDefaultInstance()));
		DefaultConverterRepository.getDefaultConverterRepository()
				.addConverter(new ProtocolBufferConverter<>(TaskStateType.TaskState.getDefaultInstance()));
	}

	public static final String SCOPE = "/allocation";
	private final static Logger LOG = Logger.getLogger(AllocationServer.class.getName());
	private final Listener listener;
	private final BlockingQueue<ResourceAllocation> queue;
	private final AllocationService service;

	public AllocationServer() throws InterruptedException, RSBException {
		QueueAdapter<ResourceAllocation> qa = new QueueAdapter<>();

		this.service = new AllocationService();
		this.listener = Factory.getInstance().createListener(SCOPE);
		this.listener.addFilter(new OriginFilter(NotificationService.getInstance().getID(), true));
		this.listener.addHandler(qa, true);
		this.queue = qa.getQueue();
	}

	public void waitForShutdown() throws InterruptedException {
		while (this.listener.isActive()) {
			ResourceAllocation a = this.queue.take();
			switch (a.getState()) {
				case REQUESTED:
					service.requested(a);
					break;
				case CANCELLED:
				case ABORTED:
				case RELEASED:
					service.released(a);
					break;
				case ALLOCATED:
				case REJECTED:
				case SCHEDULED:
					LOG.log(Level.WARNING, "''{0}'' illegal request ''{1}'', ignoring ({2})", new String[]{a.getId(), a.getState().name(), a.toString().replaceAll("\n", " ")});
					break;
			}

		}
	}

	public void activate() throws RSBException {
		this.listener.activate();
		LOG.log(Level.INFO, "RSB Communication activated.");
	}

	public void deactivate() throws RSBException, InterruptedException {
		this.listener.deactivate();
		LOG.log(Level.INFO, "RSB Communication deactivated.");
	}
}
