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
package de.citec.csra.allocation.vis;

import de.citec.csra.allocation.srv.AllocationServer;
import de.citec.csra.rst.util.IntervalUtils;
import java.awt.BasicStroke;
import static java.awt.BasicStroke.CAP_BUTT;
import static java.awt.BasicStroke.JOIN_BEVEL;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.gantt.TaskSeries;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeSeriesDataItem;
import org.jfree.data.xy.XYDataset;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;
import rsb.Event;
import rsb.Factory;
import rsb.Handler;
import rsb.Informer;
import rsb.Listener;
import rsb.ParticipantId;
import rsb.RSBException;
import rsb.converter.DefaultConverterRepository;
import rsb.converter.ProtocolBufferConverter;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State;
import rst.communicationpatterns.TaskStateType;
import rst.timing.IntervalType.Interval;

/**
 * An example to show how we can create a dynamic chart.
 */
public class MovingChart extends ApplicationFrame implements ActionListener, Handler {

	private final static int DEFAULT_PAST = 60000;
	private final static int DEFAULT_FUTURE = 1200000;

	static {
		DefaultConverterRepository.getDefaultConverterRepository()
				.addConverter(new ProtocolBufferConverter<>(ResourceAllocation.getDefaultInstance()));
		DefaultConverterRepository.getDefaultConverterRepository()
				.addConverter(new ProtocolBufferConverter<>(Interval.getDefaultInstance()));
		DefaultConverterRepository.getDefaultConverterRepository()
				.addConverter(new ProtocolBufferConverter<>(TaskStateType.TaskState.getDefaultInstance()));
	}

	/**
	 * The time series data.
	 */
	private final TimeSeries plustime;
	private final int past;
	private final int future;

	/**
	 * Timer to refresh graph after every 1/4th of a second
	 */
	private final Timer timer = new Timer(100, this);
	private final TimeSeriesCollection dataset = new TimeSeriesCollection();
//	TaskSeriesCollection categories = new TaskSeriesCollection();

	private final ValueMarker marker;
	private int events = 1;

	Map<String, TimeSeries> sers = new HashMap<>();
	Map<String, TaskSeries> tasks = new HashMap<>();
	Map<String, Long> values = new HashMap<>();
	final JFreeChart chart;

	public MovingChart(final String title, int past, int future) {

		super(title);

		this.past = past;
		this.future = future;

		this.marker = new ValueMarker(System.currentTimeMillis());
		marker.setPaint(Color.black);

		this.plustime = new TimeSeries("+" + future / 1000 + "s");
		this.dataset.addSeries(this.plustime);
		this.chart = createChart(this.dataset);
//		this.timer.setInitialDelay(1000);
		this.plustime.addOrUpdate(new Millisecond(new Date(System.currentTimeMillis() - past)), 0);

		//Sets background color of chart
		chart.setBackgroundPaint(Color.LIGHT_GRAY);

		//Created JPanel to show graph on screen
		final JPanel content = new JPanel(new BorderLayout());

		//Created Chartpanel for chart area
		final ChartPanel chartPanel = new ChartPanel(chart);

		//Added chartpanel to main panel
		content.add(chartPanel);

		//Sets the size of whole window (JPanel)
		chartPanel.setPreferredSize(new java.awt.Dimension(1500, 600));

		//Puts the whole content on a Frame
		setContentPane(content);

		XYLineAndShapeRenderer r = (XYLineAndShapeRenderer) this.chart.getXYPlot().getRendererForDataset(dataset);
		r.setSeriesPaint(0, Color.BLACK);

		this.timer.start();

	}

