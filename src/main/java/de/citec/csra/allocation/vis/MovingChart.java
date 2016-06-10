package de.citec.csra.allocation.vis;

import de.citec.csra.allocation.srv.AllocationService;
import java.awt.BasicStroke;
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
import javax.swing.JPanel;
import javax.swing.Timer;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.gantt.TaskSeries;
import org.jfree.data.gantt.TaskSeriesCollection;
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
import rsb.Listener;
import rsb.converter.DefaultConverterRepository;
import rsb.converter.ProtocolBufferConverter;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State;
import rst.timing.IntervalType.Interval;

/**
 * An example to show how we can create a dynamic chart.
 */
public class MovingChart extends ApplicationFrame implements ActionListener, Handler {

	static {
		DefaultConverterRepository.getDefaultConverterRepository()
				.addConverter(new ProtocolBufferConverter<>(ResourceAllocation.getDefaultInstance()));
		DefaultConverterRepository.getDefaultConverterRepository()
				.addConverter(new ProtocolBufferConverter<>(Interval.getDefaultInstance()));
	}

	/**
	 * The time series data.
	 */
	private final TimeSeries plustime;
	private final TimeSeries currenttime;
	private final int PAST = 10000;
	private final int FUTURE = 60000;
	/**
	 * Timer to refresh graph after every 1/4th of a second
	 */
	private final Timer timer = new Timer(250, this);
	TimeSeriesCollection dataset = new TimeSeriesCollection();
	TaskSeriesCollection categories = new TaskSeriesCollection();

	private int events = 1;

	Map<String, TimeSeries> sers = new HashMap<>();
	Map<String, TaskSeries> tasks = new HashMap<>();
	Map<String, Long> values = new HashMap<>();
	final JFreeChart chart;

	/**
	 * Constructs a new dynamic chart application.
	 *
	 * @param title the frame title.
	 */
	public MovingChart(final String title) {

		super(title);
		this.plustime = new TimeSeries("+" + FUTURE / 1000 + "s");
		this.currenttime = new TimeSeries("Current");

		this.dataset.addSeries(this.plustime);
		this.dataset.addSeries(this.currenttime);

		this.chart = createChart(this.dataset);
//		this.timer.setInitialDelay(1000);
		this.plustime.addOrUpdate(new Millisecond(new Date(System.currentTimeMillis() - PAST)), 0);
		this.currenttime.addOrUpdate(new Millisecond(new Date(System.currentTimeMillis() - PAST)), 0.01);

		//Sets background color of chart
		chart.setBackgroundPaint(Color.LIGHT_GRAY);

		//Created JPanel to show graph on screen
		final JPanel content = new JPanel(new BorderLayout());

		//Created Chartpanel for chart area
		final ChartPanel chartPanel = new ChartPanel(chart);

		//Added chartpanel to main panel
		content.add(chartPanel);

		//Sets the size of whole window (JPanel)
		chartPanel.setPreferredSize(new java.awt.Dimension(800, 500));

		//Puts the whole content on a Frame
		setContentPane(content);

		XYLineAndShapeRenderer r = (XYLineAndShapeRenderer) this.chart.getXYPlot().getRendererForDataset(dataset);
		r.setSeriesPaint(0, Color.BLACK);
		r.setSeriesPaint(1, Color.BLUE);

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
				true,
				true,
				false
		);

		final XYPlot plot = result.getXYPlot();

		plot.setBackgroundPaint(new Color(0xf8f8ed));
		plot.setDomainGridlinesVisible(true);
		plot.setDomainGridlinePaint(Color.lightGray);
		plot.setRangeGridlinesVisible(true);
		plot.setRangeGridlinePaint(Color.lightGray);

		ValueAxis xaxis = plot.getDomainAxis();
		xaxis.setAutoRange(true);
		xaxis.setTickLabelsVisible(false);
		//Domain axis would show data of 60 seconds for a time
		xaxis.setFixedAutoRange(PAST + FUTURE);  // 60 seconds
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
				ap.append(id);
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

		this.plustime.addOrUpdate(new Millisecond(new Date(System.currentTimeMillis() + FUTURE)), 0);
		this.currenttime.addOrUpdate(new Millisecond(new Date(System.currentTimeMillis())), 0.01);
		List<TimeSeries> ts = this.dataset.getSeries();
		List<TimeSeries> del = new LinkedList<>();
		long now = System.currentTimeMillis();
		int active = 0;
		for (TimeSeries t : ts) {
			if (!t.equals(this.plustime) && !t.equals(this.currenttime)) {
				List<TimeSeriesDataItem> its = t.getItems();
				long last = 0;
				for (TimeSeriesDataItem it : its) {
					long end = it.getPeriod().getLastMillisecond();
					if (end > last) {
						last = end;
					}
				}
				if (now - last > PAST) {
					del.add(t);
				} else {
					active++;
				}
			}
		}
		if (active == 0) {
			for (TimeSeries d : del) {
				this.dataset.removeSeries(d);
			}
			if (del.size() > 0) {
				this.chart.getXYPlot().setRenderer(new XYLineAndShapeRenderer(true, false));
				XYLineAndShapeRenderer r = (XYLineAndShapeRenderer) this.chart.getXYPlot().getRendererForDataset(dataset);
				r.setSeriesPaint(0, Color.BLACK);
				r.setSeriesPaint(1, Color.BLUE);
			}
		}
	}

	/**
	 * Starting point for the dynamic graph application.
	 *
	 * @param args ignored.
	 */
	public static void main(final String[] args) throws Exception {

		final MovingChart demo = new MovingChart("Resource Allocation Chart");
		Listener l = Factory.getInstance().createListener(AllocationService.SCOPE);
		l.addHandler(demo, true);

		demo.pack();
		RefineryUtilities.centerFrameOnScreen(demo);
		l.activate();
		demo.setVisible(true);

	}

	public void updateDataPoints(String id, String resource, long start, long end, State state) {
		TimeSeries series = this.dataset.getSeries(id);
		if (series == null) {
			series = new TimeSeries(id);
			this.dataset.addSeries(series);
		}

		int stroke = -1;
		Color c = null;
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
				r.setSeriesStroke(number, new BasicStroke(stroke));
			}
			if (c != null) {
				r.setSeriesPaint(number, c);
			}
		}
		long channel;
		if (values.containsKey(resource)) {
			channel = values.get(resource);
		} else {
			channel = events++;
			values.put(resource, channel);
		}

		series.clear();
		series.addOrUpdate(new Millisecond(new Date(start)), channel);
		series.addOrUpdate(new Millisecond(new Date(end)), channel);

	}

	@Override
	public void internalNotify(Event event) {
		if (event.getData() instanceof ResourceAllocation) {
			ResourceAllocation update = (ResourceAllocation) event.getData();
			long start = update.getSlot().getBegin().getTime();
			long end = update.getSlot().getEnd().getTime();
			for (String resource : update.getResourceIdsList()) {
				String label = update.getDescription().replaceAll(":.*", "") + " (" + update.getId().substring(0, 4) + ")";
				updateDataPoints(label, resource, start, end, update.getState());
			}

		}
	}

}
