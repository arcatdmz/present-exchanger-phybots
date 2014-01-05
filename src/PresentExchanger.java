import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.phybots.Phybots;
import com.phybots.gui.DrawableFrame;
import com.phybots.hakoniwa.Hakoniwa;
import com.phybots.hakoniwa.HakoniwaEntity;
import com.phybots.hakoniwa.HakoniwaRobot;
import com.phybots.service.ServiceAbstractImpl;
import com.phybots.task.Move;
import com.phybots.task.Task;
import com.phybots.utils.Location;
import com.phybots.utils.Position;
import com.phybots.utils.ScreenPosition;

/**
 * Amida kuji.
 *
 * @author Jun KATO
 */
public class PresentExchanger {

	/** Default names */
	private String[] names = new String[] {
		"Honda",
		"Teramoto",
		"Kato",
		"Umetani",
		"Okamura",
		"Kenshi",
		"Yasuda",
		"Jim",
		"Erik",
		"Nakajima",
		"Takeo"
	};

	/** Colors used for drawing robots */
	final private Color[] colors = new Color[] {
		Color.black,
		Color.cyan,
		Color.blue,
		Color.orange,
		Color.red,
		Color.green,
		Color.magenta,
		Color.pink,
		Color.yellow
	};

	/** Hakoniwa service instance */
	private Hakoniwa hakoniwa;
	/** Robots */
	private List<HakoniwaRobot> robots;
	/** Robots not yet bound to people */
	private Set<HakoniwaRobot> unselectedRobots;

	/** Numbers of robots */
	private int size;
	/** Numbers of selected robots */
	private int selecting = 0;
	/** @see #isStarted() */
	private boolean isStarted = false;

	/** Index table for names of people */
	private int[] nameTable;
	/** Index table for goal positions */
	private int[] goalTable;

	/** Whether the user is binding robots to people */
	private boolean isSelecting() { return selecting < size; }
	/** Whether behaviors are already assigned to robots or not */
	private boolean isStarted() { return isStarted; }

	public static void main(String[] args) {
		new PresentExchanger(args);
	}

	public PresentExchanger(String[] args) {

		// Change default names if specified.
		if (args.length > 0) {
			names = args;
		}
		size = names.length;
		nameTable = new int[size];
		goalTable = new int[size];

		// Shuffle a list of people.
		List<Integer> people = new ArrayList<Integer>();
		for (int i = 0; i < size; i ++) { people.add(i); }
		Collections.shuffle(people);

		// Suppose all people stand in a queue.
		// They take present from their front person.
		for (int i = 0; i < size; i ++) {
			for (int j = 0; j < size; j ++) {
				if (people.get(j) == i) {
					goalTable[i] = people.get(j-1 >= 0 ? j-1 : size-1);
				}
			}
			nameTable[i] = -1;
		}

		initHakoniwa();
		initGUI();
	}

	private void initHakoniwa() {

		// Run hakoniwa service.
		hakoniwa = new Hakoniwa();
		hakoniwa.setAntialiased(true);
		hakoniwa.setViewportSize(1024, 600);
		hakoniwa.start();

		// Instantiate robots.
		robots = new ArrayList<HakoniwaRobot>();
		for (int i = 0; i < size; i ++) {
			final HakoniwaRobot robot = new HakoniwaRobot("robo:"+i,
					new Location(
							hakoniwa.getRealWidth()*(i+1)/(size+1),
							hakoniwa.getRealHeight()*7/8,
							-Math.PI/2));
			robot.setColor(colors[i%colors.length]);
			robots.add(robot);
		}
		unselectedRobots = new HashSet<HakoniwaRobot>(robots);
	}