	/**
	 * Creates a sample chart.
	 *
	 * @param dataset the dataset.
	 *
	 * @return A sample chart.
	 */
	private JFreeChart createChart(final XYDataset dataset) {
		final JFreeChart result = ChartFactory.createTimeSeriesChart(
				null,
				"Time",
				"Resource",
				dataset,
				false,
				true,
				false
		);

		final XYPlot plot = result.getXYPlot();

		plot.addDomainMarker(this.marker);
		plot.setBackgroundPaint(new Color(0xf8f8ed));
		plot.setDomainGridlinesVisible(true);
		plot.setDomainGridlinePaint(Color.lightGray);
		plot.setRangeGridlinesVisible(true);
		plot.setRangeGridlinePaint(Color.lightGray);

		ValueAxis xaxis = plot.getDomainAxis();
		xaxis.setAutoRange(true);
		xaxis.setTickLabelsVisible(false);
		//Domain axis would show data of 60 seconds for a time
		xaxis.setFixedAutoRange(this.past + this.future);  // 60 seconds
		xaxis.setVerticalTickLabels(true);
		ValueAxis yaxis = plot.getRangeAxis();
		yaxis.setAutoRangeMinimumSize(1.8);
		yaxis.setAutoRange(true);

		NumberAxis range = (NumberAxis) plot.getRangeAxis();
		range.setTickUnit(new NumberTickUnit(1));
		range.setNumberFormatOverride(new NumberFormat() {
			@Override
			public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
				return format((long) number, toAppendTo, pos);
			}

			private String getID(long number) {
				return values.entrySet().stream().filter(e -> e.equals(number)).findFirst().get().getKey();
			}

			@Override
			public StringBuffer format(long number, StringBuffer ap, FieldPosition pos) {
				String id = "N/A";
				if (number == 0) {
					id = "(Time)";
				}
				if (values.containsValue(number)) {
					for (Map.Entry<String, Long> entry : values.entrySet()) {
						if (entry.getValue() == number) {
							id = entry.getKey();
							break;
						}
					}
				}
				id = id.replaceFirst("/$", "");
				ap.append(id);
				if (id.length() > 32) {
					ap.replace(15, ap.length() - 15, "..");
				}
				return ap;
			}

			@Override
			public Number parse(String source, ParsePosition parsePosition) {
				return null;
			}
		});

//		this.chart.getXYPlot().getRenderer(1).set
//		XYLineAndShapeRenderer r = (XYLineAndShapeRenderer) this.chart.getXYPlot().getRendererForDataset(dataset);
		return result;
	}

	/**
	 * Generates an random entry for a particular call made by time for every
	 * 1/4th of a second.
	 *
	 * @param e the action event.
	 */
	@Override
	public void actionPerformed(final ActionEvent e) {

		this.plustime.addOrUpdate(new Millisecond(new Date(System.currentTimeMillis() + this.future)), 0);
		List<TimeSeries> ts = this.dataset.getSeries();
		List<TimeSeries> del = new LinkedList<>();
		long now = System.currentTimeMillis();
		marker.setValue(now);
		int active = 0;
		for (TimeSeries t : ts) {
			if (!t.equals(this.plustime)) {
				List<TimeSeriesDataItem> its = t.getItems();
				long last = 0;
				for (TimeSeriesDataItem it : its) {
					long end = it.getPeriod().getLastMillisecond();
					if (end > last) {
						last = end;
					}
				}
				if (now - last > this.past) {
					del.add(t);
				} else {
					active++;
				}
			}
		}

		synchronized (this.dataset) {
			if (active == 0) {
			for (TimeSeries d : del) {
				this.dataset.removeSeries(d);
			}
				if (del.size() > 0) {
				this.chart.getXYPlot().setRenderer(new XYLineAndShapeRenderer(true, false));
				XYLineAndShapeRenderer r = (XYLineAndShapeRenderer) this.chart.getXYPlot().getRendererForDataset(dataset);
				r.setSeriesPaint(0, Color.BLACK);
			}
		}
	}
	}

	/**
	 * Starting point for the dynamic graph application.
	 *
	 * @param args ignored.
	 */
	public static void main(final String[] args) throws InterruptedException, RSBException {

		int past = DEFAULT_PAST;
		int future = DEFAULT_FUTURE;

		if (args.length > 0) {
			if (args.length == 2) {
				try {
					past = Integer.valueOf(args[0]);
					future = Integer.valueOf(args[1]);
				} catch (IllegalArgumentException ex) {
					System.err.println("Could not read integer values for PAST or FUTURE.\nusage: csra-allocation-viewer [PAST FUTURE]");
					System.exit(1);
				}
			} else {
				System.err.println("usage: csra-allocation-viewer [PAST FUTURE]");
				System.exit(1);
			}
		}

		final MovingChart demo = new MovingChart("Resource Allocation Chart", past, future);
		Listener l = Factory.getInstance().createListener(AllocationServer.getScope());
		final Informer myInformer = Factory.getInstance().createInformer(AllocationServer.getScope());
		final Object monitor = new Object();

		final String queryId = UUID.randomUUID().toString();
		final String randomResource = UUID.randomUUID().toString();
		final ResourceAllocation query = ResourceAllocation.newBuilder().
				setDescription("server id query").
				setId(queryId).
				setSlot(IntervalUtils.buildRelativeRst(0, 10000, TimeUnit.MICROSECONDS)).
				setPolicy(Policy.FIRST).
				setPriority(Priority.NO).
				setState(State.REQUESTED).
				setInitiator(Initiator.SYSTEM).
				addResourceIds(randomResource).
				build();

		Handler filterInstall = (event) -> {
			boolean hasFilter = false;
			if (event.getData() instanceof ResourceAllocation) {
				ResourceAllocation response = (ResourceAllocation) event.getData();
				if (response.getId().equals(queryId)) {
					if (!event.getId().getParticipantId().equals(myInformer.getId())) {
						final ParticipantId serverId = event.getId().getParticipantId();
						if (!hasFilter) {
							l.addFilter((toFilter) -> {
								boolean ok = toFilter.getId().getParticipantId().equals(serverId);
								return ok;
							});
							hasFilter = true;
						}
						synchronized (monitor) {
							monitor.notify();
						}

					}
				}
			}
		};
		l.addHandler(filterInstall, true);

		l.activate();
		myInformer.activate();
		myInformer.publish(query);
		myInformer.deactivate();
		synchronized (monitor) {
			try {
				monitor.wait(5000);
			} catch (InterruptedException ex) {
				System.out.println("failed to install server filter, timeout");
			}
		}
		
		l.removeHandler(filterInstall, true);
		l.addHandler(demo, true);

		demo.pack();
		RefineryUtilities.centerFrameOnScreen(demo);
		demo.setVisible(true);

	}

	public void updateDataPoints(String id, String label, String resource, long start, long end, State state, Priority prio, boolean token) {
		synchronized (this.dataset) {
			TimeSeries series = this.dataset.getSeries(id);
			if (series == null) {
				series = new TimeSeries(id);
				this.dataset.addSeries(series);
			}

			series.setDomainDescription(label);
			int stroke = -1;
			Color c = null;

			boolean randomcolor = false;
			if (!randomcolor) {
				switch (prio) {
					case EMERGENCY:
						c = Color.RED;
						break;
					case URGENT:
						c = Color.ORANGE;
						break;
					case HIGH:
						c = Color.YELLOW;
						break;
					case NORMAL:
						c = Color.GREEN;
						break;
					case LOW:
						c = Color.BLUE;
						break;
					case NO:
						c = Color.BLACK;
						break;
				}
			}

			switch (state) {
				case REQUESTED:
					stroke = 1;
					break;
				case SCHEDULED:
					stroke = 3;
					break;
				case ALLOCATED:
					stroke = 9;
					break;
				case RELEASED:
					stroke = 1;
					break;
				case REJECTED:
				case CANCELLED:
				case ABORTED:
					c = Color.GRAY;
					stroke = 1;
					break;
			}

			XYLineAndShapeRenderer r = (XYLineAndShapeRenderer) this.chart.getXYPlot().getRendererForDataset(dataset);

			int number = -1;
			for (int i = 0; i < this.dataset.getSeries().size(); i++) {
				TimeSeries t = this.dataset.getSeries(i);
				if (t.getKey().equals(id)) {
					number = i;
				}
			}
			if (number > 0) {
				if (stroke > 0) {
					if (token) {
						r.setSeriesStroke(number, new BasicStroke(stroke, CAP_BUTT, JOIN_BEVEL, 1, new float[]{1.5f, .5f}, .5f));
					} else {
						r.setSeriesStroke(number, new BasicStroke(stroke, CAP_BUTT, JOIN_BEVEL, 1));
					}
				}
				if (c != null) {
					r.setSeriesPaint(number, c);
				}
			}

			long channel;
			String key = resource; //prio
			if (values.containsKey(key)) {
				channel = values.get(key);
			} else {
				channel = events++;
				values.put(key, channel);
			}

			if (!series.isEmpty()) {
				series.clear();
			}
			series.addOrUpdate(new Millisecond(new Date(start)), channel);
			series.addOrUpdate(new Millisecond(new Date(end)), channel);
		}
	}

	@Override
	public void internalNotify(Event event) {
		if (event.getData() instanceof ResourceAllocation) {
			ResourceAllocation update = (ResourceAllocation) event.getData();
			SwingUtilities.invokeLater(new Updater(update));
		}
	}

	private class Updater implements Runnable {

		private final ResourceAllocation update;

		public Updater(ResourceAllocation update) {
			this.update = update;
		}

		@Override
		public void run() {
			long start = update.getSlot().getBegin().getTime() / 1000;
			long end = update.getSlot().getEnd().getTime() / 1000;
			for (String resource : update.getResourceIdsList()) {
				String label = update.getDescription().replaceAll(":.*", "") + " (" + update.getId().substring(0, 4) + ")";
				updateDataPoints(update.getId() + ":" + resource, label, resource, start, end, update.getState(), update.getPriority(), update.getId().split("#").length > 1);

			}
		}
	}
}