	private void initGUI() {

		// Make and show a window for showing status of hakoniwa.
		final DrawableFrame frame = new PresentExchangerFrame();
		frame.setFrameSize(hakoniwa.getWidth(), hakoniwa.getHeight());
		// frame.setResizable(false);

		// Add mouse motion listener.
		frame.getPanel().addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				if (isStarted()) {
					updateMouseJoint(e.getX(), e.getY());
				}
			}
		});

		// Add mouse listener.
		frame.getPanel().addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (isStarted()) {
					updateMouseJoint(e.getX(), e.getY());
				}
			}
			@Override
			public void mouseReleased(MouseEvent e) {

				// Finished selecting robots.
				if (!isSelecting()) {
					if (!isStarted()) {
						start();
						return;
					}
					destroyMouseJoint();
					return;
				}

				// Get the nearest robot.
				HakoniwaRobot nearestRobot = getNearestRobot(
						hakoniwa.screenToReal(new ScreenPosition(
								e.getX(), e.getY())),
						unselectedRobots);
				unselectedRobots.remove(nearestRobot);

				// Pair the robot with the name.
				for (int j = 0; j < size; j ++) {
					if (robots.get(j).equals(nearestRobot)) {
						nameTable[j] = selecting ++;
						break;
					}
				}
			}
		});

		// Repaint the window periodically.
		new ServiceAbstractImpl() {
			private static final long serialVersionUID = -7271397592357355157L;
			@Override
			public void run() {
				frame.repaint();
			}
		}.start();
	}

	private void updateMouseJoint(int x, int y) {
		final Position p = hakoniwa.screenToReal(new ScreenPosition(x, y));
		HakoniwaEntity jointedEntity;
		if (hakoniwa.hasMouseJoint()) {
			jointedEntity = hakoniwa.getMouseJointedEntity();
		} else {
			final HakoniwaRobot robot = getNearestRobot(p, robots);
			final Position position = hakoniwa.getPosition(robot);
			if (position.distance(p) > robot.getRadius()) {
				final double direction = position.getRelativeDirection(p);
				p.set(
						position.getX() + Math.cos(direction)*robot.getRadius(),
						position.getY() + Math.sin(direction)*robot.getRadius());
			}
			jointedEntity = robot;
		}
		hakoniwa.updateMouseJoint(p, jointedEntity);
	}

	private void destroyMouseJoint() {
		hakoniwa.destroyMouseJoint();
	}

	/**
	 * Start robots to move to the goals.
	 */
	private void start() {
		for (int i = 0; i < size; i ++) {
			final Task move = new Move(
					hakoniwa.getRealWidth()*(goalTable[i]+1)/(size+1),
					hakoniwa.getRealHeight()/8);
			move.assign(robots.get(i));
			move.start();
		}
		isStarted = true;
	}

	/**
	 * Get the nearest robot from the specified position in the specified collection.
	 */
	private HakoniwaRobot getNearestRobot(Position p, Collection<HakoniwaRobot> robots) {
		double nearestDistanceSq = Double.MAX_VALUE;
		HakoniwaRobot nearestRobot = null;
		for (HakoniwaRobot robot : robots) {
			double distance = hakoniwa.getPosition(robot).distanceSq(p);
			if (distance < nearestDistanceSq) {
				nearestRobot = robot;
				nearestDistanceSq = distance;
			}
		}
		return nearestRobot;
	}

	/**
	 * Java frame to show the status of hakoniwa.
	 */
	private class PresentExchangerFrame extends DrawableFrame {
		private static final long serialVersionUID = 8178535735488594688L;
		private Stroke stroke;
		private GeneralPath goalPath;

		@Override public void dispose() {
			super.dispose();
			Phybots.getInstance().dispose();
		}

		@Override
		public void paint2D(Graphics2D g) {
			g.setRenderingHint(
					RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(
					RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			// Prepare for drawing shapes for the first time.
			if (stroke == null) {
				stroke = new BasicStroke(5,
						BasicStroke.CAP_SQUARE,
						BasicStroke.JOIN_ROUND);
				goalPath = new GeneralPath();
				goalPath.moveTo(-5f, -5f);
				goalPath.lineTo(5f, 5f);
				goalPath.moveTo(-5f, 5f);
				goalPath.lineTo(5f, -5f);
			}

			// Clear the screen.
			g.setColor(Color.white);
			g.fillRect(0, 0, getFrameWidth(), getFrameHeight());

			// Show message while selecting robots.
			g.setColor(Color.black);
			if (isSelecting()) {
				g.drawString(
						"Please select your robot by clicking, "+names[selecting]+".",
						10, 18);
			}

			// Draw goals.
			g.setStroke(stroke);
			g.setColor(Color.lightGray);
			for (int i = 0; i < size; i ++) {
				final int x = hakoniwa.getWidth()*(i+1)/(size + 1);
				final int y = hakoniwa.getHeight()*7/8;
				drawGoal(g, x, y);
			}

			// Draw robots and their owners names.
			for (int i = 0; i < size; i ++) {
				final int x = hakoniwa.getWidth()*(i+1)/(size + 1);
				final int y = hakoniwa.getHeight()*7/8;
				if (nameTable[i] >= 0) {

					// Name of the owner of the goal.
					g.setColor(Color.gray);
					g.drawString("Present from", x-20, y+25);
					g.drawString(names[nameTable[i]], x-20, y+38);

					// Name of the robot.
					final ScreenPosition scrPosition =
						hakoniwa.realToScreen(
								hakoniwa.getPosition(robots.get(i)));
					g.setColor(Color.black);
					g.drawString(names[nameTable[i]],
							scrPosition.getX()+18,
							scrPosition.getY()+5);
				}
			}
			hakoniwa.drawImage(g);
		}

		/**
		 * Draw goal shape at the specified position in the specified graphics context.
		 */
		private void drawGoal(Graphics2D g, int x, int y) {
			final AffineTransform af = g.getTransform();
			g.translate(x, y);
			g.draw(goalPath);
			g.setTransform(af);
		}
	}
}
